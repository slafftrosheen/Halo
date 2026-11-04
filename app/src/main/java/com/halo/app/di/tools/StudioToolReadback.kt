package com.halo.app.di.tools

import com.halo.app.data.studio.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/** Projections of the canonical document, each fitting the Runtime's 4,000-character envelope. */
internal object StudioToolReadback {
    val views = listOf("document", "layers", "layer", "text", "effects", "capabilities", "versions")

    fun summary(project: StudioProject): JSONObject = JSONObject()
        .put("projectId", project.id).put("revision", project.revision)
        .put("selectedLayerId", project.session.current.selectedLayerId ?: JSONObject.NULL)
        .put("title", project.title).put("width", project.session.current.width).put("height", project.session.current.height)
        .put("layerCount", project.session.current.layers.size).put("localDraft", project.hasUnsavedChanges)
        .put("canUndo", project.session.undo.isNotEmpty()).put("canRedo", project.session.redo.isNotEmpty())

    suspend fun list(studio: StudioService, args: Map<String, Any?>): JSONObject {
        val query = if ("query" in args) (args["query"] as? String ?: error("Expected query")).also { require(it.length <= 200) } else ""
        val offset = integer(args, "offset", 0, 0, Int.MAX_VALUE)
        val limit = integer(args, "limit", 4, 1, 4)
        val rows = studio.projects.observeProjects().first().filter { it.title.contains(query, ignoreCase = true) }
        val page = rows.drop(offset).take(limit)
        val values = JSONArray()
        page.forEach { row -> studio.projects.get(row.id)?.let { project ->
            values.put(summary(project).put("title", project.title.take(40)).put("titleTruncated", project.title.length > 40))
        } }
        val more = offset.toLong() + page.size < rows.size
        return JSONObject().put("projects", values).put("more", more)
            .put("nextProjectOffset", if (more) offset + page.size else JSONObject.NULL)
    }

    suspend fun get(studio: StudioService, args: Map<String, Any?>): JSONObject {
        val project = requireNotNull(studio.projects.get(id(args, "projectId")))
        val document = project.session.current
        val view = if ("view" in args) args["view"] as? String ?: error("Expected view")
            else if ("layerId" in args) "layer" else if ("offset" in args || "limit" in args) "layers" else "document"
        require(view in views)
        // The first document read discovers the revision; every subsequent projection
        // is pinned, so pagination cannot silently combine different document states.
        require(view == "document" || "revision" in args)
        if ("revision" in args) {
            val revision = args["revision"] as? Number ?: error("Expected revision")
            require(revision.toDouble() == project.revision.toDouble())
        }
        require("sample" !in args || view == "document" && "revision" in args)
        require("offset" !in args && "limit" !in args || view == "layers")
        require("textOffset" !in args && "textLimit" !in args || view == "text")
        require(("layerId" in args) == (view in setOf("layer", "text", "effects")))
        val layer = if ("layerId" in args) requireNotNull(document.layers.find { it.id == id(args, "layerId") }) else null
        val result = JSONObject().put("projectId", project.id).put("revision", project.revision).put("view", view)
        when (view) {
            "document" -> {
                val metadata = summary(project)
                metadata.keys().forEach { key -> result.put(key, metadata.get(key)) }
                result.put("availableViews", JSONArray(views)).put("background", document.background)
                    .put("recipe", JSONObject().put("quarterTurns", document.recipe.quarterTurns)
                        .put("mirror", document.recipe.mirrorHorizontal).put("squareCrop", document.recipe.squareCrop)
                        .put("maxEdge", document.recipe.maxEdge).put("format", document.recipe.format.name)
                        .put("crop", document.recipe.crop?.let { rect(StudioRect(it.x, it.y, it.width, it.height)) } ?: JSONObject.NULL))
                studio.selectedInk(project.id)?.takeIf { it.projectRevision == project.revision }?.let { selected ->
                    result.put("inkSelection", JSONObject().put("layerId", selected.layerId).put("projectRevision", selected.projectRevision)
                        .put("sourceRevision", selected.selection.sourceRevision).put("strokeCount", selected.selection.selectedStrokeIds.size)
                        .put("bounds", JSONObject().put("left", selected.selection.bounds.left).put("top", selected.selection.bounds.top)
                            .put("right", selected.selection.bounds.right).put("bottom", selected.selection.bounds.bottom)))
                }
                if ("sample" in args) sample(studio, project, args.getValue("sample"), result)
            }
            "layers" -> {
                val offset = integer(args, "offset", 0, 0, StudioDocument.MAX_LAYERS)
                val limit = integer(args, "limit", 4, 1, 4)
                val page = document.layers.drop(offset).take(limit)
                val more = offset + page.size < document.layers.size
                result.put("layers", JSONArray().apply { page.forEach { row ->
                    put(JSONObject().put("id", row.id).put("name", row.name.take(40)).put("nameTruncated", row.name.length > 40)
                        .put("kind", row.kind.name).put("visible", row.visible).put("locked", row.locked)
                        .put("editable", document.isEditable(row)).put("parentId", row.parentId ?: JSONObject.NULL))
                } }).put("layersTruncated", more).put("nextLayerOffset", if (more) offset + page.size else JSONObject.NULL)
            }
            "layer" -> result.put("layer", properties(requireNotNull(layer), document))
            "text" -> {
                val row = requireNotNull(layer)
                val offset = integer(args, "textOffset", 0, 0, row.text.length)
                val limit = integer(args, "textLimit", 384, 2, 384)
                require(offset == 0 || offset == row.text.length || !Character.isSurrogatePair(row.text[offset - 1], row.text[offset]))
                var end = (offset + limit).coerceAtMost(row.text.length)
                if (end < row.text.length && end > offset && Character.isSurrogatePair(row.text[end - 1], row.text[end])) end--
                result.put("layerId", row.id).put("text", row.text.substring(offset, end)).put("textOffset", offset)
                    .put("textLength", row.text.length).put("nextTextOffset", if (end < row.text.length) end else JSONObject.NULL)
            }
            "effects" -> {
                val row = requireNotNull(layer)
                result.put("layerId", row.id).put("effects", JSONArray().apply { row.effects.forEach { put(StudioCreativeCodec.effect(it)) } })
            }
            "capabilities" -> {
                result.put("brushes", JSONArray().apply { StudioCreativePresets.brushes.forEach { put(JSONObject().put("id", it.id.name).put("width", it.width).put("opacity", it.opacity)) } })
                    .put("palette", JSONArray(StudioCreativePresets.palette))
                    .put("styles", JSONArray().apply { StudioCreativePresets.styles.forEach { put(JSONObject().put("id", it.id).put("color", it.color).put("opacity", it.opacity).put("typography", StudioCreativeCodec.typography(it.typography))) } })
                    .put("supportedEffects", JSONArray(StudioEffectKind.entries.map { it.name }))
                    .put("supportedBlends", JSONArray(StudioBlend.entries.map { it.name }))
            }
            "versions" -> result.put("versions", JSONArray(studio.projects.versions(project.id).map { it.revision }))
        }
        check(studio.projects.get(project.id)?.revision == project.revision) { "Project changed during readback" }
        return result
    }

