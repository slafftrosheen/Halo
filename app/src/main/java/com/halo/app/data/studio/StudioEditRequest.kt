package com.halo.app.data.studio

import com.halo.app.data.workspace.CanvasImageCrop
import com.halo.app.data.workspace.CanvasImageFormat

sealed interface StudioEditRequest {
    data class Command(val value: StudioCommand) : StudioEditRequest
    data object Undo : StudioEditRequest
    data object Redo : StudioEditRequest
    data class Restore(val revision: Long) : StudioEditRequest
    data class Import(val objectId: String, val sourceVersion: String) : StudioEditRequest
}

/** Closed data-only editing vocabulary. No scripts, paths, reflection or external URLs. */
object StudioEditParser {
    fun parse(document: StudioDocument, operation: String, fields: Map<String, Any?>): StudioEditRequest {
        fun only(vararg keys: String) = require(fields.keys.all { it in keys }) { "Unexpected edit field" }
        fun layer(): StudioLayer = requireNotNull(document.layers.find { it.id == fields.text("layerId") }) { "Layer unavailable" }
        val command: StudioCommand = when (operation) {
            "undo" -> { only(); return StudioEditRequest.Undo }
            "redo" -> { only(); return StudioEditRequest.Redo }
            "restore_version" -> { only("version"); return StudioEditRequest.Restore(fields.long("version")) }
            "import_image" -> { only("objectId", "sourceVersion"); return StudioEditRequest.Import(fields.text("objectId"), fields.text("sourceVersion").also { require(it.length in 1..200) }) }
            "add_text", "add_rectangle", "add_ellipse", "add_ink" -> {
                only("name", "text", "color", "bounds", "ink")
                val kind = when (operation) {
                    "add_text" -> StudioLayerKind.TEXT
                    "add_rectangle" -> StudioLayerKind.RECTANGLE
                    "add_ellipse" -> StudioLayerKind.ELLIPSE
                    else -> StudioLayerKind.INK
                }
                if (kind != StudioLayerKind.TEXT) require("text" !in fields)
                if (kind != StudioLayerKind.INK) require("ink" !in fields)
                StudioCommand.Add(StudioLayer(name = fields.text("name"), kind = kind,
                    bounds = fields["bounds"]?.let(::rect) ?: StudioRect(),
                    color = fields["color"]?.let(::color) ?: 0xff18203a.toInt(),
                    text = if (kind == StudioLayerKind.TEXT) fields.text("text") else "",
                    ink = if (kind == StudioLayerKind.INK) fields.text("ink") else ""))
            }
            "update_layer" -> {
                only("layerId", "changes")
                val current = layer()
                val changes = fields["changes"].objectFields()
                require(changes.isNotEmpty() && changes.keys.all { it in setOf("name", "visible", "locked", "opacity", "rotation",
                    "color", "text", "fontSize", "strokeWidth", "mask", "bounds", "brightness", "contrast", "saturation") })
                StudioCommand.Replace(current.copy(
                    name = if ("name" in changes) changes.text("name") else current.name,
                    visible = if ("visible" in changes) changes.flag("visible") else current.visible,
                    locked = if ("locked" in changes) changes.flag("locked") else current.locked,
                    opacity = if ("opacity" in changes) changes.integer("opacity") else current.opacity,
                    rotation = if ("rotation" in changes) changes.integer("rotation") else current.rotation,
                    color = if ("color" in changes) color(changes["color"]) else current.color,
                    text = if ("text" in changes) changes.text("text") else current.text,
                    fontSize = if ("fontSize" in changes) changes.integer("fontSize") else current.fontSize,
                    strokeWidth = if ("strokeWidth" in changes) changes.integer("strokeWidth") else current.strokeWidth,
                    bounds = if ("bounds" in changes) rect(changes["bounds"]) else current.bounds,
                    mask = if ("mask" in changes) changes["mask"]?.let(::rect) else current.mask,
                    brightness = if ("brightness" in changes) changes.integer("brightness") else current.brightness,
                    contrast = if ("contrast" in changes) changes.integer("contrast") else current.contrast,
                    saturation = if ("saturation" in changes) changes.integer("saturation") else current.saturation))
            }
            "set_ink" -> {
                only("layerId", "ink")
                val current = layer(); require(current.kind == StudioLayerKind.INK)
                StudioCommand.Replace(current.copy(ink = fields.text("ink")))
            }
            "delete_layer" -> { only("layerId"); StudioCommand.Remove(fields.text("layerId")) }
            "move_layer" -> { only("layerId", "index"); StudioCommand.Move(fields.text("layerId"), fields.integer("index")) }
            "select_layer" -> { only("layerId"); StudioCommand.Select(fields["layerId"]?.let { require(it is String); it }) }
            "resize" -> { only("width", "height"); StudioCommand.Resize(fields.integer("width"), fields.integer("height")) }
            "background" -> { only("color"); StudioCommand.Background(color(fields["color"])) }
            "transform" -> {
                only("quarterTurns", "mirror", "squareCrop", "maxEdge", "format", "crop")
                require(fields.isNotEmpty())
                val old = document.recipe
                StudioCommand.Transform(old.copy(
                    quarterTurns = if ("quarterTurns" in fields) fields.integer("quarterTurns") else old.quarterTurns,
                    mirrorHorizontal = if ("mirror" in fields) fields.flag("mirror") else old.mirrorHorizontal,
                    squareCrop = if ("squareCrop" in fields) fields.flag("squareCrop") else old.squareCrop,
                    maxEdge = if ("maxEdge" in fields) fields.integer("maxEdge") else old.maxEdge,
                    format = if ("format" in fields) CanvasImageFormat.valueOf(fields.text("format")) else old.format,
                    crop = if ("crop" in fields) fields["crop"]?.let(::rect)?.let { CanvasImageCrop(it.x, it.y, it.width, it.height) } else old.crop))
            }
            else -> error("Unsupported Studio operation")
        }
        document.apply(command) // Validate before admitting a request to any persistence owner.
        return StudioEditRequest.Command(command)
    }
    private fun Any?.objectFields(): Map<String, Any?> {
        require(this is Map<*, *> && keys.all { it is String })
        return entries.associate { (key, value) -> key as String to value }
    }
    private fun rect(value: Any?): StudioRect {
        val fields = value.objectFields()
        require(fields.keys == setOf("x", "y", "width", "height"))
        return StudioRect(fields.integer("x"), fields.integer("y"), fields.integer("width"), fields.integer("height"))
    }
    private fun color(value: Any?): Int {
        require(value is String && value.matches(Regex("#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?")))
        return (if (value.length == 7) "ff" + value.drop(1) else value.drop(1)).toLong(16).toInt()
    }
    private fun Map<String, Any?>.text(key: String): String = (get(key) as? String) ?: error("Expected edit text")
    private fun Map<String, Any?>.flag(key: String): Boolean = (get(key) as? Boolean) ?: error("Expected edit flag")
    private fun Map<String, Any?>.integer(key: String): Int = long(key).also { require(it in Int.MIN_VALUE..Int.MAX_VALUE) }.toInt()
    private fun Map<String, Any?>.long(key: String): Long {
        val value = get(key); require(value is Number)
        val number = value.toDouble()
        require(number.isFinite() && number % 1.0 == 0.0 && number in -9_007_199_254_740_991.0..9_007_199_254_740_991.0)
        return number.toLong()
    }
}
