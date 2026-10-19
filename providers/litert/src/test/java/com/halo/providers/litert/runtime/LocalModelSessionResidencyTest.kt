package com.halo.providers.litert.runtime

import com.halo.sdk.provider.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Fake native runtimes prove residency matching without loading a multi-GB artifact. */
@RunWith(RobolectricTestRunner::class)
class LocalModelSessionResidencyTest {
    private class FakeRuntime : LocalInferenceRuntime {
        override val format = LocalModelFormat.LITERT_LM
        override val state = MutableStateFlow(LocalModelSessionState.IDLE)
        override var loadedModelId: String? = null
            private set
        var loads = 0
        var unloads = 0
        var failUnload = false
        var failLoad = false
        var generateBody: (suspend () -> RuntimeGenerationResult)? = null

        override fun isLoaded(): Boolean = loadedModelId != null

        override suspend fun loadModel(modelId: String, modelPath: String, config: RuntimeLoadConfig): Result<RuntimeLoadResult> {
            loads++
            loadedModelId = modelId
            if (failLoad) return Result.failure(IllegalStateException("Load failed after allocation"))
            return Result.success(RuntimeLoadResult(config.computeBackend, 0, config.contextSize))
        }
        override suspend fun generate(config: RuntimeGenerationConfig, onToken: suspend (String) -> Unit) =
            Result.success(generateBody?.invoke() ?: RuntimeGenerationResult("ready", tokenCount = 1))
        override suspend fun cancelGeneration() = Unit
        override suspend fun unloadModel(): Result<Unit> {
            unloads++
            if (failUnload) return Result.failure(IllegalStateException("Native engine is still mapped"))
            loadedModelId = null
            return Result.success(Unit)
        }
    }

    @Test fun sameIdentityAndRequestedProfileReusesOneEngine() = runTest {
        val runtimes = mutableListOf<FakeRuntime>()
        val session = LocalModelSession({ FakeRuntime().also(runtimes::add) }, backgroundScope)
        val config = RuntimeLoadConfig(contextSize = 4096, computeBackend = LocalComputeBackend.CPU)
        assertTrue(session.loadModel("A", "/model-a", config = config, artifactSha256 = "sha-a").isSuccess)
        assertTrue(session.isResidentFor("A", "/model-a", "sha-a", config))
        assertTrue(session.loadModel("A", "/model-a", config = config, artifactSha256 = "sha-a").isSuccess)
        assertEquals(1, runtimes.size)
        assertEquals(1, runtimes.single().loads)
    }

    @Test fun differentProfilePathOrShaRequiresRealReload() = runTest {
        val runtimes = mutableListOf<FakeRuntime>()
        val session = LocalModelSession({ FakeRuntime().also(runtimes::add) }, backgroundScope)
        val cpu = RuntimeLoadConfig(contextSize = 4096, computeBackend = LocalComputeBackend.CPU)
        val gpu = cpu.copy(computeBackend = LocalComputeBackend.GPU)
        assertTrue(session.loadModel("A", "/a", config = cpu, artifactSha256 = "hash-1").isSuccess)
        assertFalse(session.isResidentFor("A", "/a", "hash-1", gpu))
        assertTrue(session.loadModel("A", "/a", config = gpu, artifactSha256 = "hash-1").isSuccess)
        assertFalse(session.isResidentFor("A", "/a", "hash-1", cpu))
        assertTrue(session.loadModel("A", "/b", config = gpu, artifactSha256 = "hash-1").isSuccess)
        assertTrue(session.loadModel("A", "/b", config = gpu, artifactSha256 = "hash-2").isSuccess)
        assertEquals(4, runtimes.size)
        assertEquals(1, runtimes[0].unloads)
        assertEquals(1, runtimes[1].unloads)
        assertEquals(1, runtimes[2].unloads)
        assertEquals(1, runtimes[3].loads)
        assertTrue(session.isResidentFor("A", "/b", "hash-2", gpu))
    }

