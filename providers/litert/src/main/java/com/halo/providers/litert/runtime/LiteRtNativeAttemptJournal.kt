package com.halo.providers.litert.runtime

import android.util.Log
import com.halo.sdk.provider.LocalComputeBackend
import com.halo.sdk.runtime.Storage
import org.json.JSONObject
import java.util.UUID

/**
 * The native boundary a pending [LiteRtNativeAttempt] may be sitting inside.
 *
 * These names stay in sync with the state machine's stage order:
 * engine init -> conversation create -> first-token wait -> generating -> teardown.
 */
enum class LiteRtNativeAttemptStage {
    ENGINE_INIT,
    CONVERSATION_CREATE,
    FIRST_TOKEN_WAIT,
    GENERATING,
    TEARDOWN,
}

/** One in-flight native LiteRT attempt. Bounded identifiers and enum names only. */
data class LiteRtNativeAttempt(
    val attemptId: String,
    val appSessionId: String,
    val modelId: String,
    val artifactSha256: String?,
    val backend: LocalComputeBackend,
    val stage: LiteRtNativeAttemptStage,
    val startedAtEpochMs: Long,
)

/**
 * Durable journal of the single in-flight native LiteRT attempt.
 *
 * A hard native crash (SIGSEGV inside LiteRT-LM/nnapi engine code) cannot be caught by a Kotlin
 * `try/catch`: the process is already gone. The only way the *next* launch can know that Halo was
 * inside a native LiteRT boundary — and on which accelerator — is a record that reached durable
 * storage **before** the native call. The journal stores exactly one pending attempt, keyed
 * `litert_native_attempt_pending`; it is armed before engine init / conversation create /
 * generation, advanced at each stage, and cleared on clean completion or teardown.
 *
 * Content-free by construction: prompts, transcripts, generated text, tool arguments and PCM are
 * never persisted here. Only model ids, artifact digests, accelerator, stage and timestamps.
 */
class LiteRtNativeAttemptJournal(private val storage: Storage) {

    /** One per app process: lets a restart tell this process's attempt apart from older ones. */
    val appSessionId: String = UUID.randomUUID().toString()

    fun pendingAttempt(): LiteRtNativeAttempt? = readPending()

    /**
     * Arms the pending record so it is durable before the next native call. Each arm replaces the
     * previous pending record with a fresh attempt id.
     */
    fun arm(
        modelId: String,
        artifactSha256: String?,
        backend: LocalComputeBackend,
        stage: LiteRtNativeAttemptStage = LiteRtNativeAttemptStage.ENGINE_INIT,
    ) {
        val attempt = LiteRtNativeAttempt(
            attemptId = UUID.randomUUID().toString(),
            appSessionId = appSessionId,
            modelId = modelId,
            artifactSha256 = artifactSha256,
            backend = backend,
            stage = stage,
            startedAtEpochMs = System.currentTimeMillis(),
        )
        persist(attempt)
        Log.i(
            TAG,
            "$EVENT_ARMED model=$modelId sha=${shaPrefix(artifactSha256)} " +
                "backend=${backend.name} stage=${stage.name} attempt=${attempt.attemptId.take(8)}",
        )
    }

    /** Advances the pending record to the next native boundary. No-op when nothing is armed. */
    fun advance(stage: LiteRtNativeAttemptStage) {
        val current = readPending() ?: return
        persist(current.copy(stage = stage))
        Log.i(
            TAG,
            "$EVENT_STAGE attempt=${current.attemptId.take(8)} model=${current.modelId} " +
                "backend=${current.backend.name} stage=${stage.name}",
        )
    }

    /**
     * The attempt reached a clean endpoint (complete generation / successful teardown). The
     * pending record is removed; a later restart must not attribute anything to it.
     */
    fun clear() {
        val current = readPending() ?: return
        storage.remove(PENDING_KEY)
        Log.i(
            TAG,
            "$EVENT_CLEARED attempt=${current.attemptId.take(8)} model=${current.modelId} " +
                "backend=${current.backend.name} lastStage=${current.stage.name}",
        )
    }

    private fun readPending(): LiteRtNativeAttempt? =
        storage.getString(PENDING_KEY)?.let { json ->
            runCatching {
                val obj = JSONObject(json)
                LiteRtNativeAttempt(
                    attemptId = obj.optString("attemptId"),
                    appSessionId = obj.optString("appSessionId"),
                    modelId = obj.optString("modelId"),
                    artifactSha256 = if (obj.isNull("artifactSha256")) null else obj.optString("artifactSha256").ifBlank { null },
                    backend = runCatching {
                        LocalComputeBackend.valueOf(obj.optString("backend"))
                    }.getOrDefault(LocalComputeBackend.AUTO),
                    stage = runCatching {
                        LiteRtNativeAttemptStage.valueOf(obj.optString("stage"))
                    }.getOrDefault(LiteRtNativeAttemptStage.ENGINE_INIT),
                    startedAtEpochMs = obj.optLong("startedAtEpochMs"),
                )
            }.getOrNull()
        }

    private fun persist(attempt: LiteRtNativeAttempt) {
        try {
            storage.putString(PENDING_KEY, attempt.toJson())
        } catch (e: Throwable) {
            // Journaling is observability, never a load gate: but a write that fails to commit
            // means the durable invariant is broken, so it must be visible.
            Log.e(
                TAG,
                "$EVENT_ARM_FAILED model=${attempt.modelId} backend=${attempt.backend.name} " +
                    "type=${e::class.java.simpleName}",
            )
        }
    }

    private fun LiteRtNativeAttempt.toJson(): String = JSONObject().apply {
        put("attemptId", attemptId)
        put("appSessionId", appSessionId)
        put("modelId", modelId)
        put("artifactSha256", artifactSha256 ?: JSONObject.NULL)
        put("backend", backend.name)
        put("stage", stage.name)
        put("startedAtEpochMs", startedAtEpochMs)
    }.toString()

    private fun shaPrefix(sha: String?): String = sha?.take(12) ?: "unknown"

    companion object {
        private const val TAG = "LiteRtNativeAttemptJournal"
        private const val PENDING_KEY = "litert_native_attempt_pending"

        const val EVENT_ARMED = "HALO_LITERT_NATIVE_ATTEMPT_ARMED"
        const val EVENT_STAGE = "HALO_LITERT_NATIVE_ATTEMPT_STAGE"
        const val EVENT_CLEARED = "HALO_LITERT_NATIVE_ATTEMPT_CLEARED"
        const val EVENT_ARM_FAILED = "HALO_LITERT_NATIVE_ATTEMPT_ARM_FAILED"
    }
}
