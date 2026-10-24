package com.halo.providers.litert.runtime

import android.os.Build
import com.halo.providers.litert.LiteRtLocalProvider
import com.halo.sdk.provider.LocalComputeBackend
import com.halo.sdk.runtime.Storage
import org.json.JSONObject

/**
 * DOGFOOD-005 accelerator-qualification state.
 *
 * `Engine.initialize()` succeeding is **never** qualification: on physical hardware a GPU engine
 * can initialize, create a conversation, begin generation and then kill the Android process
 * mid-token with no catchable exception. A GPU stability verdict requires repeated, complete
 * generation sequences for this exact key
 * (artifact SHA-256 + LiteRT-LM runtime version + device/build identity + backend).
 *
 * New invariant:
 *     installed != verified != resident != generation-proven != accelerator-qualified
 */
enum class LiteRtBackendQualification {
    /** Nothing is known about this accelerator on this key yet. AUTO resolves to CPU. */
    UNKNOWN,

    /** A stability probe is in flight. AUTO resolves to CPU for normal user turns. */
    VERIFYING,

    /** The stability sequence passed. This is not comparative CPU/GPU performance proof. */
    QUALIFIED,

    /** An unclean native exit was attributed to this accelerator. AUTO stays on CPU. */
    QUARANTINED,
}

/**
 * Normalized previous-process exit reason, decoupled from the Android 11+
 * `ApplicationExitInfo` raw constants so the decision rules are unit-testable on the JVM.
 */
enum class LiteRtProcessExitReason {
    /** Benign, system-managed exit (for example a clean package-update replacement). */
    NORMAL,

    /** The user (or debugger/IDE) stopped the process. */
    USER_REQUESTED,

    /** Unhandled Java exception reported by Android. */
    CRASH,

    /** Native crash reported by Android; a tombstone may be available. */
    CRASH_NATIVE,

    /** Application Not Responding kill. */
    ANR,

    /** LMKD / heavyweight resource kill. */
    LOW_MEMORY,

    /** Known reason code, but not one Halo treats as benign. */
    OTHER,

    /** No exit information was available at all. */
    UNKNOWN,
}

object LiteRtProcessExit {
    // ApplicationExitInfo.REASON_* constants (API 30+). Kept as literals so the normalization
    // rules stay unit-testable without the Android API surface.
    const val REASON_UNKNOWN = 0
    const val REASON_EXIT_SELF = 1
    const val REASON_LOW_MEMORY = 3
    const val REASON_CRASH = 4
    const val REASON_CRASH_NATIVE = 5
    const val REASON_ANR = 6
    const val REASON_PERMISSION_CHANGE = 8
    const val REASON_EXCESSIVE_RESOURCES = 9
    const val REASON_USER_REQUESTED = 10
    const val REASON_USER_STOPPED = 11
    const val REASON_PACKAGE_STATE_CHANGE = 15
    const val REASON_PACKAGE_UPDATED = 16

    /**
     * Android reports Java and native crashes as distinct reason codes. Trace availability
     * is not a discriminator: native tombstones may also be exposed through the trace stream.
     */
    fun normalize(reason: Int): LiteRtProcessExitReason = when (reason) {
        REASON_UNKNOWN -> LiteRtProcessExitReason.UNKNOWN
        REASON_USER_REQUESTED, REASON_USER_STOPPED -> LiteRtProcessExitReason.USER_REQUESTED
        REASON_LOW_MEMORY, REASON_EXCESSIVE_RESOURCES -> LiteRtProcessExitReason.LOW_MEMORY
        REASON_CRASH -> LiteRtProcessExitReason.CRASH
        REASON_CRASH_NATIVE -> LiteRtProcessExitReason.CRASH_NATIVE
        REASON_ANR -> LiteRtProcessExitReason.ANR
        REASON_EXIT_SELF, REASON_PERMISSION_CHANGE, REASON_PACKAGE_STATE_CHANGE,
        REASON_PACKAGE_UPDATED -> LiteRtProcessExitReason.NORMAL
        else -> LiteRtProcessExitReason.OTHER
    }

    /** True when this normalized exit is a managed/benign stop, not an unclean death. */
    fun isClean(reason: LiteRtProcessExitReason): Boolean = when (reason) {
        LiteRtProcessExitReason.NORMAL, LiteRtProcessExitReason.USER_REQUESTED -> true
        else -> false
    }
}

/** Bounded, content-free description of one backend resolution decision. */
data class LiteRtBackendResolution(
    val requested: LocalComputeBackend,
    val effective: LocalComputeBackend,
    val gpuQualification: LiteRtBackendQualification,
    val reason: String,
)

