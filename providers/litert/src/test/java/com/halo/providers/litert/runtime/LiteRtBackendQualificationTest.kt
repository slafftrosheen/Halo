package com.halo.providers.litert.runtime

import com.halo.sdk.provider.LocalComputeBackend
import com.halo.sdk.runtime.Storage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import android.app.ApplicationExitInfo

@RunWith(RobolectricTestRunner::class)
class LiteRtBackendQualificationTest {
    private class MemoryStorage : Storage {
        private val values = mutableMapOf<String, Any>()
        override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
        override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        override fun clear() { values.clear() }
    }

    @Test fun androidExitCodesMatchPlatformRatherThanGuessedConstants() {
        assertEquals(LiteRtProcessExitReason.USER_REQUESTED, LiteRtProcessExit.normalize(ApplicationExitInfo.REASON_USER_REQUESTED))
        assertEquals(LiteRtProcessExitReason.LOW_MEMORY, LiteRtProcessExit.normalize(ApplicationExitInfo.REASON_LOW_MEMORY))
        assertEquals(LiteRtProcessExitReason.CRASH, LiteRtProcessExit.normalize(ApplicationExitInfo.REASON_CRASH))
        assertEquals(LiteRtProcessExitReason.CRASH_NATIVE, LiteRtProcessExit.normalize(ApplicationExitInfo.REASON_CRASH_NATIVE))
        assertEquals(LiteRtProcessExitReason.NORMAL, LiteRtProcessExit.normalize(ApplicationExitInfo.REASON_PERMISSION_CHANGE))
        assertEquals(LiteRtProcessExitReason.UNKNOWN, LiteRtProcessExit.normalize(ApplicationExitInfo.REASON_UNKNOWN))
    }

    @Test fun catalogGpuPreferenceDoesNotBypassQualification() {
        for (qualification in LiteRtBackendQualification.entries) {
            val resolution = LiteRtBackendQualificationPolicy.resolve(LocalComputeBackend.GPU, qualification, explicitGpuOverride = false)
            assertEquals(if (qualification == LiteRtBackendQualification.QUALIFIED) LocalComputeBackend.GPU else LocalComputeBackend.CPU, resolution.effective)
        }
        assertEquals(LocalComputeBackend.GPU, LiteRtBackendQualificationPolicy.resolve(LocalComputeBackend.GPU, LiteRtBackendQualification.QUARANTINED, true).effective)
    }

    @Test fun stableGpuDoesNotBecomeAnAutomaticSpeedRecommendationOnEightGbPhone() {
        val storage = MemoryStorage()
        val sha = "a".repeat(64)
        LiteRtBackendQualificationStore(storage, "runtime", { "device" }, { "build" }).markQualified(sha)
        val stored = LiteRtBackendQualificationStore(storage, "runtime", { "device" }, { "build" })
        val resolution = LiteRtBackendQualificationPolicy.resolve(LocalComputeBackend.AUTO, stored.qualificationFor(sha))
        val mobile = LiteRtMobileBackendPolicy.apply(resolution, 7_970_328_576L, false)
        assertEquals(LocalComputeBackend.AUTO, mobile.requested)
        assertEquals(LocalComputeBackend.CPU, mobile.effective)
        assertEquals(LiteRtBackendQualification.QUALIFIED, mobile.gpuQualification)
        assertTrue(mobile.reason.contains("comparative performance unproven"))
        assertEquals(LiteRtBackendQualification.QUALIFIED, stored.qualificationFor(sha))
    }

    @Test fun autoAndExplicitCpuRemainCpuForEveryStabilityState() {
        for (qualification in LiteRtBackendQualification.entries) {
            for (requested in listOf(LocalComputeBackend.AUTO, LocalComputeBackend.CPU)) {
                assertEquals(LocalComputeBackend.CPU,
                    LiteRtBackendQualificationPolicy.resolve(requested, qualification).effective)
            }
            assertEquals(LocalComputeBackend.GPU,
                LiteRtBackendQualificationPolicy.resolve(LocalComputeBackend.GPU, qualification, true).effective)
        }
    }

    @Test fun qualificationDoesNotCrossArtifactOrRuntimeAndSurvivesRecreation() {
        val storage = MemoryStorage()
        val store = LiteRtBackendQualificationStore(storage, "runtime", { "device" }, { "build" })
        val sha = "a".repeat(64)
        store.markQualified(sha)
        assertEquals(LiteRtBackendQualification.QUALIFIED, LiteRtBackendQualificationStore(storage, "runtime", { "device" }, { "build" }).qualificationFor(sha))
        assertEquals(LiteRtBackendQualification.UNKNOWN, store.qualificationFor("a".repeat(16) + "b".repeat(48)))
        assertEquals(LiteRtBackendQualification.UNKNOWN, LiteRtBackendQualificationStore(storage, "new", { "device" }).qualificationFor(sha))
        store.recordUncleanExit(sha, LiteRtProcessExitReason.CRASH_NATIVE)
        assertEquals(LiteRtBackendQualification.QUARANTINED, store.qualificationFor(sha))
    }

