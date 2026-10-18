package com.halo.app.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.halo.app.ui.icons.HaloIcon
import com.halo.app.ui.icons.HaloGlyph
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.language.uiText
import com.halo.app.ui.fx.haloTrace
import com.halo.sdk.runtime.SpeechOutputFallback
import com.halo.sdk.runtime.SpeechOutputTarget
import com.halo.sdk.runtime.TtsRequested
import kotlinx.coroutines.*
import java.util.UUID

val LocalPrivateSpeech = compositionLocalOf { false }

private data class CloudReadConsent(val providerId: String, val providerName: String,
    val content: String)

/**
 * One explicit, reusable Voice/Text affordance for Study and Workspace editors.
 *
 * Selected local STT, never Android/OEM cloud recognition. A recognized utterance
 * remains an uncommitted preview until the user accepts it into the current draft.
 * Reading uses Halo's one canonical routed TtsRequested playback owner.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HaloVoiceTextActions(
    text: String,
    onText: (String) -> Unit,
    enabled: Boolean = true,
    allowDictation: Boolean = true,
    allowLongDictation: Boolean = false,
    guideDictation: Boolean = false,
    dictationRequest: Long = 0,
    onRequestConsumed: () -> Unit = {},
    onCommand: ((String) -> Unit)? = null,
    readText: String? = null,
    readActionLabel: String? = null,
    compactRead: Boolean = false,
    language: String? = null,
    maxChars: Int = 12000,
    appendDictation: Boolean = true,
    appendSeparator: String = "\n",
    applySearchImmediately: Boolean = false,
    spokenTransform: (String) -> String? = { it },
    previewIsValid: (String) -> Boolean = { true },
    fieldContent: (@Composable (@Composable () -> Unit) -> Unit)? = null,
) {
    // Design previews have no Android speech host. Keep the editable field available
    // without constructing microphone, playback or provider services.
    if (androidx.compose.ui.platform.LocalInspectionMode.current) {
        fieldContent?.invoke({})
        return
    }
    val fieldId = remember { UUID.randomUUID().toString() }
    val stopEpoch by VoiceFieldCancellation.epoch.collectAsState()
    var acceptedStopEpoch by remember { mutableLongStateOf(VoiceFieldCancellation.epoch.value) }
    var permissionEpoch by remember { mutableStateOf<Long?>(null) }
    val kidsPrivate by ManualDependencyGraph.kidsPreferences.enabled.collectAsState()
    val privateSpeech = LocalPrivateSpeech.current || kidsPrivate
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current
    val scope=rememberCoroutineScope()
    val latestText by rememberUpdatedState(text)
    val latestOnText by rememberUpdatedState(onText)
    val latestTransform by rememberUpdatedState(spokenTransform)
    var searchUndo by remember { mutableStateOf<Pair<String, String>?>(null) }
    var heard by remember { mutableStateOf<String?>(null) }
    var phase by remember { mutableStateOf("IDLE") }
    var requestLong by remember { mutableStateOf(false) }
    var longCapture by remember { mutableStateOf(false) }
    var longPaused by remember { mutableStateOf(false) }
    var longDone by remember { mutableStateOf(false) }
    var longTranscript by remember { mutableStateOf("") }
    var resumeLong by remember { mutableStateOf<CompletableDeferred<Unit>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<String?>(null) }
    var editedPreview by remember { mutableStateOf("") }
    var captureJob by remember { mutableStateOf<Job?>(null) }
    var finish by remember { mutableStateOf<CompletableDeferred<Unit>?>(null) }
    var spokenId by remember { mutableStateOf<String?>(null) }
    var spokenContent by remember { mutableStateOf<String?>(null) }
    var publishJob by remember { mutableStateOf<Job?>(null) }
    var microphonePermissionPending by remember { mutableStateOf(false) }
    var pendingCloudRead by remember { mutableStateOf<CloudReadConsent?>(null) }
    val localReady by ManualDependencyGraph.speechRecognitionAvailability.collectAsState()
    val output=remember { ManualDependencyGraph.haloSpeechOutputCoordinator }
    val reader = remember { ManualDependencyGraph.readAloud }
    val readingState by reader.state.collectAsState()
    val speechRegistry=remember { ManualDependencyGraph.speechSynthesisRegistry }

    fun publishReading(content: String, requiredProviderId: String, cloudApproved: Boolean = false) {
        VoiceFieldCancellation.stop()
        acceptedStopEpoch = VoiceFieldCancellation.epoch.value
        val requestId=UUID.randomUUID().toString()
        publishJob?.cancel()
        spokenId=requestId
        spokenContent=content
        publishJob=scope.launch {
            try {
                captureJob?.cancelAndJoin()
                currentCoroutineContext().ensureActive()
                if(speechRegistry.selector.ttsSelection.value.id!=requiredProviderId) {
                    error="Voice provider changed. Please choose Read aloud again."
                    if(spokenId==requestId) spokenId=null
                    return@launch
                }
                reader.start(requestId, content, requiredProviderId, cloudApproved, language)
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) {
                error="Reading aloud is unavailable with the current voice settings."
                if(spokenId==requestId) spokenId=null
            }
        }
    }

    fun cancelCapture() {
        finish?.complete(Unit)
        val job=captureJob
        if(job!=null && !job.isCompleted) {
            // A cancelled native recognition may need to finish its JNI call
            // and release the mic/ASR lease. Do not admit a new capture yet.
            phase="CANCELLING"
            job.cancel()
        } else {
            finish=null
            captureJob=null
            phase="IDLE"
        }
    }
    fun beginCapture(long: Boolean = requestLong) {
        // Runtime permission dialogs and app switches can temporarily pause Halo.
        // Never acquire the microphone from a late permission callback in background.
        if(!lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        if(phase!="IDLE" || !enabled || !allowDictation || !localReady) return
        VoiceFieldCancellation.stop()
        acceptedStopEpoch = VoiceFieldCancellation.epoch.value
        error=null
        pending=null
        phase="RECORDING"
        longCapture = long
        longPaused = false
        longDone = false
        longTranscript = ""
        // A deliberate microphone press interrupts any response, avoiding echo
        // and sharing the same local ASR/audio-input lease infrastructure.
        output.stop("Workspace dictation requested")
        val signal=CompletableDeferred<Unit>()
        finish=signal
        captureJob=scope.launch {
            var session: com.halo.app.speech.diagnostics.SpeechDiagnosticSession?=null
            try {
                val runner=ManualDependencyGraph.speechDiagnosticRunner
                val chunks = mutableListOf<String>()
                var accepted = false
                var count = 0
                val maxTakes = if (long) 8 else 1
                while (count < maxTakes) {
                    if (long && longPaused) {
                        phase = "PAUSED"
                        val resumed = CompletableDeferred<Unit>()
                        resumeLong = resumed
                        resumed.await()
                        resumeLong = null
                    }
                    if (long && longDone) break
                    phase = "RECORDING"
                    val take = if (count == 0) signal else CompletableDeferred<Unit>()
                    finish = take
                    val captured = runner.captureSession(take, maxDurationMs = 15_000)
                    session = captured
                    currentCoroutineContext().ensureActive()
                    phase = "RECOGNIZING"
                    val result = runner.recognizeSelected(captured)
                    currentCoroutineContext().ensureActive()
                    captured.close(); session = null
                    val heardText = result.recognition?.text?.trim().orEmpty()
                    accepted = result.gate == "Accepted" && heardText.isNotBlank()
                    if (!accepted) break
                    chunks += heardText
                    longTranscript = chunks.joinToString("\n")
                    count++
                    if (!long || longDone || longTranscript.length >= maxChars) break
                }
                if (!accepted && chunks.isNotEmpty()) error = "The last take was unclear. Review the text that was heard."
                val utterance = chunks.joinToString("\n")
                when {
                    utterance.isBlank() ->
                        error="Speech was not clear. You can try again or type instead."
                    else -> {
                        if (utterance.length > maxChars) error = "Speech text is too long for this field."
                        val transformed = latestTransform(utterance)
                        heard = utterance
                        editedPreview = transformed ?: utterance
                        if (onCommand != null) onCommand(utterance)
                        else if (applySearchImmediately && transformed != null) {
                            searchUndo = latestText to transformed
                            latestOnText(transformed)
                        } else pending = utterance
                    }
                }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) {
                error="Voice input unavailable. Check your microphone and local speech model."
                if (longTranscript.isNotBlank()) { pending = longTranscript; editedPreview = longTranscript }
            } finally {
                withContext(NonCancellable) { session?.close() }
                finish=null
                resumeLong=null
                longCapture=false
                captureJob=null
                phase="IDLE"
            }
        }
    }

    LaunchedEffect(stopEpoch) {
        if (stopEpoch != acceptedStopEpoch) {
            microphonePermissionPending = false
            pending = null
            pendingCloudRead = null
            cancelCapture()
            publishJob?.cancel()
            spokenId?.let(output::cancelScopedTransaction)
            spokenId = null
        }
    }

    DisposableEffect(phase, pending, pendingCloudRead, longCapture) {
        VoiceFieldCancellation.active(fieldId, phase != "IDLE" || pending != null || pendingCloudRead != null || longCapture)
        onDispose { VoiceFieldCancellation.active(fieldId, false) }
    }
    val askPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) permissionResult@{ granted ->
        if (permissionEpoch != VoiceFieldCancellation.epoch.value) return@permissionResult
        permissionEpoch = null
        if(granted) {
            // The platform may deliver permission before ON_RESUME. Resume only
            // this explicit pending press, never automatic background listening.
            microphonePermissionPending=true
            if(lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                microphonePermissionPending=false
                beginCapture()
            }
        } else {
            microphonePermissionPending=false
            error="Microphone permission is required only when you choose to speak."
        }
    }
    LaunchedEffect(readText,enabled) {
        val consent=pendingCloudRead
        if(!enabled || consent?.content != readText?.take(120_000)) pendingCloudRead=null
        if (spokenId != null && readingState?.id == spokenId && readText?.take(120_000) != spokenContent) {
            reader.stop(); spokenId = null; spokenContent = null
        }
    }
    DisposableEffect(lifecycle,output) {
        val observer=LifecycleEventObserver { _,event ->
            when(event) {
                Lifecycle.Event.ON_RESUME -> if(microphonePermissionPending) {
                    microphonePermissionPending=false
                    beginCapture()
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    if (event == Lifecycle.Event.ON_STOP) permissionEpoch = null
                    cancelCapture()
                    publishJob?.cancel()
                    spokenId?.let(output::cancelScopedTransaction)
                    spokenId=null
                    pendingCloudRead=null
                }
                else -> Unit
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose {
            lifecycle.lifecycle.removeObserver(observer)
            cancelCapture()
            publishJob?.cancel()
            spokenId?.let(output::cancelScopedTransaction)
            microphonePermissionPending=false
            pendingCloudRead=null
        }
    }

    fun requestSpeech(long: Boolean) {
        if (phase == "RECORDING") {
            if (longCapture) longDone = true
            finish?.complete(Unit)
        } else if (phase == "IDLE" && enabled && localReady) {
            requestLong = long
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                beginCapture(long)
            else {
                VoiceFieldCancellation.stop()
                acceptedStopEpoch = VoiceFieldCancellation.epoch.value
                permissionEpoch = acceptedStopEpoch
                askPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
    LaunchedEffect(dictationRequest) {
        if (dictationRequest > 0) { if (enabled) requestSpeech(false); onRequestConsumed() }
    }
    fun requestReading() {
        val content = readText?.take(120_000)?.takeIf(String::isNotBlank) ?: return
        if (spokenId != null && readingState?.id == spokenId) reader.toggle()
        else {
            cancelCapture()
            val selectedId = speechRegistry.selector.ttsSelection.value.id
            val selected = speechRegistry.providers.firstOrNull { it.metadata.id == selectedId }
            when {
                selectedId != "AUTOMATIC" && selected == null -> error = "Reading aloud is unavailable with the current voice settings."
                selected?.metadata?.local == false && privateSpeech -> {
                    error = "Reading stays on this phone. The cloud voice is not used."
                    publishReading(content, selectedId)
                }
                selected?.metadata?.local == false -> pendingCloudRead = CloudReadConsent(selectedId, selected.metadata.displayName, content)
                else -> publishReading(content, selectedId)
            }
        }
    }
    val actions: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!readText.isNullOrBlank()) {
                val reading = readingState?.takeIf { it.id == spokenId }?.let { it.playing || it.preparing } == true
                val label = uiText(when {
                    reading -> "Pause reading"
                    readingState?.id == spokenId && spokenId != null -> "Resume reading"
                    readText.length > 120_000 -> "Read first part aloud"
                    else -> readActionLabel ?: "Read aloud"
                })
                TextButton(onClick = ::requestReading, enabled = enabled,
                    modifier = Modifier.heightIn(min = if (kidsPrivate) 56.dp else 48.dp)) {
                    HaloIcon(if (reading) HaloGlyph.PAUSE else HaloGlyph.SPEAK,
                        if (compactRead || fieldContent != null) label else null)
                    if (!compactRead && fieldContent == null) Text(" " + label)
                }
            }
            if (allowDictation) {
                val micEnabled = enabled && (phase == "RECORDING" || (phase == "IDLE" && localReady))
                val canGuide = guideDictation && phase == "IDLE" && micEnabled &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                val label = uiText(if (phase == "RECORDING") "Finish speaking" else "Speak instead of typing")
                if (fieldContent != null) Box(
                    Modifier.size(if (kidsPrivate) 72.dp else 48.dp)
                        .haloTrace(canGuide, MaterialTheme.colorScheme.primary, lapOnAppear = true)
                        .semantics { contentDescription = label }
                        .combinedClickable(enabled = micEnabled, role = Role.Button,
                            onClickLabel = label,
                            onLongClickLabel = if (allowLongDictation) uiText("Long dictation") else null,
                            onLongClick = if (allowLongDictation) ({ requestSpeech(true) }) else null,
                            onClick = { requestSpeech(false) }),
                    contentAlignment = Alignment.Center,
                ) { HaloIcon(if (phase == "RECORDING") HaloGlyph.STOP else HaloGlyph.LISTEN, null) }
                else {
                    TextButton(onClick = { requestSpeech(false) }, enabled = micEnabled,
                        modifier = Modifier.heightIn(min = if (kidsPrivate) 56.dp else 48.dp)
                            .haloTrace(canGuide, MaterialTheme.colorScheme.primary, lapOnAppear = true)) {
                        HaloIcon(if (phase == "RECORDING") HaloGlyph.STOP else HaloGlyph.LISTEN, null)
                        Text(" " + label)
                    }
                    if (allowLongDictation) TextButton(onClick = { requestSpeech(true) },
                        enabled = enabled && localReady && phase == "IDLE",
                        modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Long dictation")) }
                }
            }
        }
    }
    if (fieldContent != null) fieldContent(actions) else actions()
    if (allowDictation && !localReady) {
        Text(uiText("Install a local speech model in Voice settings."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = {
            val activity = com.halo.app.ui.mini.miniHaloActivityOwner(context)
            if (activity is com.halo.app.ui.mini.MiniHaloActivity)
                com.halo.app.ui.mini.MiniHaloEntry.pendingDraft.value = latestText.take(2048)
            ManualDependencyGraph.requestedHaloScreen.value = "settings/voice/input"
        }, modifier = Modifier.heightIn(min = if (kidsPrivate) 56.dp else 48.dp)) { Text(uiText("Get speech model")) }
    } else if (allowDictation && allowLongDictation && fieldContent != null && phase == "IDLE")
        Text(uiText("Hold the microphone for long dictation."), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (phase == "RECOGNIZING" || phase == "CANCELLING") {
        Text(uiText(if (phase == "RECOGNIZING") "Recognizing speech…" else "Stopping…"), style = MaterialTheme.typography.bodySmall)
        if (phase == "RECOGNIZING") TextButton(onClick = ::cancelCapture,
            modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Cancel")) }
    }
    if (longCapture) ModalBottomSheet(onDismissRequest = ::cancelCapture) {
        androidx.compose.foundation.layout.Column(Modifier.heightIn(max = 600.dp)
            .verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text(uiText("Long dictation"), style = MaterialTheme.typography.titleLarge)
            Text(uiText("Up to two minutes of local audio, in short takes. Text appears after each take. Nothing is inserted until you review it."))
            Text(uiText(when (phase) { "PAUSED" -> "Paused"; "RECOGNIZING" -> "Recognizing speech…"; "CANCELLING" -> "Stopping…"; else -> "Finish speaking" }))
            if (longTranscript.isNotBlank()) Text(longTranscript)
            TextButton(onClick = {
                if (longPaused) { longPaused = false; resumeLong?.complete(Unit) }
                else { longPaused = true; finish?.complete(Unit) }
            }, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText(if (longPaused) "Resume" else "Pause")) }
            Button(onClick = { longDone = true; finish?.complete(Unit); resumeLong?.complete(Unit) },
                modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Done")) }
            TextButton(onClick = ::cancelCapture, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Discard")) }
        }
    }
    searchUndo?.let { (before, applied) ->
        Row {
            Text(applied, style = MaterialTheme.typography.bodySmall)
            TextButton(enabled = latestText == applied, onClick = {
                if (latestText == applied) latestOnText(before)
                searchUndo = null
            }) { Text(uiText("Undo")) }
        }
    }
    pendingCloudRead?.let { consent ->
        AlertDialog(onDismissRequest={pendingCloudRead=null},
            title={Text(uiText("Cloud voice confirmation"))},
            text={
                Text(uiText("The selected cloud voice will receive this text. Do you want to send it?")+
                    " ("+consent.providerName+")")
            },
            confirmButton={
                TextButton(onClick={
                    pendingCloudRead=null
                    publishReading(consent.content,consent.providerId,cloudApproved=true)
                }) { Text(uiText("Send and read")) }
            },
            dismissButton={
                TextButton(onClick={pendingCloudRead=null}) { Text(uiText("Keep text private")) }
            })
    }
    pending?.let {
        androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.primaryContainer,
            shape = com.halo.app.ui.design.HaloShapes.card) {
            androidx.compose.foundation.layout.Column(Modifier.padding(12.dp)) {
                Text(uiText("Review spoken text"), style = MaterialTheme.typography.labelLarge)
                heard?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                androidx.compose.material3.OutlinedTextField(value = editedPreview,
                    onValueChange = { editedPreview = it.take(maxChars) },
                    label = { Text(uiText("Edit before using")) }, minLines = 2)
                if (!previewIsValid(editedPreview.trim())) Text(uiText("Enter digits to confirm this value."),
                    style = MaterialTheme.typography.bodySmall)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = editedPreview.isNotBlank() && previewIsValid(editedPreview.trim()) &&
                        (if (appendDictation && latestText.isNotBlank()) latestText.trimEnd().length + appendSeparator.length else 0) + editedPreview.trim().length <= maxChars,
                        onClick = {
                            val spoken = editedPreview.trim()
                            val before = if (appendDictation) latestText.trimEnd() else ""
                            val candidate = if (before.isBlank()) spoken else before + appendSeparator + spoken
                            if (candidate.length <= maxChars) { latestOnText(candidate); pending = null; editedPreview = "" }
                            else error = "Speech text is too long for this field."
                        }, modifier = Modifier.heightIn(min = if (kidsPrivate) 56.dp else 48.dp)) { Text(uiText("Use spoken text")) }
                    TextButton(onClick = { pending = null; editedPreview = "" },
                        modifier = Modifier.heightIn(min = if (kidsPrivate) 56.dp else 48.dp)) { Text(uiText("Discard")) }
                }
            }
        }
    }
    error?.let { message ->
        Text(uiText(message), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error)
    }
}
