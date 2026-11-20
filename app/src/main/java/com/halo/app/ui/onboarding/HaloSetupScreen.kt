package com.halo.app.ui.onboarding

import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import com.halo.app.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.language.AppLanguage
import com.halo.app.language.uiText
import com.halo.app.ai.GenerationProof
import com.halo.app.ui.screens.home.TextSubmissionPhase
import com.halo.app.ui.screens.settings.rememberProviderGenerationCheck
import com.halo.sdk.runtime.SpeechOutputTarget
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.map
import com.halo.runtime.cognition.agent.*

@Composable
fun HaloSetupScreen(store: SetupProgressStore, onOpen: (String) -> Unit, onExit: () -> Unit) {
    val progress by store.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val owner = LocalLifecycleOwner.current
    var permissionRevision by remember { mutableIntStateOf(0) }
    fun granted(value: String): Boolean { permissionRevision; return ContextCompat.checkSelfPermission(context, value) == PackageManager.PERMISSION_GRANTED }
    val microphone = granted(Manifest.permission.RECORD_AUDIO)
    val speech by Graph.speechRecognitionAvailability.collectAsStateWithLifecycle()
    val voice by Graph.voiceSettingsViewModel.uiState.collectAsStateWithLifecycle()
    var testedLanguage by rememberSaveable { mutableStateOf("") }
    var testedModel by rememberSaveable { mutableStateOf("") }
    var microphoneTested by rememberSaveable { mutableStateOf(false) }
    var recognitionTested by rememberSaveable { mutableStateOf(false) }
    val recognitionVerified = recognitionTested && testedLanguage == voice.selectedLocale && testedModel == voice.selectedProviderId
    val providers by Graph.providerControlRepository.rows.collectAsStateWithLifecycle()
    val selected = providers.firstOrNull { it.selected }
    val inference = setupAnswerVerified(selected)
    val checkAnswer = rememberProviderGenerationCheck(Graph.providerControlRepository)
    LaunchedEffect(selected?.descriptor?.providerId, selected?.generation) {
        if (selected?.generation == GenerationProof.FAILED) store.diagnostic(SetupError.PROVIDER_CONNECTION)
    }
    val audio by Graph.haloSpeechOutputCoordinator.outputTest.collectAsStateWithLifecycle()
    val accessories by Graph.audioAccessoryRegistry.accessories.collectAsStateWithLifecycle()
    val devices by Graph.devicesViewModel.uiState.collectAsStateWithLifecycle()
    val deviceReady = accessories.isNotEmpty() || devices.devices.any { it.isConnected }
    val fullChecksReady = microphone && microphoneTested && speech && recognitionVerified && inference && audio.audibleConfirmed && (progress.intention != "Use headphones or glasses" || deviceReady)
    val runFlow = remember { Graph.agentRunStore.observeRuns().map { it as List<AgentRun>? } }
    val runs by runFlow.collectAsStateWithLifecycle(initialValue = null)
    val home by Graph.homeViewModel.uiState.collectAsStateWithLifecycle()
    val submission by Graph.homeViewModel.textSubmission.collectAsStateWithLifecycle()
    var transcript by remember { mutableStateOf<String?>(null) }
    var speechError by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var speechJob by remember { mutableStateOf<Job?>(null) }
    var finishCapture by remember { mutableStateOf<CompletableDeferred<Unit>?>(null) }
    var request by rememberSaveable { mutableStateOf("") }
    var leave by remember { mutableStateOf(false) }
    val answer = home.presentedIdentity?.takeIf { it.interactionId == progress.interactionId }
    LaunchedEffect(answer, submission, runs, progress.interactionId) {
        val id = progress.interactionId ?: return@LaunchedEffect
        if (runs != null && (!submission.requestId.orEmpty().startsWith("native:") || runs.orEmpty().any { it.interactionId.value == id }) && SetupInteractionEvidence.actionsConfirmed(runs.orEmpty().filter { it.interactionId.value == id }, requireAction = submission.requestId.orEmpty().startsWith("native:")) && answer != null && answer.text.isNotBlank() && submission.interactionId == id && submission.phase == TextSubmissionPhase.COMPLETED)
            store.verifyInteraction(id, committed = answer.source == com.halo.app.ui.screens.home.ResponsePresentationSource.COMMITTED_CONVERSATION, pending = answer.continuationPending)
    }
    DisposableEffect(owner, progress.stage) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionRevision++
            if (event == Lifecycle.Event.ON_STOP) { speechJob?.cancel(); Graph.haloSpeechOutputCoordinator.stopOutputTest() }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); speechJob?.cancel(); Graph.haloSpeechOutputCoordinator.stopOutputTest() }
    }
    fun back() { if(progress.stage == SetupStage.WELCOME) leave = true else store.back() }
    BackHandler { back() }
    if (leave) AlertDialog(onDismissRequest = { leave = false }, title = { Text(uiText("Save setup for later?")) },
        text = { Text(uiText("Your choices are saved. Open Guided setup in Settings to continue.")) },
        confirmButton = { TextButton(onClick = onExit) { Text(uiText("Explore Halo")) } },
        dismissButton = { TextButton(onClick = { leave = false }) { Text(uiText("Keep setting up")) } })
    // AppShell already applies safe drawing (including IME) insets to this surface.
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0), bottomBar = {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shadowElevation = 0.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if(progress.stage == SetupStage.FIRST) { if(store.finish(progress.limited || !fullChecksReady)) onExit() }
                    else { if(progress.stage == SetupStage.READINESS && !fullChecksReady) store.limited(true); store.next() }
                }, enabled = !recording && (progress.stage != SetupStage.FIRST || progress.firstVerified), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(uiText(when { progress.stage == SetupStage.FIRST && (progress.limited || !fullChecksReady) -> "Open Halo with limitations"; progress.stage == SetupStage.FIRST -> "Open Halo"; progress.stage == SetupStage.READINESS && !fullChecksReady -> "Continue with limitations"; else -> "Continue" }))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = ::back) { Text(uiText("Back")) }
                    TextButton(onClick = { leave = true }) { Text(uiText("Save and leave")) }
                }
            }
        }
    }) { padding ->
        // New steps start at their heading; the current step keeps saveable scroll on rotation.
        // Scope only the scroll state, preserving the form body's composition and drafts.
        val stepScroll = key(progress.stage) { rememberScrollState() }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(stepScroll)
            .padding(horizontal = 20.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(uiText("Setup") + " · ${progress.stage.ordinal + 1} / ${SetupStage.entries.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(progress = { (progress.stage.ordinal + 1).toFloat() / SetupStage.entries.size },
                modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
            Box(Modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                // The welcome ring may breathe, but setup does not pretend to be listening
                // or thinking before the associated runtime activity actually starts.
                // HaloSignalFace observes the system reduced-motion preference itself.
                com.halo.app.ui.presence.HaloSignalFace(
                    com.halo.app.ui.presence.HaloSignalPresentation(
                        state = com.halo.app.ui.presence.HaloSignalState.IDLE,
                        label = "Ready"
                    ),
                    size = if (progress.stage == SetupStage.WELCOME) 160.dp else 80.dp,
                    contentDescription = uiText("Halo"))
            }
            Text(uiText(progress.stage.title), style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.semantics { heading() })
            when(progress.stage) {
                SetupStage.WELCOME -> {
                    Copy("Halo helps you ask questions, save ideas and get things done. Your notes stay on this phone unless you choose to share them. You decide which services, permissions and devices Halo can use.")
                    val diagnostics by store.diagnosticsEnabled.collectAsStateWithLifecycle()
                    Row(Modifier.fillMaxWidth()) { Text(uiText("Save setup diagnostics on this phone"),Modifier.weight(1f)); Switch(diagnostics,onCheckedChange=store::enableDiagnostics) }
                    Copy("Optional diagnostics record only setup stage, outcome and error type. No keys, speech or private content are recorded.")
                    val language by Graph.appLanguagePreferences.language.collectAsStateWithLifecycle()
                    AppLanguage.entries.forEach { option -> Choice(option.nativeName, language == option) {
                        Graph.appLanguagePreferences.select(option); scope.launch { Graph.speechLanguageSettingsRepository.select(option.tag) }
                    } }
                }
                SetupStage.USE -> {
                    Copy("Pick a starting point. You can use every feature later.")
                    SetupProgressStore.intentions.forEach { label -> Choice(uiText(label), progress.intention == label) { store.intention(label) } }
                }
                SetupStage.PERMISSIONS -> {
                    Copy("Access is optional. Typed questions and private notes remain available when you decline.")
                    Text(androidx.compose.ui.res.stringResource(com.halo.app.R.string.capability_guide_explanation))
                    TextButton(onClick = { onOpen("permissions") }) { Text(androidx.compose.ui.res.stringResource(com.halo.app.R.string.capability_guide_title)) }
                    Copy("Continue skips any permission you do not want to grant.")
                }
                SetupStage.AI -> {
                    Copy("Choose how Halo answers questions. Online answers send your request and allowed context to your chosen service. Local models run on this phone and need a separate download.")
                    Text(stringResource(R.string.setup_settings_handoff))
                    Secondary(stringResource(R.string.setup_services)) { onOpen("settings/intelligence/providers") }
                    Secondary(stringResource(R.string.setup_answer_model)) { onOpen("settings/intelligence/models?role=CHAT") }
                    Secondary(stringResource(R.string.setup_local_models)) { onOpen("settings/intelligence/on-device") }
                    selected?.let { Text(it.descriptor.displayName + " · " + (it.lastSelectedModelId ?: stringResource(R.string.setup_answer_model))) }
                    Choice(uiText("Configure later / limited mode"), progress.limited) { store.limited(!progress.limited) }
                    if(progress.limited) Copy("Limited mode keeps notes, Canvas and supported phone actions available. AI answers and voice may need setup later.")
                }
                SetupStage.SPEECH -> {
                    Text(stringResource(R.string.setup_settings_handoff))
                    Secondary("Choose or download a listening model") { onOpen("settings/voice/input") }
                    Secondary(stringResource(R.string.setup_speaking)) { onOpen("settings/voice/output") }
                    Copy("Choose a language, then say a short phrase. Recognition text is shown here only; it is not included in your setup report.")
                    if (!microphone) Secondary("Allow microphone") { onOpen("permissions") }
                    OutlinedButton(enabled = microphone && speechJob == null, onClick = {
                        speechError = false; transcript = null; recording = true; microphoneTested = false; recognitionTested = false
                        val signal = CompletableDeferred<Unit>(); finishCapture = signal
                        speechJob = scope.launch(start = CoroutineStart.LAZY) {
                            var capture: com.halo.app.speech.diagnostics.SpeechDiagnosticSession? = null
                            try {
                                capture = Graph.speechDiagnosticRunner.captureSession(signal, maxDurationMs = 10_000)
                                recording = false
                                microphoneTested = capture.rms > 0.0 && capture.activity.voicedDurationMs >= 120
                                val result = if(speech) Graph.speechDiagnosticRunner.recognizeSelected(capture) else null
                                transcript = result?.recognition?.text?.takeIf { it.isNotBlank() && result.gate == "Accepted" }
                                speechError = transcript == null
                                if(speechError) store.diagnostic(SetupError.SPEECH_TEST)
                                recognitionTested = transcript != null
                                testedLanguage = voice.selectedLocale; testedModel = voice.selectedProviderId
                            } catch(cancelled: CancellationException) { throw cancelled }
                            catch(_: Exception) { speechError = true; store.diagnostic(SetupError.SPEECH_TEST) }
                            finally { capture?.close(); recording = false; finishCapture = null; speechJob = null }
                        }
                        speechJob?.start()
                    }, modifier = Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(uiText("Test microphone and listening")) }
                    if(recording) { Copy("Listening…"); Secondary("Finish phrase") { finishCapture?.complete(Unit) } }
                    if(speechJob != null && !recording) Copy("Working on it…")
                    if(speechJob?.isActive == true) Secondary("Cancel test") { speechJob?.cancel() }
                    transcript?.let { Text(it, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                    if(speechError) { Copy("Could not recognize that phrase. Try again or choose another listening model."); Secondary("Choose another listening option") { onOpen("settings/voice/input") } }
                    val testPhrase = uiText("This is Halo. Can you hear me?")
                    Secondary("Play a Halo response") { Graph.haloSpeechOutputCoordinator.testOutput(SpeechOutputTarget.Phone, "Phone speaker", testPhrase) }
                    if(audio.outcome == "Playback completed") Secondary("I heard it") { Graph.haloSpeechOutputCoordinator.confirmOutputTest() }
                    if(audio.audibleConfirmed) Copy("You confirmed audible output")
                    else if(audio.outcome != "Not tested") Copy(when(audio.outcome) { "Playback completed" -> "Did you hear Halo? Confirm only what you heard."; "Preparing", "Route verified · Playing" -> "Working on it…"; else -> "Not checked or needs setup" })
                    Secondary("Stop audio") { Graph.haloSpeechOutputCoordinator.stopOutputTest() }
                    Secondary("Choose another voice") { onOpen("settings/voice/output") }
                }
                SetupStage.DEVICES -> {
                    Copy("Phone-only use works without headphones or glasses. Pair a device only if you want to use one. Halo will show what that device can actually do.")
                    Text(stringResource(R.string.setup_settings_handoff))
                    accessories.forEach { Text(it.label) }
                    devices.devices.forEach { Text(it.name + " · " + uiText(if(it.isConnected) "Connected" else "Not connected")) }
                    Copy(if(accessories.isEmpty()) "No audio accessory is available right now." else "Available audio does not prove that you heard it. Use the audio test to check.")
                    Secondary("Find or connect a device") { onOpen("devices") }
                    Secondary("Test device audio") { onOpen("settings/voice/output") }
                    Copy("Continue skips device setup. No wearable is required.")
                }
                SetupStage.PRIVACY -> {
                    Copy("Notes, Canvas, conversations and saved context are stored on this device. You can review or remove them in Workspace. Selected context may be sent with a request only when your sharing settings allow it.")
                    Copy("Online services have their own data policies. Local use needs no account. Backup and restore are manual; this setup does not enable cloud sync.")
                    Secondary("Review what may be shared") { onOpen("settings/intelligence/context") }
                    Secondary("Review saved memory") { onOpen("memory_learning") }
                }
                SetupStage.EVERYDAY -> {com.halo.app.ui.everyday.EverydaySetupPage()}
                SetupStage.READINESS -> {
                    Copy("These checks show availability, not a promise that a request will succeed. Test speaking and try a real request next.")
                    Readiness("Microphone", microphone && microphoneTested, "guided_setup", { store.open(SetupStage.SPEECH) })
                    Readiness("Listening", speech && recognitionVerified, "guided_setup", { store.open(SetupStage.SPEECH) })
                    Readiness("Answers", inference, "settings/intelligence/providers", onOpen)
                    Readiness("Audible response", audio.audibleConfirmed, "guided_setup", { store.open(SetupStage.SPEECH) })
                    Readiness("Optional device", deviceReady, "devices", onOpen)
                    Secondary("Check selected service", enabled = selected?.configured == true) { selected?.let { Graph.providerControlRepository.testConnection(it.descriptor.providerId) } }
                    Copy("An answer test sends only a short test phrase to your selected service, with no saved notes or conversation. Online usage may be charged.")
                    OutlinedButton(enabled = selected?.configured == true && !selected.lastSelectedModelId.isNullOrBlank() && selected.generation != GenerationProof.CHECKING,
                        onClick = { selected?.let { checkAnswer(it.descriptor.providerId) } },
                        modifier = Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(uiText("Test an answer")) }
                    if(selected?.generation == GenerationProof.CHECKING) Copy("Working on it…")
                    if(selected?.generation == GenerationProof.FAILED) Copy("Could not connect. Check the key, internet connection and service access, then try again. You can choose another option or continue in limited mode.")
                    Secondary("Share sanitized setup report") {
                        val report=store.report(microphone && microphoneTested,speech && recognitionVerified,inference,audio.audibleConfirmed,deviceReady)
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type="text/plain"; putExtra(Intent.EXTRA_TEXT,report) },null))
                    }
                    Choice(uiText("Continue with limitations"), progress.limited) { store.limited(!progress.limited) }
                }
                SetupStage.FIRST -> {
                    Copy("Send a real request. Halo will interpret it, decide what is needed, use only allowed actions and show its reply. If approval is needed, continue in Activity.")
                    Copy("Try asking the time, opening a Halo screen, or creating a note. You can edit the request before sending.")
                    val example=uiText("What time is it?")
                    HaloTextField(request, { request=it.take(2000) }, label={Text(uiText("Your first request"))}, placeholder={Text(example)}, modifier=Modifier.fillMaxWidth(), kind = VoiceFieldKind.TEXT, maxChars = 2000)
                    OutlinedButton(enabled=!submission.busy && submission.phase != TextSubmissionPhase.WAITING_FOR_INPUT, onClick={
                        val before = Graph.homeViewModel.textSubmission.value.interactionId
                        Graph.homeViewModel.submitText(request.ifBlank { example })
                        Graph.homeViewModel.textSubmission.value.interactionId?.takeIf { it != before }?.let(store::bindInteraction)
                    }, modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(uiText("Send to Halo")) }
                    if(submission.interactionId == progress.interactionId) {
                        Copy(when(submission.phase) {
                            TextSubmissionPhase.SUBMITTING,TextSubmissionPhase.ACCEPTED,TextSubmissionPhase.PROCESSING -> "Working on it…"
                            TextSubmissionPhase.WAITING_FOR_INPUT -> "Needs your approval"
                            TextSubmissionPhase.FAILED,TextSubmissionPhase.CANCELLED,TextSubmissionPhase.TIMED_OUT -> "Could not finish. Review the result before trying again."
                            else -> "Your reply appears here."
                        })
                        if(submission.busy) Secondary("Stop") { Graph.homeViewModel.cancelSession() }
                    }
                    answer?.let { Text(it.text, modifier=Modifier.semantics { liveRegion=LiveRegionMode.Polite }) }
                    Secondary("Open Activity") { onOpen("activity") }
                    if(progress.firstVerified) Copy("Halo completed your request and saved the reply. You can continue from Home, and review actions in Activity.")
                    else {
                        Secondary("Fix answer setup") { onOpen("settings/intelligence/providers") }
                        Secondary("Enter limited mode without a verified interaction") { if(store.finish(true)) onExit() }
                        Copy("A previous request is not automatically repeated after restart. Check Home or Activity before sending again.")
                    }
                }
            }
        }
    }
}

@Composable private fun Copy(text: String) { Text(uiText(text), style=MaterialTheme.typography.bodyLarge) }
@Composable private fun Secondary(label: String, enabled: Boolean = true, action: () -> Unit) {
    OutlinedButton(onClick=action, enabled=enabled, modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(uiText(label)) }
}
@Composable private fun Choice(label: String, selected: Boolean, action: () -> Unit) {
    Surface(onClick = action, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
        .semantics { role = Role.RadioButton; this.selected = selected },
        shape = com.halo.app.ui.design.HaloShapes.card,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RadioButton(selected = selected, onClick = null)
            Text(label, style = MaterialTheme.typography.titleSmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
        }
    }
}
@Composable private fun Readiness(label: String, ready: Boolean, route: String, open: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
        Text(uiText(label),style=MaterialTheme.typography.titleMedium)
        Copy(if(ready) "Ready" else "Not checked or needs setup")
        if(!ready) TextButton(onClick={open(route)}) { Text(uiText("Fix or choose another option")) }
    } }
}
