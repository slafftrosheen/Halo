package com.halo.app.ui.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.language.AppLanguage
import com.halo.app.language.uiText
import com.halo.app.ai.ProviderConnectionState
import com.halo.app.ui.screens.home.TextSubmissionPhase
import com.halo.sdk.provider.AiModelRef
import com.halo.sdk.provider.AiCapability
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
    var denied by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted; permissionRevision++; if(!granted) store.diagnostic(SetupError.PERMISSION_DENIED)
    }
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
    var testedAnswerProvider by rememberSaveable { mutableStateOf("") }
    var testedAnswerModel by rememberSaveable { mutableStateOf("") }
    val inference = selected?.connection == ProviderConnectionState.READY && selected.generation == com.halo.app.ai.GenerationProof.PROVEN && selected.descriptor.providerId == testedAnswerProvider && selected.lastSelectedModelId == testedAnswerModel && selected.generationModelId == testedAnswerModel
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
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(uiText("Setup") + " · ${progress.stage.ordinal + 1} / ${SetupStage.entries.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(progress = { (progress.stage.ordinal + 1).toFloat() / SetupStage.entries.size },
                modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
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
                    Copy("Microphone access lets Halo hear you only when you start talking. Without it, you can still type and save notes.")
                    PermissionChoice("Allow microphone", microphone) { permission.launch(Manifest.permission.RECORD_AUDIO) }
                    if(Build.VERSION.SDK_INT >= 33) {
                        Copy("Notifications let Halo show reminders and finished tasks. You can use the app without them.")
                        PermissionChoice("Allow notifications", granted(Manifest.permission.POST_NOTIFICATIONS)) { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
                    }
                    Copy("Nearby-device access is requested only when you choose a device. Files and photos are selected individually; setup does not need access to your whole library.")
                    if(denied) {
                        Copy("Access was not granted. Try again, continue without it, or allow it in Android settings.")
                        Secondary("Open Android permissions") { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
                    }
                    Copy("Continue skips any permission you do not want to grant.")
                }
                SetupStage.AI -> {
                    Copy("Choose how Halo answers questions. Online answers send your request and allowed context to your chosen service. Local models run on this phone and need a separate download.")
                    OnlineSetup(onOpen, store)
                    var localSetup by rememberSaveable { mutableStateOf(false) }
                    Secondary("Local/private model") { localSetup = !localSetup }
                    if(localSetup) LocalAnswerSetup { onOpen("settings/intelligence/on-device") }
                    Secondary("Bring my own provider") { onOpen("settings/intelligence/providers") }
                    Choice(uiText("Configure later / limited mode"), progress.limited) { store.limited(!progress.limited) }
                    if(progress.limited) Copy("Limited mode keeps notes, Canvas and supported phone actions available. AI answers and voice may need setup later.")
                }
                SetupStage.SPEECH -> {
                    Copy("Choose a language, then say a short phrase. Recognition text is shown here only; it is not included in your setup report.")
                    val language by Graph.speechLanguageSettingsRepository.languageMode.collectAsStateWithLifecycle()
                    listOf("en" to "English", "ru" to "Русский", "lv" to "Latviešu").forEach { (tag,name) -> Choice(name, language == tag) { scope.launch { Graph.speechLanguageSettingsRepository.select(tag); Graph.voiceSettingsViewModel.refreshState() } } }
                    if(!microphone) Secondary("Allow microphone") { permission.launch(Manifest.permission.RECORD_AUDIO) }
                    if(denied) Secondary("Open Android permissions") { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
                    if(voice.models["whisper-base"] != true) {
                        Copy("Multilingual listening works on this phone. Download the balanced model once, about 161 MB. It supports English, Russian and Latvian; recognition still needs testing.")
                        OutlinedButton(enabled=!voice.isDownloading,onClick={Graph.voiceSettingsViewModel.installModel("whisper-base")},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(uiText("Download multilingual listening")) }
                    } else Secondary("Use multilingual listening") { Graph.voiceSettingsViewModel.selectModel("speech.sherpa_onnx.whisper_base") }
                    if(voice.isDownloading) { LinearProgressIndicator(progress={voice.downloadProgress},modifier=Modifier.fillMaxWidth()); Copy("Downloading listening model…") }
                    if(voice.downloadError != null) Copy("Download did not finish. Check your connection and free space, then retry or choose another option.")
                    Secondary("Choose or download a listening model") { onOpen("settings/voice/input") }
                    Copy("Downloads start only when you choose them. Language support depends on the installed model and Android voice.")
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
                SetupStage.READINESS -> {
                    Copy("These checks show availability, not a promise that a request will succeed. Test speaking and try a real request next.")
                    Readiness("Microphone", microphone && microphoneTested, "guided_setup", { store.open(SetupStage.PERMISSIONS) })
                    Readiness("Listening", speech && recognitionVerified, "guided_setup", { store.open(SetupStage.SPEECH) })
                    Readiness("Answers", inference, "settings/intelligence/providers", onOpen)
                    Readiness("Audible response", audio.audibleConfirmed, "guided_setup", { store.open(SetupStage.SPEECH) })
                    Readiness("Optional device", deviceReady, "devices", onOpen)
                    Secondary("Check selected service") { selected?.let { Graph.providerControlRepository.testConnection(it.descriptor.providerId) } }
                    Copy("An answer test sends only a short test phrase to your selected service, with no saved notes or conversation. Online usage may be charged.")
                    OutlinedButton(enabled=selected != null && selected.generation != com.halo.app.ai.GenerationProof.CHECKING,onClick={
                        val target=selected ?: return@OutlinedButton
                        val model=target.lastSelectedModelId ?: return@OutlinedButton
                        testedAnswerProvider=""; testedAnswerModel=""
                        scope.launch {
                            Graph.providerControlRepository.checkGeneration(target.descriptor.providerId).join()
                            val current=Graph.providerControlRepository.rows.value.firstOrNull { it.selected }
                            if(current?.descriptor?.providerId == target.descriptor.providerId && current.lastSelectedModelId == model && current.generation == com.halo.app.ai.GenerationProof.PROVEN && current.generationModelId == model) {
                                testedAnswerProvider=target.descriptor.providerId; testedAnswerModel=model
                            } else store.diagnostic(SetupError.PROVIDER_CONNECTION)
                        }
                    },modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(uiText("Test an answer")) }
                    if(selected?.generation == com.halo.app.ai.GenerationProof.CHECKING) Copy("Working on it…")
                    if(selected?.generation == com.halo.app.ai.GenerationProof.FAILED) Copy("Could not connect. Check the key, internet connection and service access, then try again. You can choose another option or continue in limited mode.")
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
                    OutlinedTextField(request, { request=it.take(2000) }, label={Text(uiText("Your first request"))}, placeholder={Text(example)}, modifier=Modifier.fillMaxWidth())
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
@Composable private fun Secondary(label: String, action: () -> Unit) {
    OutlinedButton(onClick=action, modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(uiText(label)) }
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
@Composable private fun PermissionChoice(label: String, ready: Boolean, action: () -> Unit) {
    if(ready) Copy("Access granted") else Secondary(label,action)
}
@Composable private fun Readiness(label: String, ready: Boolean, route: String, open: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
        Text(uiText(label),style=MaterialTheme.typography.titleMedium)
        Copy(if(ready) "Ready" else "Not checked or needs setup")
        if(!ready) TextButton(onClick={open(route)}) { Text(uiText("Fix or choose another option")) }
    } }
}

@Composable private fun OnlineSetup(open: (String) -> Unit, store: SetupProgressStore) {
    val control=Graph.providerControlRepository
    val rows by control.rows.collectAsStateWithLifecycle()
    var onlineProvider by rememberSaveable { mutableStateOf("openrouter") }
    val row=rows.firstOrNull { it.descriptor.providerId == onlineProvider }
    val catalogue by control.catalogue.state.collectAsStateWithLifecycle()
    val context=LocalContext.current
    var key by remember(onlineProvider) { mutableStateOf("") } // Never Saveable: secrets cannot enter the saved-state bundle.
    var failed by remember(onlineProvider) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope=rememberCoroutineScope()
    Copy("Recommended online setup: OpenRouter. Create a key on its website, then paste it below. The service may charge for usage; check its pricing and privacy policy. Halo does not create an account for you.")
    val keyPages=mapOf("openrouter" to "https://openrouter.ai/settings/keys", "gemini" to "https://aistudio.google.com/apikey", "openai" to "https://platform.openai.com/api-keys", "anthropic" to "https://platform.claude.com/settings/keys")
    rows.filter { it.descriptor.providerId in keyPages }.forEach { option ->
        Choice(option.descriptor.displayName, onlineProvider == option.descriptor.providerId) { if(!busy) onlineProvider=option.descriptor.providerId }
    }
    Copy(when(onlineProvider) {
        "gemini" -> "Gemini: create a key in Google AI Studio. Google may ask you to choose a project and accept its terms. Use Advanced for Google sign-in instead."
        "openai" -> "OpenAI: create a key in the API platform. A ChatGPT subscription does not replace API access; check API billing separately."
        "anthropic" -> "Claude: create a key in the Claude developer platform. Check that your account has API access and available usage credit."
        else -> "OpenRouter: create a key in its key settings. Check available credit, model prices and usage limits before choosing a model."
    })
    Secondary("Open service key page") { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(keyPages.getValue(onlineProvider)))) }
    OutlinedTextField(key,{key=it.take(4096)},label={Text(uiText("API key"))},visualTransformation=PasswordVisualTransformation(),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Password),singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth())
    OutlinedButton(enabled=!busy, modifier=Modifier.fillMaxWidth().heightIn(min=48.dp), onClick={
        val clipboard=context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        key=clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.take(4096).orEmpty()
    }) { Text(uiText("Paste key")) }
    OutlinedButton(enabled=!busy && (key.isNotBlank() || row?.credentialConfigured == true),onClick={
        busy=true; failed=false
        control.save(onlineProvider,if(onlineProvider == "gemini") mapOf("gemini_auth_mode" to "API_KEY") else emptyMap(),key.takeIf{it.isNotBlank()}) { saved ->
            key=""
            if(!saved) { failed=true; busy=false } else scope.launch {
                try { control.testConnection(onlineProvider).join(); control.refreshModels(onlineProvider).join()
                    failed=control.rows.value.firstOrNull { it.descriptor.providerId == onlineProvider }?.connection != ProviderConnectionState.READY
                    if(failed) store.diagnostic(SetupError.PROVIDER_CONNECTION)
                } finally { busy=false }
            }
        }
    }, modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(uiText(if(busy) "Checking…" else "Save and check key")) }
    if(failed) Copy("Could not connect. Check the key, internet connection and service access, then try again. You can choose another option or continue in limited mode.")
    if(row?.connection == ProviderConnectionState.READY) {
        Copy("Connected. Choose an answer model to use with Halo.")
        catalogue.models.filter { it.model.providerId == onlineProvider && AiCapability.TEXT in it.model.inputCapabilities }.take(8).forEach { entry ->
            Choice(entry.model.displayName, Graph.aiProviderRouter.selectedProviderId.value == onlineProvider && Graph.aiProviderRouter.selectedModelId.value == entry.model.id) {
                Graph.aiProviderRouter.selectTarget(AiModelRef(onlineProvider,entry.model.id))
            }
        }
        Secondary("More answer models") { open("settings/intelligence/models?providerId=${Uri.encode(onlineProvider)}") }
    }
    Secondary("Advanced provider settings") { open("settings/intelligence/providers/${Uri.encode(onlineProvider)}") }
}


@Composable private fun LocalAnswerSetup(advanced: () -> Unit) {
    val models=Graph.intelligenceViewModel
    val state by models.uiState.collectAsStateWithLifecycle()
    Copy("Local answers keep the request on this phone. Download a model explicitly, then select and test it. Phone memory and language quality vary; online setup or limited mode remain available.")
    val choices=state.catalogModels.filter { it.supportsText && it.access == com.halo.providers.litert.models.LiteRtCatalogAccess.OPEN_DIRECT }.take(3)
    choices.forEach { model ->
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(model.displayName,style=MaterialTheme.typography.titleMedium)
            Text("${model.expectedBytes / 1_000_000} MB")
            val installed=state.localModels.any { it.id == model.id && it.validation == com.halo.providers.litert.models.LocalModelValidation.VERIFIED_ARTIFACT }
            val download=state.downloadStates[model.id]
            val busy=download is com.halo.providers.litert.models.DownloadState.Downloading || download is com.halo.providers.litert.models.DownloadState.Verifying || download == com.halo.providers.litert.models.DownloadState.Queued || download == com.halo.providers.litert.models.DownloadState.Preparing
            if(busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Secondary("Cancel download") { models.cancelDownload(model.id) } }
            else if(installed) {
                Secondary("Use this local model") { models.selectLocalModel(model.id) }
                OutlinedButton(enabled=state.verifyingModelId == null,onClick={models.verifyModel(model.id)},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(uiText("Test local answers")) }
                if(state.verifications[model.id] is com.halo.providers.litert.models.ModelVerificationResult.Verified) Copy("Local answer test passed")
                if(state.verifications[model.id] is com.halo.providers.litert.models.ModelVerificationResult.Failed) Copy("Local answer test failed. Choose another model or continue in limited mode.")
            } else Secondary("Download this local model") { models.downloadCatalogModel(model) }
            if(download is com.halo.providers.litert.models.DownloadState.Failed || state.downloadErrors[model.id] != null) Copy("Download did not finish. Check your connection and free space, then retry or choose another option.")
        } }
    }
    if(choices.isEmpty()) Copy("No downloadable local answer model is available. Choose online setup or limited mode.")
    Secondary("Advanced local model options",advanced)
}
