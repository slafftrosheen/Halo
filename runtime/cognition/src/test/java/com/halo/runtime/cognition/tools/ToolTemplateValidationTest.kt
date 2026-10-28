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

    @Test fun boundValuesStillNeedOrdinaryValidation() {
        assertNotNull(registry.validate(ToolCall("bound", "rename", mapOf("id" to "note", "revision" to 0, "title" to "Name"))))
        assertNull(registry.validate(ToolCall("bound", "rename", mapOf("id" to "note", "revision" to 1, "title" to "Name"))))
    }
}
