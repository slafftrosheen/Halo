package com.halo.app.ui.screens.settings

import com.halo.app.language.uiText
import com.halo.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import com.halo.app.ui.presence.HaloSignalFace
import com.halo.app.ui.presence.HaloSignalPresentation
import com.halo.app.ui.presence.HaloSignalState


import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
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
    SettingsChildZone("Listening", onBack)
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
            .padding(horizontal = com.halo.app.ui.design.HaloSpacing.screen, vertical = 14.dp)
    ) {
        if (!com.halo.app.ui.presence.LocalShellHaloOrb.current) HaloWorkspaceHeader(
            title = uiText("Listening"),
            subtitle = uiText("Choose how halo listens."),
            onBack = onBack
        )

        Spacer(Modifier.height(20.dp))

        HaloSettingsSection(title = uiText("Listening")) {
            Text(uiText(listeningChoiceLabel(uiState.selectedProviderId)))
            Text(uiText(uiState.recognitionPhase))

            Text(uiText("Listening downloads stay on this phone."), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { viewModel.selectModel("AUTOMATIC") }, enabled = !uiState.isTesting && !uiState.isDownloading) {
                Text(uiText(if (uiState.selectedProviderId == "AUTOMATIC") "Automatic · Selected" else "Use automatic selection"))
            }
            Text(uiText("Automatic uses an installed listening download."))
        }
        Spacer(Modifier.height(20.dp))

        var languagePicker by rememberSaveable { mutableStateOf(false) }
        var removeDownload by rememberSaveable { mutableStateOf<String?>(null) }
        var removeVad by rememberSaveable { mutableStateOf(false) }
        removeDownload?.let { model ->
            AlertDialog(onDismissRequest = { removeDownload = null },
                title = { Text(uiText("Remove this listening download?")) },
                text = { Text(uiText("Your selected listening choice stays the same. You can download this one again later.")) },
                confirmButton = { TextButton(onClick = { viewModel.removeModel(model); removeDownload = null }) { Text(uiText("Remove download")) } },
                dismissButton = { TextButton(onClick = { removeDownload = null }) { Text(uiText("Cancel")) } })
        }
        if (removeVad) AlertDialog(onDismissRequest = { removeVad = false },
            title = { Text(uiText("Remove hands-free support?")) },
            text = { Text(uiText("Halo will use basic speech activity detection. You can download hands-free support again later.")) },
            confirmButton = { TextButton(onClick = { viewModel.removeModel("silero-vad"); removeVad = false }) { Text(uiText("Remove download")) } },
            dismissButton = { TextButton(onClick = { removeVad = false }) { Text(uiText("Cancel")) } })
        HaloSettingsSection(title = uiText("Language")) {
            HaloWorkspaceRow(title = uiState.availableLocales.firstOrNull { it.tag == uiState.selectedLocale }?.displayName ?: uiState.selectedLocale,
                trailingText = uiText("Change"), enabled = !uiState.isTesting && !uiState.isDownloading, onClick = { languagePicker = true })
        }
        if (languagePicker) AlertDialog(onDismissRequest = { languagePicker = false }, title = { Text(uiText("Listening language")) },
            text = { Column { uiState.availableLocales.forEach { option ->
                TextButton(enabled = !uiState.isTesting && !uiState.isDownloading, onClick = { viewModel.selectLocale(option.tag); languagePicker = false }) { Text(uiText(option.displayName)) }
            } } }, confirmButton = { TextButton(onClick = { languagePicker = false }) { Text(uiText("Close")) } })

        Spacer(Modifier.height(20.dp))

        HaloSettingsSection(title = uiText("Local models")) {
            com.halo.providers.sherpa.stt.SherpaSpeechModelCatalog.recognitionModels.forEach { spec ->
                val installed = uiState.models[spec.id] == true
                val selected = uiState.selectedProviderId == spec.providerId
                val resident = uiState.residentModelId == spec.id
                val compatible = spec.supportsLanguage(uiState.selectedLocale)
                val downloading = uiState.downloadingModelId == spec.id
                HaloWorkspaceCard(accent = HaloPastelBlue) {
                    Text(uiText(listeningChoiceLabel(spec.providerId)), style = MaterialTheme.typography.titleSmall)
                    Text(uiText(when (spec.tier) {
                        com.halo.providers.sherpa.stt.SpeechModelTier.FAST -> "Fast"
                        com.halo.providers.sherpa.stt.SpeechModelTier.BALANCED -> "Balanced · Recommended"
                        com.halo.providers.sherpa.stt.SpeechModelTier.QUALITY -> "High quality"
                        com.halo.providers.sherpa.stt.SpeechModelTier.EXPERIMENTAL -> "Experimental multilingual"
                    }))
                    Text(uiText(if (spec.tier == com.halo.providers.sherpa.stt.SpeechModelTier.FAST) "A smaller download for short requests." else "For longer dictation and more languages."))
                    Text(uiText("%1\$d MB", spec.expectedBytes / 1_000_000) + " · " + uiText(when {
                        downloading && uiState.verifying -> "Verifying"
                        downloading -> "Downloading"
                        uiState.failedModelId == spec.id && !installed -> "Failed"
                        resident -> "Downloaded · in use"
                        selected && installed -> "Installed · Selected"
                        selected -> "Download this choice before listening"
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
                    if (installed) TextButton(onClick = { removeDownload = spec.id },
                        enabled = !selected && uiState.selectedModelName != spec.displayName && !uiState.isTesting && !uiState.isDownloading) { Text(uiText("Remove download")) }
                }
            }
        }
        HaloSettingsSection(title = uiText("Hands-free listening")) {
            Text(uiText(if (uiState.sileroInstalled) "Hands-free support downloaded" else "Basic listening available"))
            if (!uiState.sileroInstalled) HaloWorkspaceButton(text = uiText("Download hands-free support"),
                onClick = { viewModel.installModel("silero-vad") }, enabled = !uiState.isDownloading && !uiState.isTesting)
            else TextButton(onClick = { removeVad = true }, enabled = !uiState.isDownloading && !uiState.isTesting) { Text(uiText("Remove hands-free support")) }
            if (uiState.downloadingModelId == "silero-vad") HaloProgressPresentation(uiText(if (uiState.verifying) "Verifying" else "Downloading"), completed = if (uiState.verifying) null else uiState.downloadedBytes, total = uiState.expectedBytes)
        }
        uiState.downloadError?.let { HaloFeedbackSurface(uiText("Download or removal did not finish"), uiText("Check storage and your connection, then retry. The selected model is shown above."), kind = HaloFeedbackKind.FAILURE) }
        HaloSettingsSection(title = uiText("Try listening")) {
            Text(uiText("Say a short sentence. It stays on this phone and does not start an action."))
            HaloFocusAction(text = uiText(if (uiState.testRecording) "Finish capture" else if (uiState.isTesting) "Recognizing…" else "Try listening"), glyph = HaloGlyph.SPEAK,
                onClick = {
                    if (uiState.testRecording) viewModel.finishTest()
                    else if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) viewModel.startTest()
                    else microphonePermission.launch(android.Manifest.permission.RECORD_AUDIO)
                },
                enabled = !uiState.isDownloading && (!uiState.isTesting || uiState.testRecording))
            if (uiState.isTesting) TextButton(onClick = viewModel::cancelTest) { Text(uiText("Cancel test")) }
            uiState.testResult?.let { Text(it) }
            Text(uiText("Something to try:"))
            SpeechTestReferences.forEach { Text(it) }

        }
        DisposableEffect(viewModel) { onDispose { viewModel.cancelTest() } }

        Spacer(Modifier.height(32.dp))
    }
}

/** The mic-test CTA reflects the single SpeechRecognitionProbe lease owner. */
internal enum class VoiceMicAction { START, FINISH, WAIT, DISABLED }
internal fun voiceMicAction(state: VoiceSettingsUiState): VoiceMicAction = when {
    state.isDownloading -> VoiceMicAction.DISABLED
    state.testRecording -> VoiceMicAction.FINISH
    state.isTesting -> VoiceMicAction.WAIT
    else -> VoiceMicAction.START
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceSettingsScreen(viewModel: VoiceSettingsViewModel, onBack: () -> Unit,
    onSpeechOutput: () -> Unit = {}, onSpeechRecognition: () -> Unit = {}) {
    SettingsChildZone("Voice & reading", onBack, listOf(settingsChildLink("onSpeechOutput", "Speaking", onSpeechOutput), settingsChildLink("onSpeechRecognition", "Listening", onSpeechRecognition)))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val conversation by viewModel.conversation.collectAsStateWithLifecycle()
    val affinity by com.halo.app.di.ManualDependencyGraph.haloSpeechOutputCoordinator.session.affinity.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var micPermissionDenied by rememberSaveable { mutableStateOf(false) }
    val micPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        micPermissionDenied = !granted
        if (granted) viewModel.startTest()
    }
    // The existing SpeechRecognitionProbe owns the microphone and performs the
    // recognition test. No permission request or recording occurs on screen entry.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.cancelTest()
                Lifecycle.Event.ON_RESUME -> viewModel.refreshState()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.cancelTest()
        }
    }
    var detailedConversation by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (!com.halo.app.ui.presence.LocalShellHaloOrb.current) HaloWorkspaceHeader(title = uiText("Voice & reading"), subtitle = uiText("Listening, dictation and reading aloud"), onBack = onBack)
        Surface(
            shape = com.halo.app.ui.design.HaloShapes.card,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(1.dp,
                MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HaloSignalFace(
                        HaloSignalPresentation(
                            state = if (uiState.testRecording) HaloSignalState.CAPTURING
                                else if (uiState.isTesting) HaloSignalState.WAITING
                                else HaloSignalState.IDLE,
                            label = if (uiState.testRecording) "Capturing voice"
                                else if (uiState.isTesting) "Recognizing…" else "Ready"
                        ),
                        size = 88.dp,
                        contentDescription = stringResource(R.string.brand_voice_try_mic)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.brand_voice_try_mic),
                            style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.brand_voice_mic_explanation),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Button(
                    onClick = {
                        if (uiState.testRecording) viewModel.finishTest()
                        else if (androidx.core.content.ContextCompat.checkSelfPermission(
                            context, android.Manifest.permission.RECORD_AUDIO
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            micPermissionDenied = false
                            viewModel.startTest()
                        } else micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                    },
                    enabled = voiceMicAction(uiState) in setOf(VoiceMicAction.START, VoiceMicAction.FINISH),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) {
                    Text(stringResource(when (voiceMicAction(uiState)) {
                        VoiceMicAction.FINISH -> R.string.brand_voice_finish
                        VoiceMicAction.WAIT -> R.string.brand_voice_recognizing
                        VoiceMicAction.START, VoiceMicAction.DISABLED -> R.string.brand_voice_start_test
                    }))
                }
                if (micPermissionDenied) {
                    Text(stringResource(R.string.brand_voice_mic_denied),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = {
                        context.startActivity(android.content.Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.parse("package:${context.packageName}")
                        ))
                    }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(uiText("Open Android app settings"))
                    }
                }
                if (uiState.isTesting) TextButton(onClick = viewModel::cancelTest) {
                    Text(uiText("Cancel test"))
                }
                uiState.testResult?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
        HaloSettingsSection(title = uiText("Input")) {
            Text(uiText(listeningChoiceLabel(uiState.selectedProviderId)))
            Text(uiText(if (uiState.isDownloading) "Downloading" else uiState.recognitionPhase))
            Text(uiText("Language: %1\$s", uiState.availableLocales.firstOrNull { it.tag == uiState.selectedLocale }?.displayName ?: uiState.selectedLocale))
            HaloWorkspaceDestination(uiText("Listening"), uiText("Choose how halo listens"), onSpeechRecognition)
        }
        HaloSettingsSection(title = uiText("Output")) {
            viewModel.synthesisRegistry?.let { registry ->
                val selected by registry.selector.ttsSelection.collectAsStateWithLifecycle()
                val provider = registry.providers.firstOrNull { it.metadata.id == selected.id }
                    ?: registry.providers.firstOrNull { it.metadata.id == "provider.android.tts" }
                Text(uiText(voiceChoiceLabel(provider?.metadata?.id)))
                (provider as? com.halo.app.audio.KokoroSpeechProvider)?.let { local ->
                    val status by local.state.collectAsStateWithLifecycle()
                    Text(uiText(status.phase))
                    Text(uiText(if (local.multilingualInstalled()) "English and Chinese" else "English only"), style = MaterialTheme.typography.bodySmall)
                }
                (provider as? com.halo.providers.elevenlabs.tts.ElevenLabsTtsProvider)?.let { cloud ->
                    val health by cloud.state.collectAsStateWithLifecycle()
                    Text(cloud.cachedCatalogue()?.voices?.firstOrNull { it.id == cloud.voiceId() }?.name ?: "Choose a voice")
                    Text(uiText(if (!health.configured) "Setup required" else if (health.ready) "Ready" else "Configured"))
                }
            }
            HaloWorkspaceDestination(uiText("halo's voice"), uiText("Choose a voice and where to hear it"), onSpeechOutput)
        }
        val reading = com.halo.app.di.ManualDependencyGraph.readAloud
        val readSpeed by reading.speedPreference.collectAsStateWithLifecycle()
        val followWords by reading.followWords.collectAsStateWithLifecycle()
        val autoAnswers by reading.autoReadAnswers.collectAsStateWithLifecycle()
        val readingNotice by reading.preferenceNotice.collectAsStateWithLifecycle()
        HaloSettingsSection(title = uiText("Reading")) {
            readingNotice?.let { Text(uiText(it), color = MaterialTheme.colorScheme.error) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(.75, 1.0, 1.25, 1.5).forEach { rate ->
                    FilterChip(readSpeed == rate, { reading.speed(rate) }, { Text("${rate}×") })
                }
            }
            HaloWorkspaceToggleRow(title = uiText("Follow words"), checked = followWords, onCheckedChange = reading::setFollowWords)
            HaloWorkspaceToggleRow(title = uiText("Read new answers aloud"), checked = autoAnswers, onCheckedChange = reading::setAutoReadAnswers)
            Text(uiText("Automatic reading uses an on-device voice. Cloud reading always asks before sending text."),
                style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick = { detailedConversation = !detailedConversation },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(if (detailedConversation) R.string.brand_voice_hide_advanced
                else R.string.brand_voice_advanced))
        }
        if (detailedConversation) {
        HaloSettingsSection(title = uiText("Conversation")) {
            HaloWorkspaceToggleRow(title = uiText("Interrupt halo when I speak"), checked = conversation.spokenInterruptionEnabled,
                onCheckedChange = { viewModel.updateConversation(conversation.copy(spokenInterruptionEnabled = it)) })
            Text(uiText("Physical controls always interrupt immediately."))
            Text(uiText("How quickly halo pauses"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                com.halo.app.audio.InterruptionSensitivity.entries.forEach { timing ->
                    FilterChip(
                        selected = conversation.interruptionSensitivity == timing,
                        onClick = { viewModel.updateConversation(conversation.copy(interruptionSensitivity = timing)) },
                        modifier = Modifier.heightIn(min = 48.dp),
                        label = { Text(uiText(when (timing) {
                            com.halo.app.audio.InterruptionSensitivity.QUICK -> "Quick · respond to short speech"
                            com.halo.app.audio.InterruptionSensitivity.NATURAL -> "Natural · allow brief acknowledgements"
                            com.halo.app.audio.InterruptionSensitivity.DELIBERATE -> "Deliberate · wait for sustained speech"
                        })) }
                    )
                }
            }
            HaloWorkspaceToggleRow(title = uiText("Start speaking early"), checked = conversation.streamingSpeechEnabled,
                onCheckedChange = { viewModel.updateConversation(conversation.copy(streamingSpeechEnabled = it)) })
            Text(uiText("Start reading complete sentences while halo is still writing."))
            Text(uiText("Spoken answer style"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                com.halo.sdk.runtime.SpokenResponseStyle.entries.forEach { style ->
                    FilterChip(
                        selected = conversation.responseStyle == style,
                        onClick = { viewModel.updateConversation(conversation.copy(responseStyle = style)) },
                        modifier = Modifier.heightIn(min = 48.dp),
                        label = { Text(uiText(style.name.lowercase().replaceFirstChar { it.uppercase() })) }
                    )
                }
            }
        }
        HaloSettingsSection(title = uiText("Routing")) {
            Text(uiText("Reply where I spoke"), Modifier.padding(16.dp))
            HaloWorkspaceToggleRow(title = uiText("Phone fallback"), checked = conversation.wearablePhoneFallback,
                onCheckedChange = viewModel::setWearablePhoneFallback)
        }

        }
    }
}

/** Capability names, never model identifiers, on ordinary voice settings. */
internal fun listeningChoiceLabel(providerId: String): String = when {
    providerId == "AUTOMATIC" -> "Listening · automatic"
    providerId.contains("tiny", ignoreCase = true) -> "Listening · light"
    providerId.contains("small", ignoreCase = true) -> "Listening · accurate"
    providerId.contains("base", ignoreCase = true) -> "Listening · balanced"
    else -> "Listening · multilingual"
}

internal fun voiceChoiceLabel(providerId: String?): String = when {
    providerId == null || providerId == "AUTOMATIC" -> "Automatic voice"
    providerId.contains("android", ignoreCase = true) -> "Phone's voice"
    providerId.contains("elevenlabs", ignoreCase = true) -> "Online voices"
    providerId.contains("supertonic", ignoreCase = true) -> "Natural voices"
    else -> "Downloaded voices"
}