    @Test fun failedTeardownBlocksNewModelAllocationUntilReleased() = runTest {
        val runtimes = mutableListOf<FakeRuntime>()
        val session = LocalModelSession({ FakeRuntime().also(runtimes::add) }, backgroundScope)
        val config = RuntimeLoadConfig(computeBackend = LocalComputeBackend.CPU)
        assertTrue(session.loadModel("A", "/a", config = config, artifactSha256 = "sha").isSuccess)
        runtimes.first().failUnload = true
        val failure = session.loadModel("B", "/b", config = config, artifactSha256 = "sha-b")
        assertTrue(failure.isFailure)
        assertEquals(1, runtimes.size)
        assertFalse(session.isLoaded())
        assertEquals("A", session.loadedModelId)
        runtimes.first().failUnload = false
        assertTrue(session.loadModel("B", "/b", config = config, artifactSha256 = "sha-b").isSuccess)
        assertEquals(2, runtimes.size)
        assertTrue(session.isResidentFor("B", "/b", "sha-b", config))
    }

    @Test fun previousLoadFailureCanRecoverOnNextAttempt() = runTest {
        val runtimes = mutableListOf<FakeRuntime>()
        val session = LocalModelSession({ FakeRuntime().also(runtimes::add) }, backgroundScope)
        val config = RuntimeLoadConfig(computeBackend = LocalComputeBackend.CPU)
        assertTrue(session.loadModel("A", "/a", config = config).isSuccess)
        assertTrue(session.unloadModel().isSuccess)
        assertFalse(session.isLoaded())
        assertTrue(session.loadModel("A", "/a", config = config).isSuccess)
        assertEquals(2, runtimes.size)
    }

    @Test fun failedLoadCleanupRetainsOwnerUntilNativeTeardownSucceeds() = runTest {
        val first = FakeRuntime().apply { failLoad = true; failUnload = true }
        var allocations = 0
        val session = LocalModelSession({ allocations++; if (allocations == 1) first else FakeRuntime() }, backgroundScope)
        assertTrue(session.loadModel("A", "/a").isFailure)
        assertTrue(session.loadModel("B", "/b").isFailure)
        assertEquals(1, allocations)
        first.failUnload = false
        assertTrue(session.loadModel("B", "/b").isSuccess)
        assertEquals(2, allocations)
    }

    @Test fun cancelledCallerCancelsItsBackendJobAndDoesNotPoisonNextRequest() = runTest {
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val runtime = FakeRuntime().apply { generateBody = {
            entered.complete(Unit)
            try { awaitCancellation() } finally { cancelled.complete(Unit) }
        } }
        val session = LocalModelSession({ runtime }, backgroundScope)
        session.loadModel("A", "/a").getOrThrow()
        val request = launch { session.generate(RuntimeGenerationConfig(prompt = "first")) }
        entered.await()
        request.cancelAndJoin()
        cancelled.await()
        runtime.generateBody = null
        assertEquals("ready", session.generate(RuntimeGenerationConfig(prompt = "next")).getOrThrow().text)
    }

    @Test fun replacementWaitsForActiveInferenceBeforeDisposingRuntime() = runTest {
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val first = FakeRuntime().apply { generateBody = {
            entered.complete(Unit); finish.await(); RuntimeGenerationResult("done", tokenCount = 1)
        } }
        var allocations = 0
        val session = LocalModelSession({ if (allocations++ == 0) first else FakeRuntime() }, backgroundScope)
        session.loadModel("A", "/a").getOrThrow()
        val request = async { session.generate(RuntimeGenerationConfig(prompt = "first")) }
        entered.await()
        val replacement = async { session.loadModel("B", "/b") }
        runCurrent()
        assertEquals(0, first.unloads)
        assertFalse(replacement.isCompleted)
        finish.complete(Unit)
        assertTrue(request.await().isSuccess)
        assertTrue(replacement.await().isSuccess)
        assertEquals(1, first.unloads)
    }
}
