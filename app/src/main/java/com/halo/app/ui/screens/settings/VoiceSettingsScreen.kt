package com.halo.app.ui.screens.settings

import com.halo.app.language.uiText

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import com.halo.app.ui.design.HaloFocusAction
import com.halo.app.ui.design.HaloProgressPresentation
import com.halo.app.ui.design.HaloFeedbackSurface
import com.halo.app.ui.design.HaloFeedbackKind
import com.halo.app.ui.icons.HaloGlyph
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.halo.app.ui.theme.HaloPastelBlue
import com.halo.app.ui.theme.HaloPastelMint
import com.halo.app.ui.workspace.HaloWorkspaceButton
import com.halo.app.ui.workspace.HaloWorkspaceCard
import com.halo.app.ui.workspace.HaloWorkspaceHeader
import com.halo.app.ui.workspace.HaloWorkspaceRow
import com.halo.app.ui.workspace.HaloWorkspaceSection
import com.halo.app.ui.workspace.HaloWorkspaceDestination
import com.halo.app.ui.workspace.HaloWorkspaceToggleRow

@Composable
fun SpeechRecognitionSettingsScreen(
    viewModel: VoiceSettingsViewModel,
    onBack: () -> Unit,
    onSpeechLab: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val microphonePermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.startTest() }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        HaloWorkspaceHeader(
            title = uiText("Speech recognition"),
            subtitle = uiText("Local speech model and language."),
            onBack = onBack
        )

        TextButton(onClick = onSpeechLab) { Text(uiText("Speech Lab · compare one sample")) }
        Spacer(Modifier.height(20.dp))

        HaloWorkspaceSection(title = uiText("Speech recognition")) {
            Text(uiText("Active model: %1\$s", uiState.selectedModelName))
            Text(uiText(uiState.recognitionPhase))
            uiState.recognitionMessage?.let { Text(uiText(it), style = MaterialTheme.typography.bodySmall) }
            Text(uiText("Installed models initialize when you use recognition. Automatic language is a configuration, not a detected language."), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { viewModel.selectModel("AUTOMATIC") }, enabled = !uiState.isTesting && !uiState.isDownloading) {
                Text(uiText(if (uiState.selectedProviderId == "AUTOMATIC") "Automatic · Selected" else "Use automatic selection"))
            }
            Text(uiText("Automatic prefers installed Base, then Tiny."))
        }
        Spacer(Modifier.height(20.dp))

        var languagePicker by rememberSaveable { mutableStateOf(false) }
        var testDetails by rememberSaveable { mutableStateOf(false) }
        HaloWorkspaceSection(title = uiText("Language")) {
            HaloWorkspaceRow(title = uiState.availableLocales.firstOrNull { it.tag == uiState.selectedLocale }?.displayName ?: uiState.selectedLocale,
                trailingText = uiText("Change"), enabled = !uiState.isTesting && !uiState.isDownloading, onClick = { languagePicker = true })
        }
        if (languagePicker) AlertDialog(onDismissRequest = { languagePicker = false }, title = { Text(uiText("Speech recognition language")) },
            text = { Column { uiState.availableLocales.forEach { option ->
                TextButton(enabled = !uiState.isTesting && !uiState.isDownloading, onClick = { viewModel.selectLocale(option.tag); languagePicker = false }) { Text(uiText(option.displayName)) }
            } } }, confirmButton = { TextButton(onClick = { languagePicker = false }) { Text(uiText("Close")) } })

        Spacer(Modifier.height(20.dp))

        HaloWorkspaceSection(title = uiText("Local models")) {
            com.halo.providers.sherpa.stt.SherpaSpeechModelCatalog.recognitionModels.forEach { spec ->
                val installed = uiState.models[spec.id] == true
                val selected = uiState.selectedProviderId == spec.providerId
                val resident = uiState.residentModelId == spec.id
                val compatible = spec.supportsLanguage(uiState.selectedLocale)
                val downloading = uiState.downloadingModelId == spec.id
                HaloWorkspaceCard(accent = HaloPastelBlue) {
                    Text(spec.displayName, style = MaterialTheme.typography.titleSmall)
                    Text(uiText(when (spec.tier) {
                        com.halo.providers.sherpa.stt.SpeechModelTier.FAST -> "Fast"
                        com.halo.providers.sherpa.stt.SpeechModelTier.BALANCED -> "Balanced · Recommended"
                        com.halo.providers.sherpa.stt.SpeechModelTier.QUALITY -> "High quality"
                        com.halo.providers.sherpa.stt.SpeechModelTier.EXPERIMENTAL -> "Experimental multilingual"
                    }))
                    Text(uiText(spec.description))
                    Text(uiText("%1\$d MB", spec.expectedBytes / 1_000_000) + " · " + uiText(when {
                        downloading && uiState.verifying -> "Verifying"
                        downloading -> "Downloading"
                        uiState.failedModelId == spec.id && !installed -> "Failed"
                        resident -> "Installed · Resident"
                        selected && installed -> "Installed · Selected"
                        selected -> "Selected speech model is not installed"
                        installed -> "Installed"
                        else -> "Not installed"
                    }))
                    if (!compatible) Text(uiText("Model does not support %1\$s", uiState.selectedLocale))
                    if (uiState.selectedLocale == "lv" && spec.family == com.halo.providers.sherpa.stt.SpeechModelFamily.WHISPER) Text(uiText("Latvian supported"))
                    if (downloading) {
                        HaloProgressPresentation(uiText(if (uiState.verifying) "Verifying" else "Downloading"),
                            completed = if (uiState.verifying) null else uiState.downloadedBytes, total = uiState.expectedBytes)
                        TextButton(onClick = viewModel::pauseModelDownload) { Text(uiText("Pause download")) }
                        if (uiState.expectedBytes > 0) Text(uiText("%1\$d MB / %2\$d MB", uiState.downloadedBytes / 1_000_000, uiState.expectedBytes / 1_000_000))
                    }
                    HaloWorkspaceButton(text = uiText(if (installed) "Select" else "Download"), onClick = {
                        if (installed) viewModel.selectModel(spec.providerId) else viewModel.installModel(spec.id)
                    }, enabled = compatible && !selected && !uiState.isDownloading && !uiState.isTesting)
                    if (installed) TextButton(onClick = { viewModel.removeModel(spec.id) },
                        enabled = !selected && uiState.selectedModelName != spec.displayName && !uiState.isTesting && !uiState.isDownloading) { Text(uiText("Delete model")) }
                }
            }
        }
        HaloWorkspaceSection(title = uiText("Speech detector")) {
            Text(uiText(if (uiState.sileroInstalled) "Silero VAD · Ready" else "Energy fallback · Silero VAD not installed"))
            if (!uiState.sileroInstalled) HaloWorkspaceButton(text = uiText("Install Silero VAD"),
                onClick = { viewModel.installModel("silero-vad") }, enabled = !uiState.isDownloading && !uiState.isTesting)
            if (uiState.downloadingModelId == "silero-vad") HaloProgressPresentation(uiText(if (uiState.verifying) "Verifying" else "Downloading"), completed = if (uiState.verifying) null else uiState.downloadedBytes, total = uiState.expectedBytes)
        }
        uiState.downloadError?.let { HaloFeedbackSurface(uiText("Download or removal did not finish"), uiText("Check storage and your connection, then retry. The selected model is shown above."), kind = HaloFeedbackKind.FAILURE) }
        HaloWorkspaceSection(title = uiText("Try listening")) {
            Text(uiText("One local utterance. Audio stays in memory. No commands or conversation entry."))
            HaloFocusAction(text = uiText(if (uiState.testRecording) "Finish capture" else if (uiState.isTesting) "Recognizing…" else "Test speech recognition"), glyph = HaloGlyph.SPEAK,
                onClick = {
                    if (uiState.testRecording) viewModel.finishTest()
                    else if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) viewModel.startTest()
                    else microphonePermission.launch(android.Manifest.permission.RECORD_AUDIO)
                },
                enabled = !uiState.isDownloading && (!uiState.isTesting || uiState.testRecording))
            if (uiState.isTesting) TextButton(onClick = viewModel::cancelTest) { Text(uiText("Cancel test")) }
            uiState.testResult?.let { Text(it) }
            Text(uiText("Reference phrases (speak them yourself):"))
            SpeechTestReferences.forEach { Text(it) }
            TextButton(onClick = { testDetails = !testDetails }) { Text(uiText(if (testDetails) "Hide capture details" else "Capture details")) }
            if (testDetails) Text(uiText("Capture profile: threshold 0.50 · minimum speech 200 ms · silence 350 ms · maximum speech 8 s · pre-roll 400 ms"))
        }
        DisposableEffect(viewModel) { onDispose { viewModel.cancelTest() } }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun VoiceSettingsScreen(viewModel: VoiceSettingsViewModel, onBack: () -> Unit,
    onSpeechOutput: () -> Unit = {}, onSpeechRecognition: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val conversation by viewModel.conversation.collectAsStateWithLifecycle()
    val affinity by com.halo.app.di.ManualDependencyGraph.haloSpeechOutputCoordinator.session.affinity.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HaloWorkspaceHeader(title = uiText("Voice"), subtitle = uiText("Listening and speaking"), onBack = onBack)
        HaloWorkspaceSection(title = uiText("Input")) {
            Text(uiState.selectedModelName)
            Text(uiText(if (uiState.isDownloading) "Downloading" else uiState.recognitionPhase))
            Text(uiText("Language: %1\$s", uiState.availableLocales.firstOrNull { it.tag == uiState.selectedLocale }?.displayName ?: uiState.selectedLocale))
            HaloWorkspaceDestination(uiText("Speech recognition"), uiText("Choose a listening model and language"), onSpeechRecognition)
        }
        HaloWorkspaceSection(title = uiText("Output")) {
            viewModel.synthesisRegistry?.let { registry ->
                val selected by registry.selector.ttsSelection.collectAsStateWithLifecycle()
                val provider = registry.providers.firstOrNull { it.metadata.id == selected.id }
                    ?: registry.providers.firstOrNull { it.metadata.id == "provider.android.tts" }
                Text(provider?.metadata?.displayName ?: "Android TTS")
                (provider as? com.halo.providers.elevenlabs.tts.ElevenLabsTtsProvider)?.let { cloud ->
                    val health by cloud.state.collectAsStateWithLifecycle()
                    Text(cloud.cachedCatalogue()?.voices?.firstOrNull { it.id == cloud.voiceId() }?.name ?: "Choose a voice")
                    Text(uiText(if (!health.configured) "Setup required" else if (health.ready) "Ready" else "Configured"))
                }
            }
            HaloWorkspaceDestination(uiText("Speech output"), uiText("Choose a voice and output provider"), onSpeechOutput)
        }
        HaloWorkspaceSection(title = uiText("Conversation")) {
            HaloWorkspaceToggleRow(title = uiText("Interrupt Halo when I speak"), checked = conversation.spokenInterruptionEnabled,
                onCheckedChange = { viewModel.updateConversation(conversation.copy(spokenInterruptionEnabled = it)) })
            Text(uiText("Physical controls always interrupt immediately."))
            Text(uiText("Interruption timing"))
            com.halo.app.audio.InterruptionSensitivity.entries.forEach { timing ->
                TextButton(onClick = { viewModel.updateConversation(conversation.copy(interruptionSensitivity = timing)) }) {
                    Text((if (conversation.interruptionSensitivity == timing) "✓ " else "") + uiText(when (timing) {
                        com.halo.app.audio.InterruptionSensitivity.QUICK -> "Quick · respond to short speech"
                        com.halo.app.audio.InterruptionSensitivity.NATURAL -> "Natural · allow brief acknowledgements"
                        com.halo.app.audio.InterruptionSensitivity.DELIBERATE -> "Deliberate · wait for sustained speech"
                    }))
                }
            }
            HaloWorkspaceToggleRow(title = uiText("Start speaking early"), checked = conversation.streamingSpeechEnabled,
                onCheckedChange = { viewModel.updateConversation(conversation.copy(streamingSpeechEnabled = it)) })
            Text(uiText("Speak complete sentences while a tool-free answer is still being generated."))
            Text(uiText("Spoken answer style"))
            com.halo.sdk.runtime.SpokenResponseStyle.entries.forEach { style ->
                TextButton(onClick = { viewModel.updateConversation(conversation.copy(responseStyle = style)) }) {
                    Text((if (conversation.responseStyle == style) "✓ " else "") + uiText(style.name.lowercase().replaceFirstChar { it.uppercase() }))
                }
            }
        }
        HaloWorkspaceSection(title = uiText("Routing")) {
            Text(uiText("Reply where I spoke"), Modifier.padding(16.dp))
            HaloWorkspaceToggleRow(title = uiText("Phone fallback"), checked = conversation.wearablePhoneFallback,
                onCheckedChange = viewModel::setWearablePhoneFallback)
        }
        affinity?.let { route ->
            HaloWorkspaceSection(title = uiText("Last voice reply")) {
                Text("Origin: ${route.originLabel}")
                Text("Intended output: ${route.intendedTargetLabel}")
                Text("Actual output: ${route.resolvedEndpointLabel ?: "Not played"}")
                Text("Fallback: ${route.fallbackPolicyLabel}")
            }
        }
    }
}
