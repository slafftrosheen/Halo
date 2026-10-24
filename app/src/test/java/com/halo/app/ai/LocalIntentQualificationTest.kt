package com.halo.app.ai

import android.app.Application
import com.halo.providers.litert.models.LocalModelInventoryState
import com.halo.sdk.provider.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LocalIntentQualificationTest {
    @Test fun missingConsentAndProofAreIndependentAndNeverMutateSavedChoices() = runTest {
        val f = IntentQualificationFixture(backgroundScope); f.warm()
        assertFalse(LocalIntentModelRouter.isEnabled(f.storage))
        f.certify()
        assertFalse(LocalIntentModelRouter.isEnabled(f.storage))
        val writes = f.storage.writes
        assertNull(f.router().classify("Open camera"))
        assertEquals(0L, f.provider.modelSession.generationRequestCount)
        assertEquals(writes, f.storage.writes)
        f.optIn(); assertTrue(LocalIntentModelRouter.isEnabled(f.storage))
        f.optIn(false); assertFalse(LocalIntentModelRouter.isEnabled(f.storage))
    }
    @Test fun readyButUnqualifiedOptInDoesNotResolveInventoryOrEnqueueInference() = runTest {
        val f = IntentQualificationFixture(backgroundScope); f.warm(); f.optIn()
        val resolves = f.inventory.resolves; val writes = f.storage.writes
        val router = f.router()
        listOf("Open camera", "Открой камеру", "Atver kameru", "Otwórz aparat").forEach { assertNull(router.classify(it)) }
        assertEquals(0L, f.provider.modelSession.generationRequestCount)
        assertTrue(f.runtime.generations.isEmpty())
        assertEquals(resolves, f.inventory.resolves); assertEquals(writes, f.storage.writes)
    }
    @Test fun exactPassedIntentSuiteAdmitsBoundedAdvisoryAndFailureRevokesIt() = runTest {
        val f = IntentQualificationFixture(backgroundScope); f.warm(); f.optIn(); assertTrue(f.certify())
        assertTrue(f.proofs.isIntentQualified(f.provider, f.artifact))
        assertEquals(com.halo.engines.interpretation.rules.ModelIntentDecision.ACTION, withContext(Dispatchers.Default) { f.router().classify("Open camera") })
        assertEquals(1, f.runtime.generations.size)
        val config = f.runtime.generations.single()
        assertEquals(12, config.maxTokens); assertEquals(false, config.thinkingEnabled)
        assertTrue(config.availableTools.isEmpty())
        assertTrue(f.certify(completed = false, attempted = 1, matched = 0, noAdvice = 1, elapsed = listOf(22_000)))
        assertFalse(f.proofs.isIntentQualified(f.provider, f.artifact))
        assertNull(f.router().classify("Open camera")); assertEquals(1, f.runtime.generations.size)
    }
    @Test fun genericFastOrOtherBackendTimingCannotQualifyIntent() = runTest {
        val f = IntentQualificationFixture(backgroundScope); f.warm()
        val env = requireNotNull(f.proofs.intentEnvironment(f.provider, f.artifact))
        val target = WorkloadTarget(f.provider.id, f.artifact.id, f.artifact.sha256)
        for ((role, backend) in listOf(LocalWorkload.FAST to WorkloadBackend.CPU, LocalWorkload.INTENT to WorkloadBackend.GPU)) {
            f.proofs.append(WorkloadTaskProof(target, role, backend, env, true, true, System.currentTimeMillis(), warmMs = 1))
            assertFalse(f.proofs.isIntentQualified(f.provider, f.artifact))
        }
    }
    @Test fun partialWrongColdOrLateSuitesDoNotBecomeProof() = runTest {
        val f = IntentQualificationFixture(backgroundScope); f.warm()
        f.certify(attempted = 47); assertFalse(f.proofs.isIntentQualified(f.provider, f.artifact))
        f.certify(warm = 47); assertFalse(f.proofs.isIntentQualified(f.provider, f.artifact))
        f.certify(matched = 47); assertFalse(f.proofs.isIntentQualified(f.provider, f.artifact))
        f.certify(matched = 47, noAdvice = 1); assertFalse(f.proofs.isIntentQualified(f.provider, f.artifact))
        f.certify(elapsed = List(47) { 10L } + 2001L); assertFalse(f.proofs.isIntentQualified(f.provider, f.artifact))
    }
    @Test fun changedPackageOrSettingsExpireProofWithoutRewritingIt() = runTest {
        val f = IntentQualificationFixture(backgroundScope); f.warm(); f.certify()
        assertTrue(WorkloadQualificationStore(f.context, f.storage).isIntentQualified(f.provider, f.artifact))
        val writes = f.storage.writes
        f.packageRevision(1001)
        assertFalse(WorkloadQualificationStore(f.context, f.storage).isIntentQualified(f.provider, f.artifact))
        f.storage.putInt("litert_context_${f.model.id}", 2048)
        assertFalse(f.proofs.isIntentQualified(f.provider, f.artifact))
        assertEquals(writes + 1, f.storage.writes)
    }
    @Test fun changedArtifactOrUnavailableInventoryCannotReuseProof() = runTest {
        val f = IntentQualificationFixture(backgroundScope); f.warm(); f.certify()
        assertFalse(f.proofs.isIntentQualified(f.provider, f.artifact.copy(sha256 = "a".repeat(64))))
        f.inventory.inventoryState.value = LocalModelInventoryState.Loading
        assertFalse(f.proofs.isIntentQualified(f.provider, f.artifact))
    }
    @Test fun proofCannotBeBorrowedAcrossDeviceRuntimeOrProtocolIdentity() = runTest {
        val f = IntentQualificationFixture(backgroundScope); f.warm()
        val env = requireNotNull(f.proofs.intentEnvironment(f.provider, f.artifact))
        val target = WorkloadTarget(f.provider.id, f.artifact.id, f.artifact.sha256)
        for (other in listOf(env.copy(device = "other"), env.copy(runtime = "other"), env.copy(settingsFingerprint = "other"))) {
            f.proofs.append(WorkloadTaskProof(target, LocalWorkload.INTENT, WorkloadBackend.CPU, other, true, true, System.currentTimeMillis(), warmMs = 1))
            assertFalse(f.proofs.isIntentQualified(f.provider, f.artifact))
        }
    }
    @Test fun futureOrNegativeDurationRecordsAreNotQualification() = runTest {
        val f = IntentQualificationFixture(backgroundScope); f.warm()
        val env = requireNotNull(f.proofs.intentEnvironment(f.provider, f.artifact))
        val target = WorkloadTarget(f.provider.id, f.artifact.id, f.artifact.sha256)
        f.proofs.append(WorkloadTaskProof(target, LocalWorkload.INTENT, WorkloadBackend.CPU, env, true, true,
            System.currentTimeMillis() + 60_000, warmMs = 1))
        assertFalse(f.proofs.isIntentQualified(f.provider, f.artifact))
        f.proofs.append(WorkloadTaskProof(target, LocalWorkload.INTENT, WorkloadBackend.CPU, env, true, true,
            System.currentTimeMillis(), warmMs = -1))
        assertFalse(f.proofs.isIntentQualified(f.provider, f.artifact))
    }
    @Test fun latestFailureWinsEqualTimestampAndRejectsStalePass() = runTest {
        val f = IntentQualificationFixture(backgroundScope); f.warm()
        val env = requireNotNull(f.proofs.intentEnvironment(f.provider, f.artifact))
        val good = WorkloadTaskProof(WorkloadTarget(f.provider.id, f.artifact.id, f.artifact.sha256), LocalWorkload.INTENT,
            WorkloadBackend.CPU, env, true, true, System.currentTimeMillis(), warmMs = 1)
        f.proofs.append(good); assertTrue(f.proofs.isIntentQualified(f.provider, f.artifact))
        f.proofs.append(good.copy(correct = false)); assertFalse(f.proofs.isIntentQualified(f.provider, f.artifact))
    }
    @Test fun wrongFixtureAndUnknownPackageCannotWriteQualifiedEvidence() = runTest {
        val f = IntentQualificationFixture(backgroundScope); f.warm()
        assertFalse(f.certify(fixture = "a".repeat(64)))
        assertTrue(f.proofs.history().isEmpty())
        f.packageRevision(0)
        assertNull(WorkloadQualificationStore(f.context, f.storage).intentEnvironment(f.provider, f.artifact))
    }
    @Test fun futureFailureCannotResurrectAnOlderPassingRecord() = runTest {
        val f = IntentQualificationFixture(backgroundScope); f.warm(); f.certify()
        assertTrue(f.proofs.isIntentQualified(f.provider, f.artifact))
        val good = f.proofs.history().single()
        f.proofs.append(good.copy(correct = false, measuredAtMs = System.currentTimeMillis() + 60_000))
        assertFalse(f.proofs.isIntentQualified(f.provider, f.artifact))
    }
    @Test fun appendedFailureWinsEvenAfterWallClockRollback() = runTest {
        val f = IntentQualificationFixture(backgroundScope); f.warm(); f.certify()
        val good = f.proofs.history().single()
        f.proofs.append(good.copy(correct = false, measuredAtMs = (good.measuredAtMs - 1000).coerceAtLeast(1)))
        assertFalse(f.proofs.isIntentQualified(f.provider, f.artifact))
    }
    @Test fun malformedLatestPersistedEvidenceCannotExposeAnOlderPass() = runTest {
        val f = IntentQualificationFixture(backgroundScope); f.warm(); f.certify()
        val key = "workload.qualification.history.v2"
        val original = requireNotNull(f.storage.getString(key))
        for (field in listOf("at", "warm")) {
            val rows = org.json.JSONArray(original)
            val malformed = org.json.JSONObject(rows.getJSONObject(0).toString()).put("correct", false).put(field, "invalid")
            rows.put(malformed); f.storage.putString(key, rows.toString())
            val restarted = WorkloadQualificationStore(f.context, f.storage)
            assertFalse(restarted.isIntentQualified(f.provider, f.artifact))
            assertTrue(restarted.history().isEmpty())
        }
    }
    @Test fun slowInitialReadCannotOverwriteNewlyPublishedRevocation() = runTest {
        val f = IntentQualificationFixture(backgroundScope); f.warm(); f.certify()
        val good = f.proofs.history().single()
        val store = WorkloadQualificationStore(f.context, f.storage)
        val captured = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val first = java.util.concurrent.atomic.AtomicBoolean(true)
        f.storage.afterStringRead = { key ->
            if (key == "workload.qualification.history.v2" && first.compareAndSet(true, false)) {
                captured.countDown(); check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
            }
        }
        val staleRead = async(Dispatchers.Default) { store.history() }
        try {
            assertTrue(withContext(Dispatchers.Default) { captured.await(5, java.util.concurrent.TimeUnit.SECONDS) })
            store.append(good.copy(correct = false))
        } finally { release.countDown() }
        staleRead.await(); f.storage.afterStringRead = {}
        assertFalse(store.isIntentQualified(f.provider, f.artifact))
        assertFalse(store.history().last().correct)
    }

    @Test fun malformedMeasuredCountsAndDurationsCannotWriteProof() = runTest {
        val f = IntentQualificationFixture(backgroundScope); f.warm()
        for (durations in listOf(List(47) { 10L }, List(48) { -1L })) {
            try { f.certify(elapsed = durations); fail("Invalid measurement accepted") }
            catch (_: IllegalArgumentException) { }
        }
        assertTrue(f.proofs.history().isEmpty())
    }

    @Test fun qualificationIsRecheckedBeforeInferenceAndBeforeAcceptingTheReply() = runTest {
        val f = IntentQualificationFixture(backgroundScope); f.warm(); f.optIn()
        var checks = 0
        assertNull(f.router { _, _ -> ++checks == 1 }.classify("Open camera"))
        assertTrue(f.runtime.generations.isEmpty())
        checks = 0
        assertNull(withContext(Dispatchers.Default) { f.router { _, _ -> ++checks < 3 }.classify("Open camera") })
        assertEquals(1, f.runtime.generations.size)
    }
}
