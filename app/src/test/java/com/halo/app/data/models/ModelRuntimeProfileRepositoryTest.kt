package com.halo.app.data.models

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.ui.screens.settings.retainRoleQualification
import com.halo.providers.litert.runtime.*
import com.halo.sdk.provider.LocalComputeBackend
import com.halo.app.ui.screens.settings.markProbeProvenance
import com.halo.sdk.provider.ToolCallProtocol
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ModelRuntimeProfileRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: ModelRuntimeProfileRepository
    private fun profile(id: String = "good", backend: String = "CPU", ttft: Long = 3000) = ModelRuntimeProfileEntity(
        id, "artifact", "sha", "device", "LITERT", backend, 4096, 4, 0, 1200, ttft, 0f, 8.4f,
        textGenerationProven = true, runtimeVersion = "runtime", compatibilityEpoch = 1)
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).allowMainThreadQueries().build()
        repo = ModelRuntimeProfileRepository(db.modelRuntimeProfileDao())
    }
    @After fun close() { db.close() }
    private suspend fun preferred(sha: String = "sha", device: String = "device", version: String = "runtime", epoch: Int = 1) =
        repo.preferred("artifact", sha, device, version, epoch)

    @Test fun absentBenchmarkCannotBeCreatedByAgenticProvenance() = runBlocking {
        assertFalse(markProbeProvenance(repo, profile(), ToolCallProtocol.HALO_PROMPT, true))
        assertTrue(db.modelRuntimeProfileDao().getProfilesForModel("artifact", "device").isEmpty())
    }

    private fun roleRow() = LocalModelRoleQualification("artifact", "sha", 100,
        LocalComputeBackend.AUTO, LocalComputeBackend.CPU, 4096, 4, 0, 1200,
        agentic = LocalAgenticQualification(LocalRoleProbeVerdict.PASS, ToolCallProtocol.HALO_PROMPT, true, true),
        startedAtMs = 1, completedAtMs = 2)

    @Test fun completedRoleSuiteRetainsExistingExactProfileImmediately() = runBlocking {
        val existing = profile(); repo.save(existing)
        val row = retainRoleQualification(repo, existing, roleRow())
        assertTrue(row.agentic!!.provenanceRetained)
        val persisted = db.modelRuntimeProfileDao().getProfileById(existing.id)!!
        assertTrue(persisted.haloPromptToolLoopProven); assertTrue(persisted.agenticContinuationProven)
    }
    @Test fun invalidatedOrBackendChangedRoleEvidenceCannotRetainProvenance() = runBlocking {
        val existing = profile(); repo.save(existing)
        assertFalse(retainRoleQualification(repo, existing, roleRow().copy(failure = LocalRoleProbeFailure.ARTIFACT_CHANGED)).agentic!!.provenanceRetained)
        assertFalse(retainRoleQualification(repo, existing, roleRow().copy(backend = LocalComputeBackend.GPU)).agentic!!.provenanceRetained)
        assertFalse(db.modelRuntimeProfileDao().getProfileById(existing.id)!!.agenticContinuationProven)
    }

    @Test fun fullCompatibilityIdentityRequired() = runBlocking {
        repo.save(profile())
        assertEquals("good", preferred()?.id)
        assertNull(preferred(sha = "different"))
        assertNull(preferred(device = "different"))
        assertNull(preferred(version = "different"))
        assertNull(preferred(epoch = 2))
        assertNull(repo.preferred("artifact", "sha", "device", "runtime", 1, runtime = "OTHER"))
    }
    @Test fun failureDoesNotReplaceProvenVariantAndOrderingUsesLatency() = runBlocking {
        repo.save(profile())
        repo.save(profile("failure").copy(isStable = false, textGenerationProven = false, failureReason = "GENERATION_FAILED", ttftMs = 0))
        assertEquals(2, db.modelRuntimeProfileDao().getProfilesForModel("artifact", "device").size)
        assertEquals("good", preferred()?.id)
        repo.save(profile("faster", "GPU", 2000))
        assertEquals("faster", preferred()?.id)
        assertEquals("good", repo.preferred("artifact", "sha", "device", "runtime", 1, backend = "CPU")?.id)
    }
    @Test fun nativeAndPromptProvenanceAreIndependentAndFailuresCannotProveContinuation() = runBlocking {
        val native = profile()
        val prompt = profile("prompt", "CPU", 3000).copy(contextSize = 2048)
        repo.save(native); repo.save(prompt)
        markProbeProvenance(repo, native, ToolCallProtocol.NATIVE, true)
        val n = db.modelRuntimeProfileDao().getProfileById(native.id)!!
        assertTrue(n.nativeToolCallingProven); assertFalse(n.haloPromptToolLoopProven); assertTrue(n.agenticContinuationProven)
        markProbeProvenance(repo, prompt, ToolCallProtocol.HALO_PROMPT, false)
        assertFalse(db.modelRuntimeProfileDao().getProfileById(prompt.id)!!.agenticContinuationProven)
        markProbeProvenance(repo, prompt, ToolCallProtocol.HALO_PROMPT, true)
        val p = db.modelRuntimeProfileDao().getProfileById(prompt.id)!!
        assertFalse(p.nativeToolCallingProven); assertTrue(p.haloPromptToolLoopProven); assertTrue(p.agenticContinuationProven)
    }
    @Test fun remeasurementPreservesIndependentProofForTheSameVariant() = runBlocking {
        repo.save(profile().copy(nativeToolCallingProven = true, agenticContinuationProven = true))
        repo.save(profile("remeasured", ttft = 6000))
        val measured = preferred()!!
        assertEquals("remeasured", measured.id)
        assertEquals(6000L, measured.ttftMs)
        assertTrue(measured.nativeToolCallingProven)
        assertTrue(measured.agenticContinuationProven)
        assertFalse(measured.haloPromptToolLoopProven)
    }

    @Test fun staleProbeCannotMutateAnotherIdentityEvenWithSameId() = runBlocking {
        val p = profile(); repo.save(p)
        assertEquals(0, repo.markAgenticContinuationProven(p.copy(artifactFingerprint = "other")))
        assertEquals(0, repo.markNativeToolCallingProven(p.copy(deviceFingerprint = "other")))
        assertEquals(0, repo.markPromptToolLoopProven(p.copy(backend = "GPU")))
        assertEquals(0, repo.markTextGenerationProven(p.copy(contextSize = 1024)))
        assertFalse(db.modelRuntimeProfileDao().getProfileById(p.id)!!.agenticContinuationProven)
    }
}
