package com.halo.app.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.halo.sdk.runtime.SpeechOutputFallback
import com.halo.sdk.runtime.SpeechOutputTarget
import com.halo.sdk.runtime.TtsRequested
import kotlinx.coroutines.*
import java.util.UUID

/**
 * One explicit, reusable Voice/Text affordance for Study and Workspace editors.
 *
 * Selected local STT, never Android/OEM cloud recognition. A recognized utterance
 * remains an uncommitted preview until the user accepts it into the current draft.
 * Reading uses Halo's one canonical routed TtsRequested playback owner.
 */
@Composable
fun HaloVoiceTextActions(
    text: String,
    onText: (String) -> Unit,
    enabled: Boolean = true,
    allowDictation: Boolean = true,
    readText: String? = null,
    language: String? = null,
    maxChars: Int = 12000,
    appendDictation: Boolean = true,
) {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current
    val scope=rememberCoroutineScope()
    val latestText by rememberUpdatedState(text)
    val latestOnText by rememberUpdatedState(onText)
    var phase by remember { mutableStateOf("IDLE") }
    var error by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<String?>(null) }
    var editedPreview by remember { mutableStateOf("") }
    var captureJob by remember { mutableStateOf<Job?>(null) }
    var finish by remember { mutableStateOf<CompletableDeferred<Unit>?>(null) }
    var spokenId by remember { mutableStateOf<String?>(null) }
    val localReady by ManualDependencyGraph.speechRecognitionAvailability.collectAsState()
    val output=remember { ManualDependencyGraph.haloSpeechOutputCoordinator }

    fun cancelCapture() {
        captureJob?.cancel()
        finish?.complete(Unit)
        finish=null
        captureJob=null
        phase="IDLE"
    }
    fun beginCapture() {
        if(phase!="IDLE" || !enabled || !allowDictation) return
        error=null
        pending=null
        phase="RECORDING"
        // A deliberate microphone press interrupts any response, avoiding echo
        // and sharing the same local ASR/audio-input lease infrastructure.
        output.stop("Workspace dictation requested")
        val signal=CompletableDeferred<Unit>()
        finish=signal
        captureJob=scope.launch {
            var session: com.halo.app.speech.diagnostics.SpeechDiagnosticSession?=null
            try {
                val runner=ManualDependencyGraph.speechDiagnosticRunner
                session=runner.captureSession(signal,maxDurationMs=15_000)
                phase="RECOGNIZING"
                val result=runner.recognizeSelected(session)
                currentCoroutineContext().ensureActive()
                val utterance=result.recognition?.text?.trim().orEmpty()
                when {
                    result.gate!="Accepted" || utterance.isBlank() ->
                        error="Speech was not clear. You can try again or type instead."
                    utterance.length > maxChars ->
                        error="Speech text is too long for this field."
                    else -> {
                        editedPreview=utterance
                        pending=utterance
                    }
                }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) {
                error="Voice input unavailable. Check your microphone and local speech model."
            } finally {
                withContext(NonCancellable) { session?.close() }
                finish=null
                captureJob=null
                phase="IDLE"
            }
        }
    }

    val askPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(granted) beginCapture()
        else error="Microphone permission is required only when you choose to speak."
    }
    DisposableEffect(lifecycle,output) {
        val observer=LifecycleEventObserver { _,event ->
            if(event==Lifecycle.Event.ON_PAUSE || event==Lifecycle.Event.ON_STOP) {
                cancelCapture()
                spokenId?.let(output::stopIfTransaction)
                spokenId=null
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose {
            lifecycle.lifecycle.removeObserver(observer)
            cancelCapture()
            spokenId?.let(output::stopIfTransaction)
        }
    }

    Row(horizontalArrangement=Arrangement.spacedBy(8.dp),
        modifier=Modifier.horizontalScroll(rememberScrollState())) {
        if(allowDictation) {
            TextButton(
                modifier=Modifier.heightIn(min=48.dp),
                enabled=enabled && (phase!="RECOGNIZING") && (localReady || phase=="RECORDING"),
                onClick={
                    when(phase) {
                        "RECORDING" -> finish?.complete(Unit)
                        "IDLE" -> {
                            if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)
                                beginCapture()
                            else askPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                }) {
                Icon(if(phase=="RECORDING") Icons.Rounded.Stop else Icons.Rounded.Mic,
                    contentDescription=null)
                Text(" "+uiText(when(phase) {
                    "RECORDING" -> "Finish speaking"
                    "RECOGNIZING" -> "Recognizing speech…"
                    else -> "Speak instead of typing"
                }))
            }
            if(phase=="RECOGNIZING") TextButton(onClick=::cancelCapture,
                modifier=Modifier.heightIn(min=48.dp)) { Text(uiText("Cancel")) }
            if(!localReady) Text(uiText("Install a local speech model in Voice settings."),
                style=MaterialTheme.typography.bodySmall)
        }
        if(!readText.isNullOrBlank()) {
            TextButton(enabled=enabled, modifier=Modifier.heightIn(min=48.dp),onClick={
                val id=spokenId
                if(id!=null && output.stopIfTransaction(id)) spokenId=null
                else {
                    val requestId=UUID.randomUUID().toString()
                    spokenId=requestId
                    scope.launch {
                        try {
                            ManualDependencyGraph.startAndroidSpeaker()
                            ManualDependencyGraph.eventBus.publish(
                                TtsRequested(text=readText.take(8000),transactionId=requestId,
                                    language=language,target=SpeechOutputTarget.HostDefault,
                                    fallback=SpeechOutputFallback.DISALLOW_PHONE))
                        } catch(cancelled: CancellationException) { throw cancelled }
                        catch(_: Exception) { error="Reading aloud is unavailable with the current voice settings." }
                    }
                }
            }) {
                Icon(Icons.Rounded.VolumeUp,contentDescription=null)
                Text(" "+uiText("Read aloud"))
            }
        }
    }
    pending?.let {
        AlertDialog(onDismissRequest={pending=null;editedPreview=""},title={
            Text(uiText("Review spoken text"))
        },text={
            androidx.compose.material3.OutlinedTextField(
                value=editedPreview,onValueChange={editedPreview=it.take(maxChars)},
                label={Text(uiText("Edit before using"))},minLines=2)
        },confirmButton={
            Button(enabled=editedPreview.isNotBlank(),onClick={
                val spoken=editedPreview.trim()
                val before=if(appendDictation) latestText.trimEnd() else ""
                val candidate=if(before.isBlank()) spoken else before+"\n"+spoken
                if(candidate.length<=maxChars) {
                    latestOnText(candidate)
                    pending=null
                    editedPreview=""
                } else error="Speech text is too long for this field."
            }) { Text(uiText("Use spoken text")) }
        },dismissButton={
            TextButton(onClick={pending=null;editedPreview=""}) { Text(uiText("Discard")) }
        })
    }
    error?.let { message ->
        AlertDialog(onDismissRequest={error=null},title={Text(uiText("Voice"))},
            text={Text(uiText(message))},confirmButton={
                TextButton(onClick={error=null}) { Text(uiText("OK")) }
            })
    }
}
