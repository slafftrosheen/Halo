package com.halo.providers.litert.models

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LiteRtDownloadPresentationTest {
    @Test
    fun requestIsQueuedThenPreparedDownloadedAndCancellationBecomesPaused() {
        runBlocking {
        val enteredDownload = CompletableDeferred<Unit>()
        val downloader = object : LiteRtModelDownloader {
            override suspend fun download(
                artifact: LiteRtCatalogModel,
                destinationPart: File,
                onProgress: (DownloadProgress) -> Unit,
                resumeFrom: Long,
            ): DownloadResult {
                destinationPart.writeBytes(ByteArray(16))
                onProgress(DownloadProgress(16, artifact.expectedBytes, 0.16f))
                enteredDownload.complete(Unit)
                awaitCancellation()
            }
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.cacheDir, "download-presentation-${System.nanoTime()}").apply { mkdirs() }
        val artifact = LiteRtCatalogModel(
            id = "presentation-test",
            displayName = "Presentation test",
            repoId = "halo/test",
            revision = "a3c5d805ae362dff7f580bc25f2dfb9a5a7eaa76",
            filename = "presentation-test.litertlm",
            expectedBytes = 2_000_000L,
            sha256 = "0".repeat(64),
            contextWindowTokens = 1024,
            supportsVision = false,
            supportsTools = false,
            minLiteRtLmVersion = "0.17.1",
        )
        val manager = LiteRtModelManager(context, root, downloader, listOf(artifact))
        val seen = mutableListOf<DownloadState>()
        val collector = launch(Dispatchers.Unconfined) {
            manager.downloadStates.collect { states -> states[artifact.id]?.let(seen::add) }
        }

        manager.downloadCatalogModel(artifact)
        enteredDownload.await()
        assertTrue(seen.any { it is DownloadState.Queued })
        assertTrue(seen.any { it is DownloadState.Preparing })
        assertTrue(seen.any { it is DownloadState.Downloading })

        manager.cancelDownload(artifact.id)
        awaitState(manager, artifact.id) { it is DownloadState.Paused }
        assertTrue(manager.downloadStates.value[artifact.id] is DownloadState.Paused)
            collector.cancelAndJoin()
            root.deleteRecursively()
        }
    }

    private fun awaitState(
        manager: LiteRtModelManager,
        id: String,
        predicate: (DownloadState?) -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!predicate(manager.downloadStates.value[id]) && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        assertTrue(predicate(manager.downloadStates.value[id]))
    }
}
