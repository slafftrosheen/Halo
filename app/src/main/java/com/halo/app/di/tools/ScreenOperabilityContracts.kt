package com.halo.app.di.tools

import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolDefinition

/** Domain metadata for central registration; never grants policy or source access. */
object ScreenOperabilityContracts {
    val readTools = setOf("halo_screen_state", "halo_screen_preview")
    val mutationTools = setOf("halo_screen_apply", "halo_screen_settings", "halo_screen_undo",
        "halo_screen_save_preset", "halo_screen_remove_preset")

    private fun text(max: Int) = mapOf("type" to "string", "maxLength" to max)
    private val boolean = mapOf("type" to "boolean")
    private val integer = mapOf("type" to "integer", "minimum" to 0, "maximum" to Int.MAX_VALUE)
    private fun record(fields: Map<String, Any>) = mapOf("type" to "object", "properties" to fields,
        "required" to fields.keys.toList(), "additionalProperties" to false)
    private val layout = record(mapOf("name" to text(64), "version" to integer,
        "appearance" to record(mapOf("font" to text(16), "background" to text(16), "translucent" to boolean)),
        "cards" to mapOf("type" to "array", "maxItems" to 24, "items" to record(mapOf(
            "index" to integer, "widgetType" to text(32), "size" to text(16), "pinned" to boolean,
            "archived" to boolean, "hasSource" to boolean, "hasConfiguration" to boolean,
            "typography" to record(mapOf("titlePercent" to integer, "valuePercent" to integer, "clockPercent" to integer)),
            "placement" to mapOf("anyOf" to listOf(record(mapOf("column" to integer, "row" to integer,
                "width" to integer, "height" to integer)), mapOf("type" to "null"))))))))
    private val resultFields: Map<String, Any> = mapOf(
        "revision" to (text(64) + mapOf("pattern" to "^[a-f0-9]{64}$")),
        "previewToken" to (text(64) + mapOf("pattern" to "^[a-f0-9]{64}$")),
        "preview" to layout, "layout" to mapOf("anyOf" to listOf(layout, mapOf("type" to "null"))),
        "settings" to record(mapOf("hidePrivate" to boolean, "keepCharging" to boolean, "dimAtNight" to boolean, "topics" to text(1000))),
        "canUndo" to boolean, "applied" to boolean, "persisted" to boolean, "changed" to boolean,
        "nativeWidgets" to mapOf("type" to "array", "maxItems" to 11, "items" to text(32)),
        "presets" to mapOf("type" to "array", "maxItems" to 12, "items" to record(mapOf(
            "presetId" to text(100), "name" to text(64), "cardCount" to integer))))

    fun forDefinition(definition: ToolDefinition): ToolOperability? {
        if (definition.name !in readTools + mutationTools) return null
        val preview = definition.name == "halo_screen_preview"
        val fields = if (preview) mapOf("revision" to "string", "previewToken" to "string", "preview" to "object", "applied" to "boolean")
            else mapOf("revision" to "string", "layout" to listOf("object", "null"), "settings" to "object",
                "canUndo" to "boolean", "nativeWidgets" to "array", "presets" to "array", "applied" to "boolean", "persisted" to "boolean", "changed" to "boolean")
        val required = if (preview) listOf("revision", "previewToken", "preview", "applied")
            else listOf("revision", "layout", "settings", "canUndo", "nativeWidgets", "presets") +
                if (definition.name in mutationTools) listOf("applied", "persisted", "changed") else emptyList()
        return ToolOperability(
            // Reuse the already published local workspace capabilities. Screen is
            // presentation configuration, not a new device/runtime capability.
            capabilityId = CapabilityId(if (definition.name in readTools) "workspace.read" else "workspace.write"),
            owner = "LocalScreenStore", operation = if (definition.name in readTools) ToolOperation.QUERY
                else if (definition.name == "halo_screen_save_preset") ToolOperation.CREATE else ToolOperation.EDIT,
            resultEncoding = ToolResultEncoding.JSON,
            resultSchema = mapOf("type" to "object", "properties" to fields.keys.associateWith { resultFields.getValue(it) },
                "required" to required, "additionalProperties" to false),
            sourceVersionArguments = if (definition.name == "halo_screen_state") emptySet() else setOf("revision"),
            resultIdFields = emptySet(),
            limitations = setOf("Configuration only; no source bodies, IDs, citations or arbitrary config returned.",
                "Typed and STT-final intent use the registered executor; real speech and rendering remain separate acceptance.",
                "Screen metadata/topics/preset names require the existing personal-source dispatch policy.",
                "No OS/display/microphone/source/provider permission or Full Power change.",
                "Settings require existing durable runtime approval; privacy weakening also requires runtime-scoped interaction context.",
                "Mutation descriptors must use NEVER_REPLAY; preview tokens are integrity checks, not authority."),
        )
    }
}
