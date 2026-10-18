package com.halo.app.ai

import com.halo.engines.ai.LocalWorkloadResolver
import com.halo.providers.litert.LiteRtLocalProvider
import com.halo.providers.litert.models.*
import com.halo.providers.litert.runtime.*
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import org.json.JSONArray
import org.json.JSONObject

enum class WorkloadLabAction { VERIFY_ARTIFACT, INSPECT_METADATA, COLD_INITIALIZE, WARM_REUSE, SHORT_TEXT, MULTI_TURN, EMBEDDING, INDEX, RETRIEVAL }
data class WorkloadLabResult(val action: String="NONE",val modelId: String?=null,val sha256: String?=null,
    val status: String="NOT_RUN",val errorCode: String?=null,val durationMs: Long?=null,val backend: String?=null,
    val initMs: Long?=null,val loadMs: Long?=null,val conversationMs: Long?=null,val firstTokenMs: Long?=null,
    val embeddingMs: Long?=null,val batchSize: Int?=null,val estimatedMemoryBytes: Long?=null,
    val dimension: Int?=null,val indexSize: Int?=null,val privatePreview: String?=null)

/** No init-time actions. Every native call below is reached only through an explicit UI action. */
class WorkloadLabController(private val context: android.content.Context,private val models: LiteRtModelManager,private val provider: ()->LiteRtLocalProvider?,
    private val resources: NativeResourceCoordinator,private val storage: Storage,
    private val qualifications: WorkloadQualificationStore,private val semantic: ()->WorkspaceSemanticService) {
    private val device by lazy { com.halo.app.data.models.DeviceRuntimeIdentity.current(context).fingerprint }
    private fun thermalSafe()=android.os.Build.VERSION.SDK_INT>=29 &&
        context.getSystemService(android.os.PowerManager::class.java).currentThermalStatus<3
    private val lock=Mutex()
    private val mutable=MutableStateFlow(WorkloadLabResult())
    val result=mutable.asStateFlow()
    private val busyState=MutableStateFlow(false)
    val busy=busyState.asStateFlow()
    val resourceState get()=resources.snapshot
    fun installed()=(models.inventoryState.value as? LocalModelInventoryState.Ready)?.models.orEmpty().filter { it.verified }
    private fun metadata(artifact: LocalModelDescriptor): JSONObject? = try {
        storage.getString("workload.native.v2.${artifact.sha256}")?.let(::JSONObject)
            ?.takeIf { it.optString("runtime")==LiteRtLocalProvider.RUNTIME_VERSION && it.optInt("epoch")==WorkloadQualificationStore.EPOCH && it.optString("device")==device && it.optInt("android")==android.os.Build.VERSION.SDK_INT }
    } catch(_: Exception) { null }
    fun candidate(artifact: LocalModelDescriptor,workload: LocalWorkload): WorkloadCandidate {
        val target=WorkloadTarget(LiteRtLocalProvider.ProviderIds.LITERT,artifact.id,artifact.sha256)
        val info=metadata(artifact)
        val tasks=buildSet {
            if(info?.optString("task")=="LLM") {
                addAll(listOf(LocalWorkload.FAST,LocalWorkload.CHAT,LocalWorkload.THINK))
                if(info.optBoolean("tools")) add(LocalWorkload.ACTION)
                if(info.optBoolean("vision")) add(LocalWorkload.VISION)
            }
            if(info?.optString("task")=="EMBEDDING") add(LocalWorkload.EMBED)
        }
        val nativeBackends=info?.optJSONArray("backends")?.let { values -> (0 until values.length()).mapNotNull { i ->
            WorkloadBackend.entries.firstOrNull { it.name==values.getString(i) }
        }.toSet() }.orEmpty()
        val local=provider()
        val profile=WorkloadPreferences(storage).profile(workload)
        val requested=if(profile.backend!=WorkloadBackend.AUTO) profile.backend else local?.backendResolutionForDescriptor(artifact)?.effective?.name
            ?.let { WorkloadBackend.valueOf(it) } ?: WorkloadBackend.CPU
        val backends=nativeBackends.intersect(setOf(if(LocalWorkload.EMBED in tasks) WorkloadBackend.CPU else requested))
        val proofs=if(local!=null) qualifications.current(target,qualifications.environment(local,artifact,workload,resources.budget.value.allowConcurrentHeavyweights)) else emptyList()
        return WorkloadCandidate(target,tasks,true,info!=null,
            local?.modelSession?.loadedModelId==artifact.id,true,backends,artifact.bytes+512L*1024*1024,proofs=proofs)
    }
    fun readiness(workload: LocalWorkload,router: AiProviderRouter): WorkloadResolution {
        val local=provider() ?: return WorkloadResolution(workload,null,WorkloadBackend.AUTO,null,WorkloadReadiness.UNAVAILABLE,"PROVIDER_UNAVAILABLE")
        val default=router.selectedProviderId.value?.let { id -> router.selectedModelId.value?.let { WorkloadTarget(id,it) } }
        val assignment=WorkloadPreferences(storage).assignment(workload,router.roleBindings.value,default)
        val candidates=installed().map { candidate(it,workload) }
        // Environment is candidate-specific. Resolve each candidate with its own exact identity.
        val resolutions=candidates.map { c ->
            val artifact=installed().first { it.id==c.target.modelId }
            LocalWorkloadResolver().resolve(WorkloadRequest(workload,qualifications.environment(local,artifact,workload,
                resources.budget.value.allowConcurrentHeavyweights),availableBytes=resources.budget.value.availableBytes),assignment,listOf(c))
        }
        return resolutions.firstOrNull { it.state in setOf(WorkloadReadiness.READY,WorkloadReadiness.NEEDS_LOADING) }
            ?: resolutions.firstOrNull { it.target?.modelId==assignment.target?.modelId } ?: resolutions.firstOrNull()
            ?: WorkloadResolution(workload,assignment.target,assignment.execution.backend,null,WorkloadReadiness.UNAVAILABLE,"NO_INSTALLED_CANDIDATES")
    }
    fun automaticTarget(workload: AiWorkloadSpec): AiModelRef? {
        if(!thermalSafe() || !storage.getBoolean("workload_adaptive_enabled",false) || resources.budget.value.pressure!=NativeMemoryPressure.NORMAL) return null
        val local=provider() ?: return null
        val kind=LocalWorkload.from(AiRoleRequirements.role(workload))
        val options=installed().mapNotNull { artifact ->
            val c=candidate(artifact,kind)
            val request=WorkloadRequest(kind,qualifications.environment(local,artifact,kind,resources.budget.value.allowConcurrentHeavyweights),
                availableBytes=resources.budget.value.availableBytes)
            WorkloadAdaptivePolicy.rank(request,listOf(c)).singleOrNull()?.let { candidate ->
                val resolution=LocalWorkloadResolver().resolve(request,WorkloadAssignment(kind,WorkloadSelection.AUTOMATIC,
                    execution=WorkloadPreferences(storage).profile(kind)),listOf(candidate))
                if(resolution.state !in setOf(WorkloadReadiness.READY,WorkloadReadiness.NEEDS_LOADING)) null
                else candidate to candidate.proofs.filter { it.workload==kind && it.environment==request.environment && it.correct && it.stable && it.backend in candidate.supportedBackends }
                    .mapNotNull { if(candidate.resident) it.warmMs else it.coldMs }.minOrNull()
            }
        }
        return options.sortedWith(compareBy<Pair<WorkloadCandidate,Long?>> { it.second ?: Long.MAX_VALUE }
            .thenBy { it.first.memoryEstimateBytes }).firstOrNull()?.first?.target?.let { AiModelRef(it.providerId,it.modelId) }
    }
    suspend fun perform(action: WorkloadLabAction,modelId: String,query: String="Halo",dimension: Int=768) {
        check(lock.tryLock()) { "LAB_BUSY" }
        busyState.value=true
        val start=System.nanoTime()
        var artifact: LocalModelDescriptor?=null
        try {
            check(resources.snapshot.value.none { it.executing }) { "NATIVE_RESOURCE_BUSY" }
            val verified=models.verifyPhysicalArtifact(modelId)?.takeIf { it.verified } ?: error("ARTIFACT_NOT_VERIFIED")
            artifact=verified
            val local=requireNotNull(provider())
            var row=WorkloadLabResult(action.name,modelId,verified.sha256,"COMPLETED")
            withTimeout(300_000) {
                when(action) {
                    WorkloadLabAction.VERIFY_ARTIFACT -> Unit
                    WorkloadLabAction.INSPECT_METADATA -> row=inspect(verified,row)
                    WorkloadLabAction.COLD_INITIALIZE,WorkloadLabAction.WARM_REUSE -> {
                        val measured=local.initializeForWorkloadLab(modelId,action==WorkloadLabAction.COLD_INITIALIZE)
                        row=row.copy(loadMs=measured.first,backend=measured.second)
                    }
                    WorkloadLabAction.SHORT_TEXT -> {
                        val measured=local.benchmarkModel(modelId,prompt="What is 17 multiplied by 8? Reply with only the number.",
                            maxOutputTokens=256,backendOverride=LocalComputeBackend.CPU).getOrThrow()
                        row=row.copy(backend=measured.effectiveBackend.name,initMs=measured.engineInitDurationMs,
                            conversationMs=measured.conversationCreateDurationMs,
                            firstTokenMs=measured.timeToFirstTokenMs.takeIf { measured.responseText.isNotBlank() },
                            status=if(measured.responseText.trim()=="136") "FIXTURE_MATCH" else "FIXTURE_MISMATCH",
                            privatePreview=measured.responseText.take(512))
                    }
                    WorkloadLabAction.MULTI_TURN -> {
                        val rows=local.runRoleQualification(modelId,agentic={ _,_ ->
                            LocalAgenticQualification(LocalRoleProbeVerdict.NOT_RUN,ToolCallProtocol.NONE)
                        },onRow={ measured -> qualifications.recordRoleSuite(measured.copy(memoryPressure=measured.memoryPressure ?: resources.budget.value.pressure.name),local,verified,resources.budget.value.allowConcurrentHeavyweights) })
                        row=row.copy(status=if(rows.singleOrNull()?.chat?.verdict==LocalRoleProbeVerdict.PASS) "ROLE_SUITE_COMPLETED" else "ROLE_SUITE_FAILED")
                    }
                    WorkloadLabAction.EMBEDDING -> {
                        val embedding=com.halo.providers.litert.LiteRtEmbeddingProvider(models,resources,modelId)
                        val vectors=embedding.embed(EmbeddingRequest(listOf("Halo local semantic diagnostic."),requireNotNull(verified.sha256),dimension))
                        row=row.copy(backend="CPU",dimension=vectors.space.dimension,initMs=embedding.metrics.value?.initMs,
                            embeddingMs=embedding.metrics.value?.generationMs,batchSize=embedding.metrics.value?.batchSize,
                            estimatedMemoryBytes=embedding.metrics.value?.estimatedMemoryBytes)
                    }
                    WorkloadLabAction.INDEX -> {
                        semantic().synchronize(modelId,requireNotNull(verified.sha256),dimension)
                        row=row.copy(indexSize=semantic().size(),dimension=dimension,backend="CPU")
                    }
                    WorkloadLabAction.RETRIEVAL -> {
                        require(query.length in 1..4096)
                        val references=semantic().referencesForContext(modelId,requireNotNull(verified.sha256),dimension,query)
                        row=row.copy(indexSize=semantic().size(),dimension=dimension,backend="CPU",
                            privatePreview=references.take(4).joinToString("\n") { "${it.provenance}: ${it.excerpt.take(160)}" })
                    }
                }
            }
            mutable.value=row.copy(durationMs=(System.nanoTime()-start)/1_000_000)
        } catch(cancelled: CancellationException) {
            mutable.value=WorkloadLabResult(action.name,modelId,artifact?.sha256,"CANCELLED",durationMs=(System.nanoTime()-start)/1_000_000)
            throw cancelled
        } catch(error: Exception) {
            val code=(error as? com.halo.providers.litert.EmbeddingUnavailableException)?.reasonCode
                ?: "LAB_${error.javaClass.simpleName.take(64)}"
            mutable.value=WorkloadLabResult(action.name,modelId,artifact?.sha256,"FAILED",code,(System.nanoTime()-start)/1_000_000)
        } finally { busyState.value=false; lock.unlock() }
    }
    private suspend fun inspect(artifact: LocalModelDescriptor,row: WorkloadLabResult): WorkloadLabResult {
        val info=com.halo.providers.litert.LiteRtArtifactInspector(resources).inspect(artifact)
        val j=JSONObject().put("task",info.task).put("runtime",LiteRtLocalProvider.RUNTIME_VERSION)
            .put("device",device).put("android",android.os.Build.VERSION.SDK_INT)
            .put("epoch",WorkloadQualificationStore.EPOCH).put("backends",JSONArray(info.textBackends))
            .put("vision",info.vision).put("tools",info.nativeTools).put("dimension",info.embeddingDimension ?: JSONObject.NULL)
        withContext(Dispatchers.IO) {
            storage.putString("workload.native.v2.${artifact.sha256}",j.toString())
            // An imported generator's sidecar initially has no vision claim.
            // Record native metadata only; a real Vision Lab image probe is
            // required separately before normal image inference is admitted.
            if (info.task == "LLM") models.recordInspectedVision(
                artifact.id, requireNotNull(artifact.sha256), info.vision)
        }
        return row.copy(dimension=info.embeddingDimension)
    }
    /** Explicit allowlist: excludes privatePreview, query, transcript, source IDs, vectors and exception messages. */
    fun export(): String {
        val latest=result.value
        val j=JSONObject().put("schema",2).put("compatibilityEpoch",WorkloadQualificationStore.EPOCH)
            .put("action",latest.action).put("model",safe(latest.modelId)).put("sha256",latest.sha256)
            .put("status",latest.status).put("errorCode",latest.errorCode).put("backend",latest.backend)
            .put("durationMs",latest.durationMs ?: "NOT_MEASURED").put("engineInitMs",latest.initMs ?: "NOT_MEASURED")
            .put("loadOrReuseMs",latest.loadMs ?: "NOT_MEASURED")
            .put("conversationMs",latest.conversationMs ?: "NOT_MEASURED").put("firstVisibleTokenMs",latest.firstTokenMs ?: "NOT_MEASURED")
            .put("dimension",latest.dimension ?: "NOT_MEASURED").put("indexSize",latest.indexSize ?: "NOT_MEASURED")
            .put("tokensPerSecond","NOT_MEASURED").put("gpuMemory","NOT_MEASURED").put("gpuOperationPlacement","NOT_MEASURED")
        j.put("embeddingMs",latest.embeddingMs ?: "NOT_MEASURED").put("batchSize",latest.batchSize ?: "NOT_MEASURED")
            .put("estimatedMemoryBytes",latest.estimatedMemoryBytes ?: "NOT_MEASURED")
            .put("peakObservedProcessBytes","NOT_MEASURED").put("endOfSpeechToAudibleMs","NOT_MEASURED")
        com.halo.app.audio.WorkloadSpeechTelemetry.synthesis.value?.let { t -> j.put("lastKokoroSynthesis",JSONObject()
            .put("pack",safe(t.pack)).put("voice",t.voice).put("cacheHit",t.cacheHit).put("reused",t.reused)
            .put("initMs",t.initMs ?: "NOT_MEASURED").put("waitMs",t.waitMs ?: "NOT_MEASURED")
            .put("firstPcmMs",t.firstPcmMs).put("synthesisMs",t.synthesisMs ?: "NOT_MEASURED")
            .put("audioMs",t.audioMs).put("realTimeFactor",t.realTimeFactor ?: "NOT_MEASURED")) }
        val playback=com.halo.app.audio.WorkloadSpeechTelemetry.playback.value
        j.put("lastRoutedPlayback",JSONObject().put("status",playback.status)
            .put("firstPcmMs",playback.firstPcmMs ?: "NOT_MEASURED")
            .put("firstRoutedWriteMs",playback.firstRoutedWriteMs ?: "NOT_MEASURED")
            .put("completionMs",playback.completionMs ?: "NOT_MEASURED"))
        j.put("stages",JSONArray().apply { WorkloadStageTimeline.events.value.takeLast(64).forEach { event -> put(JSONObject()
            .put("interaction",safe(event.interactionId)).put("stage",safe(event.stageId)).put("workload",safe(event.workload))
            .put("state",event.state.name).put("reasonCode",safe(event.reasonCode)).put("waitMs",event.waitMs ?: "NOT_MEASURED")
            .put("durationMs",event.durationMs ?: "NOT_MEASURED").put("backend",event.effectiveBackend)) } })
        return j.toString(2)
    }
    private fun safe(value: String?)=value?.take(200)?.replace(Regex("[^A-Za-z0-9_.:/-]"),"_")
    fun clearPrivatePreview() { mutable.value=mutable.value.copy(privatePreview=null) }
}