    private fun properties(layer: StudioLayer, document: StudioDocument): JSONObject = JSONObject()
        .put("id", layer.id).put("name", layer.name).put("kind", layer.kind.name)
        .put("visible", layer.visible).put("locked", layer.locked).put("opacity", layer.opacity).put("rotation", layer.rotation)
        .put("color", layer.color).put("fontSize", layer.fontSize).put("strokeWidth", layer.strokeWidth)
        .put("brightness", layer.brightness).put("contrast", layer.contrast).put("saturation", layer.saturation)
        .put("bounds", rect(layer.bounds)).put("textLength", layer.text.length).put("effectCount", layer.effects.size)
        .put("parentId", layer.parentId ?: JSONObject.NULL).put("blend", layer.blend.name).put("editable", document.isEditable(layer))
        .put("typography", StudioCreativeCodec.typography(layer.typography))
        .put("imageCrop", layer.imageCrop?.let(::rect) ?: JSONObject.NULL).put("mask", layer.mask?.let(::rect) ?: JSONObject.NULL)
        .put("texture", layer.texture?.name ?: JSONObject.NULL).put("textureSeed", layer.textureSeed).put("strokeOnly", layer.strokeOnly)
        .put("sourceId", layer.source?.objectId ?: JSONObject.NULL).put("sourceVersion", layer.source?.version ?: JSONObject.NULL)

    private fun rect(value: StudioRect) = JSONObject().put("x", value.x).put("y", value.y).put("width", value.width).put("height", value.height)
    private fun id(args: Map<String, Any?>, key: String) = (args[key] as? String ?: error("Expected ID"))
        .also { require(it.matches(Regex("[A-Za-z0-9_-]{1,100}"))) }
    private fun integer(args: Map<String, Any?>, key: String, default: Int, min: Int, max: Int): Int {
        if (key !in args) return default
        val value = args[key] as? Number ?: error("Expected integer")
        val number = value.toDouble()
        require(number.isFinite() && number % 1 == 0.0 && number in min.toDouble()..max.toDouble())
        return number.toInt()
    }
    private suspend fun sample(studio: StudioService, project: StudioProject, value: Any?, result: JSONObject) {
        require(value is Map<*, *> && value.keys == setOf("x", "y"))
        fun coordinate(key: String): Float {
            val number = (value[key] as? Number ?: error("Expected coordinate")).toDouble()
            require(number.isFinite() && number % 1 == 0.0 && number in 0.0..1000.0)
            return number.toFloat()
        }
        val point = StudioCanvasGeometry.forward(project.session.current, coordinate("x"), coordinate("y"))
        require(point.first in 0f..1f && point.second in 0f..1f)
        val bitmap = studio.preview(project.id, project.revision)
        try { result.put("sampleColor", bitmap.getPixel((point.first * bitmap.width).toInt().coerceIn(0, bitmap.width - 1), (point.second * bitmap.height).toInt().coerceIn(0, bitmap.height - 1))) }
        finally { bitmap.recycle() }
        check(studio.projects.get(project.id)?.revision == project.revision) { "Project changed during sampling" }
        studio.validatePinnedSources(project.session.current)
    }
}
