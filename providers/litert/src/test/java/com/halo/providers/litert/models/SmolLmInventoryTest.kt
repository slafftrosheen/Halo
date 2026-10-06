package com.halo.providers.litert.models

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterIsInstance
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SmolLmInventoryTest {
    private val candidates = listOf(LiteRtModelCatalog.SMOLLM2_135M_INSTRUCT, LiteRtModelCatalog.SMOLLM2_360M_INSTRUCT)
    @Test fun correctLengthWrongBytesAreRejectedForBothCandidates() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (candidate in candidates) {
            val root = File(context.cacheDir, "smol-corrupt-${System.nanoTime()}").also { it.mkdirs() }
            val file = File(root, "${candidate.id}.litertlm")
            RandomAccessFile(file, "rw").use { it.setLength(candidate.expectedBytes) }
            val manager = LiteRtModelManager(context, modelRoot = root, catalog = listOf(candidate))
            assertNull(manager.verifyPhysicalArtifact(candidate.id))
            val result = manager.resolveModel(candidate.id)!!
            assertEquals(candidate.expectedBytes, result.bytes)
            assertEquals(LocalModelValidation.CORRUPT, result.validation)
            assertFalse(result.valid)
            file.delete()
        }
    }
    @Test fun exactFingerprintImportsCanonicalizeEachCandidateWithoutDuplicateId() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bytes = ByteArray(1200000) { (it % 251).toByte() }
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        for (candidate in candidates) {
            val fixture = candidate.copy(expectedBytes = bytes.size.toLong(), sha256 = sha)
            val root = File(context.cacheDir, "smol-import-${System.nanoTime()}").also { it.mkdirs() }
            File(root, "manual-${candidate.id}.litertlm").writeBytes(bytes)
            val manager = LiteRtModelManager(context, modelRoot = root, catalog = listOf(fixture))
            manager.refresh(ModelInventoryRefreshReason.USER_REQUEST)
            val models = manager.inventoryState.filterIsInstance<LocalModelInventoryState.Ready>().first().models
            assertEquals(listOf(candidate.id), models.map { it.id })
            assertTrue(models.single().verified)
        }
    }
}
