package com.halo.runtime.cognition.tools

import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import org.junit.Assert.*
import org.junit.Test

/** Deferred source regression: template validation shares the dispatch schema, without dispatch. */
class ToolTemplateValidationTest {
    private val definition = ToolDefinition("rename", "rename", "Rename", mapOf(
        "type" to "object", "properties" to mapOf(
            "id" to mapOf("type" to "string"),
            "revision" to mapOf("type" to "integer", "minimum" to 1),
            "title" to mapOf("type" to "string", "maxLength" to 8)),
        "required" to listOf("id", "revision", "title"), "additionalProperties" to false))
    private val registry = ToolRegistry(listOf(object : ToolExecutor {
        override fun definitions() = listOf(definition)
        override suspend fun execute(toolCall: ToolCall): ToolExecutionResult = error("Validation cannot execute")
    }))

    @Test fun validatesLiteralsWithoutInventingInputValues() {
        val template = ToolCall("template", "rename", mapOf("title" to "Name"))
        assertNull(registry.validateTemplate(template, mapOf("id" to "string", "revision" to "integer")))
        assertNotNull(registry.validate(template))
        assertNotNull(registry.validateTemplate(template.copy(arguments = mapOf("title" to "Too long a title")),
            mapOf("id" to "string", "revision" to "integer")))
    }

    @Test fun rejectsMissingWrongUnknownOrOverlappingInputs() {
        val template = ToolCall("template", "rename", mapOf("title" to "Name"))
        assertNotNull(registry.validateTemplate(template, mapOf("id" to "string")))
        assertNotNull(registry.validateTemplate(template, mapOf("id" to "string", "revision" to "string")))
        assertNotNull(registry.validateTemplate(template, mapOf("id" to "string", "revision" to "integer", "extra" to "string")))
        assertNotNull(registry.validateTemplate(template, mapOf("id" to "string", "revision" to "integer", "title" to "string")))
    }

    private fun numericRegistry(property: Map<String, Any>): ToolRegistry = ToolRegistry(listOf(object : ToolExecutor {
        override fun definitions() = listOf(ToolDefinition("bounded", "bounded", "Exact argument validation", mapOf(
            "type" to "object", "properties" to mapOf("revision" to property),
            "required" to listOf("revision"), "additionalProperties" to false)))
        override suspend fun execute(toolCall: ToolCall): ToolExecutionResult = error("Must not dispatch")
    }))

    @Test fun integerMaximumRejectsLongImmediatelyBeyondDoublePrecision() {
        val lastAllowed = 9_007_199_254_740_992L
        val registry = numericRegistry(mapOf("type" to "integer", "maximum" to lastAllowed))
        assertNull(registry.validate(ToolCall("allowed", "bounded", mapOf("revision" to lastAllowed))))
        assertNotNull(registry.validate(ToolCall("rounded-up", "bounded", mapOf("revision" to lastAllowed + 1L))))
    }

    @Test fun integerMinimumRejectsLongJustBelowLargeThreshold() {
        val min = 9_007_199_254_740_993L
        val registry = numericRegistry(mapOf("type" to "integer", "minimum" to min))
        assertNull(registry.validate(ToolCall("allowed", "bounded", mapOf("revision" to min))))
        assertNotNull(registry.validate(ToolCall("rounded-down", "bounded", mapOf("revision" to min - 1L))))
    }

    @Test fun longMaximumRetainsExactExclusiveNextValueAndDoesNotOverflow() {
        val max = Long.MAX_VALUE - 1L
        val registry = numericRegistry(mapOf("type" to "integer", "maximum" to max))
        assertNull(registry.validate(ToolCall("allowed", "bounded", mapOf("revision" to max))))
        assertNotNull(registry.validate(ToolCall("excess", "bounded", mapOf("revision" to Long.MAX_VALUE))))
    }

    @Test fun fractionalAndNonFiniteNumbersRemainRejected() {
        val registry = numericRegistry(mapOf("type" to "integer", "minimum" to 1L, "maximum" to 100L))
        assertNull(registry.validate(ToolCall("whole", "bounded", mapOf("revision" to 3.0))))
        for (invalid in listOf(3.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertNotNull(registry.validate(ToolCall("invalid", "bounded", mapOf("revision" to invalid))))
        }
    }

    @Test fun boundIntegerTemplateStillNeedsExactValidationAtUse() {
        val max = 9_007_199_254_740_992L
        val registry = numericRegistry(mapOf("type" to "integer", "maximum" to max))
        assertNull(registry.validateTemplate(ToolCall("reviewed", "bounded", emptyMap()), mapOf("revision" to "integer")))
        assertNotNull(registry.validate(ToolCall("bound", "bounded", mapOf("revision" to max + 1))))
    }

    @Test fun boundValuesStillNeedOrdinaryValidation() {
        assertNotNull(registry.validate(ToolCall("bound", "rename", mapOf("id" to "note", "revision" to 0, "title" to "Name"))))
        assertNull(registry.validate(ToolCall("bound", "rename", mapOf("id" to "note", "revision" to 1, "title" to "Name"))))
    }
}