    @Test fun appReplacementRevokesPositiveGpuQualificationAndCanRequalify() {
        val storage = MemoryStorage()
        val sha = "a".repeat(64)
        val old = LiteRtBackendQualificationStore(storage, "runtime", { "device" }, { "old" })
        old.markQualified(sha)
        val replaced = LiteRtBackendQualificationStore(storage, "runtime", { "device" }, { "new" })
        assertEquals(LiteRtBackendQualification.UNKNOWN, replaced.qualificationFor(sha))
        assertEquals(LocalComputeBackend.CPU, LiteRtBackendQualificationPolicy.resolve(
            LocalComputeBackend.GPU, replaced.qualificationFor(sha), false).effective)
        replaced.markQualified(sha)
        assertEquals(LiteRtBackendQualification.QUALIFIED, replaced.qualificationFor(sha))
        assertEquals(LiteRtBackendQualification.UNKNOWN, old.qualificationFor(sha))
    }

    @Test fun appReplacementPreservesNegativeQuarantineAndAmbiguousExitHistory() {
        val storage = MemoryStorage()
        val sha = "a".repeat(64)
        val old = LiteRtBackendQualificationStore(storage, "runtime", { "device" }, { "old" })
        old.quarantine(sha, "observed native crash")
        val replaced = LiteRtBackendQualificationStore(storage, "runtime", { "device" }, { "new" })
        assertEquals(LiteRtBackendQualification.QUARANTINED, replaced.qualificationFor(sha))
        assertEquals("observed native crash", replaced.recordFor(sha)?.quarantineReason)
        old.clear(sha)
        old.recordUncleanExit(sha, LiteRtProcessExitReason.UNKNOWN)
        assertEquals(1, replaced.recordFor(sha)?.ambiguousUncleanExits)
        assertEquals(LiteRtBackendQualificationStore.UncleanExitDecision.Outcome.QUARANTINED,
            replaced.recordUncleanExit(sha, LiteRtProcessExitReason.UNKNOWN).outcome)
    }

    @Test fun legacyPositiveRecordDoesNotQualifyButLegacyQuarantineRemains() {
        val storage = MemoryStorage()
        val sha = "a".repeat(64)
        val store = LiteRtBackendQualificationStore(storage, "runtime", { "device" }, { "build" })
        val legacy = org.json.JSONObject().put("artifactSha256", sha).put("runtimeVersion", "runtime")
            .put("deviceIdentity", "device").put("state", "QUALIFIED").put("qualifiedAtMs", 1)
        storage.putString(store.gpuStorageKey(sha), legacy.toString())
        assertEquals(LiteRtBackendQualification.UNKNOWN, store.qualificationFor(sha))
        storage.putString(store.gpuStorageKey(sha), legacy.put("state", "QUARANTINED").toString())
        assertEquals(LiteRtBackendQualification.QUARANTINED, store.qualificationFor(sha))
    }

    @Test fun missingBuildIdentityCannotWriteOrReadPositiveQualification() {
        val storage = MemoryStorage()
        val sha = "a".repeat(64)
        LiteRtBackendQualificationStore(storage, "runtime", { "device" }, { "build" }).markQualified(sha)
        val unknown = LiteRtBackendQualificationStore(storage, "runtime", { "device" })
        assertEquals(LiteRtBackendQualification.UNKNOWN, unknown.qualificationFor(sha))
        assertThrows(IllegalStateException::class.java) { unknown.markQualified(sha) }
    }

    @Test fun journalClearsCleanAttemptsAndSurvivesProcessRecreation() {
        val storage = MemoryStorage()
        val journal = LiteRtNativeAttemptJournal(storage)
        journal.arm("test", "a".repeat(64), LocalComputeBackend.GPU)
        journal.advance(LiteRtNativeAttemptStage.FIRST_TOKEN_WAIT)
        val recreated = LiteRtNativeAttemptJournal(storage)
        assertNotEquals(recreated.appSessionId, recreated.pendingAttempt()!!.appSessionId)
        assertEquals(LiteRtNativeAttemptStage.FIRST_TOKEN_WAIT, recreated.pendingAttempt()!!.stage)
        journal.clear()
        assertNull(recreated.pendingAttempt())
    }

    @Test(expected = IllegalArgumentException::class) fun zeroGenerationsCannotQualifyGpu() {
        LiteRtGpuStabilityTracker(0)
    }
}
