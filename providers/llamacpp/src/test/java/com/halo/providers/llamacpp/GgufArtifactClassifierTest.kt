package com.halo.providers.llamacpp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class GgufArtifactClassifierTest {

    @Test
    fun classifiesSmallTextModelAsBase() {
        assertEquals(
            GgufArtifactRole.BASE_MODEL,
            GgufArtifactClassifier.classifyRemote("Qwen3-0.6B-Q4_K_M.gguf")
        )
    }

    @Test
    fun classifiesMmprojAsProjector() {
        assertEquals(
            GgufArtifactRole.MULTIMODAL_PROJECTOR,
            GgufArtifactClassifier.classifyRemote("mmproj-F16.gguf")
        )
        assertEquals(
            GgufArtifactRole.MULTIMODAL_PROJECTOR,
            GgufArtifactClassifier.classifyRemote("ggml-model-Q4_K_M-mmproj-f16.gguf")
        )
    }

    @Test
    fun classifiesImatrixAsAuxiliary() {
        assertEquals(
            GgufArtifactRole.IMATRIX,
            GgufArtifactClassifier.classifyRemote("imatrix.gguf")
        )
        assertEquals(
            GgufArtifactRole.IMATRIX,
            GgufArtifactClassifier.classifyRemote("some_folder/imatrix.gguf")
        )
    }

    @Test
    fun classifiesMtpAuxiliaryAsAuxiliary() {
        assertEquals(
            GgufArtifactRole.MTP_AUXILIARY,
            GgufArtifactClassifier.classifyRemote("MTP_gemma-4-12B-it-MTP-Q8_0.gguf")
        )
    }

    @Test
    fun classifiesSplitShardAsShard() {
        assertEquals(
            GgufArtifactRole.SPLIT_MODEL_SHARD,
            GgufArtifactClassifier.classifyRemote("Qwen3-0.6B-Q4_K_M-00001-of-00004.gguf")
        )
    }

    @Test
    fun classifiesNonGgufAsUnknownAuxiliary() {
        assertEquals(
            GgufArtifactRole.UNKNOWN_AUXILIARY,
            GgufArtifactClassifier.classifyRemote("model.safetensors")
        )
    }

    @Test
    fun localFileDetectionMatchesRemoteClassification() {
        val dir = Files.createTempDirectory("halo-artifact-classifier-").toFile()
        try {
            val base = newFile(dir, "model-Q4_K_M.gguf")
            assertFalse(GgufArtifactClassifier.isKnownAuxiliaryLocalFile(base))
            assertEquals(
                GgufArtifactRole.BASE_MODEL,
                GgufArtifactClassifier.classifyLocalFile(base)
            )

            val imatrix = newFile(dir, "hf_bad_id_imatrix.gguf")
            assertTrue(GgufArtifactClassifier.isKnownAuxiliaryLocalFile(imatrix))
            assertEquals(
                GgufArtifactRole.IMATRIX,
                GgufArtifactClassifier.classifyLocalFile(imatrix)
            )

            val mtp = newFile(dir, "hf_bad_id_MTP_gemma-4-12B-it-MTP-Q8_0.gguf")
            assertTrue(GgufArtifactClassifier.isKnownAuxiliaryLocalFile(mtp))

            val mmproj = newFile(dir, "hf_bad_id_mmproj-F16.gguf")
            assertTrue(GgufArtifactClassifier.isKnownAuxiliaryLocalFile(mmproj))
            assertEquals(
                GgufArtifactRole.MULTIMODAL_PROJECTOR,
                GgufArtifactClassifier.classifyLocalFile(mmproj)
            )
            assertTrue(GgufArtifactClassifier.isKnownAuxiliaryLocalFile(mmproj))
            assertEquals(
                GgufArtifactRole.MULTIMODAL_PROJECTOR,
                GgufArtifactClassifier.classifyLocalFile(mmproj)
            )

            assertFalse(GgufArtifactClassifier.isKnownAuxiliaryLocalFile(File("does-not-exist.gguf")))
            assertTrue(GgufArtifactClassifier.isKnownAuxiliaryLocalFile(mmproj))
            assertEquals(
                GgufArtifactRole.MULTIMODAL_PROJECTOR,
                GgufArtifactClassifier.classifyLocalFile(mmproj)
            )
            assertTrue(GgufArtifactClassifier.isKnownAuxiliaryLocalFile(mmproj))
            assertEquals(
                GgufArtifactRole.MULTIMODAL_PROJECTOR,
                GgufArtifactClassifier.classifyLocalFile(mmproj)
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun invalidRoleDetectionExcludesBaseModel() {
        val baseRole = GgufArtifactRole.BASE_MODEL
        assertFalse(GgufArtifactClassifier.isDeterministicallyInvalidRole(baseRole))
        assertTrue(GgufArtifactClassifier.isDeterministicallyInvalidRole(GgufArtifactRole.IMATRIX))
        assertTrue(GgufArtifactClassifier.isDeterministicallyInvalidRole(GgufArtifactRole.MTP_AUXILIARY))
        assertTrue(GgufArtifactClassifier.isDeterministicallyInvalidRole(GgufArtifactRole.MULTIMODAL_PROJECTOR))
        assertTrue(GgufArtifactClassifier.isDeterministicallyInvalidRole(GgufArtifactRole.SPLIT_MODEL_SHARD))
        assertTrue(GgufArtifactClassifier.isDeterministicallyInvalidRole(GgufArtifactRole.UNKNOWN_AUXILIARY))
    }

    private fun newFile(dir: File, name: String): File {
        val file = File(dir, name)
        file.writeBytes(ByteArray(16))
        return file
    }
}
