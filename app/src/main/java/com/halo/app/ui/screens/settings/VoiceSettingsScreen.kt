package com.halo.app.ui.screens.settings

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
            title = "Speech recognition",
            subtitle = "Local speech model and language.",
            onBack = onBack
        )

        TextButton(onClick = onSpeechLab) { Text("Speech Lab · compare one sample") }
        Spacer(Modifier.height(20.dp))

        HaloWorkspaceSection(title = "Speech recognition") {
            Text("Selected: ${uiState.selectedModelName}")
            TextButton(onClick = { viewModel.selectModel("AUTOMATIC") }, enabled = !uiState.isTesting && !uiState.isDownloading) {
                Text(if (uiState.selectedProviderId == "AUTOMATIC") "Automatic · Selected" else "Use automatic selection")
            }
            Text("Automatic prefers installed Base, then Tiny.")
        }
        Spacer(Modifier.height(20.dp))

        var languagePicker by remember { mutableStateOf(false) }
        HaloWorkspaceSection(title = "Language") {
            HaloWorkspaceRow(title = uiState.availableLocales.firstOrNull { it.tag == uiState.selectedLocale }?.displayName ?: uiState.selectedLocale,
                trailingText = "Change", onClick = { languagePicker = true })
        }
        if (languagePicker) AlertDialog(onDismissRequest = { languagePicker = false }, title = { Text("Speech recognition language") },
            text = { Column { uiState.availableLocales.forEach { option ->
                TextButton(onClick = { viewModel.selectLocale(option.tag); languagePicker = false }) { Text(option.displayName) }
            } } }, confirmButton = { TextButton(onClick = { languagePicker = false }) { Text("Close") } })

        Spacer(Modifier.height(20.dp))

        HaloWorkspaceSection(title = "Local models") {
            com.halo.providers.sherpa.stt.SherpaSpeechModelCatalog.recognitionModels.forEach { spec ->
                val installed = uiState.models[spec.id] == true
                val selected = uiState.selectedProviderId == spec.providerId
                val resident = uiState.residentModelId == spec.id
                val compatible = spec.supportsLanguage(uiState.selectedLocale)
                val downloading = uiState.downloadingModelId == spec.id
                HaloWorkspaceCard(accent = HaloPastelBlue) {
                    Text(spec.displayName, style = MaterialTheme.typography.titleSmall)
                    Text(when (spec.tier) {
                        com.halo.providers.sherpa.stt.SpeechModelTier.FAST -> "Fast"
                        com.halo.providers.sherpa.stt.SpeechModelTier.BALANCED -> "Balanced · Recommended"
                        com.halo.providers.sherpa.stt.SpeechModelTier.QUALITY -> "High quality"
                        com.halo.providers.sherpa.stt.SpeechModelTier.EXPERIMENTAL -> "Experimental multilingual"
                    })
                    Text(spec.description)
                    Text("${spec.expectedBytes / 1_000_000} MB · " + when {
                        downloading && uiState.verifying -> "Verifying"
                        downloading -> "Downloading"
                        uiState.failedModelId == spec.id && !installed -> "Failed"
                        resident -> "Installed · Resident"
                        selected && installed -> "Installed · Selected"
                        selected -> "Selected speech model is not installed"
                        installed -> "Installed"
                        else -> "Not installed"
                    })
                    if (!compatible) Text("Model does not support ${uiState.selectedLocale}")
                    if (uiState.selectedLocale == "lv" && spec.family == com.halo.providers.sherpa.stt.SpeechModelFamily.WHISPER) Text("Latvian supported")
                    if (downloading) {
                        LinearProgressIndicator(progress = { uiState.downloadProgress }, modifier = Modifier.fillMaxWidth())
                        Text("${uiState.downloadedBytes / 1_000_000} MB / ${uiState.expectedBytes / 1_000_000} MB · ${(uiState.downloadProgress * 100).toInt()}%")
                    }
                    HaloWorkspaceButton(text = if (installed) "Select" else "Download", onClick = {
                        if (installed) viewModel.selectModel(spec.providerId) else viewModel.installModel(spec.id)
                    }, enabled = compatible && !selected && !uiState.isDownloading && !uiState.isTesting)
                    if (installed) TextButton(onClick = { viewModel.removeModel(spec.id) },
                        enabled = !selected && uiState.selectedModelName != spec.displayName && !uiState.isTesting && !uiState.isDownloading) { Text("Delete model") }
                }
            }
        }
        HaloWorkspaceSection(title = "Speech detector") {
            Text(if (uiState.sileroInstalled) "Silero VAD · Ready" else "Energy fallback · Silero VAD not installed")
            if (!uiState.sileroInstalled) HaloWorkspaceButton(text = "Install Silero VAD",
                onClick = { viewModel.installModel("silero-vad") }, enabled = !uiState.isDownloading && !uiState.isTesting)
            if (uiState.downloadingModelId == "silero-vad") Text(if (uiState.verifying) "Verifying" else "Downloading ${(uiState.downloadProgress * 100).toInt()}%")
        }
        uiState.downloadError?.let { Text("Failed: $it", color = MaterialTheme.colorScheme.error) }
        HaloWorkspaceSection(title = "Speech diagnostics") {
            Text("One local utterance. Audio stays in memory. No commands or conversation entry.")
            HaloWorkspaceButton(text = if (uiState.testRecording) "Finish capture" else if (uiState.isTesting) "Recognizing…" else "Test speech recognition",
                onClick = {
                    if (uiState.testRecording) viewModel.finishTest()
                    else if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) viewModel.startTest()
                    else microphonePermission.launch(android.Manifest.permission.RECORD_AUDIO)
                },
                enabled = !uiState.isDownloading && (!uiState.isTesting || uiState.testRecording))
            if (uiState.isTesting) TextButton(onClick = viewModel::cancelTest) { Text("Cancel test") }
            uiState.testResult?.let { Text(it) }
            Text("Capture profile: threshold 0.50 · minimum speech 200 ms · silence 350 ms · maximum speech 8 s · pre-roll 400 ms")
            Text("Reference phrases (speak them yourself):")
            SpeechTestReferences.forEach { Text(it) }
        }
        DisposableEffect(viewModel) { onDispose { viewModel.cancelTest() } }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun VoiceSettingsScreen(viewModel: VoiceSettingsViewModel, onBack: () -> Unit,
    onSpeechOutput: () -> Unit = {}, onSpeechRecognition: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HaloWorkspaceHeader(title = "Voice", subtitle = "Speech input, output and routing", onBack = onBack)
        HaloWorkspaceSection(title = "Input") {
            Text(uiState.selectedModelName)
            Text(if (uiState.isInstalled) "Ready" else if (uiState.isDownloading) "Downloading" else "Not installed")
            Text("Language: " + (uiState.availableLocales.firstOrNull { it.tag == uiState.selectedLocale }?.displayName ?: uiState.selectedLocale))
            TextButton(onClick = onSpeechRecognition) { Text("Speech recognition") }
        }
        HaloWorkspaceSection(title = "Output") {
            viewModel.synthesisRegistry?.let { registry ->
                val selected by registry.selector.ttsSelection.collectAsStateWithLifecycle()
                val provider = registry.providers.firstOrNull { it.metadata.id == selected.id }
                    ?: registry.providers.firstOrNull { it.metadata.id == "provider.android.tts" }
                Text(provider?.metadata?.displayName ?: "Android TTS")
                (provider as? com.halo.providers.elevenlabs.tts.ElevenLabsTtsProvider)?.let { cloud ->
                    val health by cloud.state.collectAsStateWithLifecycle()
                    Text(cloud.cachedCatalogue()?.voices?.firstOrNull { it.id == cloud.voiceId() }?.name ?: "Choose a voice")
                    Text(if (!health.configured) "Setup required" else if (health.ready) "Ready" else "Configured")
                }
            }
            TextButton(onClick = onSpeechOutput) { Text("Speech output") }
        }
        HaloWorkspaceSection(title = "Routing") {
            HaloWorkspaceRow(title = "Glasses replies", trailingText = "On")
            HaloWorkspaceRow(title = "Phone fallback", trailingText = if (uiState.wearablePhoneFallback) "On" else "Off",
                onClick = { viewModel.setWearablePhoneFallback(!uiState.wearablePhoneFallback) })
        }
    }
}
