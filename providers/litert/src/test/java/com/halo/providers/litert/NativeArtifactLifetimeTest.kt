package com.halo.providers.litert

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.halo.providers.litert.models.*
import com.halo.sdk.provider.EmbeddingRequest
import com.halo.sdk.runtime.*
import com.halo.sdk.runtime.resource.ResourceLeaseManager
import com.halo.runtime.kernel.resource.DefaultResourceLeaseManager
import kotlinx.coroutines.flow.first
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class NativeArtifactLifetimeTest {
    private class NativeSession : LiteRtNativeArtifactSession {
        var onNative: () -> Unit = {}
        var closeFailure = false
        var computeCancellation = false
        var initializeFailure = false
        var closes = 0
        var initialized = false
        var closed = false
        var metadata = LiteRtNativeArtifactInfo("EMBEDDING", listOf("CPU"), false, false, false, true, 2, false, false)
        override fun inspect(): LiteRtNativeArtifactInfo {
            onNative()
            return metadata
        }
        override fun initializeEmbedding(vision: Boolean, audio: Boolean) {
            onNative()
            if (initializeFailure) error("fixture initialization")
            initialized = true
        }
        override fun computeEmbedding(input: LocalEmbeddingInput): List<Float> {
            onNative()
            if (computeCancellation) throw CancellationException("fixture cancellation")
            return listOf(1f, 0f)
        }
        override fun close() {
            onNative()
            closes++
            if (closeFailure) error("fixture teardown")
            closed = true
        }
    }

    private class Fixture(context: Context, scope: CoroutineScope, leases: ResourceLeaseManager? = null) {
        val root = java.nio.file.Files.createTempDirectory("halo-native-artifact-").toFile()
        val bytes = ByteArray(1_200_000) { (it % 251).toByte() }
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val model = LiteRtCatalogModel("native-fixture", "Fixture", "halo/fixture",
            "a3c5d805ae362dff7f580bc25f2dfb9a5a7eaa76", "fixture.litertlm", bytes.size.toLong(), sha,
            4096, false, false, minLiteRtLmVersion = "0.17.1", task = LiteRtArtifactTask.EMBEDDING)
        val manager = LiteRtModelManager(context, modelRoot = root, catalog = listOf(model))
        val resources = NativeResourceCoordinator(resourceLeaseManager = leases, scope = scope)
        val session = NativeSession()
        var opens = 0
        val factory = LiteRtNativeArtifactFactory { opens++; session.onNative(); session }
        val request get() = EmbeddingRequest(listOf("fixture text"), sha, 2)
        init { File(root, "${model.id}.litertlm").writeBytes(bytes) }
        suspend fun prepare() { resources.updateBudget(NativeResourceBudget(8L shl 30, 8L shl 30)) }
        fun assertPinned() {
            val writer = ModelArtifactFileLocks.tryAcquireWrite(root, model.id)
            if (writer != null) { writer.close(); fail("Native access outlived its artifact pin") }
        }
        suspend fun cleanup() {
            session.closeFailure = false
            resources.releaseIdle("fixture cleanup")
            root.deleteRecursively()
        }
    }

    @Test fun cancellationDuringAdmissionReleasesComputeLeaseAndArtifactPin() = runTest {
        val leases = DefaultResourceLeaseManager()
        val f = Fixture(ApplicationProvider.getApplicationContext(), backgroundScope, leases)
        val entered = CompletableDeferred<Unit>()
        val unblock = CompletableDeferred<Unit>()
        val blocker = object : NativeResourceOwner {
            override val nativeResourceId = "fixture:admission-blocker"
            override val nativeResourceKind = NativeResourceKind.HEAVY_TTS
            override val defaultResourcePolicy = WorkloadResourcePolicy(RuntimeWorkloadClass.TTS)
            override suspend fun releaseIfIdle(): Boolean {
                entered.complete(Unit)
                unblock.await()
                return true
            }
        }
        var releasing: Job? = null
        var embedding: Job? = null
        try {
            f.prepare()
            assertTrue(f.resources.acquire(blocker))
            f.resources.mark(blocker, NativeResourceState.RESIDENT, "fixture idle")
            releasing = launch(Dispatchers.Default) { f.resources.releaseIdle("block admission") }
            withContext(Dispatchers.Default) { withTimeout(5_000) { entered.await() } }
            val provider = LiteRtEmbeddingProvider(f.manager, f.resources, f.model.id, f.factory)
            embedding = launch(Dispatchers.Default) { provider.embed(f.request) }
            withContext(Dispatchers.Default) {
                withTimeout(5_000) {
                    leases.snapshotState.first { state -> state.active.any { it.holder.id == provider.nativeResourceId } }
                }
            }
            f.assertPinned()
            embedding.cancel()
            unblock.complete(Unit)
            withContext(Dispatchers.Default) { withTimeout(5_000) { embedding.join(); releasing.join() } }
            assertTrue(embedding.isCancelled)
            assertFalse(leases.snapshot().active.any { it.holder.id == provider.nativeResourceId })
            assertNull(f.resources.executionSnapshot(provider))
            assertEquals(0, f.opens)
            assertTrue(f.manager.deleteModel(f.model.id))
        } finally {
            unblock.complete(Unit)
            withContext(NonCancellable) { embedding?.cancelAndJoin(); releasing?.cancelAndJoin(); f.cleanup() }
        }
    }

    @Test fun embeddingPinsExactFileThroughInspectionInitializationComputationAndClose() = runTest {
        val f = Fixture(ApplicationProvider.getApplicationContext(), backgroundScope)
        try {
            f.prepare()
            f.session.onNative = f::assertPinned
            val provider = LiteRtEmbeddingProvider(f.manager, f.resources, f.model.id, f.factory)
            assertEquals(1, provider.embed(f.request).vectors.size)
            assertTrue(f.session.initialized)
            assertTrue(f.session.closed)
            assertTrue(f.manager.deleteModel(f.model.id))
        } finally { f.cleanup() }
    }

    @Test fun unavailableOrChangedLeaseNeverOpensNativeMetadata() = runTest {
        val f = Fixture(ApplicationProvider.getApplicationContext(), backgroundScope)
        try {
            f.prepare()
            val provider = LiteRtEmbeddingProvider(f.manager, f.resources, f.model.id, f.factory)
            val writer = requireNotNull(ModelArtifactFileLocks.tryAcquireWrite(f.root, f.model.id))
            try {
                val failure = runCatching { provider.embed(f.request) }.exceptionOrNull()
                assertEquals("ARTIFACT_LEASE_UNAVAILABLE", failure?.message)
            } finally { writer.close() }
            File(f.root, "${f.model.id}.litertlm").writeText("changed")
            assertEquals("ARTIFACT_LEASE_UNAVAILABLE", runCatching { provider.embed(f.request) }.exceptionOrNull()?.message)
            assertEquals(0, f.opens)
        } finally { f.cleanup() }
    }

    @Test fun failedNativeTeardownRetainsPinUntilCoordinatorRetryActuallyCloses() = runTest {
        val f = Fixture(ApplicationProvider.getApplicationContext(), backgroundScope)
        try {
            f.prepare()
            f.session.onNative = f::assertPinned
            f.session.closeFailure = true
            val provider = LiteRtEmbeddingProvider(f.manager, f.resources, f.model.id, f.factory)
            assertEquals("NATIVE_TEARDOWN_FAILED", runCatching { provider.embed(f.request) }.exceptionOrNull()?.message)
            assertFalse(f.manager.deleteModel(f.model.id))
            withContext(Dispatchers.Default) { withTimeout(2_000) { f.resources.releaseIdle("retry") } }
            assertFalse(f.manager.deleteModel(f.model.id))
            f.session.closeFailure = false
            withContext(Dispatchers.Default) { withTimeout(2_000) { f.resources.releaseIdle("retry") } }
            assertTrue(f.session.closed)
            assertTrue(f.manager.deleteModel(f.model.id))
        } finally { f.cleanup() }
    }

    @Test fun cancellationAndInitializationFailureReleaseNativeAndFileOwnership() = runTest {
        for (cancel in listOf(false, true)) {
            val f = Fixture(ApplicationProvider.getApplicationContext(), backgroundScope)
            try {
                f.prepare()
                f.session.onNative = f::assertPinned
                f.session.computeCancellation = cancel
                f.session.initializeFailure = !cancel
                val provider = LiteRtEmbeddingProvider(f.manager, f.resources, f.model.id, f.factory)
                val failure = runCatching { provider.embed(f.request) }.exceptionOrNull()
                if (cancel) assertTrue(failure is CancellationException) else assertTrue(failure is EmbeddingUnavailableException)
                assertTrue(f.session.closed)
                assertTrue(f.manager.deleteModel(f.model.id))
            } finally { f.cleanup() }
        }
    }

    @Test fun inspectorUsesExactFilePinAndRetainsItAcrossMetadataCloseFailure() = runTest {
        val f = Fixture(ApplicationProvider.getApplicationContext(), backgroundScope)
        try {
            f.prepare()
            f.session.onNative = f::assertPinned
            f.session.closeFailure = true
            val artifact = requireNotNull(f.manager.verifyPhysicalArtifact(f.model.id))
            val inspector = LiteRtArtifactInspector(f.resources, f.manager, f.factory)
            assertEquals("NATIVE_TEARDOWN_FAILED", runCatching { inspector.inspect(artifact) }.exceptionOrNull()?.message)
            assertFalse(f.manager.deleteModel(f.model.id))
            f.session.closeFailure = false
            withContext(Dispatchers.Default) { withTimeout(2_000) { f.resources.releaseIdle("retry metadata") } }
            assertTrue(f.manager.deleteModel(f.model.id))
        } finally { f.cleanup() }
    }

    @Test fun cancellationRemainsPrimaryWhenTeardownAlsoFailsAndPinIsRetained() = runTest {
        val f = Fixture(ApplicationProvider.getApplicationContext(), backgroundScope)
        try {
            f.prepare()
            f.session.onNative = f::assertPinned
            f.session.computeCancellation = true
            f.session.closeFailure = true
            val provider = LiteRtEmbeddingProvider(f.manager, f.resources, f.model.id, f.factory)
            val failure = runCatching { provider.embed(f.request) }.exceptionOrNull()
            assertTrue(failure is CancellationException)
            // Coroutine stack-trace recovery may wrap cancellation with the original
            // exception as its cause; teardown evidence must survive that boundary.
            assertTrue(generateSequence(requireNotNull(failure)) { it.cause }
                .any { error -> error.suppressed.any { it.message == "fixture teardown" } })
            assertFalse(f.manager.deleteModel(f.model.id))
            f.session.closeFailure = false
            f.resources.releaseIdle("retry cancelled teardown")
            assertTrue(f.manager.deleteModel(f.model.id))
        } finally { f.cleanup() }
    }

    @Test fun unsupportedNativeMetadataNeverInitializesEmbeddingAndReleasesFilePin() = runTest {
        for (invalid in listOf("task", "dimension", "cpu")) {
            val f = Fixture(ApplicationProvider.getApplicationContext(), backgroundScope)
            try {
                f.prepare()
                f.session.onNative = f::assertPinned
                f.session.metadata = when (invalid) {
                    "task" -> f.session.metadata.copy(embedding = false)
                    "dimension" -> f.session.metadata.copy(embeddingDimension = 3)
                    else -> f.session.metadata.copy(textBackends = listOf("GPU"))
                }
                val provider = LiteRtEmbeddingProvider(f.manager, f.resources, f.model.id, f.factory)
                assertTrue(runCatching { provider.embed(f.request) }.exceptionOrNull() is EmbeddingUnavailableException)
                assertFalse(f.session.initialized)
                assertTrue(f.session.closed)
                assertTrue(f.manager.deleteModel(f.model.id))
            } finally { f.cleanup() }
        }
    }

    @Test fun deniedMemoryBudgetReleasesPinBeforeAnyNativeHandleIsOpened() = runTest {
        val f = Fixture(ApplicationProvider.getApplicationContext(), backgroundScope)
        try {
            f.resources.updateBudget(NativeResourceBudget(1, 0))
            val provider = LiteRtEmbeddingProvider(f.manager, f.resources, f.model.id, f.factory)
            assertEquals("MEMORY_NOT_ADMITTED", runCatching { provider.embed(f.request) }.exceptionOrNull()?.message)
            assertEquals(0, f.opens)
            assertTrue(f.manager.deleteModel(f.model.id))
        } finally { f.cleanup() }
    }

    @Test fun inventoryWithoutLeaseSupportFailsClosedForExecutionAndInspection() = runTest {
        val f = Fixture(ApplicationProvider.getApplicationContext(), backgroundScope)
        try {
            f.prepare()
            val noPins = object : LocalModelInventoryRepository {
                override val inventoryState = f.manager.inventoryState
                override suspend fun refresh(reason: ModelInventoryRefreshReason) { f.manager.refresh(reason) }
                override suspend fun resolveModel(modelId: String) = f.manager.resolveModel(modelId)
                override fun invalidate(reason: String) {}
            }
            val artifact = requireNotNull(noPins.resolveModel(f.model.id))
            assertEquals("ARTIFACT_LEASE_UNAVAILABLE", runCatching {
                LiteRtEmbeddingProvider(noPins, f.resources, f.model.id, f.factory).embed(f.request)
            }.exceptionOrNull()?.message)
            assertEquals("ARTIFACT_LEASE_UNAVAILABLE", runCatching {
                LiteRtArtifactInspector(f.resources, noPins, f.factory).inspect(artifact)
            }.exceptionOrNull()?.message)
            assertEquals(0, f.opens)
        } finally { f.cleanup() }
    }

    @Test fun physicalInspectionPreservesUnverifiedImportWithoutGrantingVerifiedLease() = runTest {
        val f = Fixture(ApplicationProvider.getApplicationContext(), backgroundScope)
        try {
            f.prepare()
            val imported = LiteRtModelManager(ApplicationProvider.getApplicationContext(), modelRoot = f.root, catalog = emptyList())
            val artifact = requireNotNull(imported.resolveModel(f.model.id))
            assertTrue(artifact.valid)
            assertFalse(artifact.verified)
            assertNull(imported.acquireVerifiedArtifactLease(artifact.id, f.sha))
            f.session.onNative = f::assertPinned
            assertEquals(2, LiteRtArtifactInspector(f.resources, imported, f.factory).inspect(artifact).embeddingDimension)
            assertTrue(f.session.closed)
            assertFalse(requireNotNull(imported.resolveModel(artifact.id)).verified)
            assertTrue(imported.deleteModel(artifact.id))
        } finally { f.cleanup() }
    }
}
