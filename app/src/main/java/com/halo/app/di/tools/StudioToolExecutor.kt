package com.halo.app.di.tools

import com.halo.app.data.studio.*
import com.halo.app.data.workspace.CanvasImageFormat
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/** Data-only adapter onto the same local Studio commands used by touch controls. */
class StudioToolExecutor(private val studio: StudioService) : ToolExecutor {
    private val text = mapOf("type" to "string", "maxLength" to 120)
    private val id = mapOf("type" to "string", "pattern" to "^[A-Za-z0-9_-]{1,100}$")
    private val revision = mapOf("type" to "integer", "minimum" to 1, "maximum" to 9_007_199_254_740_991L)
    private fun schema(properties: Map<String, Any>, required: List<String> = properties.keys.toList()) =
        mapOf("type" to "object", "properties" to properties, "required" to required, "additionalProperties" to false)
    private fun definition(name: String, description: String, properties: Map<String, Any>, required: List<String> = properties.keys.toList()) =
        ToolDefinition(name, name, description, schema(properties, required))
    private val rect = schema(mapOf("x" to mapOf("type" to "integer", "minimum" to -1000, "maximum" to 2000),
        "y" to mapOf("type" to "integer", "minimum" to -1000, "maximum" to 2000),
        "width" to mapOf("type" to "integer", "minimum" to 1, "maximum" to 3000),
        "height" to mapOf("type" to "integer", "minimum" to 1, "maximum" to 3000)))
    private val changes: Map<String, Any> = mapOf(
        "name" to text, "text" to mapOf("type" to "string", "maxLength" to 2000),
        "color" to mapOf("type" to "string", "pattern" to "^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$"),
        "visible" to mapOf("type" to "boolean"), "locked" to mapOf("type" to "boolean"),
        "opacity" to mapOf("type" to "integer", "minimum" to 0, "maximum" to 1000),
        "rotation" to mapOf("type" to "integer", "minimum" to -360, "maximum" to 360),
        "fontSize" to mapOf("type" to "integer", "minimum" to 1, "maximum" to 1000),
        "strokeWidth" to mapOf("type" to "integer", "minimum" to 1, "maximum" to 100),
        "brightness" to mapOf("type" to "integer", "minimum" to -1000, "maximum" to 1000),
        "contrast" to mapOf("type" to "integer", "minimum" to 0, "maximum" to 3000),
        "saturation" to mapOf("type" to "integer", "minimum" to 0, "maximum" to 3000),
        "bounds" to rect, "mask" to mapOf("anyOf" to listOf(rect, mapOf("type" to "null"))),
        "imageCrop" to mapOf("anyOf" to listOf(rect,mapOf("type" to "null")))) + StudioCreativeToolSchemas.fields
    private val fields = schema(changes + mapOf(
        "layerId" to mapOf("anyOf" to listOf(id, mapOf("type" to "null"))),
        "changes" to schema(changes, emptyList()),
        "layerIds" to StudioCreativeToolSchemas.ids,"layout" to StudioCreativeToolSchemas.layout,"style" to StudioCreativeToolSchemas.style,
        "x" to mapOf("type" to "integer","minimum" to 0,"maximum" to 1000),"y" to mapOf("type" to "integer","minimum" to 0,"maximum" to 1000),
        "tolerance" to mapOf("type" to "integer","minimum" to 0,"maximum" to 255),
        "ink" to mapOf("type" to "string", "maxLength" to 262144),
        "index" to mapOf("type" to "integer", "minimum" to 0, "maximum" to 31),
        "objectId" to id, "version" to revision,
        "sourceVersion" to mapOf("type" to "string", "minLength" to 1, "maxLength" to 200),
        "width" to mapOf("type" to "integer", "minimum" to 1, "maximum" to 4096),
        "height" to mapOf("type" to "integer", "minimum" to 1, "maximum" to 4096),
        "quarterTurns" to mapOf("type" to "integer", "minimum" to 0, "maximum" to 3),
        "mirror" to mapOf("type" to "boolean"), "squareCrop" to mapOf("type" to "boolean"),
        "maxEdge" to mapOf("type" to "integer", "enum" to listOf(512, 1024, 2048, 4096)),
        "format" to mapOf("type" to "string", "enum" to listOf("PNG", "JPEG")),
        "crop" to mapOf("anyOf" to listOf(rect, mapOf("type" to "null")))), emptyList())
    override fun definitions() = listOf(
        definition("halo_studio_open", "Request presentation of an exact existing Studio project. Dispatch does not prove the page rendered.", mapOf("projectId" to id)),
        definition("halo_studio_ai_draft", "Request user review of an AI edit to an exact Studio revision. Opens the existing provider/privacy/spend review; does not generate or send pixels by itself. A kept result is a separate copy. Never infer provider billing from proposal status.",
            mapOf("projectId" to id, "revision" to revision, "prompt" to mapOf("type" to "string", "minLength" to 1, "maxLength" to 4000))),
        definition("halo_studio_status", "Read a transient Studio presentation/proposal receipt. UNKNOWN after process death or expiry of retained history; never replay an uncertain action. Proposal disposition does not confirm provider billing.", mapOf("requestId" to id)),
        definition("halo_studio_current", "Read the project currently attached to Halo Studio UI, if any. This is a Studio document reference, not proof of OS foreground or file contents.", emptyMap()),
        definition("halo_studio_list", "Find saved local Studio projects. Results contain IDs and current revisions, never pixels.",
            mapOf("query" to mapOf("type" to "string", "maxLength" to 200)), emptyList()),
        definition("halo_studio_create", "Create a separate local layered Studio project. Optional sourceId/sourceVersion together create a new project from that exact image; cannot combine with preset/dimensions. No provider or network. Originals remain unchanged.",
            mapOf("title" to text, "handoffId" to id, "sourceId" to id, "sourceVersion" to mapOf("type" to "string", "minLength" to 1, "maxLength" to 200), "width" to mapOf("type" to "integer", "minimum" to 1, "maximum" to 4096),
                "height" to mapOf("type" to "integer", "minimum" to 1, "maximum" to 4096),
                "preset" to mapOf("type" to "string", "enum" to StudioPreset.entries.map { it.name })), listOf("title")),
        definition("halo_studio_get", "Read Studio layer/effect/typography metadata, local presets and saved versions. Optional sample plus exact revision returns one locally rendered ARGB pixel at normalized project coordinates; no recognition or network. A sample outside the cropped artboard is rejected.",
            mapOf("projectId" to id,"revision" to revision,"layerId" to id,"offset" to mapOf("type" to "integer","minimum" to 0,"maximum" to 32),"limit" to mapOf("type" to "integer","minimum" to 1,"maximum" to 4),
                "sample" to schema(mapOf("x" to mapOf("type" to "integer","minimum" to 0,"maximum" to 1000),"y" to mapOf("type" to "integer","minimum" to 0,"maximum" to 1000)))),listOf("projectId")),
        definition("halo_studio_edit", "Edit the shared Design/Paint Studio draft at its exact revision. Bounds/mask/imageCrop use thousandths. Group/layout use layerIds (siblings), name or layout; duplicate/ungroup use layerId. update_layer supports typography, imageCrop, blend, effects and texture. Effects are editable bounded stacks with integer ARGB colours. Add texture requires name and texture. Locked ancestors fence child writes. Mode/viewport switching never edits the document. Undo/redo/restore preserve originals; never replay stale writes.",
            mapOf("projectId" to id, "revision" to revision, "operation" to mapOf("type" to "string", "enum" to listOf(
                "add_text", "add_rectangle", "add_ellipse", "add_ink", "set_ink", "update_layer", "delete_layer", "move_layer", "select_layer",
                "transform", "resize", "background", "undo", "redo", "restore_version", "import_image", "delete_selected_ink") + StudioCreativeToolSchemas.operations), "fields" to fields)),
        definition("halo_studio_save", "Save a local draft as a version, save a separate copy, rename, or discard draft changes. Copy/rename require title. Exact revision required.",
            mapOf("projectId" to id, "revision" to revision, "action" to mapOf("type" to "string", "enum" to listOf("save", "copy", "rename", "discard")),
                "title" to text), listOf("projectId", "revision", "action")),
        definition("halo_studio_export", "Render an exact local Studio draft into a new immutable Workspace PNG/JPEG with durable source/version metadata. Does not overwrite or automatically insert/share anything.",
            mapOf("projectId" to id, "revision" to revision, "format" to mapOf("type" to "string", "enum" to listOf("PNG", "JPEG")))),
        definition("halo_studio_insert", "Insert a previously exported exact Studio image on an exact current Canvas revision as a new reference. The managed Workspace copy remains authoritative.",
            mapOf("canvasId" to id, "canvasVersion" to mapOf("type" to "string", "maxLength" to 30), "outputId" to id, "fileVersion" to mapOf("type" to "string", "maxLength" to 200)))
    )
    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        fun rejected(kind: ToolRejectionKind = ToolRejectionKind.INVALID_ARGUMENTS) = ToolExecutionResult.Rejected(
            toolCall.id, toolCall.functionName, "Studio could not complete this action. Check the current project revision and source availability.", kind)
        fun success(value: JSONObject) = ToolExecutionResult.Success(toolCall.id, toolCall.functionName, value.toString())
        val args = toolCall.arguments
        fun exact(required: Set<String>, optional: Set<String> = emptySet()) {
            require(args.keys.containsAll(required) && args.keys.all { it in required || it in optional })
        }
        fun string(key: String): String = (args[key] as? String) ?: error("Expected text")
        fun objectId(key: String) = string(key).also { require(it.matches(Regex("[A-Za-z0-9_-]{1,100}"))) }
        fun number(key: String, default: Long? = null): Long {
            if (key !in args && default != null) return default
            val value = args[key]; require(value is Number)
            val numeric = value.toDouble()
            require(numeric.isFinite() && numeric % 1.0 == 0.0 && numeric in 1.0..9_007_199_254_740_991.0)
            return numeric.toLong()
        }
        try {
            when (toolCall.functionName) {
                "halo_studio_open", "halo_studio_ai_draft" -> {
                    val ai = toolCall.functionName == "halo_studio_ai_draft"
                    exact(if (ai) setOf("projectId", "revision", "prompt") else setOf("projectId"))
                    val project = objectId("projectId")
                    val request = if (ai) studio.requestAiReview(project, number("revision"), string("prompt")) else studio.requestOpen(project)
                    return ToolExecutionResult.Success(toolCall.id, toolCall.functionName,
                        JSONObject().put("requestId", request.id).put("state", "DISPATCHED").put("providerRequestSent", false).toString(),
                        com.halo.runtime.cognition.agent.ActionTruth.DISPATCHED)
                }
                "halo_studio_status" -> {
                    exact(setOf("requestId"))
                    val id = objectId("requestId"); val receipt = studio.uiRequests.receipt(id)
                    return success(JSONObject().put("requestId", id).put("state", receipt?.phase?.name ?: "UNKNOWN")
                        .put("projectId", receipt?.projectId ?: JSONObject.NULL).put("resultProjectId", receipt?.resultProjectId ?: JSONObject.NULL))
                }
                "halo_studio_current" -> {
                    exact(emptySet())
                    return success(studio.currentProject()?.let(::summary) ?: JSONObject().put("attached", false))
                }
                "halo_studio_list" -> {
                    exact(emptySet(), setOf("query"))
                    val query = if ("query" in args) string("query").also { require(it.length <= 200) } else ""
                    val rows = studio.projects.observeProjects().first().filter { it.title.contains(query, ignoreCase = true) }
                    val values = JSONArray()
                    rows.take(20).forEach { row -> studio.projects.get(row.id)?.let { values.put(summary(it)) } }
                    return success(JSONObject().put("projects", values).put("more", rows.size > 20))
                }
                "halo_studio_create" -> {
                    exact(setOf("title"), setOf("width", "height", "preset", "sourceId", "sourceVersion", "handoffId"))
                    if ("sourceId" in args || "sourceVersion" in args || "handoffId" in args) {
                        require(args.keys.containsAll(setOf("title", "sourceId", "sourceVersion")) && args.keys.all { it in setOf("title", "sourceId", "sourceVersion", "handoffId") })
                        return success(summary(studio.createFromImage(string("title"), objectId("sourceId"), string("sourceVersion"), if ("handoffId" in args) objectId("handoffId") else null)))
                    }
                    val title = string("title")
                    val document = if ("preset" in args) {
                        require("width" !in args && "height" !in args)
                        StudioTemplates.document(StudioPreset.valueOf(string("preset")), title)
                    } else {
                        val width = number("width", 1024); val height = number("height", 1024)
                        require(width <= 4096 && height <= 4096)
                        StudioDocument(width.toInt(), height.toInt())
                    }
                    return success(summary(studio.projects.create(title, document)))
                }
                "halo_studio_insert" -> {
                    exact(setOf("canvasId", "canvasVersion", "outputId", "fileVersion"))
                    val version = string("fileVersion").also { require(it.length in 1..200) }
                    val canvas = objectId("canvasId"); val output = objectId("outputId")
                    val node = studio.insertOutput(canvas, output, version, string("canvasVersion"))
                    return success(JSONObject().put("canvasId", canvas).put("outputId", output).put("nodeId", node).put("inserted", true))
                }
                "halo_studio_get" -> {
                    exact(setOf("projectId"),setOf("revision","sample","layerId","offset","limit")); val project = requireNotNull(studio.projects.get(objectId("projectId")))
                    require("sample" !in args||"revision" in args)
                    if("revision" in args) require(project.revision==number("revision"))
                    require("layerId" !in args || "offset" !in args&&"limit" !in args)
                    val result = summary(project)
                    if("sample" in args) {
                        require(project.revision==number("revision"))
                        val sample=args["sample"];require(sample is Map<*,*> && sample.keys==setOf("x","y"))
                        fun coordinate(key:String):Float {val v=sample[key];require(v is Number);val n=v.toDouble();require(n.isFinite()&&n%1==0.0&&n in 0.0..1000.0);return n.toFloat()}
                        val point=StudioCanvasGeometry.forward(project.session.current,coordinate("x"),coordinate("y"))
                        require(point.first in 0f..1f&&point.second in 0f..1f)
                        val bitmap=studio.preview(project.id,project.revision)
                        try { result.put("sampleColor",bitmap.getPixel((point.first*bitmap.width).toInt().coerceIn(0,bitmap.width-1),(point.second*bitmap.height).toInt().coerceIn(0,bitmap.height-1))) }
                        finally {bitmap.recycle()}
                        check(studio.projects.get(project.id)?.revision==project.revision) {"Project changed during sampling"}
                    }
                    result.put("brushes",JSONArray().apply {StudioCreativePresets.brushes.forEach {put(JSONObject().put("id",it.id.name).put("width",it.width).put("opacity",it.opacity))}})
                    result.put("palette",JSONArray(StudioCreativePresets.palette))
                    result.put("styles",JSONArray().apply {StudioCreativePresets.styles.forEach {put(JSONObject().put("id",it.id).put("color",it.color).put("opacity",it.opacity).put("typography",StudioCreativeCodec.typography(it.typography)))}})
                    result.put("supportedEffects",JSONArray(StudioEffectKind.entries.map {it.name}))
                    result.put("supportedBlends",JSONArray(StudioBlend.entries.map {it.name}))
                    fun pageNumber(key:String,default:Int,min:Int,max:Int):Int {
                        if(key !in args) return default
                        val value=args[key];require(value is Number);val n=value.toDouble();require(n.isFinite()&&n%1==0.0&&n in min.toDouble()..max.toDouble());return n.toInt()
                    }
                    val offset=pageNumber("offset",0,0,32);val limit=pageNumber("limit",4,1,4)
                    val layers=if("layerId" in args) listOf(requireNotNull(project.session.current.layers.find {it.id==objectId("layerId")})) else project.session.current.layers.drop(offset).take(limit)
                    val more="layerId" !in args && offset+layers.size<project.session.current.layers.size
                    result.put("layersTruncated",more).put("nextLayerOffset",if(more) offset+layers.size else JSONObject.NULL)
                    result.put("layers", JSONArray().apply { layers.forEach { layer ->
                        put(JSONObject().put("id", layer.id).put("name", layer.name.take(80)).put("kind", layer.kind.name)
                            .put("visible", layer.visible).put("locked", layer.locked).put("opacity", layer.opacity)
                            .put("rotation",layer.rotation).put("color",layer.color).put("fontSize",layer.fontSize).put("strokeWidth",layer.strokeWidth)
                            .put("brightness",layer.brightness).put("contrast",layer.contrast).put("saturation",layer.saturation)
                            .put("bounds", JSONObject().put("x", layer.bounds.x).put("y", layer.bounds.y).put("width", layer.bounds.width).put("height", layer.bounds.height))
                            .put("text", layer.text.take(120)).put("textTruncated", layer.text.length > 120)
                            .put("parentId",layer.parentId ?: JSONObject.NULL).put("blend",layer.blend.name).put("editable",project.session.current.isEditable(layer))
                            .put("typography",StudioCreativeCodec.typography(layer.typography))
                            .put("imageCrop",layer.imageCrop?.let { JSONObject().put("x",it.x).put("y",it.y).put("width",it.width).put("height",it.height) } ?: JSONObject.NULL)
                            .put("effects",JSONArray().apply { layer.effects.forEach { put(StudioCreativeCodec.effect(it)) } })
                            .put("texture",layer.texture?.name ?: JSONObject.NULL).put("textureSeed",layer.textureSeed).put("strokeOnly",layer.strokeOnly)
                            .put("sourceId", layer.source?.objectId ?: JSONObject.NULL).put("sourceVersion", layer.source?.version ?: JSONObject.NULL))
                    } })
                    result.put("versions", JSONArray(studio.projects.versions(project.id).map { it.revision }))
                    studio.selectedInk(project.id)?.takeIf { it.projectRevision == project.revision }?.let { selected -> result.put("inkSelection", JSONObject()
                        .put("layerId", selected.layerId).put("projectRevision", selected.projectRevision)
                        .put("sourceRevision", selected.selection.sourceRevision).put("strokeCount", selected.selection.selectedStrokeIds.size)
                        .put("bounds", JSONObject().put("left", selected.selection.bounds.left).put("top", selected.selection.bounds.top)
                            .put("right", selected.selection.bounds.right).put("bottom", selected.selection.bounds.bottom))) }
                    return success(result)
                }
                "halo_studio_edit" -> {
                    exact(setOf("projectId", "revision", "operation", "fields"))
                    val fields = args["fields"]; require(fields is Map<*, *> && fields.keys.all { it is String })
                    val values = fields.entries.associate { (key, value) -> key as String to value }
                    return success(summary(studio.edit(objectId("projectId"), number("revision"), string("operation"), values)))
                }
                "halo_studio_save" -> {
                    exact(setOf("projectId", "revision", "action"), setOf("title"))
                    val id = objectId("projectId"); val revision = number("revision"); val action = string("action")
                    require((action in setOf("copy", "rename")) == ("title" in args))
                    val result = when (action) {
                        "save" -> studio.projects.save(id, revision)
                        "copy" -> studio.projects.saveAs(id, revision, string("title"))
                        "rename" -> studio.projects.rename(id, revision, string("title"))
                        "discard" -> studio.projects.discardDraft(id, revision)
                        else -> return rejected()
                    }
                    return success(summary(result))
                }
                "halo_studio_export" -> {
                    exact(setOf("projectId", "revision", "format"))
                    val output = studio.export(objectId("projectId"), number("revision"), CanvasImageFormat.valueOf(string("format")))
                    return success(JSONObject().put("outputId", output.objectId.value).put("fileVersion", output.version)
                        .put("mimeType", output.mimeType).put("bytes", output.sizeBytes).put("savedToWorkspace", true))
                }
                else -> return rejected(ToolRejectionKind.TOOL_UNKNOWN)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: IllegalArgumentException) { return rejected() }
        catch (_: IllegalStateException) { return rejected(ToolRejectionKind.RESOURCE_UNAVAILABLE) }
        catch (_: Exception) { return ToolExecutionResult.Failed(toolCall.id, toolCall.functionName,
            "Studio could not complete this action. Reopen the project before retrying.") }
    }
    private fun summary(project: StudioProject) = JSONObject().put("projectId", project.id).put("revision", project.revision)
        .put("selectedLayerId",project.session.current.selectedLayerId ?: JSONObject.NULL)
        .put("title", project.title).put("width", project.session.current.width).put("height", project.session.current.height)
        .put("layerCount", project.session.current.layers.size).put("localDraft", project.hasUnsavedChanges)
        .put("canUndo", project.session.undo.isNotEmpty()).put("canRedo", project.session.redo.isNotEmpty())
}
