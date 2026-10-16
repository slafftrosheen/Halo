package com.halo.app.ui.screens.settings

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
    val models=lab.installed()
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
        if(workload in setOf(LocalWorkload.WAKE,LocalWorkload.VAD,LocalWorkload.STT,LocalWorkload.INTENT,LocalWorkload.TTS)) {
            Text(uiText("Specialized workloads use existing voice and command settings. Task qualification is separate and remains pending."))
        } else {
            val readiness=lab.readiness(workload,router)
            Text("${workload.name}: ${readiness.state.name} · ${readiness.reasonCode}",style=MaterialTheme.typography.bodySmall)
            Text("${readiness.target?.providerId.orEmpty()} / ${readiness.target?.modelId.orEmpty()}",style=MaterialTheme.typography.bodySmall)
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
        }
        OutlinedTextField(dimension,{dimension=it.take(5)},label={Text(uiText("Embedding dimensions"))},singleLine=true,enabled=!busy)
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
        OutlinedTextField(sourceIds,{sourceIds=it.take(12800)},label={Text(uiText("Source IDs, one per line"))},enabled=!busy,modifier=Modifier.fillMaxWidth())
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
        OutlinedTextField(query,{query=it.take(4096)},label={Text(uiText("Private retrieval query"))},modifier=Modifier.fillMaxWidth(),enabled=!busy)
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