/**
 * The AUTO gate. Pure and total:
 *
 * ```
 * AUTO + QUALIFIED  -> CPU (stability does not establish comparative performance)
 * catalog GPU       -> GPU only when QUALIFIED (artifact compatibility preference)
 * GPU UNKNOWN       -> CPU
 * GPU VERIFYING     -> CPU for normal user turns
 * GPU QUARANTINED   -> CPU
 * explicit CPU      -> CPU, regardless of qualification
 * explicit GPU      -> GPU, regardless of AUTO qualification (developer testing path)
 * ```
 */
object LiteRtBackendQualificationPolicy {
    fun resolve(
        requested: LocalComputeBackend,
        gpuQualification: LiteRtBackendQualification,
        explicitGpuOverride: Boolean = true,
    ): LiteRtBackendResolution {
        if (requested == LocalComputeBackend.GPU && !explicitGpuOverride) {
            // A catalog GPU request can express artifact compatibility, not a speed claim.
            // Preserve its existing stability gate instead of forcing CPU-only AUTO defaults
            // onto profiles that may forbid CPU fallback.
            val decision = resolve(LocalComputeBackend.AUTO, gpuQualification)
            return if (gpuQualification == LiteRtBackendQualification.QUALIFIED) {
                decision.copy(requested = requested, effective = LocalComputeBackend.GPU,
                    reason = "catalog GPU qualified; comparative performance unproven")
            } else decision.copy(requested = requested)
        }
        return when (requested) {
            LocalComputeBackend.CPU ->
                LiteRtBackendResolution(LocalComputeBackend.CPU, LocalComputeBackend.CPU, gpuQualification, "explicit CPU")

            LocalComputeBackend.GPU ->
                LiteRtBackendResolution(LocalComputeBackend.GPU, LocalComputeBackend.GPU, gpuQualification, "explicit developer GPU")

            LocalComputeBackend.AUTO -> when (gpuQualification) {
                LiteRtBackendQualification.QUALIFIED ->
                    // There is currently no matching paired-performance input in this gate.
                    // Do not promote a stability-only record (including existing saved records)
                    // into an automatic speed recommendation. Workload selection keeps its
                    // separate measured evidence owner; explicit developer GPU remains available.
                    LiteRtBackendResolution(requested, LocalComputeBackend.CPU, gpuQualification,
                        "gpu stable; comparative performance unproven; AUTO uses CPU")

                LiteRtBackendQualification.VERIFYING ->
                    LiteRtBackendResolution(requested, LocalComputeBackend.CPU, gpuQualification, "gpu verification in progress; normal turns use CPU")

                LiteRtBackendQualification.QUARANTINED ->
                    LiteRtBackendResolution(requested, LocalComputeBackend.CPU, gpuQualification, "gpu quarantined after unclean native exit")

                LiteRtBackendQualification.UNKNOWN ->
                    LiteRtBackendResolution(requested, LocalComputeBackend.CPU, gpuQualification, "gpu qualification unknown")
            }
        }
    }
}

/**
 * Stable device/build identity for qualification scoping. Deliberately coarse and lower-cased:
 * it must survive app restarts and never embed user-specific strings.
 */
object LiteRtDeviceIdentity {
    fun current(): String =
        listOfNotNull(
            Build.MANUFACTURER,
            Build.MODEL,
            Build.ID,
            Build.VERSION.SDK_INT.toString(),
        ).joinToString("_") { part ->
            part.lowercase().filter { c -> c.isLetterOrDigit() }.ifEmpty { "unknown" }
        }
}

/**
 * Durable, per-(artifact + runtime version + device/build + GPU) qualification state.
 *
 * Records are keyed so that a newly downloaded artifact revision, a runtime upgrade or a
 * different device always starts at UNKNOWN instead of inheriting a stranger's verdict.
 */
