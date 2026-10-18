package com.halo.app.ui.screens.settings

import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.halo.app.ai.WorkloadLabAction
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.language.uiText
import com.halo.sdk.provider.*
import kotlinx.coroutines.*

/** Passive cached inventory on entry; native inspection and probes require an explicit button. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun WorkloadDiagnosticsPanel(router: AiProviderRouter) {
    val lab=remember { ManualDependencyGraph.workloadLab }
    val storage=remember { ManualDependencyGraph.storage }
    val scope=rememberCoroutineScope()
    val clipboard=LocalClipboardManager.current
    val result by lab.result.collectAsState()
    val busy by lab.busy.collectAsState()
    val synthesis by com.halo.app.audio.WorkloadSpeechTelemetry.synthesis.collectAsState()
    val playback by com.halo.app.audio.WorkloadSpeechTelemetry.playback.collectAsState()
    val resources by lab.resourceState.collectAsState()
    var selected by remember { mutableStateOf<String?>(null) }
    var workload by remember { mutableStateOf(LocalWorkload.CHAT) }
    var job by remember { mutableStateOf<Job?>(null) }
    var dimension by remember { mutableStateOf("768") }
    var sourceIds by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var adaptive by remember { mutableStateOf(storage.getBoolean("workload_adaptive_enabled",false)) }
    var prewarm by remember { mutableStateOf(storage.getBoolean("workload_prewarm_enabled",false)) }
    var semantic by remember { mutableStateOf(storage.getBoolean("semantic_enabled",false)) }
    var failed by remember { mutableStateOf(false) }
    var embeddingChoice by remember { mutableStateOf(com.halo.app.ai.WorkloadPreferences(storage).selectedEmbedding()) }
    val models=lab.installed()
    // Use the actual runtime selector and native pack state. Specialized roles
    // do not share the conversational AiModelRole model picker.
    val speechSelector = remember { ManualDependencyGraph.providers.speechSelector }
    val sttChoice = speechSelector?.sttSelection?.collectAsState()?.value?.id ?: "AUTOMATIC"
    val ttsChoice = speechSelector?.ttsSelection?.collectAsState()?.value?.id ?: "AUTOMATIC"
    val sttAvailable by ManualDependencyGraph.speechRecognitionAvailability.collectAsState()
    val supertonic = remember {
        ManualDependencyGraph.providers.ttsProviders.filterIsInstance<com.halo.app.audio.SupertonicSpeechProvider>().firstOrNull()
    }
    val kokoro = remember {
        ManualDependencyGraph.providers.ttsProviders.filterIsInstance<com.halo.app.audio.KokoroSpeechProvider>().firstOrNull()
    }
    val supertonicStatus = supertonic?.packs?.state?.collectAsState()?.value
    val kokoroStatus = kokoro?.state?.collectAsState()?.value
    // Embedding output dimension comes from native ModelInfo and is an index
    // identity constraint, not a preference users should guess.
    LaunchedEffect(workload,selected,result.action,result.modelId,result.status) {
        if(workload==LocalWorkload.EMBED) models.firstOrNull { it.id==selected }
            ?.let(lab::embeddingDimension)?.let { dimension=it.toString() }
    }
    fun setting(key: String,value: Boolean,done: ()->Unit) { scope.launch {
        try { withContext(Dispatchers.IO) { storage.putBoolean(key,value) }; done(); failed=false }
        catch(cancelled: CancellationException) { throw cancelled }
        catch(_: Exception) { failed=true }
    } }
    DisposableEffect(lab) { onDispose { job?.cancel(); lab.clearPrivatePreview() } }
    Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(uiText("Workload diagnostics"),style=MaterialTheme.typography.titleMedium)
        Text(uiText("Opening this panel does not run a model. Measurements require explicit actions."))
        FlowRow { LocalWorkload.entries.forEach { kind -> TextButton(onClick={workload=kind}) { Text(kind.name) } } }
        when (workload) {
            LocalWorkload.STT -> {
                Text("STT: $sttChoice · ${if (sttAvailable) "AVAILABLE" else "NOT_READY"}",
                    style=MaterialTheme.typography.bodySmall)
                Text(uiText("Recognition readiness is not a passed transcription probe."),
                    style=MaterialTheme.typography.bodySmall)
            }
            LocalWorkload.TTS -> {
                Text("TTS: $ttsChoice", style=MaterialTheme.typography.bodySmall)
                Text("Kokoro: ${kokoroStatus?.phase ?: "NOT_CONFIGURED"} · pack=${kokoroStatus?.modelVersion?.take(12) ?: "NONE"}",
                    style=MaterialTheme.typography.bodySmall)
                Text("Supertonic: ${if (supertonicStatus?.selectedPackAvailable == true) "INSTALLED" else "NOT_INSTALLED"} · ${if(supertonicStatus?.audibleConfirmed == true) "VOICE_CONFIRMED" else "AUDIBLE_TEST_REQUIRED"}",
                    style=MaterialTheme.typography.bodySmall)
                Text(uiText("Voice models use Speech output settings; installed does not imply an audible pass."),
                    style=MaterialTheme.typography.bodySmall)
            }
            LocalWorkload.WAKE, LocalWorkload.VAD, LocalWorkload.INTENT -> {
                Text(uiText("This runtime function uses the existing detector or deterministic command path."),
                    style=MaterialTheme.typography.bodySmall)
                Text(uiText("A neural model is optional; this workload is not a conversational model."),
                    style=MaterialTheme.typography.bodySmall)
            }
            else -> {
                val readiness=lab.readiness(workload,router)
                Text("${workload.name}: ${readiness.state.name} · ${readiness.reasonCode}",style=MaterialTheme.typography.bodySmall)
                Text("${readiness.target?.providerId.orEmpty()} / ${readiness.target?.modelId.orEmpty()}",style=MaterialTheme.typography.bodySmall)
            }
        }
        Text(uiText("Resident resources and active execution are shown separately."))
        resources.forEach { row -> Text("${row.ownerId}: ${row.lifecycleState.name} · executing=${row.executing}",style=MaterialTheme.typography.bodySmall) }
        Text(uiText("Installed diagnostic model"))
        if(models.isEmpty()) Text(uiText("No verified installed model"))
        FlowRow { models.forEach { model ->
            TextButton(enabled=!busy,onClick={selected=model.id}) { Text(model.displayName) }
        } }
        models.firstOrNull { it.id==selected }?.let { model ->
            Text(model.id,style=MaterialTheme.typography.labelSmall)
            Text("SHA-256: ${model.sha256.orEmpty()}",style=MaterialTheme.typography.labelSmall)
            val candidate=lab.candidate(model,workload)
            Text("installed=${candidate.installed} · native=${candidate.nativeReadable} · resident=${candidate.resident} · qualified=${candidate.proofs.any { it.workload==workload && it.backend in candidate.supportedBackends && it.correct && it.stable }}",
                style=MaterialTheme.typography.bodySmall)
            if (workload == LocalWorkload.EMBED) {
                val nativeDimension = lab.embeddingDimension(model)
                Text("Native embedding dimension: ${nativeDimension ?: "INSPECT_METADATA_FIRST"}",
                    style=MaterialTheme.typography.bodySmall)
                Text("Selected EMBED role: ${embeddingChoice?.modelId ?: "NONE"}",
                    style=MaterialTheme.typography.bodySmall)
                TextButton(enabled=!busy && nativeDimension!=null && model.sha256!=null,
                    onClick={job=scope.launch {
                        try {
                            lab.assignEmbeddingModel(model.id)
                            embeddingChoice=com.halo.app.ai.WorkloadPreferences(storage).selectedEmbedding()
                            failed=false
                        }
                        catch(cancelled: CancellationException) { throw cancelled }
                        catch(_: Exception) { failed=true }
                    }}) { Text(uiText("Use this model for local semantic search")) }
            }
            if (!model.verified) Text(uiText("Imported model: inspect native task before any role qualification."),
                style=MaterialTheme.typography.bodySmall)
        }
        HaloTextField(dimension,{dimension=it.take(5)},label={Text(uiText("Embedding dimensions"))},singleLine=true,enabled=!busy, kind = VoiceFieldKind.NUMBER, maxChars = 5)
        FlowRow { WorkloadLabAction.entries.forEach { action ->
            TextButton(enabled=!busy && selected!=null,onClick={
                val model=selected ?: return@TextButton
                job=scope.launch { lab.perform(action,model,query,dimension.toIntOrNull()?.coerceIn(1,16384) ?: 768) }
            }) { Text(uiText(action.label())) }
        } }
        if(busy) TextButton(onClick={job?.cancel()}) { Text(uiText("Stop")) }
        Text("${result.action}: ${result.status} ${result.errorCode.orEmpty()}",style=MaterialTheme.typography.bodySmall)
        result.privatePreview?.let { Text(it,style=MaterialTheme.typography.bodySmall) }
        Text(uiText("Private previews are cleared when this panel closes and excluded from export."))
        synthesis?.let { t -> Text("TTS CPU · ${t.pack} · voice=${t.voice} · cache=${t.cacheHit} · reused=${t.reused} · initMs=${t.initMs ?: "NOT_MEASURED"} · firstPcmMs=${t.firstPcmMs} · audioMs=${t.audioMs} · RTF=${t.realTimeFactor ?: "NOT_MEASURED"}",style=MaterialTheme.typography.bodySmall) }
        Text("${playback.status} · firstRoutedWriteMs=${playback.firstRoutedWriteMs ?: "NOT_MEASURED"} · endOfSpeechToAudibleMs=NOT_MEASURED",style=MaterialTheme.typography.bodySmall)
        TextButton(onClick={clipboard.setText(AnnotatedString(lab.export()))}) { Text(uiText("Copy safe workload report")) }
        Row { Switch(adaptive,{value->setting("workload_adaptive_enabled",value){adaptive=value}}); Text(uiText("Use current qualified workload evidence automatically")) }
        Row { Switch(prewarm,{value->setting("workload_prewarm_enabled",value){prewarm=value}}); Text(uiText("Allow safe speculative model preparation")) }
        Text(uiText("Local semantic sources"),style=MaterialTheme.typography.titleSmall)
        Text(uiText("Select source IDs explicitly: note, canvas, study or plain-text attachment. No automatic discovery or downloads."))
        HaloTextField(sourceIds,{sourceIds=it.take(12800)},label={Text(uiText("Source IDs, one per line"))},enabled=!busy,modifier=Modifier.fillMaxWidth(), kind = VoiceFieldKind.URL_ID, maxChars = 12800)
        Text("note:<id>\ncanvas:<id>\nstudy:<id>\nattachment:<noteId>:<attachmentId>",style=MaterialTheme.typography.labelSmall)
        Row {
            TextButton(enabled=!busy,onClick={job=scope.launch {
                try { ManualDependencyGraph.semanticWorkspace.configure(true,sourceIds.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()); semantic=true; failed=false }
                catch(cancelled: CancellationException) { throw cancelled } catch(_: Exception) { failed=true }
            }}) { Text(uiText("Enable selected local sources")) }
            TextButton(enabled=!busy,onClick={job=scope.launch {
                ManualDependencyGraph.semanticWorkspace.configure(false,emptySet()); semantic=false
            }}) { Text(uiText("Disable and clear index")) }
        }
        Text(uiText(if(semantic) "Local semantic scope enabled" else "Local semantic scope disabled"))
        HaloTextField(query,{query=it.take(4096)},label={Text(uiText("Private retrieval query"))},modifier=Modifier.fillMaxWidth(),enabled=!busy, kind = VoiceFieldKind.TEXT, maxChars = 4096)
        if(failed) Text(uiText("Unable to save workload policy"),color=MaterialTheme.colorScheme.error)
    }
}
private fun WorkloadLabAction.label()=when(this) {
    WorkloadLabAction.VERIFY_ARTIFACT->"Verify installed artifact"
    WorkloadLabAction.INSPECT_METADATA->"Inspect native task metadata"
    WorkloadLabAction.COLD_INITIALIZE->"Cold initialization"
    WorkloadLabAction.WARM_REUSE->"Load or reuse engine"
    WorkloadLabAction.SHORT_TEXT->"Short text diagnostic"
    WorkloadLabAction.MULTI_TURN->"Existing role qualification suite"
    WorkloadLabAction.EMBEDDING->"Generate diagnostic embedding"
    WorkloadLabAction.INDEX->"Index selected local sources"
    WorkloadLabAction.RETRIEVAL->"Retrieve selected local sources"
}
