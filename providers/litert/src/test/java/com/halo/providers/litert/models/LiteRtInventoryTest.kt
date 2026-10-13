package com.halo.providers.litert.models

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Inventory must never claim "valid" merely because a file exists. A catalog artifact is only
 * verified when its physical byte count *and* SHA-256 match the pinned entry.
 */
@RunWith(RobolectricTestRunner::class)
class LiteRtInventoryTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val payload = ByteArray(1_200_000) { index -> (index % 251).toByte() }
    private val payloadSha = sha256(payload)

    /** Same size as [payload] but a different fingerprint, i.e. genuinely unknown to the catalog. */
    private val unknownPayload = ByteArray(1_200_000) { index -> ((index + 7) % 251).toByte() }

    @Test fun `completed background work reconciles installed artifact`() = runBlocking {
        val root = tempRoot("background-complete")
        writeArtifact(root, KNOWN_ID, payload)
        val manager = manager(root)
        manager.resolveModel(KNOWN_ID)
        // Reproduce the app manager's last WorkManager progress event before SUCCEEDED.
        manager.javaClass.getDeclaredMethod("setDownloadState", String::class.java, DownloadState::class.java)
            .apply { isAccessible = true }
            .invoke(manager, KNOWN_ID, DownloadState.Verifying(payload.size.toLong(), payload.size.toLong()))
        manager.completeBackgroundDownload(KNOWN_ID)
        assertEquals(DownloadState.Installed, manager.downloadStates.value[KNOWN_ID])
        assertTrue(manager.resolveModel(KNOWN_ID)!!.verified)
    }

    @Test fun `completed background work with missing artifact reports failure`() = runBlocking {
        val manager = manager(tempRoot("background-missing"))
        manager.completeBackgroundDownload(KNOWN_ID)
        assertTrue(manager.downloadStates.value[KNOWN_ID] is DownloadState.Failed)
    }

    @Test
    fun `explicit refresh detects replacement even when size and timestamp match`() = runBlocking {
        val root = tempRoot("mutation")
        writeArtifact(root, KNOWN_ID, payload)
        val manager = manager(root)
        val descriptor = manager.resolveModel(KNOWN_ID)!!
        val file = File(descriptor.path)
        val modified = file.lastModified()
        file.writeBytes(unknownPayload)
        file.setLastModified(modified)
        manager.refresh(ModelInventoryRefreshReason.USER_REQUEST)
        assertFalse(manager.resolveModel(KNOWN_ID)!!.valid)
        assertNull(manager.verifyPhysicalArtifact(KNOWN_ID))
    }

    @Test fun `concurrent refresh cannot turn a resolved or verified snapshot into missing`() = runBlocking {
        val root = tempRoot("concurrent-refresh")
        writeArtifact(root, KNOWN_ID, payload)
        val manager = manager(root)
        val competing = async(Dispatchers.Default) {
            repeat(20) { manager.refresh(ModelInventoryRefreshReason.USER_REQUEST) }
        }
        repeat(20) {
            assertNotNull(manager.resolveModel(KNOWN_ID))
            assertTrue(manager.verifyPhysicalArtifact(KNOWN_ID)?.verified == true)
        }
        competing.await()
    }

    @Test
    fun `a known artifact with matching bytes and sha is VERIFIED_ARTIFACT`() {
        val root = tempRoot("verified")
        writeArtifact(root, KNOWN_ID, payload)
        val manager = manager(root)

        val descriptor = runBlocking { manager.resolveModel(KNOWN_ID) }

        assertNotNull(descriptor)
        assertEquals(LocalModelValidation.VERIFIED_ARTIFACT, descriptor!!.validation)
        assertTrue(descriptor.valid)
        assertTrue(descriptor.verified)
        assertEquals(payloadSha, descriptor.sha256)
        assertEquals(payload.size.toLong(), descriptor.bytes)
        assertEquals(4096, descriptor.contextWindowTokens)
        assertFalse(descriptor.useModelDefaultContext)
    }

    @Test
    fun `a known id with a stale sha is CORRUPT and not selectable`() {
        val root = tempRoot("corrupt")
        val stale = payload.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 0x5A).toByte() }
        writeArtifact(root, KNOWN_ID, stale)
        val manager = manager(root)

        val descriptor = runBlocking { manager.resolveModel(KNOWN_ID) }

        assertNotNull(descriptor)
        assertEquals(LocalModelValidation.CORRUPT, descriptor!!.validation)
        assertFalse(descriptor.valid)
        assertFalse(descriptor.verified)
        assertNotNull(descriptor.validationDetail)
    }

    @Test
    fun `a truncated known artifact is CORRUPT`() {
        val root = tempRoot("truncated")
        writeArtifact(root, KNOWN_ID, payload.copyOf(payload.size / 2))
        val manager = manager(root)

        assertEquals(LocalModelValidation.CORRUPT, runBlocking { manager.resolveModel(KNOWN_ID) }!!.validation)
    }

    @Test
    fun `a stale sidecar hash can never override the physical fingerprint`() {
        val root = tempRoot("stale-sidecar")
        val stale = payload.copyOf().also { it[0] = (it[0].toInt() xor 0x11).toByte() }
        writeArtifact(root, KNOWN_ID, stale)
        // Sidecar claims the *pinned* fingerprint while the file on disk no longer matches it.
        File(root, "$KNOWN_ID.halo-model.json").writeText(
            InstalledLiteRtModelMetadata(
                id = KNOWN_ID,
                displayName = "Qwen 3 0.6B Dynamic INT4",
                sourceUrl = null,
                sha256 = payloadSha,
                bytes = payload.size.toLong(),
                contextWindowTokens = 4096,
                contextKnown = true,
            ).toJson()
        )
        val manager = manager(root)

        val descriptor = runBlocking { manager.resolveModel(KNOWN_ID) }!!
        assertEquals(LocalModelValidation.CORRUPT, descriptor.validation)
        assertEquals(sha256(stale), descriptor.sha256)
    }

    @Test
    fun `an unknown artifact is UNVERIFIED and uses the model default context`() {
        val root = tempRoot("unknown")
        writeArtifact(root, "some_random_import", unknownPayload)
        val manager = manager(root)

        val descriptor = runBlocking { manager.resolveModel("some_random_import") }!!

        assertEquals(LocalModelValidation.UNVERIFIED, descriptor.validation)
        // Usable, but explicitly not proven.
        assertTrue(descriptor.valid)
        assertFalse(descriptor.verified)
        assertTrue(descriptor.useModelDefaultContext)
        assertNotNull(descriptor.validationDetail)
    }

    @Test
    fun `an unknown artifact whose sidecar disagrees with its bytes is CORRUPT`() {
        val root = tempRoot("unknown-sidecar")
        writeArtifact(root, "some_random_import", unknownPayload)
        File(root, "some_random_import.halo-model.json").writeText(
            InstalledLiteRtModelMetadata(
                id = "some_random_import",
                displayName = "Some Import",
                sourceUrl = null,
                sha256 = "b".repeat(64),
                bytes = 42L,
            ).toJson()
        )
        val manager = manager(root)

        assertEquals(
            LocalModelValidation.CORRUPT,
            runBlocking { manager.resolveModel("some_random_import") }!!.validation,
        )
    }

    @Test
    fun `an empty file is INCOMPATIBLE rather than valid`() {
        val root = tempRoot("empty")
        File(root, "broken.litertlm").writeBytes(ByteArray(0))
        val manager = manager(root)

        val descriptor = runBlocking { manager.resolveModel("broken") }!!
        assertEquals(LocalModelValidation.INCOMPATIBLE, descriptor.validation)
        assertFalse(descriptor.valid)
    }

    @Test
    fun `a byte-identical import is canonicalized to the known artifact id`() {
        val root = tempRoot("canonical")
        writeArtifact(root, "Qwen3_0_6B_dynamic_03e7da1e", payload)
        val manager = manager(root)

        val canonical = runBlocking { manager.resolveModel(KNOWN_ID) }
        assertNotNull("expected canonicalization to the catalog id", canonical)
        assertEquals(LocalModelValidation.VERIFIED_ARTIFACT, canonical!!.validation)
        assertEquals("$KNOWN_ID.litertlm", File(canonical.path).name)
        assertNull(runBlocking { manager.resolveModel("Qwen3_0_6B_dynamic_03e7da1e") })
    }

    @Test
    fun `resolveModel refreshes instead of reporting MODEL_MISSING during startup`() {
        val root = tempRoot("race")
        writeArtifact(root, KNOWN_ID, payload)
        // Fresh manager: the constructor's scan may not have completed yet.
        val manager = manager(root)

        val descriptor = runBlocking { manager.resolveModel(KNOWN_ID) }
        assertNotNull(descriptor)
        assertEquals(LocalModelValidation.VERIFIED_ARTIFACT, descriptor!!.validation)
    }

    // ---------------------------------------------------------------------------------------

    private fun manager(root: File) = LiteRtModelManager(
        context = context,
        modelRoot = root,
        downloader = OkHttpLiteRtModelDownloader(client = okhttp3.OkHttpClient(), resolveUrl = { "http://127.0.0.1:1/unused" }),
        catalog = listOf(catalogEntry()),
    )

    private fun catalogEntry() = LiteRtCatalogModel(
        id = KNOWN_ID,
        displayName = "Test Artifact",
        repoId = "halo/test-artifact",
        revision = "a3c5d805ae362dff7f580bc25f2dfb9a5a7eaa76",
        filename = "test-artifact.litertlm",
        expectedBytes = payload.size.toLong(),
        sha256 = payloadSha,
        contextWindowTokens = 4096,
        supportsVision = false,
        supportsTools = false,
        minLiteRtLmVersion = "0.17.1",
    )

    private fun writeArtifact(root: File, id: String, bytes: ByteArray) {
        File(root, "$id.litertlm").writeBytes(bytes)
    }

    private fun tempRoot(name: String): File =
        File(context.cacheDir, "litert-inventory-$name-${System.nanoTime()}").also {
            it.deleteRecursively()
            it.mkdirs()
        }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val KNOWN_ID = "test-artifact"
    }
}