class LiteRtBackendQualificationStore(
    private val storage: Storage,
    private val runtimeVersion: String = LiteRtLocalProvider.RUNTIME_VERSION,
    private val deviceIdentityProvider: () -> String = { LiteRtDeviceIdentity.current() },
) {
    data class Record(
        val artifactSha256: String?,
        val runtimeVersion: String,
        val deviceIdentity: String,
        val state: LiteRtBackendQualification,
        val quarantineReason: String?,
        val ambiguousUncleanExits: Int,
        val qualifiedAtMs: Long?,
        val updatedAtMs: Long,
    )

    /** Outcome of reconciling a stale pending native attempt against a previous process exit. */
    data class UncleanExitDecision(val outcome: Outcome, val reason: String?) {
        enum class Outcome { QUARANTINED, NO_PENALTY, AMBIGUOUS_STRIKE }
    }

    fun gpuStorageKey(artifactSha256: String?): String =
        "litert_gpu_qual_${artifactSha256?.take(16) ?: "unknown"}_${runtimeVersion}_${deviceIdentityProvider()}"

    fun qualificationFor(artifactSha256: String?): LiteRtBackendQualification =
        if (artifactSha256 == null) LiteRtBackendQualification.UNKNOWN
        else recordFor(artifactSha256)?.state ?: LiteRtBackendQualification.UNKNOWN

    fun recordFor(artifactSha256: String?): Record? =
        storage.getString(gpuStorageKey(artifactSha256))
            ?.let { json ->
                runCatching {
                    val obj = JSONObject(json)
                    Record(
                        artifactSha256 = if (obj.isNull("artifactSha256")) null else obj.optString("artifactSha256").ifBlank { null },
                        runtimeVersion = obj.optString("runtimeVersion"),
                        deviceIdentity = obj.optString("deviceIdentity"),
                        state = runCatching {
                            LiteRtBackendQualification.valueOf(obj.optString("state"))
                        }.getOrDefault(LiteRtBackendQualification.UNKNOWN),
                        quarantineReason = if (obj.isNull("quarantineReason")) null else obj.optString("quarantineReason").ifBlank { null },
                        ambiguousUncleanExits = obj.optInt("ambiguousUncleanExits", 0),
                        qualifiedAtMs = if (obj.isNull("qualifiedAtMs")) null else obj.optLong("qualifiedAtMs"),
                        updatedAtMs = obj.optLong("updatedAtMs"),
                    )
                }.getOrNull()
            }
            ?.takeIf {
                it.artifactSha256 == artifactSha256 && it.runtimeVersion == runtimeVersion &&
                    it.deviceIdentity == deviceIdentityProvider()
            }

    fun markVerifying(artifactSha256: String?) {
        persist(artifactSha256, state = LiteRtBackendQualification.VERIFYING)
    }

    fun markQualified(artifactSha256: String?) {
        persist(artifactSha256, state = LiteRtBackendQualification.QUALIFIED)
    }

    /** Probe finished without passing: back to "no proof", no penalty. */
    fun markUnknown(artifactSha256: String?) {
        persist(artifactSha256, state = LiteRtBackendQualification.UNKNOWN)
    }

    fun quarantine(artifactSha256: String?, reason: String) {
        persist(artifactSha256, state = LiteRtBackendQualification.QUARANTINED, quarantineReason = reason)
    }

    /** Developer action: forget every verdict for this artifact and start at UNKNOWN. */
    fun clear(artifactSha256: String?) {
        storage.remove(gpuStorageKey(artifactSha256))
    }

    /**
     * Reconciles a stale pending native attempt against the previous process exit.
     *
     * ```
     * CRASH / CRASH_NATIVE -> quarantine
     * ANR                  -> quarantine
     * LOW_MEMORY           -> quarantine (accelerator is not operationally safe on this device)
     * USER_REQUESTED/NORMAL-> no penalty, ambiguous counter resets
     * OTHER/UNKNOWN        -> ambiguous strike; quarantine after 2 consecutive strikes
     * ```
     */
    fun recordUncleanExit(
        artifactSha256: String?,
        exit: LiteRtProcessExitReason,
    ): UncleanExitDecision {
        val current = recordFor(artifactSha256)
        return when (exit) {
            LiteRtProcessExitReason.CRASH -> {
                quarantine(artifactSha256, "previous process java crash during native LiteRT generation")
                UncleanExitDecision(UncleanExitDecision.Outcome.QUARANTINED, "crash")
            }

            LiteRtProcessExitReason.CRASH_NATIVE -> {
                quarantine(artifactSha256, "previous process native crash during LiteRT generation")
                UncleanExitDecision(UncleanExitDecision.Outcome.QUARANTINED, "native crash")
            }

            LiteRtProcessExitReason.ANR -> {
                quarantine(artifactSha256, "ANR during native LiteRT generation")
                UncleanExitDecision(UncleanExitDecision.Outcome.QUARANTINED, "anr")
            }

            LiteRtProcessExitReason.LOW_MEMORY -> {
                quarantine(artifactSha256, "low-memory kill during native LiteRT generation")
                UncleanExitDecision(UncleanExitDecision.Outcome.QUARANTINED, "low memory")
            }

            LiteRtProcessExitReason.NORMAL, LiteRtProcessExitReason.USER_REQUESTED -> {
                // A manual or managed stop must not be attributed to the accelerator: reset any
                // pending ambiguous counter instead of penalizing.
                persist(
                    artifactSha256,
                    state = current?.state ?: LiteRtBackendQualification.UNKNOWN,
                    ambiguousUncleanExits = 0,
                )
                UncleanExitDecision(UncleanExitDecision.Outcome.NO_PENALTY, "clean exit: ${exit.name.lowercase()}")
            }

            LiteRtProcessExitReason.OTHER, LiteRtProcessExitReason.UNKNOWN -> {
                val strikes = (current?.ambiguousUncleanExits ?: 0) + 1
                if (strikes >= AMBIGUOUS_EXIT_THRESHOLD) {
                    quarantine(artifactSha256, "$AMBIGUOUS_EXIT_THRESHOLD consecutive ambiguous unclean exits")
                    UncleanExitDecision(
                        UncleanExitDecision.Outcome.QUARANTINED,
                        "ambiguous exit $strikes/$AMBIGUOUS_EXIT_THRESHOLD",
                    )
                } else {
                    // One ambiguous death does not prove the accelerator guilty, but it also no
                    // longer proves it safe: drop any qualification until the next clean run.
                    persist(
                        artifactSha256,
                        state = when (current?.state) {
                            LiteRtBackendQualification.QUALIFIED,
                            LiteRtBackendQualification.VERIFYING -> LiteRtBackendQualification.UNKNOWN
                            else -> current?.state ?: LiteRtBackendQualification.UNKNOWN
                        },
                        ambiguousUncleanExits = strikes,
                    )
                    UncleanExitDecision(
                        UncleanExitDecision.Outcome.AMBIGUOUS_STRIKE,
                        "ambiguous exit $strikes/$AMBIGUOUS_EXIT_THRESHOLD",
                    )
                }
            }
        }
    }

    /** UI-safe one-liner for Model Browser / System Check. */
    fun summaryLine(artifactSha256: String?): String = when (val state = qualificationFor(artifactSha256)) {
        LiteRtBackendQualification.UNKNOWN -> "GPU: unqualified"
        LiteRtBackendQualification.VERIFYING -> "GPU: verifying"
        LiteRtBackendQualification.QUALIFIED -> "GPU: qualified"
        LiteRtBackendQualification.QUARANTINED ->
            recordFor(artifactSha256)?.quarantineReason
                ?.let { "GPU: quarantined ($it)" }
                ?: "GPU: quarantined"
    }

    private fun persist(
        artifactSha256: String?,
        state: LiteRtBackendQualification,
        quarantineReason: String? = null,
        ambiguousUncleanExits: Int? = null,
    ) {
        val current = recordFor(artifactSha256)
        val record = Record(
            artifactSha256 = artifactSha256,
            runtimeVersion = runtimeVersion,
            deviceIdentity = deviceIdentityProvider(),
            state = state,
            quarantineReason = if (state == LiteRtBackendQualification.QUARANTINED) quarantineReason else null,
            ambiguousUncleanExits = ambiguousUncleanExits ?: (current?.ambiguousUncleanExits ?: 0),
            qualifiedAtMs = when (state) {
                LiteRtBackendQualification.QUALIFIED ->
                    current?.qualifiedAtMs ?: System.currentTimeMillis()
                else -> null
            },
            updatedAtMs = System.currentTimeMillis(),
        )
        storage.putString(gpuStorageKey(artifactSha256), record.toJson())
    }

    private fun Record.toJson(): String = JSONObject().apply {
        put("artifactSha256", artifactSha256 ?: JSONObject.NULL)
        put("runtimeVersion", runtimeVersion)
        put("deviceIdentity", deviceIdentity)
        put("state", state.name)
        put("quarantineReason", quarantineReason ?: JSONObject.NULL)
        put("ambiguousUncleanExits", ambiguousUncleanExits)
        put("qualifiedAtMs", qualifiedAtMs ?: JSONObject.NULL)
        put("updatedAtMs", updatedAtMs)
    }.toString()

    companion object {
        const val AMBIGUOUS_EXIT_THRESHOLD = 2

        const val EVENT_BACKEND_RESOLUTION = "HALO_LITERT_BACKEND_RESOLUTION"
        const val EVENT_PREVIOUS_PROCESS_EXIT = "HALO_PREVIOUS_PROCESS_EXIT"
        const val EVENT_UNCLEAN_ATTEMPT_FOUND = "HALO_LITERT_UNCLEAN_ATTEMPT_FOUND"
        const val EVENT_GPU_QUALIFIED = "HALO_LITERT_GPU_QUALIFIED"
        const val EVENT_GPU_QUARANTINED = "HALO_LITERT_GPU_QUARANTINED"
        const val EVENT_GPU_QUALIFICATION_CLEARED = "HALO_LITERT_GPU_QUALIFICATION_CLEARED"
    }
}

