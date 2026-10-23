package com.halo.app.audio

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.halo.sdk.runtime.Storage
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class SupertonicAcquisitionOwnershipTest {
    private val owner = "b88fe002-14e0-48dc-85bd-6dbcd038f709"
    private val other = "c88fe002-14e0-48dc-85bd-6dbcd038f709"
    private class MemoryStorage : Storage {
        private val values = mutableMapOf<String, Any?>()
        override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { values[key] = value }
        override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        override fun clear() { values.clear() }
    }
    private fun fixture(block: suspend (SupertonicPackStore, Uri, MemoryStorage) -> Unit) = runBlocking {
        val root = java.nio.file.Files.createTempDirectory("halo-tts-owned-").toFile()
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun getNoBackupFilesDir() = root
        }
        try {
            val archive = File(root, "fixture.zip")
            ZipOutputStream(archive.outputStream()).use { zip ->
                SpeechPackRegistry.supertonic.requiredFiles.sorted().forEach { name ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(ByteArray(if (name.endsWith(".onnx")) 4097 else 20) { 1 })
                    zip.closeEntry()
                }
            }
            val storage = MemoryStorage()
            block(SupertonicPackStore(context, storage), Uri.fromFile(archive), storage)
        } finally { root.deleteRecursively() }
    }

    @Test fun `rollback keeps independent import alongside exact owned pack`() = fixture { store, archive, _ ->
        val hash = store.importArchive(archive, owner)
        assertEquals(setOf(hash), store.ownedBy(owner))
        assertFalse(store.removeOwned(hash, other))
        assertTrue(File(store.storageRoot, hash).exists())
        assertTrue(store.removeOwned(hash, owner))
        assertFalse(File(store.storageRoot, hash).exists())
    }

    @Test fun `independent reimport retains previously acquired pack`() = fixture { store, archive, _ ->
        val hash = store.importArchive(archive, owner)
        assertEquals(hash, store.importArchive(archive))
        assertTrue(store.ownedBy(owner).isEmpty())
        assertFalse(store.removeOwned(hash, owner))
        assertTrue(File(store.storageRoot, hash).exists())
    }

    @Test fun `changed and incomplete owned packs remain recoverable without inferred deletion`() = fixture { store, archive, _ ->
        val hash = store.importArchive(archive, owner)
        val model = File(File(store.storageRoot, hash), "vocoder.int8.onnx")
        model.writeBytes(ByteArray(4097) { 2 })
        var rejected = false
        try { store.removeOwned(hash, owner) } catch (_: IllegalStateException) { rejected = true }
        assertTrue(rejected)
        assertTrue(model.exists())
        model.delete()
        assertEquals(setOf(hash), store.ownedBy(owner))
        assertFalse(store.removeOwned(hash, owner))
        assertTrue(File(store.storageRoot, hash).exists())
    }

    @Test fun `official archive partial is removed only by exact receipt owner`() = fixture { store, _, _ ->
        store.storageRoot.mkdirs()
        val partial = File(store.storageRoot, "official-${SupertonicPackStore.OFFICIAL_SHA256}.tar.bz2.part").apply { writeText("prefix") }
        val receipt = File(store.storageRoot, partial.name + ".acquired").apply { writeText(owner) }
        assertFalse(store.discardOwnedOfficialPartial(other))
        assertTrue(partial.exists())
        assertTrue(store.discardOwnedOfficialPartial(owner))
        assertFalse(partial.exists())
        assertFalse(receipt.exists())
    }
}
