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
        "bounds" to rect, "mask" to mapOf("anyOf" to listOf(rect, mapOf("type" to "null"))))
    private val fields = schema(changes + mapOf(
        "layerId" to mapOf("anyOf" to listOf(id, mapOf("type" to "null"))),
        "changes" to schema(changes, emptyList()),
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
        definition("halo_studio_get", "Read one Studio project's revision, supported layer metadata, saved versions and fresh explicit Ink selection. Does not recognize handwriting or send pixels.", mapOf("projectId" to id)),
        definition("halo_studio_edit", "Edit a local draft using its exact observed revision. Bounds/mask/crop use thousandths. Add requires name; add_text also text; add_ink/set_ink use bounded portable Ink encoding. update_layer uses layerId and changes; transforms are global. Undo/redo/restore preserve originals. Stale revisions are rejected, never replayed.",
            mapOf("projectId" to id, "revision" to revision, "operation" to mapOf("type" to "string", "enum" to listOf(
                "add_text", "add_rectangle", "add_ellipse", "add_ink", "set_ink", "update_layer", "delete_layer", "move_layer", "select_layer",
                "transform", "resize", "background", "undo", "redo", "restore_version", "import_image", "delete_selected_ink")), "fields" to fields)),
        definition("halo_studio_save", "Save a local draft as a version, save a separate copy, rename, or discard draft changes. Copy/rename require title. Exact revision required.",
            mapOf("projectId" to id, "revision" to revision, "action" to mapOf("type" to "string", "enum" to listOf("save", "copy", "rename", "discard")),
                "title" to text), listOf("projectId", "revision", "action")),
        definition("halo_studio_export", "Render an exact local Studio draft into a new immutable Workspace PNG/JPEG with durable source/version metadata. Does not overwrite or automatically insert/share anything.",
            mapOf("projectId" to id, "revision" to revision, "format" to mapOf("type" to "string", "enum" to listOf("PNG", "JPEG")))),
        definition("halo_studio_insert", "Insert a previously exported exact Studio image as a new Canvas reference. The managed Workspace copy remains authoritative.",
            mapOf("canvasId" to id, "outputId" to id, "fileVersion" to mapOf("type" to "string", "maxLength" to 200)))
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
                    exact(setOf("canvasId", "outputId", "fileVersion"))
                    val version = string("fileVersion").also { require(it.length in 1..200) }
                    val canvas = objectId("canvasId"); val output = objectId("outputId")
                    val node = studio.insertOutput(canvas, output, version)
                    return success(JSONObject().put("canvasId", canvas).put("outputId", output).put("nodeId", node).put("inserted", true))
                }
                "halo_studio_get" -> {
                    exact(setOf("projectId")); val project = requireNotNull(studio.projects.get(objectId("projectId")))
                    val result = summary(project)
                    result.put("layers", JSONArray().apply { project.session.current.layers.forEach { layer ->
                        put(JSONObject().put("id", layer.id).put("name", layer.name.take(80)).put("kind", layer.kind.name)
                            .put("visible", layer.visible).put("locked", layer.locked).put("opacity", layer.opacity)
                            .put("bounds", JSONObject().put("x", layer.bounds.x).put("y", layer.bounds.y).put("width", layer.bounds.width).put("height", layer.bounds.height))
                            .put("text", layer.text.take(120)).put("textTruncated", layer.text.length > 120)
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
        .put("title", project.title).put("width", project.session.current.width).put("height", project.session.current.height)
        .put("layerCount", project.session.current.layers.size).put("localDraft", project.hasUnsavedChanges)
        .put("canUndo", project.session.undo.isNotEmpty()).put("canRedo", project.session.redo.isNotEmpty())
}
