package com.halo.app.ui.screens.settings

import com.halo.providers.litert.models.*
import org.junit.Assert.*
import org.junit.Test

class LocalModelBrowseTest {
    @Test fun capabilityFiltersComposeWithSearchAndDoNotInventToolSupport() {
        val models = LiteRtModelCatalog.BUILT_IN_MODELS
        assertTrue(browseLocalModels(models, "", LocalModelUse.AUDIO).isNotEmpty())
        assertTrue(browseLocalModels(models, "", LocalModelUse.AUDIO).all { it.supportsAudio })
        assertTrue(browseLocalModels(models, "", LocalModelUse.TEXT).all { !it.supportsAudio && !it.supportsVision })
        assertTrue(browseLocalModels(models, "gemma", LocalModelUse.VISION).all { it.supportsVision && it.displayName.contains("gemma", true) })
        val promptTools = browseLocalModels(models, "qwen3-0.6b-dynamic-int4", LocalModelUse.TOOLS)
        assertEquals(listOf(LiteRtModelCatalog.QWEN3_0_6B_DYNAMIC_INT4), promptTools)
        assertFalse(promptTools.single().supportsTools)
        assertNotEquals(LiteRtToolCallPreference.NONE, promptTools.single().toolCallPreference)
    }
    @Test fun embeddingFilterIsNotTextOrToolFilter() {
        val chat = LiteRtModelCatalog.QWEN3_0_6B_DYNAMIC_INT4
        val embedding = chat.copy(id="embedding-preview", filename="embedding-preview.litertlm",
            sha256="b".repeat(64), task=LiteRtArtifactTask.EMBEDDING)
        val source = listOf(chat, embedding)
        assertEquals(listOf(embedding), browseLocalModels(source, "", LocalModelUse.EMBEDDINGS))
        assertEquals(listOf(chat), browseLocalModels(source, "", LocalModelUse.TEXT))
        assertEquals(2, browseLocalModels(source, "", LocalModelUse.ALL).size)
    }

    @Test fun deletedOrDamagedModelsCannotUseHistoricalGenerationEvidence() {
        assertFalse(localGenerationIsCurrent(null, true))
        val model = LocalModelDescriptor("id", "Model", "path", 1, 1, null, 4096, false, false, LocalModelValidation.CORRUPT)
        assertFalse(localGenerationIsCurrent(model, true))
        assertFalse(localGenerationIsCurrent(model.copy(validation = LocalModelValidation.VERIFIED_ARTIFACT), false))
        assertTrue(localGenerationIsCurrent(model.copy(validation = LocalModelValidation.VERIFIED_ARTIFACT), true))
    }
}
