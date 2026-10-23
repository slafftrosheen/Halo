package com.halo.runtime.cognition.tools

import com.halo.sdk.provider.ToolDefinition
import org.junit.Assert.*
import org.junit.Test

class ToolOperabilityReviewTest {
    private fun descriptor() = ToolDescriptor(ToolId("edit"), ToolDefinition("edit", "edit", "Edit an exact source",
        mapOf("type" to "object", "properties" to mapOf("version" to mapOf("type" to "string")), "additionalProperties" to false)), ToolCategory.WORKSPACE,
        security = ToolSecurityPolicy.REVERSIBLE.copy(replaySafety = ReplaySafety.NEVER_REPLAY),
        operability = ToolOperability(capabilityId = CapabilityId("canvas"), owner = "CanvasOwner", operation = ToolOperation.EDIT,
            resultEncoding = ToolResultEncoding.JSON, resultSchema = mapOf("type" to "object", "properties" to mapOf("id" to mapOf("type" to "string"))),
            sourceVersionArguments = setOf("version"), resultIdFields = setOf("id")))
    @Test fun actualRegisteredSchemasAreRequiredAndVoiceParityIsMetadataNotAnExecutionClaim() {
        val feature = listOf(ToolFeatureOperability("canvas", setOf("edit")))
        assertTrue(ToolOperabilityReview.inspect(feature, listOf(descriptor())).isEmpty())
        assertEquals("NOT_REGISTERED", ToolOperabilityReview.inspect(feature, emptyList()).single().code)
        val noVoice = descriptor().let { it.copy(operability = it.operability!!.copy(channels = setOf(ToolInputChannel.TEXT))) }
        assertTrue(ToolOperabilityReview.inspect(feature, listOf(noVoice)).any { it.code == "INPUT_PARITY_MISSING" })
        assertEquals("NO_CALLABLE_OPERATION", ToolOperabilityReview.inspect(listOf(ToolFeatureOperability("gesture", emptySet())), emptyList()).single().code)
        assertTrue(ToolOperabilityReview.inspect(listOf(ToolFeatureOperability("pen", emptySet(), "Physical pen input requires a person")), emptyList()).isEmpty())
    }
    @Test fun keyedReplayWithoutAnAcceptedKeyAndMissingVersionAreReviewFailures() {
        val feature = listOf(ToolFeatureOperability("canvas", setOf("edit")))
        val unsafe = descriptor().copy(security = ToolSecurityPolicy.REVERSIBLE.copy(replaySafety = ReplaySafety.IDEMPOTENT_WITH_KEY))
        assertTrue(ToolOperabilityReview.inspect(feature, listOf(unsafe)).any { it.code == "NO_REPLAY_KEY" })
        val changed = descriptor().let { it.copy(operability = it.operability!!.copy(sourceVersionArguments = setOf("missing"))) }
        assertTrue(ToolOperabilityReview.inspect(feature, listOf(changed)).any { it.code == "MISSING_SOURCE_VERSION_FIELD" })
    }
}
