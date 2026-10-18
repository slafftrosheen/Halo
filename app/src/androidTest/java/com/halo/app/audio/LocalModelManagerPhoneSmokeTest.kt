package com.halo.app.audio

import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.providers.litert.models.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Physical acquisition checks use isolated files and never select, load, delete or download a user's model. */
@RunWith(AndroidJUnit4::class)
class LocalModelManagerPhoneSmokeTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun sessionTokenTrimsClipboardWhitespaceAndRejectsHeaderInjection() {
        val root = File(context.cacheDir, "halo-token-smoke-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val manager = LiteRtModelManager(context, root, catalog = emptyList())
            assertFalse(manager.hubTokenApplied.value)
            assertTrue(manager.applyHubToken("  hf_halodevicesmoke123\n"))
            assertTrue(manager.hubTokenApplied.value)
            assertFalse(manager.applyHubToken("hf_secret\r\nAuthorization: leaked"))
            manager.clearHubToken()
            assertFalse(manager.hubTokenApplied.value)
        } finally { root.deleteRecursively() }
    }

    @Test fun importRealInstalledArtifactThroughContentUriAndDeduplicate() = runBlocking {
        val sourceManager = Graph.modelManager
        val inventory = withTimeout(180_000) { sourceManager.inventoryState.first { it !is LocalModelInventoryState.Loading } }
            as? LocalModelInventoryState.Ready ?: error("Phone model inventory unavailable")
        val pair = inventory.models.filter { it.verified }.mapNotNull { descriptor ->
            sourceManager.catalogModel(descriptor.id)?.let { descriptor to it }
        }.minByOrNull { it.first.bytes }
        assumeTrue("No verified installed artifact to exercise real import", pair != null)
        val (source, catalog) = requireNotNull(pair)
        assumeTrue("Insufficient room for isolated import copies", context.filesDir.usableSpace > source.bytes * 4 + 64L * 1024 * 1024)
        val id = UUID.randomUUID().toString()
        val shared = File(context.filesDir, "workspace/owned/halo-import-smoke-$id").apply { mkdirs() }
        val target = File(context.cacheDir, "halo-import-smoke-$id").apply { mkdirs() }
        try {
            val input = File(shared, "actual-artifact.LITERTLM")
            File(source.path).inputStream().use { from -> input.outputStream().use { to -> from.copyTo(to) } }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.captures", input)
            val manager = LiteRtModelManager(context, target, catalog = listOf(catalog))
            val imported = manager.importModelFromUri(uri).getOrThrow()
            assertTrue(imported.verified)
            assertEquals(source.sha256, imported.sha256)
            assertEquals(catalog.id, imported.id)
            assertEquals(source.bytes, imported.bytes)
            assertNull(manager.importStatus.value)
            assertEquals(imported.id, manager.importModelFromUri(uri).getOrThrow().id)
            assertEquals(1, target.listFiles().orEmpty().count { it.extension == "litertlm" })
            assertTrue(File(source.path).exists())
            assertEquals(source.bytes, File(source.path).length())
        } finally { shared.deleteRecursively(); target.deleteRecursively() }
    }

    @Test fun unsupportedImportReportsFailureWithoutLeavingPartialFiles() = runBlocking {
        val id = UUID.randomUUID().toString()
        val shared = File(context.filesDir, "workspace/owned/halo-invalid-smoke-$id").apply { mkdirs() }
        val target = File(context.cacheDir, "halo-invalid-smoke-$id").apply { mkdirs() }
        try {
            val input = File(shared, "not-a-model.txt").apply { writeText("not a LiteRT model") }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.captures", input)
            val manager = LiteRtModelManager(context, target, catalog = emptyList())
            val result = manager.importModelFromUri(uri)
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertNull(manager.importStatus.value)
            assertTrue(target.listFiles().orEmpty().none { it.name.endsWith(".part") || it.extension == "litertlm" })
        } finally { shared.deleteRecursively(); target.deleteRecursively() }
    }
}
