package com.halo.providers.litert.models

import org.junit.Assert.*
import org.junit.Test

class LiteRtGenerationAdmissionTest {
    private val base = LiteRtModelCatalog.QWEN3_0_6B_DYNAMIC_INT4

    @Test fun chatAndVisionGeneratorsAreEligibleButNotAutomaticallyQualified() {
        assertTrue(LiteRtGenerationAdmission.permitted(base, base.filename))
        assertTrue(LiteRtGenerationAdmission.permitted(base.copy(supportsVision = true), base.filename))
    }

    @Test fun embeddingsCannotBeUsedAsTextGeneratorsOrAgentModels() {
        val model = base.copy(task = LiteRtArtifactTask.EMBEDDING)
        assertFalse(LiteRtGenerationAdmission.permitted(model, model.filename))
        assertTrue(LiteRtGenerationAdmission.explanation(model).contains("Embedding"))
    }

    @Test fun importedEmbeddingFilenameFailsClosedEvenWithoutCatalog() {
        assertFalse(LiteRtGenerationAdmission.permitted(null, "embeddinggemma-2-740m.litertlm"))
        assertFalse(LiteRtGenerationAdmission.permitted(null, "my-embedding-vector.litertlm"))
        assertTrue(LiteRtGenerationAdmission.permitted(null, "manually-imported-qwen3.litertlm"))
    }

    @Test fun nonGenerativeSpeechDoesNotEnterLiteRtConversation() {
        val model = base.copy(task = LiteRtArtifactTask.NON_GENERATIVE)
        assertFalse(LiteRtGenerationAdmission.permitted(model, model.filename))
    }
}
