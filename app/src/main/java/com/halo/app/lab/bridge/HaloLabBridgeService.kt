package com.halo.app.lab.bridge

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.lab.worker.HaloModelBenchService
import com.halo.app.storage.haloStorageLayout
import com.halo.lab.bridge.v1.IHaloLabBridge
import com.halo.lab.bridge.v1.IModelBenchWorker
import com.halo.providers.litert.models.LocalModelInventoryState
import com.halo.providers.litert.models.ModelInventoryRefreshReason
import com.halo.sdk.runtime.resource.HaloResourceIds
import com.halo.sdk.runtime.resource.ResourceAccess
import com.halo.sdk.runtime.resource.ResourceAcquireResult
import com.halo.sdk.runtime.resource.ResourceClaim
import com.halo.sdk.runtime.resource.ResourceEndpointId
import com.halo.sdk.runtime.resource.ResourceHolder
import com.halo.sdk.runtime.resource.ResourceHolderKind
import com.halo.sdk.runtime.resource.ResourceKey
import com.halo.sdk.runtime.resource.ResourceLease
import com.halo.sdk.runtime.resource.ResourceLeaseRequest
import com.halo.sdk.runtime.resource.ResourcePreemptionPolicy
import com.halo.sdk.runtime.resource.ResourcePriority
import com.halo.sdk.runtime.resource.ResourceWaitPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Authenticated opt-in AIDL endpoint. Only verified LiteRT CPU text smoke is implemented. */
class HaloLabBridgeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var journal: HaloLabJournal
    private val active = ConcurrentHashMap<String, ActiveRun>()
    private val startingAttempts = ConcurrentHashMap.newKeySet<String>()
    private val stopRequestedBeforeAttach = ConcurrentHashMap.newKeySet<String>()
    private val artifactLeases = ConcurrentHashMap<String, TimedArtifactLease>()
    private val serverInstanceId = SERVER_INSTANCE_ID
    private val binder = object : IHaloLabBridge.Stub() {
        override fun handshake(requestJson: String?): String = dispatch(requestJson) { this@HaloLabBridgeService.handshake(requestJson) }
        override fun listModels(requestJson: String?): String = dispatch(requestJson) { this@HaloLabBridgeService.listModels(requestJson) }
        override fun acquireArtifactLease(requestJson: String?): String = dispatch(requestJson) { this@HaloLabBridgeService.acquireArtifactLease(requestJson) }
        override fun renewArtifactLease(requestJson: String?): String = dispatch(requestJson) { this@HaloLabBridgeService.renewArtifactLease(requestJson) }
        override fun releaseArtifactLease(requestJson: String?): String = dispatch(requestJson) { this@HaloLabBridgeService.releaseArtifactLease(requestJson) }
        override fun submitCase(requestJson: String?): String = dispatch(requestJson) { this@HaloLabBridgeService.submitCase(requestJson) }
        override fun queryCase(requestJson: String?): String = dispatch(requestJson) { this@HaloLabBridgeService.queryCase(requestJson) }
        override fun cancelCase(requestJson: String?): String = dispatch(requestJson) { this@HaloLabBridgeService.cancelCase(requestJson) }
        override fun readEvents(requestJson: String?): String = dispatch(requestJson) { this@HaloLabBridgeService.readEvents(requestJson) }
        override fun acknowledgeEvents(requestJson: String?): String = dispatch(requestJson) { this@HaloLabBridgeService.acknowledgeEvents(requestJson) }
        override fun acquireModel(requestJson: String?) = dispatch(requestJson) { unsupported("HALO_ACQUISITION_NOT_IMPLEMENTED") }
        override fun queryAcquisition(requestJson: String?) = dispatch(requestJson) { unsupported("HALO_ACQUISITION_NOT_IMPLEMENTED") }
        override fun cancelAcquisition(requestJson: String?) = dispatch(requestJson) { unsupported("HALO_ACQUISITION_NOT_IMPLEMENTED") }
    }

    override fun onCreate() {
        super.onCreate()
        journal = HaloLabJournal(this)
        scope.launch { journal.openAttempts().forEach { recoverAttempt(it) } }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CONSENT_REVOKED) {
            active.values.forEach { run -> runCatching { run.worker?.requestStop(run.attemptId) } }
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private inline fun dispatch(raw: String?, action: () -> String): String {
        return try {
            authenticate()
            expireArtifactLeases()
            if (raw.isNullOrEmpty() || raw.toByteArray(Charsets.UTF_8).size > HaloLabProtocol.MAX_REQUEST_BYTES)
                return error("REQUEST_TOO_LARGE_OR_EMPTY")
            if (!HaloLabConsentStore(this).isEnabled()) return error("CONSENT_REQUIRED")
            action().also { if (it.toByteArray(Charsets.UTF_8).size > HaloLabProtocol.MAX_REPLY_BYTES) return error("REPLY_TOO_LARGE") }
        } catch (_: SecurityException) { error("CALLER_NOT_AUTHORIZED") }
        catch (failure: Throwable) { error("BRIDGE_${failure.javaClass.simpleName.take(48)}") }
    }

    private fun authenticate() {
        val uid = Binder.getCallingUid()
        if (checkCallingOrSelfPermission(HaloLabProtocol.BRIDGE_PERMISSION) != PackageManager.PERMISSION_GRANTED) throw SecurityException()
        val packages = packageManager.getPackagesForUid(uid).orEmpty()
        if (HaloLabProtocol.LAB_PACKAGE !in packages) throw SecurityException()
        if (packageManager.checkSignatures(packageName, HaloLabProtocol.LAB_PACKAGE) != PackageManager.SIGNATURE_MATCH) throw SecurityException()
    }

    private fun handshake(raw: String?): String {
        val request = JSONObject(raw.orEmpty())
        val version = request.optJSONObject("protocolVersion")
        if (version?.optInt("major", -1) != HaloLabProtocol.MAJOR ||
            version.optInt("minor", -1) < 0 || request.optString("clientSchemaSha256") != HaloLabProtocol.LAB_CLIENT_SCHEMA_SHA256) {
            return JSONObject().put("status", "PROTOCOL_MISMATCH").toString()
        }
        val consent = JSONArray(listOf("MODEL_INSPECTION", "LOCAL_TEST_EXECUTION"))
        val features = JSONArray(listOf("MODEL_INVENTORY", "ARTIFACT_LEASES", "TEXT_EXECUTION", "EVENT_REPLAY"))
        val capturedAt = System.currentTimeMillis()
        val sourceId = com.halo.app.BuildConfig.HALO_SOURCE_ID
        val runtimeVersion = com.halo.providers.litert.LiteRtLocalProvider.RUNTIME_VERSION
        val adapterId = "halo-lab-text-smoke-v1"
        val capabilityJson = "{\"capturedAtWallTimeMs\":$capturedAt,\"haloSourceId\":${JSONObject.quote(sourceId)}," +
            "\"runtimeId\":\"provider.litert.local\",\"runtimeVersion\":${JSONObject.quote(runtimeVersion)}," +
            "\"adapterCompatibilityId\":\"$adapterId\",\"capabilities\":[{\"role\":\"FAST\",\"backends\":[\"CPU\"]," +
            "\"supportsStreaming\":false,\"supportsToolCalls\":false,\"maximumInputTokens\":null,\"maximumOutputTokens\":8}]}"
        val runtimeCapabilities = JSONArray().put(JSONObject(capabilityJson).put("snapshotSha256", sha256(capabilityJson)))
        return JSONObject().put("status", "READY")
            .put("protocolVersion", JSONObject().put("major", HaloLabProtocol.MAJOR).put("minor", HaloLabProtocol.MINOR))
            .put("serverEpoch", journal.serverEpoch).put("serverInstanceId", serverInstanceId)
            .put("serverSourceId", sourceId)
            .put("serverSchemaSha256", HaloLabProtocol.SHA256)
            .put("grantedConsent", consent).put("supportedFeatures", features)
            .put("runtimeCapabilities", runtimeCapabilities).toString()
    }

    private fun listModels(raw: String?): String = runBlocking(Dispatchers.IO) {
        val request = JSONObject(raw.orEmpty())
        val manager = ManualDependencyGraph.providerGraph.modelManager
        var state = manager.inventoryState.value
        if (state !is LocalModelInventoryState.Ready && active.isEmpty()) {
            manager.refresh(ModelInventoryRefreshReason.USER_REQUEST)
            state = manager.inventoryState.value
        }
        if (state !is LocalModelInventoryState.Ready) return@runBlocking error("MODEL_INVENTORY_UNAVAILABLE")
        val token = request.optString("pageToken", "").takeIf { it.isNotEmpty() }
        val offset = if (token == null) 0 else {
            val bits = token.split('.')
            if (bits.size != 2 || bits[0].toLongOrNull() != state.revision) return@runBlocking error("INVENTORY_REVISION_CHANGED")
            bits[1].toIntOrNull()?.takeIf { it >= 0 } ?: return@runBlocking error("INVALID_PAGE_TOKEN")
        }
        val ordered = state.models.sortedBy { it.id }
        if (offset > ordered.size) return@runBlocking error("INVALID_PAGE_TOKEN")
        val page = ordered.drop(offset).take(HaloLabProtocol.MAX_PAGE_ITEMS)
        val models = JSONArray()
        page.forEach { item ->
            val expectedSha = item.sha256
            val pin = if (item.verified && expectedSha != null) runCatching {
                manager.acquireVerifiedArtifactLease(item.id, expectedSha)
            }.getOrNull() else null
            val manifest = try {
                if (pin != null) buildArtifactManifest(pin.first) else null
            } finally { runCatching { pin?.second?.close() } }
            val stateCode = when {
                item.verified && manifest != null -> "INSTALLED_VERIFIED"
                item.validation.name == "UNVERIFIED" && manifest != null -> "INSTALLED_UNVERIFIED"
                item.validation.name == "CORRUPT" -> "CORRUPT"
                else -> "UNSUPPORTED"
            }
            val entry = JSONObject().put("providerId", "provider.litert.local").put("modelId", item.id)
                .put("generationId", manifest?.first ?: JSONObject.NULL).put("state", stateCode)
                .put("manifest", manifest?.second ?: JSONObject.NULL)
                .put("unavailableReason", if (stateCode == "INSTALLED_VERIFIED") JSONObject.NULL else
                    if (item.verified) "ARTIFACT_SNAPSHOT_UNAVAILABLE" else "ARTIFACT_${item.validation.name}")
                .put("roles", JSONArray(if (stateCode == "INSTALLED_VERIFIED" && item.supportsText) listOf("FAST") else emptyList<String>()))
                .put("backendCodes", JSONArray(if (stateCode == "INSTALLED_VERIFIED" && item.supportsText) listOf("CPU") else emptyList<String>()))
            models.put(entry)
        }
        val next = (offset + page.size).takeIf { it < ordered.size }?.let { "${state.revision}.$it" }
        JSONObject().put("status", "SUCCESS").put("entries", models).put("nextPageToken", next ?: JSONObject.NULL)
            .put("revision", state.revision).toString()
    }

    private fun buildArtifactManifest(item: com.halo.providers.litert.models.LocalModelDescriptor): Pair<String, JSONObject>? {
        val main = java.io.File(item.path)
        val root = runCatching { haloStorageLayout(this).models.canonicalFile }.getOrNull() ?: return null
        val canonical = runCatching { main.canonicalFile }.getOrNull() ?: return null
        if (!canonical.toPath().startsWith(root.toPath()) || !canonical.isFile || canonical.length() != item.bytes) return null
        val parts = mutableListOf<Pair<String, Pair<String, Long>>>()
        val mainName = "${item.id}.litertlm"
        parts += mainName to ((item.sha256 ?: return null) to item.bytes)
        val sidecar = java.io.File(root, "${item.id}.halo-model.json")
        if (sidecar.isFile) {
            val sidecarHash = runCatching { sha256Bytes(sidecar.readBytes()) }.getOrNull() ?: return null
            parts += sidecar.name to (sidecarHash to sidecar.length())
        }
        val partsJson = JSONArray()
        parts.sortedBy { it.first }.forEach { (name, data) ->
            partsJson.put(JSONObject().put("relativeName", name).put("sha256", data.first).put("byteCount", data.second))
        }
        val generationId = sha256("provider.litert.local\n${item.id}\n" + parts.sortedBy { it.first }
            .joinToString("\n") { "${it.first}:${it.second.first}:${it.second.second}" })
        val manifest = JSONObject().put("providerId", "provider.litert.local").put("modelId", item.id)
            .put("generationId", generationId).put("parts", partsJson)
        return generationId to manifest
    }

    private fun acquireArtifactLease(raw: String?): String = runBlocking(Dispatchers.IO) {
        val req = JSONObject(raw.orEmpty())
        if (req.optString("providerId") != "provider.litert.local") return@runBlocking error("PROVIDER_UNSUPPORTED")
        val model = req.optString("modelId")
        val generation = req.optString("generationId")
        if (model.isBlank() || model.length > 160 || !SHA_PATTERN.matches(generation)) return@runBlocking error("INVALID_ARTIFACT_IDENTITY")
        val descriptor = ManualDependencyGraph.providerGraph.modelManager.resolveModel(model)
            ?.takeIf { it.verified && it.sha256 != null } ?: return@runBlocking error("VERIFIED_ARTIFACT_UNAVAILABLE")
        val sha = descriptor.sha256 ?: return@runBlocking error("ARTIFACT_SHA_UNKNOWN")
        val lease = ManualDependencyGraph.providerGraph.modelManager.acquireVerifiedArtifactLease(model, sha)
            ?: return@runBlocking error("VERIFIED_ARTIFACT_UNAVAILABLE")
        val expected = buildArtifactManifest(lease.first)?.takeIf { it.first == generation }
            ?: run { lease.second.close(); return@runBlocking error("ARTIFACT_GENERATION_CHANGED") }
        val token = UUID.randomUUID().toString()
        val expiry = SystemClock.elapsedRealtime() + LEASE_TTL_MS
        artifactLeases[token] = TimedArtifactLease(
            closeable = lease.second,
            expiresAtElapsedMs = expiry,
            providerId = "provider.litert.local",
            modelId = lease.first.id,
            generationId = generation,
            manifest = expected.second,
        )
        JSONObject().put("status", "SUCCESS").put("leaseId", token).put("providerId", "provider.litert.local")
            .put("modelId", lease.first.id).put("generationId", generation).put("manifest", expected.second)
            .put("serverEpoch", journal.serverEpoch).put("serverBootId", serverInstanceId)
            .put("expiresAtElapsedRealtimeMs", expiry).toString()
    }

    private fun renewArtifactLease(raw: String?): String {
        val token = JSONObject(raw.orEmpty()).optString("leaseId")
        if (!UUID_PATTERN.matches(token)) return error("INVALID_LEASE_ID")
        val lease = artifactLeases[token] ?: return error("LEASE_NOT_FOUND")
        lease.expiresAtElapsedMs = SystemClock.elapsedRealtime() + LEASE_TTL_MS
        return JSONObject().put("status", "SUCCESS").put("leaseId", token)
            .put("providerId", lease.providerId).put("modelId", lease.modelId).put("generationId", lease.generationId)
            .put("manifest", lease.manifest).put("serverEpoch", journal.serverEpoch).put("serverBootId", serverInstanceId)
            .put("expiresAtElapsedRealtimeMs", lease.expiresAtElapsedMs).toString()
    }

    private fun releaseArtifactLease(raw: String?): String {
        val token = JSONObject(raw.orEmpty()).optString("leaseId")
        if (!UUID_PATTERN.matches(token)) return error("INVALID_LEASE_ID")
        artifactLeases.remove(token)?.closeable?.close()
        return JSONObject().put("status", "SUCCESS").toString()
    }

    private fun submitCase(raw: String?): String {
        val req = JSONObject(raw.orEmpty())
        val attempt = req.optString("attemptId")
        val model = req.optString("modelId")
        val sha = req.optString("artifactSha256")
        val fixture = req.optString("fixtureId")
        val suppliedLease = artifactLeases[req.optString("leaseId")]
        if (!UUID_PATTERN.matches(attempt) || model.isBlank() || model.length > 160 || !SHA_PATTERN.matches(sha) || fixture != FIXTURE_ID)
            return error("UNSUPPORTED_OR_INVALID_CASE")
        if (suppliedLease == null || suppliedLease.expiresAtElapsedMs <= SystemClock.elapsedRealtime() ||
            suppliedLease.modelId != model || suppliedLease.generationId != req.optString("generationId") ||
            suppliedLease.manifest.optJSONArray("parts")?.let { parts ->
                (0 until parts.length()).map { parts.getJSONObject(it) }
                    .firstOrNull { it.optString("relativeName").endsWith(".litertlm") }?.optString("sha256")
            } != sha)
            return error("ARTIFACT_LEASE_REQUIRED")
        if (req.optString("suiteId") != "fast-smoke" || req.optString("suiteVersion") != "1" ||
            req.optString("caseId") != "short-ready" || req.optString("role") != "FAST" ||
            req.optString("requestedBackend") != "CPU" || req.optInt("maximumOutputTokens", -1) != 8 ||
            req.optString("fixtureManifestSha256") != HaloLabProtocol.FAST_SMOKE_FIXTURE_SHA256 ||
            !UUID_PATTERN.matches(req.optString("sessionId")) || !UUID_PATTERN.matches(req.optString("queueItemId")) ||
            !SHA_PATTERN.matches(req.optString("planDigestSha256"))) return error("UNSUPPORTED_OR_INVALID_CASE")
        val requestHash = sha256(listOf(attempt, model, sha, fixture, req.optString("sessionId"),
            req.optString("queueItemId"), req.optString("planDigestSha256"), req.optString("fixtureManifestSha256"))
            .joinToString("\n"))
        if (journal.openAttempts().any { it.attemptId != attempt }) return error("RECOVERY_REQUIRED")
        when (journal.accept(attempt, requestHash, model, sha)) {
            "REQUEST_ID_CONFLICT" -> return error("REQUEST_ID_CONFLICT")
            "RECOVERY_REQUIRED" -> return error("RECOVERY_REQUIRED")
            "EXISTING" -> return queryCase(raw)
        }
        // Durable intent is committed before acquiring resources, binding a worker or executing.
        scope.launch { startAttempt(attempt, model, sha) }
        return JSONObject().put("status", "ACCEPTED").put("attemptId", attempt).toString()
    }

    private suspend fun startAttempt(attemptId: String, modelId: String, sha: String) {
        if (!startingAttempts.add(attemptId)) return
        try {
        val resourceManager = ManualDependencyGraph.infra.resourceLeaseManager
        val request = ResourceLeaseRequest(
            requestId = "halo-lab:$attemptId",
            holder = ResourceHolder("halo-model-lab:$attemptId", runId = attemptId, kind = ResourceHolderKind.SYSTEM),
            claims = setOf(ResourceClaim(ResourceKey(HaloResourceIds.ComputeInference, ResourceEndpointId("host:primary")), ResourceAccess.EXCLUSIVE)),
            priority = ResourcePriority.USER_VISIBLE_BACKGROUND,
            waitPolicy = ResourceWaitPolicy.FailFast,
            preemption = ResourcePreemptionPolicy.NEVER,
        )
        val lease = (resourceManager.acquire(request) as? ResourceAcquireResult.Granted)?.lease
        if (lease == null) {
            journal.terminal(attemptId, terminalFailure(attemptId, "RESOURCE_BUSY", "HARNESS"))
            return
        }
        val manager = ManualDependencyGraph.providerGraph.modelManager
        val pinned = runCatching { manager.acquireVerifiedArtifactLease(modelId, sha) }.getOrNull()
        if (pinned == null) {
            lease.release()
            journal.terminal(attemptId, terminalFailure(attemptId, "ARTIFACT_UNAVAILABLE", "MODEL"))
            return
        }
        val run = ActiveRun(attemptId, lease, pinned.second)
        active[attemptId] = run
        journal.stage(attemptId, "PREFLIGHT", "WORKER_STARTING")
        val intent = Intent(this, HaloModelBenchService::class.java)
        val connection = WorkerConnection(run)
        run.connection = connection
        val bound = runCatching { bindService(intent, connection, BIND_AUTO_CREATE) }.getOrDefault(false)
        if (!bound) {
            finishUnstarted(run, "WORKER_BIND_FAILED", "HARNESS")
            return
        }
        run.bound = true
        if (withTimeoutOrNull(WORKER_BIND_TIMEOUT_MS) { run.ready.await(); true } != true) {
            if (stopRequestedBeforeAttach.remove(attemptId)) {
                journal.terminal(attemptId, cancelledBeforeNative(attemptId))
                releaseRun(run)
                return
            }
            finishUnstarted(run, "WORKER_BIND_TIMEOUT", "HARNESS")
            return
        }
        if (stopRequestedBeforeAttach.remove(attemptId)) {
            journal.terminal(attemptId, cancelledBeforeNative(attemptId))
            releaseRun(run)
            return
        }
        if (run.worker == null) { finishUnstarted(run, "WORKER_UNAVAILABLE", "HARNESS"); return }
        journal.stage(attemptId, "EXECUTE", "EXECUTION_STARTED")
        run.nativeStartRequested = true
        val reply = runCatching {
            JSONObject(run.worker!!.startTextProbe(attemptId, modelId, sha, SystemClock.elapsedRealtime() + ATTEMPT_DEADLINE_MS))
        }.getOrElse { finishUnstarted(run, "WORKER_START_FAILED", "HARNESS"); return }
        if (reply.optString("status") == "REJECTED") { finishUnstarted(run, reply.optString("code", "WORKER_REJECTED"), "HARNESS"); return }
        val stopRequested = AtomicBoolean(false)
        while (true) {
            val state = runCatching { JSONObject(run.worker!!.queryAttempt(attemptId)) }.getOrNull()
            if (state == null) {
                // Binder death is the only hard-cancellation proof accepted here.
                run.workerDied.await()
                break
            }
            when (state.optString("status")) {
                "TERMINAL", "FAILED", "CANCELLED" -> if (state.optString("cleanup") == "UNLOADED") {
                    journal.terminal(attemptId, state.put("workerInstanceId", run.workerId).put("artifactId", modelId))
                    releaseRun(run)
                    return
                }
                "CLEANUP_UNCONFIRMED" -> { run.workerDied.await(); break }
            }
            if (SystemClock.elapsedRealtime() > run.deadlineElapsedMs && stopRequested.compareAndSet(false, true)) {
                runCatching { run.worker?.requestStop(attemptId) }
                journal.stage(attemptId, "UNLOAD", "DEADLINE_STOP_REQUESTED")
            }
            delay(250L)
        }
        journal.terminal(attemptId, terminalFailure(attemptId, "WORKER_PROCESS_DIED", "HARNESS"))
        releaseRun(run)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            val run = active[attemptId]
            if (run != null) {
                stopAndSettle(run, attemptId, modelId, "SUPERVISOR_CANCELLED")
            } else {
                runCatching { journal.terminal(attemptId, terminalFailure(attemptId, "SUPERVISOR_CANCELLED", "HARNESS")) }
            }
            throw cancelled
        } catch (failure: Throwable) {
            val run = active[attemptId]
            if (run == null) runCatching {
                journal.terminal(attemptId, terminalFailure(attemptId, "SUPERVISOR_${failure.javaClass.simpleName.take(40)}", "HARNESS"))
            } else runCatching { stopAndSettle(run, attemptId, modelId, "SUPERVISOR_FAILURE") }
        } finally {
            startingAttempts.remove(attemptId)
        }
    }

    private suspend fun finishUnstarted(run: ActiveRun, code: String, domain: String) {
        journal.terminal(run.attemptId, terminalFailure(run.attemptId, code, domain))
        releaseRun(run)
    }

    private fun cancelledBeforeNative(attemptId: String) = JSONObject()
        .put("status", "CANCELLED").put("attemptId", attemptId).put("outcome", "CANCELLED")
        .put("validity", "UNKNOWN").put("origin", "DEVICE_MEASUREMENT")

    private suspend fun stopAndSettle(run: ActiveRun, attemptId: String, modelId: String, reason: String) {
        withContext(NonCancellable + Dispatchers.IO) {
            if (!run.nativeStartRequested && run.worker == null) {
                if (reason.contains("CANCEL")) journal.terminal(attemptId, cancelledBeforeNative(attemptId))
                else journal.terminal(attemptId, terminalFailure(attemptId, reason, "HARNESS"))
                releaseRun(run)
                return@withContext
            }
            journal.stage(attemptId, "UNLOAD", "${reason.take(64)}_STOPPING")
            runCatching { run.worker?.requestStop(attemptId) }
            while (true) {
                val state = run.worker?.let { worker -> runCatching { JSONObject(worker.queryAttempt(attemptId)) }.getOrNull() }
                if (state != null && state.optString("status") == "UNKNOWN" && state.isNull("activeAttemptId")) {
                    runCatching { journal.terminal(attemptId, terminalFailure(attemptId, reason, "HARNESS")) }
                    releaseRun(run)
                    break
                }
                if (state == null && run.workerDied.isCompleted) {
                    journal.terminal(attemptId, terminalFailure(attemptId, "WORKER_PROCESS_DIED", "HARNESS"))
                    releaseRun(run)
                    break
                }
                if (state != null && state.optString("cleanup") == "UNLOADED" &&
                    state.optString("status") in setOf("TERMINAL", "FAILED", "CANCELLED")) {
                    journal.terminal(attemptId, state.put("workerInstanceId", run.workerId).put("artifactId", modelId))
                    releaseRun(run)
                    break
                }
                delay(250L)
            }
        }
    }

    private fun releaseRun(run: ActiveRun) {
        if (!run.released.compareAndSet(false, true)) return
        run.connection?.let { if (run.bound) runCatching { unbindService(it) } }
        run.artifact.close()
        run.lease.release()
        active.remove(run.attemptId, run)
    }

    private fun queryCase(raw: String?): String {
        val req = JSONObject(raw.orEmpty())
        val attempt = req.optString("attemptId")
        if (!UUID_PATTERN.matches(attempt)) return error("INVALID_ATTEMPT_ID")
        val saved = journal.query(attempt) ?: return error("ATTEMPT_NOT_FOUND")
        if (!saved.terminal && active[attempt] == null) scope.launch { recoverAttempt(saved) }
        val live = active[attempt]?.let { run -> run.worker?.let { worker -> runCatching { JSONObject(worker.queryAttempt(attempt)) }.getOrNull() } }
        return JSONObject().put("status", "SUCCESS").put("attemptId", attempt).put("state", saved.state)
            .put("terminal", saved.terminal).put("lastSequence", saved.lastSequence)
            .put("result", if (saved.terminal) saved.payload else (live ?: saved.payload)).toString()
    }

    /** Reattaches to the one persisted attempt before any new allocation is allowed. */
    private suspend fun recoverAttempt(saved: HaloLabAttempt) {
        if (saved.terminal || active.containsKey(saved.attemptId) || !startingAttempts.add(saved.attemptId)) return
        try {
        val resourceManager = ManualDependencyGraph.infra.resourceLeaseManager
        val request = ResourceLeaseRequest(
            requestId = "halo-lab-recovery:${saved.attemptId}",
            holder = ResourceHolder("halo-model-lab-recovery:${saved.attemptId}", runId = saved.attemptId, kind = ResourceHolderKind.SYSTEM),
            claims = setOf(ResourceClaim(ResourceKey(HaloResourceIds.ComputeInference, ResourceEndpointId("host:primary")), ResourceAccess.EXCLUSIVE)),
            priority = ResourcePriority.USER_VISIBLE_BACKGROUND,
            waitPolicy = ResourceWaitPolicy.FailFast,
            preemption = ResourcePreemptionPolicy.NEVER,
        )
        val lease = (resourceManager.acquire(request) as? ResourceAcquireResult.Granted)?.lease
        if (lease == null) { startingAttempts.remove(saved.attemptId); return }
        val pinned = runCatching {
            ManualDependencyGraph.providerGraph.modelManager.acquireVerifiedArtifactLease(saved.modelId, saved.artifactSha256)
        }.getOrNull()
        if (pinned == null) { lease.release(); startingAttempts.remove(saved.attemptId); return }
        val run = ActiveRun(saved.attemptId, lease, pinned.second)
        active[saved.attemptId] = run
        startingAttempts.remove(saved.attemptId)
        run.nativeStartRequested = true
        run.connection = WorkerConnection(run)
        val bound = runCatching { bindService(Intent(this, HaloModelBenchService::class.java), run.connection!!, BIND_AUTO_CREATE) }.getOrDefault(false)
        if (!bound) { releaseRun(run); return }
        run.bound = true
        if (withTimeoutOrNull(WORKER_BIND_TIMEOUT_MS) { run.ready.await(); true } != true || run.worker == null) {
            // Preserve both leases: a previously-running native worker is not proven dead.
            return
        }
        val initial = runCatching { JSONObject(run.worker!!.queryAttempt(saved.attemptId)) }.getOrNull()
        if (initial == null) {
            run.workerDied.await()
                journal.terminal(saved.attemptId, terminalFailure(saved.attemptId, "WORKER_DIED_DURING_RECOVERY", "HARNESS"))
            releaseRun(run)
            return
        }
        if (initial.optString("status") == "UNKNOWN") {
            if (initial.isNull("activeAttemptId")) {
                journal.terminal(saved.attemptId, terminalFailure(saved.attemptId, "WORKER_ATTEMPT_NOT_RECOVERABLE", "HARNESS"))
            }
            // An unrelated active worker state is not proof that this attempt is terminal.
            releaseRun(run)
            return
        }
        var stopRequested = false
        val recoveryDeadline = SystemClock.elapsedRealtime() + ATTEMPT_DEADLINE_MS
        while (true) {
            val state = runCatching { JSONObject(run.worker!!.queryAttempt(saved.attemptId)) }.getOrNull()
            if (state == null) { run.workerDied.await(); break }
            if (state.optString("status") in setOf("TERMINAL", "FAILED", "CANCELLED") && state.optString("cleanup") == "UNLOADED") {
                journal.terminal(saved.attemptId, state.put("workerInstanceId", run.workerId).put("artifactId", saved.modelId))
                releaseRun(run)
                return
            }
            if (state.optString("status") == "CLEANUP_UNCONFIRMED") { run.workerDied.await(); break }
            if (!stopRequested && SystemClock.elapsedRealtime() > recoveryDeadline) {
                stopRequested = true
                runCatching { run.worker?.requestStop(saved.attemptId) }
                journal.stage(saved.attemptId, "UNLOAD", "RECOVERY_DEADLINE_STOP_REQUESTED")
            }
            delay(250L)
        }
        journal.terminal(saved.attemptId, terminalFailure(saved.attemptId, "WORKER_PROCESS_DIED", "HARNESS"))
        releaseRun(run)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            active[saved.attemptId]?.let { stopAndSettle(it, saved.attemptId, saved.modelId, "RECOVERY_CANCELLED") }
            throw cancelled
        } catch (_: Throwable) {
            active[saved.attemptId]?.let { stopAndSettle(it, saved.attemptId, saved.modelId, "RECOVERY_FAILURE") }
        } finally {
            startingAttempts.remove(saved.attemptId)
        }
    }

    private fun cancelCase(raw: String?): String {
        val attempt = JSONObject(raw.orEmpty()).optString("attemptId")
        if (!UUID_PATTERN.matches(attempt)) return error("INVALID_ATTEMPT_ID")
        if (active[attempt] == null && startingAttempts.contains(attempt)) {
            stopRequestedBeforeAttach += attempt
            journal.stage(attempt, "UNLOAD", "USER_STOP_REQUESTED")
            return JSONObject().put("status", "SUCCESS").put("attemptId", attempt)
                .put("disposition", "REQUEST_ACCEPTED").put("evidenceSequence", journal.query(attempt)?.lastSequence ?: 0L).toString()
        }
        val run = active[attempt] ?: return if (journal.query(attempt)?.terminal == true) {
            JSONObject().put("status", "SUCCESS").put("attemptId", attempt).put("disposition", "ALREADY_TERMINAL")
                .put("evidenceSequence", journal.query(attempt)?.lastSequence ?: 0L).toString()
        } else error("ATTEMPT_NOT_ACTIVE")
        val worker = run.worker
        if (worker == null) {
            stopRequestedBeforeAttach += attempt
            journal.stage(attempt, "UNLOAD", "USER_STOP_REQUESTED")
            return JSONObject().put("status", "SUCCESS").put("attemptId", attempt)
                .put("disposition", "REQUEST_ACCEPTED").put("evidenceSequence", journal.query(attempt)?.lastSequence ?: 0L).toString()
        }
        val result = runCatching { worker.requestStop(attempt) }.getOrElse { return error("WORKER_UNAVAILABLE") }
        journal.stage(attempt, "UNLOAD", "USER_STOP_REQUESTED")
        return JSONObject().put("status", "SUCCESS").put("attemptId", attempt)
            .put("workerInstanceId", run?.workerId ?: JSONObject.NULL)
            .put("disposition", "REQUEST_ACCEPTED").put("evidenceSequence", journal.query(attempt)?.lastSequence ?: 0L)
            .put("worker", result).toString()
    }

    private fun readEvents(raw: String?): String {
        val req = JSONObject(raw.orEmpty())
        if (!UUID_PATTERN.matches(req.optString("attemptId"))) return error("INVALID_ATTEMPT_ID")
        val arr = journal.readEvents(req.optString("attemptId"), req.optLong("afterSequence", 0L), req.optInt("limit", 64))
        return JSONObject().put("status", "SUCCESS").put("events", arr).toString()
    }

    private fun acknowledgeEvents(raw: String?): String {
        val req = JSONObject(raw.orEmpty())
        if (!UUID_PATTERN.matches(req.optString("attemptId"))) return error("INVALID_ATTEMPT_ID")
        return if (journal.acknowledge(req.optString("attemptId"), req.optLong("sequence", -1L)))
            JSONObject().put("status", "SUCCESS").toString() else error("INVALID_EVENT_CURSOR")
    }

    private fun unsupported(reason: String) = JSONObject().put("status", "UNSUPPORTED").put("reason", reason).toString()
    private fun error(code: String) = JSONObject().put("status", "ERROR").put("code", code).toString()
    private fun terminalFailure(id: String, code: String, domain: String) = JSONObject()
        .put("status", "FAILED").put("attemptId", id).put("outcome", "FAIL").put("validity", "INVALID")
        .put("failureCode", code).put("failureDomain", domain).put("origin", "DEVICE_MEASUREMENT")

    private inner class WorkerConnection(val run: ActiveRun) : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val worker = IModelBenchWorker.Stub.asInterface(service)
            if (worker == null) {
                run.ready.complete(Unit)
                return
            }
            run.worker = worker
            runCatching { worker.attachSupervisor(run.supervisorBinder, run.workerId) }
                .onSuccess { if (JSONObject(it).optString("status") == "SUCCESS") run.ready.complete(Unit) else run.ready.complete(Unit) }
                .onFailure { run.ready.complete(Unit) }
        }
        override fun onServiceDisconnected(name: ComponentName?) { run.worker = null; run.workerDied.complete(Unit) }
        override fun onBindingDied(name: ComponentName?) { run.worker = null; run.workerDied.complete(Unit) }
        override fun onNullBinding(name: ComponentName?) { run.worker = null; run.ready.complete(Unit) }
    }

    private inner class ActiveRun(val attemptId: String, val lease: ResourceLease, val artifact: java.io.Closeable) {
        val workerId = UUID.randomUUID().toString()
        val ready = kotlinx.coroutines.CompletableDeferred<Unit>()
        val workerDied = kotlinx.coroutines.CompletableDeferred<Unit>()
        val released = AtomicBoolean(false)
        val deadlineElapsedMs = SystemClock.elapsedRealtime() + ATTEMPT_DEADLINE_MS + STOP_GRACE_MS
        var worker: IModelBenchWorker? = null
        var connection: ServiceConnection? = null
        var bound = false
        @Volatile var nativeStartRequested = false
        val supervisorBinder: IBinder = Binder()
    }

    private data class TimedArtifactLease(
        val closeable: java.io.Closeable,
        @Volatile var expiresAtElapsedMs: Long,
        val providerId: String,
        val modelId: String,
        val generationId: String,
        val manifest: JSONObject,
    )

    private fun expireArtifactLeases() {
        val now = SystemClock.elapsedRealtime()
        artifactLeases.entries.removeIf { (key, lease) ->
            if (lease.expiresAtElapsedMs <= now) { runCatching { lease.closeable.close() }; true } else false
        }
    }

    override fun onDestroy() {
        artifactLeases.values.forEach { runCatching { it.closeable.close() } }
        artifactLeases.clear()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_CONSENT_REVOKED = "com.aistudio.braintool.xkqzps.action.HALO_LAB_CONSENT_REVOKED"
        private val SERVER_INSTANCE_ID = UUID.randomUUID().toString()
        private const val FIXTURE_ID = "arith-multiply-17x8-v1"
        private const val LEASE_TTL_MS = 5 * 60_000L
        private const val ATTEMPT_DEADLINE_MS = 60_000L
        private const val STOP_GRACE_MS = 5_000L
        private const val WORKER_BIND_TIMEOUT_MS = 5_000L
        private val UUID_PATTERN = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        private val SHA_PATTERN = Regex("[0-9a-f]{64}")
        private fun sha256(value: String): String = java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        private fun sha256Bytes(value: ByteArray): String = java.security.MessageDigest.getInstance("SHA-256")
            .digest(value).joinToString("") { "%02x".format(it) }
    }
}
