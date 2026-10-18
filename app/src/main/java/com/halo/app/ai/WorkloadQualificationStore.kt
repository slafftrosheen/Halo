package com.halo.app.ai

import android.content.Context
import android.os.Build
import com.halo.app.data.models.DeviceRuntimeIdentity
import com.halo.providers.litert.LiteRtLocalProvider
import com.halo.providers.litert.models.LocalModelDescriptor
import com.halo.providers.litert.runtime.*
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Additive workload evidence, not a replacement for the existing LLM role/performance repositories. */
class WorkloadQualificationStore(context: Context,private val storage: Storage) {
    private val device by lazy { DeviceRuntimeIdentity.current(context).fingerprint }
    private val lock=Mutex()
    @Volatile private var cached: List<WorkloadTaskProof>?=null
    fun environment(provider: LiteRtLocalProvider,artifact: LocalModelDescriptor,workload: LocalWorkload,
        concurrency: Boolean): WorkloadEnvironment {
        // EMBED is a separate LiteRT EmbeddingEngine task, not a language
        // generation config. LLM tuning and engine loading must not be read as
        // embedding qualification evidence.
        val configuration=if(workload==LocalWorkload.EMBED) "EMBED_CPU_DIMENSION_VERIFIED_SEPARATELY"
            else provider.loadConfigFor(artifact).toString()
        val tuning=if(workload==LocalWorkload.EMBED) "EMBED_NATIVE" else provider.preferences.tuning(artifact.id).toString()
        val workloadPolicy=WorkloadPreferences(storage).profile(workload)
        val identity="native=$configuration|tuning=$tuning|workload=$workloadPolicy|concurrent=$concurrency|"+
            "qualification-suite=$ROLE_PROBE_SUITE_VERSION; deterministic-chat; declared-thinking-reasoning"
        return WorkloadEnvironment("litertlm/${LiteRtLocalProvider.RUNTIME_VERSION}",EPOCH,device,Build.VERSION.SDK_INT.toString(),hash(identity))
    }
    suspend fun recordRoleSuite(row: LocalModelRoleQualification,provider: LiteRtLocalProvider,
        artifact: LocalModelDescriptor,concurrency: Boolean) {
        if(row.artifactFingerprint!=artifact.sha256 || !artifact.verified || row.backend==null || row.contextTokens==null ||
            row.threads==null || row.gpuLayers==null || ROLE_PROBE_SUITE_VERSION<2) return
        val config=provider.loadConfigFor(artifact)
        // A suite for a different native configuration is historical, never relabelled as current.
        if(config.useModelDefaultContext || config.contextSize!=row.contextTokens || config.cpuThreads!=row.threads) return
        val target=WorkloadTarget(provider.id,row.modelId,row.artifactFingerprint)
        val stable=row.failure==null && row.memoryPressure=="NORMAL" && row.fast?.successfulWarmSamples==3 && row.fast?.warmResidencyProven==true
        val chat=row.chat?.let { it.total==3 && it.passed==3 && it.instructionPass && it.arithmeticPass && it.contextRecallPass } == true
        val roles=listOf(
            LocalWorkload.CHAT to chat,
            LocalWorkload.FAST to (chat && row.fast?.verdict==LocalRoleProbeVerdict.PASS),
            LocalWorkload.THINK to (chat && row.reasoning?.let { it.total==3 && it.passed==3 }==true),
            LocalWorkload.ACTION to (row.agentic?.let { it.verdict==LocalRoleProbeVerdict.PASS && it.initialToolPass && it.continuationPass && it.provenanceRetained }==true))
        roles.forEach { (workload,correct) ->
            val policy=WorkloadPreferences(storage).profile(workload)
            if(storage.getString("workload.execution.v1.${workload.name}")!=null &&
                (policy.contextTokens!=row.contextTokens || policy.backend !in setOf(WorkloadBackend.AUTO,WorkloadBackend.valueOf(requireNotNull(row.backend).name)))) return@forEach
            append(WorkloadTaskProof(target,workload,WorkloadBackend.valueOf(requireNotNull(row.backend).name),
            environment(provider,artifact,workload,concurrency),correct,stable,row.completedAtMs,
            coldMs=row.fast?.coldTtftMs,warmMs=row.fast?.medianWarmTtftMs)) }
    }
    suspend fun append(proof: WorkloadTaskProof)=withContext(Dispatchers.IO) { lock.withLock {
        require(proof.target.artifactSha256?.matches(Regex("[a-f0-9]{64}"))==true)
        require(proof.environment.compatibilityEpoch==EPOCH && proof.measuredAtMs>0)
        val history=(history()+proof).takeLast(256)
        storage.putString(KEY,JSONArray().apply { history.forEach { put(encode(it)) } }.toString())
        cached=history
    } }
    fun history(): List<WorkloadTaskProof> = cached ?: readHistory().also { cached=it }
    private fun readHistory(): List<WorkloadTaskProof> = try {
        val text=storage.getString(KEY) ?: "[]"; require(text.length<=1024*1024)
        val array=JSONArray(text); require(array.length()<=256)
        (0 until array.length()).mapNotNull { i -> runCatching { decode(array.getJSONObject(i)) }.getOrNull() }
    } catch(_: Exception) { emptyList() }
    fun current(target: WorkloadTarget,environment: WorkloadEnvironment): List<WorkloadTaskProof> = history()
        .filter { it.target==target && it.environment==environment && it.measuredAtMs<=System.currentTimeMillis()+300_000 }
        .groupBy { it.workload to it.backend }.values.mapNotNull { rows -> rows.maxByOrNull { it.measuredAtMs } }
    private fun encode(p: WorkloadTaskProof)=JSONObject().put("provider",p.target.providerId).put("model",p.target.modelId)
        .put("sha",p.target.artifactSha256).put("workload",p.workload.name).put("backend",p.backend.name)
        .put("runtime",p.environment.runtime).put("epoch",p.environment.compatibilityEpoch).put("device",p.environment.device)
        .put("android",p.environment.androidVersion).put("settings",p.environment.settingsFingerprint)
        .put("correct",p.correct).put("stable",p.stable).put("at",p.measuredAtMs)
        .put("cold",p.coldMs ?: JSONObject.NULL).put("warm",p.warmMs ?: JSONObject.NULL)
        .put("peakProcess",p.peakProcessBytes ?: JSONObject.NULL).put("audible",p.audibleConfirmed ?: JSONObject.NULL)
    private fun decode(j: JSONObject)=WorkloadTaskProof(WorkloadTarget(j.getString("provider"),j.getString("model"),j.getString("sha")),
        LocalWorkload.valueOf(j.getString("workload")),WorkloadBackend.valueOf(j.getString("backend")),
        WorkloadEnvironment(j.getString("runtime"),j.getInt("epoch"),j.getString("device"),j.getString("android"),j.getString("settings")),
        j.getBoolean("correct"),j.getBoolean("stable"),j.getLong("at"),
        j.takeUnless { it.isNull("cold") }?.getLong("cold"),j.takeUnless { it.isNull("warm") }?.getLong("warm"),
        j.takeUnless { it.isNull("peakProcess") }?.getLong("peakProcess"),j.takeUnless { it.isNull("audible") }?.getBoolean("audible"))
    companion object {
        const val EPOCH=2
        private const val KEY="workload.qualification.history.v2"
        private fun hash(text: String)=MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
