package com.halo.app.ai

import android.app.Application
import com.halo.providers.litert.runtime.LocalModelRoleQualification
import com.halo.sdk.provider.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class WorkloadQualificationStoreTest {
    private fun proof(f: IntentQualificationFixture, store: WorkloadQualificationStore = f.proofs,
        workload: LocalWorkload = LocalWorkload.CHAT, backend: WorkloadBackend = WorkloadBackend.CPU) =
        WorkloadTaskProof(WorkloadTarget(f.provider.id, f.artifact.id, f.artifact.sha256), workload, backend,
            store.environment(f.provider, f.artifact, workload, false), true, true, System.currentTimeMillis(),
            coldMs = 100, warmMs = 10)

    @Test fun packageReplacementInvalidatesEveryGeneralWorkloadButKeepsHistory() = runTest {
        val f = IntentQualificationFixture(backgroundScope)
        val workloads = listOf(LocalWorkload.FAST, LocalWorkload.CHAT, LocalWorkload.THINK,
            LocalWorkload.ACTION, LocalWorkload.EMBED, LocalWorkload.VISION)
        val original = workloads.map { proof(f, workload = it).also { p -> f.proofs.append(p) } }
        f.packageRevision(2000)
        val replaced = WorkloadQualificationStore(f.context, f.storage)
        original.forEach { p ->
            val env = replaced.environment(f.provider, f.artifact, p.workload, false)
            assertNotEquals(p.environment, env)
            assertTrue(replaced.current(p.target, env).isEmpty())
        }
        assertEquals(original, replaced.history())
    }

    @Test fun sameInstallationCanReopenAndUseItsEvidence() = runTest {
        val f = IntentQualificationFixture(backgroundScope)
        val p = proof(f)
        f.proofs.append(p)
        val reopened = WorkloadQualificationStore(f.context, f.storage)
        assertEquals(p.environment, reopened.environment(f.provider, f.artifact, p.workload, false))
        assertEquals(listOf(p), reopened.current(p.target, p.environment))
    }

    @Test fun missingInstallationIdentityCannotExposeEvenMatchingPassingHistory() = runTest {
        val f = IntentQualificationFixture(backgroundScope)
        f.packageRevision(0)
        val unknown = WorkloadQualificationStore(f.context, f.storage)
        val p = proof(f, unknown)
        unknown.append(p)
        assertEquals(listOf(p), unknown.history())
        assertTrue(unknown.current(p.target, p.environment).isEmpty())
    }

    @Test fun unknownInstallationCannotPublishRoleMeasurement() = runTest {
        val f = IntentQualificationFixture(backgroundScope)
        val config = f.provider.loadConfigFor(f.artifact)
        val now = System.currentTimeMillis()
        val row = LocalModelRoleQualification(f.artifact.id, requireNotNull(f.artifact.sha256),
            f.model.expectedBytes, LocalComputeBackend.CPU, LocalComputeBackend.CPU,
            config.contextSize, config.cpuThreads, 0, 1, startedAtMs = now, completedAtMs = now)
        f.proofs.recordRoleSuite(row, f.provider, f.artifact, false)
        assertEquals(4, f.proofs.history().size)
        f.packageRevision(0)
        val unknown = WorkloadQualificationStore(f.context, IntentQualificationFixture.MemoryStorage())
        unknown.recordRoleSuite(row, f.provider, f.artifact, false)
        assertTrue(unknown.history().isEmpty())
    }

    @Test fun latestFailureWinsEqualTimestamp() = runTest {
        val f = IntentQualificationFixture(backgroundScope)
        val p = proof(f)
        val failed = p.copy(correct = false, stable = false)
        f.proofs.append(p); f.proofs.append(failed)
        assertEquals(listOf(failed), f.proofs.current(p.target, p.environment))
    }

    @Test fun latestFailureWinsAfterWallClockRollback() = runTest {
        val f = IntentQualificationFixture(backgroundScope)
        val p = proof(f)
        val failed = p.copy(correct = false, measuredAtMs = p.measuredAtMs - 1000)
        f.proofs.append(p); f.proofs.append(failed)
        assertEquals(listOf(failed), f.proofs.current(p.target, p.environment))
    }

    @Test fun futureFailureDoesNotResurrectPriorPass() = runTest {
        val f = IntentQualificationFixture(backgroundScope)
        val p = proof(f)
        f.proofs.append(p)
        f.proofs.append(p.copy(correct = false, measuredAtMs = p.measuredAtMs + 600_000))
        assertTrue(f.proofs.current(p.target, p.environment).isEmpty())
        val reopened = WorkloadQualificationStore(f.context, f.storage)
        assertTrue(reopened.current(p.target, p.environment).isEmpty())
    }

    @Test fun laterValidMeasurementCanQualifyAgain() = runTest {
        val f = IntentQualificationFixture(backgroundScope)
        val p = proof(f)
        f.proofs.append(p.copy(correct = false, measuredAtMs = p.measuredAtMs + 600_000))
        f.proofs.append(p)
        assertEquals(listOf(p), f.proofs.current(p.target, p.environment))
    }

    @Test fun otherTargetsAndEnvironmentsCannotRevokeOrSupplyEvidence() = runTest {
        val f = IntentQualificationFixture(backgroundScope)
        val p = proof(f)
        f.proofs.append(p)
        f.proofs.append(p.copy(correct = false, target = p.target.copy(modelId = "other")))
        f.proofs.append(p.copy(correct = false, environment = p.environment.copy(settingsFingerprint = "other")))
        assertEquals(listOf(p), f.proofs.current(p.target, p.environment))
    }

    @Test fun failureIsScopedToItsActualBackendAndWorkload() = runTest {
        val f = IntentQualificationFixture(backgroundScope)
        val cpu = proof(f)
        val gpu = cpu.copy(backend = WorkloadBackend.GPU)
        val fast = cpu.copy(workload = LocalWorkload.FAST)
        val failed = cpu.copy(stable = false)
        listOf(cpu, gpu, fast, failed).forEach { f.proofs.append(it) }
        assertEquals(setOf(gpu, fast, failed), f.proofs.current(cpu.target, cpu.environment).toSet())
    }

    @Test fun revokedProofCannotProduceAutomaticRecommendation() = runTest {
        val f = IntentQualificationFixture(backgroundScope)
        val p = proof(f)
        f.proofs.append(p)
        fun candidates() = listOf(WorkloadCandidate(p.target, setOf(p.workload), true, true, true, true,
            setOf(WorkloadBackend.CPU), 1024, proofs = f.proofs.current(p.target, p.environment)))
        val request = WorkloadRequest(p.workload, p.environment, availableBytes = 2048)
        assertEquals(1, WorkloadAdaptivePolicy.rank(request, candidates()).size)
        f.proofs.append(p.copy(correct = false))
        assertTrue(WorkloadAdaptivePolicy.rank(request, candidates()).isEmpty())
    }
}
