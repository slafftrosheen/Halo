package com.halo.app.ui.toolkit

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.halo.app.R
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.storage.haloStorageLayout
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import com.halo.app.ui.mini.miniHaloActivityOwner
import com.halo.app.ui.presence.BindShellPageActions
import com.halo.app.ui.presence.ShellPageAction
import com.halo.app.ui.presence.ShellPageOwner
import com.halo.engines.speech.SpeechGateResult
import com.halo.engines.speech.SpeechRecognitionQuality
import com.halo.engines.speech.SpeechTranscriptGate
import com.halo.providers.sherpa.stt.SherpaSpeechModelCatalog
import com.halo.providers.sherpa.stt.SherpaSpeechRecognizerFactory
import com.halo.sdk.provider.*
import java.io.File
import kotlinx.coroutines.*

private enum class PrivateChatPhase { IDLE, CAPTURING, TRANSCRIBING, ANSWERING }
private const val privateQuestionLimit = 4_000

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HaloPrivateChatScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val window = miniHaloActivityOwner(context)?.window
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsState()
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(TextFieldValue()) }
    var answer by remember { mutableStateOf("") }
    var phase by remember { mutableStateOf(PrivateChatPhase.IDLE) }
    var error by remember { mutableStateOf<Int?>(null) }
    var transaction by remember { mutableStateOf<Job?>(null) }
    var captureEnd by remember { mutableStateOf<CompletableDeferred<Unit>?>(null) }
    var micPending by remember { mutableStateOf(false) }
    var epoch by remember { mutableLongStateOf(0L) }
    var privacyActive by remember(window) { mutableStateOf(false) }

    fun stopWork() {
        epoch++
        transaction?.cancel()
        transaction = null
        captureEnd = null
        micPending = false
        phase = PrivateChatPhase.IDLE
    }
    fun wipe() {
        stopWork()
        draft = TextFieldValue()
        answer = ""
        error = null
    }
    fun close() {
        wipe()
        onBack()
    }
    fun runWork(next: PrivateChatPhase, failure: Int, action: suspend (Long) -> Unit) {
        if (phase != PrivateChatPhase.IDLE || !privacyActive || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        val current = ++epoch
        phase = next
        error = null
        transaction = scope.launch {
            try { action(current) }
            catch (timeout: TimeoutCancellationException) { if (current == epoch) error = failure }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (current == epoch) error = failure }
            finally {
                if (current == epoch) {
                    captureEnd = null
                    phase = PrivateChatPhase.IDLE
                    transaction = null
                }
            }
        }
    }
    fun dictate() {
        runWork(PrivateChatPhase.CAPTURING, R.string.private_chat_dictation_error) { current ->
            val finish = CompletableDeferred<Unit>()
            captureEnd = finish
            val text = Graph.haloPause.whileActive {
                privateDictation(context, finish) {
                    if (current == epoch) phase = PrivateChatPhase.TRANSCRIBING
                }
            }
            currentCoroutineContext().ensureActive()
            if (current == epoch) {
                val joined = listOf(draft.text, text).filter(String::isNotBlank).joinToString("\n").take(privateQuestionLimit)
                draft = TextFieldValue(joined, TextRange(joined.length))
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (privacyActive) {
            if (granted) micPending = true else error = R.string.private_chat_mic_permission
        }
    }
    LaunchedEffect(lifecycleState, micPending) {
        if (micPending && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) {
            micPending = false
            dictate()
        }
    }
    DisposableEffect(window, lifecycle) {
        val flag = WindowManager.LayoutParams.FLAG_SECURE
        val alreadySecure = window?.let { it.attributes.flags and flag != 0 } == true
        if (window != null) {
            window.addFlags(flag)
            privacyActive = true
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) wipe()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            wipe()
            privacyActive = false
            if (!alreadySecure) window?.clearFlags(flag)
        }
    }
    BackHandler { close() }

    val title = stringResource(R.string.private_chat_title)
    val closeLabel = stringResource(R.string.private_chat_close)
    val help = stringResource(R.string.private_chat_help)
    val status = stringResource(R.string.private_chat_status)
    BindShellPageActions(ShellPageOwner("private_chat", statusLabel = status, guidanceBlocked = true),
        listOf(ShellPageAction("page.private_chat.close", closeLabel, HaloGlyph.STOP) { close() }))

    val edge = MaterialTheme.colorScheme.outline
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            TextButton({ close() }, modifier = Modifier.heightIn(min = 48.dp)) { Text(closeLabel) }
        }
        Column(Modifier.fillMaxWidth().drawBehind {
            val stroke = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
            drawRoundRect(edge, cornerRadius = CornerRadius(16.dp.toPx()), style = stroke)
        }.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HaloIcon(HaloGlyph.SECURITY, null)
                Text(stringResource(R.string.private_chat_origin), style = MaterialTheme.typography.titleMedium)
            }
            Text(help, style = MaterialTheme.typography.bodyMedium)
        }
        if (privacyActive) {
            OutlinedTextField(draft, { if (it.text.length <= privateQuestionLimit) draft = it },
                modifier = Modifier.fillMaxWidth(), enabled = phase == PrivateChatPhase.IDLE, minLines = 3, maxLines = 7,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                label = { Text(stringResource(R.string.private_chat_question)) })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val question = draft.text.trim()
                    if (question.isNotEmpty()) runWork(PrivateChatPhase.ANSWERING, R.string.private_chat_model_error) { current ->
                        answer = ""
                        val result = privateAnswer(question)
                        currentCoroutineContext().ensureActive()
                        if (current == epoch) answer = result
                    }
                }, enabled = draft.text.isNotBlank() && phase == PrivateChatPhase.IDLE,
                    modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.private_chat_ask))
                }
                OutlinedButton(onClick = {
                    if (phase == PrivateChatPhase.CAPTURING) captureEnd?.complete(Unit)
                    else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) dictate()
                    else permission.launch(Manifest.permission.RECORD_AUDIO)
                }, enabled = phase == PrivateChatPhase.IDLE || phase == PrivateChatPhase.CAPTURING,
                    modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(if (phase == PrivateChatPhase.CAPTURING) R.string.private_chat_finish_dictation else R.string.private_chat_dictate))
                }
                if (phase != PrivateChatPhase.IDLE) TextButton({ stopWork() }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        }
        if (phase != PrivateChatPhase.IDLE) Text(stringResource(when (phase) {
            PrivateChatPhase.CAPTURING -> R.string.private_chat_listening
            PrivateChatPhase.TRANSCRIBING -> R.string.private_chat_transcribing
            else -> R.string.private_chat_answering
        }), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        if (answer.isNotBlank()) Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text(answer, Modifier.fillMaxWidth().padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** Resolve against the ordinary policy. Generate through the pinned delegate without shared telemetry or a cognitive turn. */
private suspend fun privateAnswer(question: String): String = withTimeout(60_000) {
    val request = AiRequest(text = question, maxOutputTokens = 512, publishStreamingTokens = false,
        reasoningMode = AiReasoningMode.DISABLED, toolUseMode = ToolUseMode.NONE)
    val workload = AiWorkloadSpec(kind = AiWorkloadKind.QUESTION, streaming = AiFeatureRequirement.NOT_NEEDED,
        allowRemote = false, allowFallback = false)
    val target = Graph.aiProviderRouter.resolveExecutionTarget(request, workload)
    check(target.provider.isLocal && target.model.executionLocation == AiExecutionLocation.ON_DEVICE)
    ensureActive()
    val pinned = request.copy(modelId = target.modelId)
    pinned.validateAgainst(target.effectiveCapabilities)
    // The native provider acquires and releases its own computation lease.
    val response = Graph.haloPause.whileActive { target.provider.generate(pinned, AiContext.EMPTY) }
    check(response.toolCalls.isEmpty() && response.payloadRefId == null)
    check(response.providerId.isBlank() || response.providerId == target.providerId)
    val requestedModel = response.requestedModelId ?: response.modelId
    check(requestedModel == null || requestedModel == target.modelId)
    response.text.trim().take(16_000).also { check(it.isNotBlank()) }
}

/** The microphone lease, receive buffers and transcript gate are shared. The decoder is explicitly native. */
private suspend fun privateDictation(context: Context, finish: CompletableDeferred<Unit>, processing: () -> Unit): String {
    val root = haloStorageLayout(context).speechModels
    val language = Graph.speechDiagnosticRunner.language().substringBefore('-').substringBefore('_')
    val model = withContext(Dispatchers.IO) {
        fun usable(spec: SherpaSpeechModelCatalog.ModelSpec) = spec.supportsLanguage(language) &&
            SherpaSpeechModelCatalog.isInstalled(File(root, spec.directoryName), spec)
        val selected = Graph.providers.speechSelector?.sttSelection?.value?.id ?: "AUTOMATIC"
        SherpaSpeechModelCatalog.resolve(selected, ::usable)
            ?: SherpaSpeechModelCatalog.resolve("AUTOMATIC", ::usable)
            ?: SherpaSpeechModelCatalog.recognitionModels.firstOrNull(::usable)
    } ?: error("Local speech model unavailable")
    val session = Graph.speechDiagnosticRunner.captureSession(finish, maxDurationMs = 15_000)
    try {
        processing()
        return Graph.hostGraph.audioPerceptionEngine.withDiagnosticRecognitionOwnership {
            val decoder = SherpaSpeechRecognizerFactory.create(File(root, model.directoryName), model,
                language, Graph.providerGraph.nativeResourceCoordinator)
            try {
                val result = withContext(Dispatchers.Default) { decoder.recognize(session.capture.audio) }
                val activity = session.activity
                val gate = SpeechTranscriptGate().evaluate(result.text, SpeechRecognitionQuality(confidence = result.confidence,
                    audioDurationMs = activity.durationMs, voicedDurationMs = activity.voicedDurationMs,
                    voicedRatio = activity.voicedRatio, peakAmplitude = activity.peakAmplitude,
                    language = result.language, qualityEstimator = activity.estimator.name))
                check(gate !is SpeechGateResult.Rejected)
                result.text.trim().also { check(it.isNotBlank()) }
            } finally {
                withContext(NonCancellable + Dispatchers.Default) { decoder.close(); decoder.awaitQuiescent() }
            }
        }
    } finally { session.close() }
}
