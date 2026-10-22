package com.halo.app.lab.worker

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Process
import com.halo.app.storage.haloStorageLayout
import com.halo.lab.bridge.v1.IModelBenchWorker
import com.halo.providers.litert.LiteRtLocalProvider
import com.halo.providers.litert.models.LiteRtModelManager
import com.halo.sdk.provider.LocalComputeBackend
import com.halo.host.android.AndroidStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.util.concurrent.atomic.AtomicBoolean

/** Native model code lives in Halo's private :modelbench process, separate from the supervisor. */
class HaloModelBenchService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var models: LiteRtModelManager
    private lateinit var localProvider: LiteRtLocalProvider
    private lateinit var modelRoot: java.io.File
    private val lock = Any()
    private var supervisorToken: IBinder? = null
    private var supervisorDeath: IBinder.DeathRecipient? = null
    private var activeAttemptId: String? = null
    private var activePayloadHash: String? = null
    private var activeJob: Job? = null
    private var processRunLockFile: RandomAccessFile? = null
    private var processRunLock: FileLock? = null
    @Volatile private var cleanupConfirmed = true
    private var stateJson: JSONObject = JSONObject().put("state", "IDLE")
    private val selfKillScheduled = AtomicBoolean(false)

    private val binder = object : IModelBenchWorker.Stub() {
        override fun attachSupervisor(supervisor: IBinder?, workerInstanceId: String?): String =
            attach(supervisor, workerInstanceId)

        override fun startTextProbe(attemptId: String?, modelId: String?, expectedSha256: String?, deadlineElapsedRealtimeMs: Long): String =
            start(attemptId.orEmpty(), modelId.orEmpty(), expectedSha256.orEmpty(), deadlineElapsedRealtimeMs)

        override fun queryAttempt(attemptId: String?): String = query(attemptId.orEmpty())

        override fun requestStop(attemptId: String?): String = stop(attemptId.orEmpty())
    }

    override fun onCreate() {
        super.onCreate()
        check(isBenchmarkProcess()) { "Benchmark service is not isolated from Halo's normal process" }
        modelRoot = haloStorageLayout(this).models
        models = LiteRtModelManager(this, modelRoot = modelRoot, backgroundDownloads = false)
        localProvider = LiteRtLocalProvider(
            context = this,
            inventoryRepository = models,
            storage = AndroidStorage(this),
            nativeResourceCoordinator = null,
        )
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun attach(token: IBinder?, workerId: String?): String = synchronized(lock) {
        if (token == null || workerId.isNullOrBlank() || workerId.length > 96) return@synchronized reply("REJECTED", "SUPERVISOR_REQUIRED")
        val existing = supervisorToken
        if (existing != null && existing.isBinderAlive && activeJob?.isActive == true && existing !== token) {
            return@synchronized reply("REJECTED", "WORKER_RECOVERY_REQUIRED")
        }
        runCatching {
            supervisorDeath?.let { existing?.unlinkToDeath(it, 0) }
            val death = IBinder.DeathRecipient {
                // This binder callback does not share the native execution thread or its locks.
                scheduleSelfTermination("SUPERVISOR_DIED", delayMs = 1_000L)
            }
            token.linkToDeath(death, 0)
            supervisorToken = token
            supervisorDeath = death
        }.onFailure { return@synchronized reply("REJECTED", "SUPERVISOR_UNAVAILABLE") }
        reply("SUCCESS", "ATTACHED").put("workerInstanceId", workerId).toString()
    }

    private fun start(attemptId: String, modelId: String, expectedSha256: String, deadline: Long): String = synchronized(lock) {
        if (!UUID_REGEX.matches(attemptId) || modelId.isBlank() || modelId.length > 160 || !SHA_REGEX.matches(expectedSha256))
            return@synchronized reply("REJECTED", "INVALID_REQUEST")
        if (supervisorToken?.isBinderAlive != true) return@synchronized reply("REJECTED", "SUPERVISOR_UNAVAILABLE")
        val payloadHash = sha256("$attemptId\n$modelId\n$expectedSha256\n$FIXTURE_ID")
        if (activeAttemptId == attemptId) {
            if (activePayloadHash != payloadHash) return@synchronized reply("REJECTED", "REQUEST_ID_CONFLICT")
            return@synchronized stateJson.toString()
        }
        if (activeJob?.isActive == true) return@synchronized reply("REJECTED", "WORKER_BUSY")
        val now = android.os.SystemClock.elapsedRealtime()
        if (deadline <= now) return@synchronized reply("REJECTED", "DEADLINE_EXPIRED")
        if (deadline - now > MAX_ATTEMPT_DEADLINE_MS) return@synchronized reply("REJECTED", "DEADLINE_TOO_LARGE")
        // A previous native worker may outlive its former supervisor. Its OS lock remains held
        // until native cleanup or process death, so a new allocation cannot overlap it.
        val lockFile = runCatching {
            val dir = java.io.File(modelRoot, ".lab-leases")
            if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) throw java.io.IOException("LOCK_DIR_UNAVAILABLE")
            RandomAccessFile(java.io.File(dir, "worker-allocation.lock"), "rw")
        }.getOrNull() ?: return@synchronized reply("REJECTED", "WORKER_LOCK_UNAVAILABLE")
        val processLock = runCatching { lockFile.channel.tryLock() }.getOrNull()
        if (processLock == null) {
            lockFile.close()
            return@synchronized reply("REJECTED", "WORKER_RECOVERY_REQUIRED")
        }
        processRunLockFile = lockFile
        processRunLock = processLock
        cleanupConfirmed = false
        activeAttemptId = attemptId
        activePayloadHash = payloadHash
        selfKillScheduled.set(false)
        stateJson = reply("PENDING", "ACCEPTED").put("attemptId", attemptId)
        activeJob = serviceScope.launch {
            execute(attemptId, modelId, expectedSha256, deadline)
        }
        stateJson.toString()
    }

    private suspend fun execute(attemptId: String, modelId: String, expectedSha256: String, deadline: Long) {
        var terminal: JSONObject? = null
        try {
            if (android.os.SystemClock.elapsedRealtime() >= deadline) throw ProbeFailure("DEADLINE_EXPIRED")
            synchronized(lock) { stateJson = reply("PENDING", "EXECUTING").put("attemptId", attemptId) }
            val exact = models.verifyPhysicalArtifact(modelId)
                ?.takeIf { it.verified && it.sha256 == expectedSha256 }
                ?: throw ProbeFailure("ARTIFACT_IDENTITY_CHANGED")
            val result = localProvider.benchmarkModel(
                modelId = modelId,
                prompt = FIXTURE_PROMPT,
                maxOutputTokens = 8,
                backendOverride = LocalComputeBackend.CPU,
            ).getOrElse { throw it }
            if (result.artifactSha256 != expectedSha256 || result.effectiveBackend != LocalComputeBackend.CPU)
                throw ProbeFailure("EXECUTOR_IDENTITY_MISMATCH")
            val fixtureMatch = result.responseText.trim() == FIXTURE_EXPECTED
            terminal = JSONObject()
                .put("status", "TERMINAL")
                .put("attemptId", attemptId)
                .put("outcome", if (fixtureMatch) "PASS" else "FAIL")
                .put("validity", "VALID")
                .put("failureDomain", if (fixtureMatch) JSONObject.NULL else "MODEL")
                .put("failureCode", if (fixtureMatch) JSONObject.NULL else "FIXTURE_MISMATCH")
                .put("providerId", "provider.litert.local")
                .put("modelId", exact.id)
                .put("artifactBytes", exact.bytes)
                .put("artifactSha256", expectedSha256)
                .put("requestedBackend", result.requestedBackend.name)
                .put("effectiveBackend", result.effectiveBackend.name)
                .put("runtimeVersion", LiteRtLocalProvider.RUNTIME_VERSION)
                .put("loadMs", result.loadDurationMs)
                .put("timeToFirstTokenMs", result.timeToFirstTokenMs)
                .put("generationMs", result.generationDurationMs)
                .put("response", result.responseText.take(MAX_OUTPUT_CHARS))
                .put("outputTruncated", result.responseText.length > MAX_OUTPUT_CHARS)
                .put("fixtureId", FIXTURE_ID)
                .put("origin", "DEVICE_MEASUREMENT")
        } catch (cancelled: CancellationException) {
            terminal = reply("CANCELLED", "USER_STOP").put("attemptId", attemptId)
                .put("outcome", "CANCELLED").put("validity", "UNKNOWN")
            throw cancelled
        } catch (failure: Throwable) {
            val failureCode = (failure as? ProbeFailure)?.code ?: "WORKER_${failure.javaClass.simpleName.take(48)}"
            val failureDomain = when {
                failureCode.startsWith("ARTIFACT_") -> "MODEL"
                failureCode.startsWith("EXECUTOR_") -> "ADAPTER"
                failureCode == "DEADLINE_EXPIRED" -> "ENVIRONMENT"
                else -> "RUNTIME"
            }
            terminal = reply("FAILED", failureCode)
                .put("attemptId", attemptId)
                .put("outcome", "FAIL").put("validity", "INVALID")
                .put("failureCode", failureCode).put("failureDomain", failureDomain)
                .put("origin", "DEVICE_MEASUREMENT")
        } finally {
            val unloaded = withContext(NonCancellable + Dispatchers.IO) {
                runCatching { localProvider.unloadLocalModel() }.getOrDefault(false)
            }
            if (!unloaded) {
                synchronized(lock) { stateJson = reply("CLEANUP_UNCONFIRMED", "WORKER_SELF_TERMINATING").put("attemptId", attemptId) }
                scheduleSelfTermination("CLEANUP_UNCONFIRMED", delayMs = 100L)
            } else {
                cleanupConfirmed = true
                synchronized(lock) {
                    if (activeAttemptId == attemptId) stateJson = (terminal ?: reply("FAILED", "UNKNOWN_WORKER_OUTCOME"))
                        .put("cleanup", "UNLOADED")
                        .put("workerProcessId", Process.myPid())
                    activeJob = null
                    runCatching { processRunLock?.release() }
                    runCatching { processRunLockFile?.close() }
                    processRunLock = null
                    processRunLockFile = null
                    selfKillScheduled.set(false)
                }
            }
        }
    }

    private fun query(attemptId: String): String = synchronized(lock) {
        if (attemptId != activeAttemptId) reply("UNKNOWN", "ATTEMPT_NOT_IN_WORKER").put("attemptId", attemptId)
            .put("activeAttemptId", activeAttemptId ?: JSONObject.NULL).toString()
        else stateJson.toString()
    }

    private fun stop(attemptId: String): String = synchronized(lock) {
        if (attemptId != activeAttemptId) return@synchronized reply("REJECTED", "ATTEMPT_NOT_ACTIVE")
        val job = activeJob
        if (job?.isActive != true) return@synchronized stateJson.toString()
        stateJson = reply("STOPPING", "STOP_REQUESTED").put("attemptId", attemptId)
        job.cancel(CancellationException("HaloModelLab requested stop"))
        scheduleSelfTermination("STOP_GRACE_EXPIRED", delayMs = STOP_GRACE_MS)
        stateJson.toString()
    }

    private fun scheduleSelfTermination(reason: String, delayMs: Long) {
        if (!selfKillScheduled.compareAndSet(false, true)) return
        Thread({
            try { Thread.sleep(delayMs) } catch (_: InterruptedException) { return@Thread }
            val stillActive = reason == "SUPERVISOR_DIED" || !cleanupConfirmed
            if (stillActive) Process.killProcess(Process.myPid())
        }, "halo-modelbench-watchdog").apply { isDaemon = true; start() }
    }

    private fun isBenchmarkProcess(): Boolean = if (android.os.Build.VERSION.SDK_INT >= 28)
        android.app.Application.getProcessName() == "$packageName:modelbench"
    else (getSystemService(ACTIVITY_SERVICE) as? android.app.ActivityManager)?.runningAppProcesses
        ?.firstOrNull { it.pid == Process.myPid() }?.processName == "$packageName:modelbench"

    private fun reply(status: String, code: String) = JSONObject().put("status", status).put("code", code)

    override fun onDestroy() {
        supervisorDeath?.let { supervisorToken?.unlinkToDeath(it, 0) }
        serviceScope.cancel()
        super.onDestroy()
    }

    private class ProbeFailure(val code: String) : IllegalStateException(code)

    companion object {
        private const val FIXTURE_ID = "fast-smoke-1-short-ready"
        private const val FIXTURE_PROMPT = "Reply with exactly READY."
        private const val FIXTURE_EXPECTED = "READY"
        private const val MAX_OUTPUT_CHARS = 512
        private const val STOP_GRACE_MS = 5_000L
        private const val MAX_ATTEMPT_DEADLINE_MS = 6 * 60_000L
        private val UUID_REGEX = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        private val SHA_REGEX = Regex("[0-9a-f]{64}")

        private fun sha256(value: String): String = java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
