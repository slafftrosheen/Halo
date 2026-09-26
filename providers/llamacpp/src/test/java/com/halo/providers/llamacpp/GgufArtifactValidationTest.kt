package com.halo.providers.llamacpp

import com.halo.providers.llamacpp.GgufArtifactClassifier.GgufArtifactClassificationInternal
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GgufArtifactValidationTest {

    @Test
    fun rejectsAuxiliaryRolesAsRunnableBase() {
        assertFalse(
            GgufArtifactValidation.isRunnableBaseArtifact(
                GgufArtifactRole.MULTIMODAL_PROJECTOR,
                GgufModelMetadata("mmproj", 0, 0, 0, null, null),
                "mmproj-f16.gguf"
            )
        )
        assertFalse(
            GgufArtifactValidation.isRunnableBaseArtifact(
                GgufArtifactRole.IMATRIX,
                null,
                "imatrix.gguf"
            )
        )
        assertFalse(
            GgufArtifactValidation.isRunnableBaseArtifact(
                GgufArtifactRole.MTP_AUXILIARY,
                null,
                "MTP_gemma-4-12B-it-MTP-Q8_0.gguf"
            )
        )
    }

    @Test
    fun acceptsBaseModelRoleAsRunnableCandidate() {
        val llama = GgufModelMetadata("llama", 4096, 32, 4096, 32, 8)
        assertTrue(
            GgufArtifactValidation.isRunnableBaseArtifact(
                GgufArtifactRole.BASE_MODEL,
                llama,
                "qwen3-0.6b-q4_k_m.gguf"
            )
        )
    }

    @Test
    fun baseModelWithoutArchitectureIsStillRunnableCandidate() {
        val metadata = GgufModelMetadata(null, 8192, 20, 2048, null, null)
        assertTrue(
            GgufArtifactValidation.isRunnableBaseArtifact(
                GgufArtifactRole.BASE_MODEL,
                metadata,
                "some-model-q4_0.gguf"
            )
        )
    }

    @Test
    fun rejectsBlankFilenameAsRunnableBase() {
        assertFalse(
            GgufArtifactValidation.isRunnableBaseArtifact(
                GgufArtifactRole.BASE_MODEL,
                null,
                ""
            )
        )
        assertFalse(
            GgufArtifactValidation.isRunnableBaseArtifact(
                GgufArtifactRole.BASE_MODEL,
                null,
                "not-a-gguf.bin"
            )
        )
    }

    @Test
    fun baseModelMetadataPlausibilityIsAdvisableNotAuthoritative() {
        assertTrue(
            GgufArtifactClassificationInternal.isBaseModelMetadataPlausible(
                GgufModelMetadata("llama", 4096, 32, 4096, 32, 8)
            )
        )
        assertFalse(
            GgufArtifactClassificationInternal.isBaseModelMetadataPlausible(
                GgufModelMetadata(null, null, null, null, null, null)
            )
        )
    }

    @Test
    fun completeArtifactDownloadStillDoesNotImplyRunnableBase() {
        val file = java.io.File.createTempFile("halo-complete-but-not-runnable-", ".gguf")
        try {
            file.writeBytes(ByteArray(100))
            assertFalse(
                GgufArtifactValidation.isRunnableBaseArtifact(
                    GgufArtifactRole.MULTIMODAL_PROJECTOR,
                    GgufModelMetadata("mmproj", null, null, null, null, null),
                    "mmproj-f16.gguf"
                )
            )
            assertTrue(
                GgufArtifactValidation.isRunnableBaseArtifact(
                    GgufArtifactRole.BASE_MODEL,
                    GgufModelMetadata("llama", 4096, 32, 4096, 32, 8),
                    "model-Q4_K_M.gguf"
                )
            )
        } finally {
            file.delete()
        }
    }
}
