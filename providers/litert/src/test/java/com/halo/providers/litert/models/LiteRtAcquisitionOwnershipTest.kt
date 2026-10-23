package com.halo.providers.litert.models

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class LiteRtAcquisitionOwnershipTest {
    private val owner = "b88fe002-14e0-48dc-85bd-6dbcd038f709"
    private val other = "c88fe002-14e0-48dc-85bd-6dbcd038f709"
    private val bytes = ByteArray(1_200_000) { (it % 251).toByte() }
    private val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private val model = LiteRtCatalogModel(id = "ownership-fixture", displayName = "Fixture", repoId = "halo/fixture",
        revision = "a3c5d805ae362dff7f580bc25f2dfb9a5a7eaa76", filename = "fixture.litertlm",
        expectedBytes = bytes.size.toLong(), sha256 = hash, contextWindowTokens = 4096,
        supportsVision = false, supportsTools = false, minLiteRtLmVersion = "0.17.1")

    private fun fixture(block: suspend (File, LiteRtModelManager) -> Unit) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = java.nio.file.Files.createTempDirectory("halo-litert-owned-").toFile()
        val manager = LiteRtModelManager(context, modelRoot = root, catalog = listOf(model))
        try { block(root, manager) } finally { root.deleteRecursively() }
    }
    private fun install(root: File, token: String? = owner) {
        File(root, "${model.id}.litertlm").writeBytes(bytes)
        File(root, "${model.id}.halo-model.json").writeText(
            InstalledLiteRtModelMetadata.fromCatalog(model, null).copy(acquisitionOwner = token).toJson())
    }

    @Test fun `selected unloaded candidate is protected from setup rollback`() = fixture { root, manager ->
        install(root)
        manager.observeProtectedModels { setOf(model.id) }
        assertFalse(manager.deleteModel(model.id, owner))
        assertArrayEquals(bytes, File(root, "${model.id}.litertlm").readBytes())
        manager.observeProtectedModels { emptySet() }
        assertTrue(manager.deleteModel(model.id, owner))
    }

    @Test fun `independent and differently owned artifacts are retained`() = fixture { root, manager ->
        install(root, null)
        assertFalse(manager.deleteModel(model.id, owner))
        install(root, other)
        assertFalse(manager.deleteModel(model.id, owner))
        assertArrayEquals(bytes, File(root, "${model.id}.litertlm").readBytes())
    }

    @Test fun `changed physical bytes fail closed despite matching receipt`() = fixture { root, manager ->
        install(root)
        val changed = bytes.copyOf().apply { this[0] = (this[0].toInt() xor 1).toByte() }
        File(root, "${model.id}.litertlm").writeBytes(changed)
        assertFalse(manager.deleteModel(model.id, owner))
        assertArrayEquals(changed, File(root, "${model.id}.litertlm").readBytes())
    }

    @Test fun `partial cleanup is scoped to transaction and never deletes installed file`() = fixture { root, manager ->
        install(root)
        val part = File(root, "${model.id}.litertlm.part").apply { writeText("prefix") }
        val sidecar = LiteRtPartialDownloadMetadata.sidecarFor(part)
        sidecar.writeText(LiteRtPartialDownloadMetadata.forArtifact(model, part.length()).copy(acquisitionOwner = owner).toJson())
        assertFalse(manager.discardOwnedPartial(model.id, other))
        assertTrue(part.exists())
        assertTrue(manager.discardOwnedPartial(model.id, owner))
        assertFalse(part.exists())
        assertTrue(File(root, "${model.id}.litertlm").exists())
    }
}
