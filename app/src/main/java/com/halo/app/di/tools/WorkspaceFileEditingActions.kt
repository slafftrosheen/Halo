package com.halo.app.di.tools

import com.halo.app.data.workspace.*
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

/** Typed editing extension of FileToolExecutor. All effects stay with existing local owners. */
class WorkspaceFileEditingActions(private val tools: WorkspaceFileTools) : ToolExecutor {
    private val id = mapOf("type" to "string", "pattern" to WorkspaceActionIds.PATTERN)
    private val version = mapOf("type" to "string", "minLength" to 1, "maxLength" to 200)
    private val revision = mapOf("type" to "integer", "minimum" to 1, "maximum" to Int.MAX_VALUE)
    private fun text(max: Int) = mapOf("type" to "string", "maxLength" to max)
    private fun integer(min: Int, max: Int) = mapOf("type" to "integer", "minimum" to min, "maximum" to max)
    private fun enumeration(values: List<String>) = mapOf("type" to "string", "enum" to values)
    private fun schema(properties: Map<String, Any>, required: List<String> = properties.keys.toList()) =
        mapOf("type" to "object", "properties" to properties, "required" to required, "additionalProperties" to false)
    private val source = schema(mapOf("objectId" to id, "version" to version))
    private fun sources(min: Int) = mapOf("type" to "array", "minItems" to min, "maxItems" to 6, "items" to source)
    private val format = enumeration(WorkspaceTextFormat.entries.map { it.name })
    private val crop = schema(mapOf("x" to integer(0, 999), "y" to integer(0, 999), "width" to integer(1, 1000), "height" to integer(1, 1000)))
    private val recipe = schema(mapOf("quarterTurns" to integer(0, 3), "mirror" to mapOf("type" to "boolean"),
        "squareCrop" to mapOf("type" to "boolean"), "maxEdge" to mapOf("type" to "integer", "enum" to listOf(512, 1024, 2048, 4096)),
        "format" to enumeration(CanvasImageFormat.entries.map { it.name }), "crop" to crop), emptyList())
    private val stitch = schema(mapOf("mode" to enumeration(CanvasStitchMode.entries.map { it.name }),
        "gap" to integer(0, 24), "maxEdge" to integer(1024, 4096), "format" to enumeration(CanvasImageFormat.entries.map { it.name }),
        "background" to enumeration(CanvasStitchBackground.entries.map { it.name })), emptyList())
    private val editFields = schema(mapOf("text" to text(200000), "start" to integer(0, 200000), "end" to integer(0, 200000),
        "expectedText" to text(200000), "replacement" to text(200000), "row" to integer(0, 999), "column" to integer(0, 63),
        "index" to integer(0, 1000), "value" to text(16000)), emptyList())
    private fun definition(name: String, description: String, fields: Map<String, Any>, required: List<String> = fields.keys.toList()) =
        ToolDefinition(name, name, description, schema(fields, required))
    override fun definitions() = listOf(
        definition("halo_file_tool_capabilities", "Read actual deterministic file tool states and limits. PDF page images are raster output, never native PDF editing. Office roundtrips are unavailable.", emptyMap()),
        definition("halo_file_tools_open", "Request presentation of local File tools, optionally for an exact active Workspace file/note. Dispatch does not prove rendering or perform an edit.", mapOf("objectId" to id), emptyList()),
        definition("halo_text_create", "Create a local literal UTF-8 TXT/Markdown/CSV document using the canonical Notebook owner. No provider. CSV values never execute formulas.",
            mapOf("title" to text(120), "format" to format, "text" to text(200000)), listOf("title", "format")),
        definition("halo_text_import", "Create an editable copy from an exact imported UTF-8 TXT/Markdown/CSV version. Pin the source; preserve original bytes and permissions. Unsupported encodings are rejected.", mapOf("objectId" to id, "version" to version)),
        definition("halo_text_get", "Read a bounded chunk of a local literal file document, its exact revision/format and total length. Chunk offsets are UTF-16 code units; truncated output is not the whole document.",
            mapOf("noteId" to id, "offset" to integer(0, 200000), "length" to integer(1, 4000)), listOf("noteId")),
        definition("halo_text_edit", "Edit the exact observed local document revision. replace_range also requires matching expectedText; CSV row/column/cell edits use zero-based indices. Undo/redo refer only to bounded live-session history and also require the exact revision. Stale writes never refresh or replay.",
            mapOf("noteId" to id, "revision" to revision, "operation" to enumeration(listOf("replace", "replace_range", "set_cell", "insert_row", "delete_row", "insert_column", "delete_column", "undo", "redo")), "fields" to editFields)),
        definition("halo_text_export", "Export an exact local document/note revision as a new managed UTF-8 file with durable source links. Ordinary rich notes allow explicit TXT/Markdown projection, not guessed CSV conversion.",
            mapOf("noteId" to id, "revision" to revision, "format" to format)),
        definition("halo_image_transform", "Transform/convert an exact image into a new local PNG/JPEG. Original is retained. Crop coordinates use thousandths and cannot combine with squareCrop.",
            mapOf("objectId" to id, "version" to version, "recipe" to recipe)),
        definition("halo_image_batch", "Serially transform 1–6 exact images. Creates a durable Notebook receipt before processing; each output and receipt item commit together. Partial/cancelled work is never automatically replayed.",
            mapOf("title" to text(120), "sources" to sources(1), "recipe" to recipe)),
        definition("halo_image_collage", "Combine 2–6 exact source images using canonical horizontal/vertical/grid/hero composition; creates a separate managed output with every exact source link.",
            mapOf("sources" to sources(2), "recipe" to stitch)),
        definition("halo_file_batch_status", "Read a durable local batch receipt and saved per-item output IDs. INTERRUPTED means no active owner, not permission to replay. File availability must be checked before opening.", mapOf("noteId" to id)),
        definition("halo_pdf_page_info", "Read actual page count for an exact supported PDF. Does not read PDF text or claim native editing.", mapOf("objectId" to id, "version" to version)),
        definition("halo_pdf_extract_page_image", "Rasterize one exact PDF page into a new source-linked PNG. Original vector/text PDF is unchanged. This is page-image extraction, not a modified PDF.",
            mapOf("objectId" to id, "version" to version, "page" to integer(1, 9999))),
        definition("halo_pdf_annotation_create", "Create a local vector annotation note linked to an exact immutable PDF source. Does not alter or rewrite the PDF.",
            mapOf("objectId" to id, "version" to version, "title" to text(120))),
        definition("halo_pdf_annotation_get", "Read annotation-note revision, exact PDF source and marked pages; optionally a bounded portable Ink page. Omitted Ink is never an empty drawing. No OCR or handwriting recognition.",
            mapOf("noteId" to id, "page" to integer(1, 9999)), listOf("noteId")),
        definition("halo_pdf_annotation_edit", "Replace marks on one page at the exact annotation-note revision using bounded portable Halo Ink. Empty Ink clears that page. Original PDF remains untouched.",
            mapOf("noteId" to id, "revision" to revision, "page" to integer(1, 9999), "ink" to text(262144))),
        definition("halo_pdf_annotation_export_page_image", "Export an exact annotation-note page as a new PNG, retaining original PDF and vector annotation note. Raster output, not native PDF editing.",
            mapOf("noteId" to id, "revision" to revision, "page" to integer(1, 9999))),
        definition("halo_file_attach_canvas", "Requires exact current canvasVersion from halo_workspace_canvases as well as source version. Add an exact active file/note version as a Canvas reference. Source remains authoritative in Workspace; this never shares it externally.",
            mapOf("canvasId" to id, "canvasVersion" to version, "objectId" to id, "version" to version))
    )
    private val names by lazy { definitions().map { it.name }.toSet() }
    fun owns(name: String) = name in names
    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        val args = toolCall.arguments
        fun exact(required: Set<String>, optional: Set<String> = emptySet()) = require(args.keys.containsAll(required) && args.keys.all { it in required || it in optional })
        fun string(key: String) = args[key] as? String ?: error("Expected text")
        fun id(key: String) = string(key).also { require(WorkspaceActionIds.isValid(it)) }
        fun number(key: String, min: Int = 1, max: Int = Int.MAX_VALUE, default: Int? = null): Int =
            if (key !in args && default != null) default else exactInteger(args[key], min, max)
        fun source() = WorkspaceFileVersion(id("objectId"), string("version"))
        fun success(value: JSONObject) = ToolExecutionResult.Success(toolCall.id, toolCall.functionName, value.toString())
        try {
            return when (toolCall.functionName) {
                "halo_file_tool_capabilities" -> { exact(emptySet()); success(JSONObject().put("capabilities", JSONArray().apply {
                    WorkspaceFileToolCatalog.capabilities.forEach { put(JSONObject().put("id", it.id).put("state", it.state.name).put("limits", it.limits)) }
                })) }
                "halo_file_tools_open" -> {
                    exact(emptySet(), setOf("objectId")); val request = tools.requestOpen(if ("objectId" in args) id("objectId") else null)
                    ToolExecutionResult.Success(toolCall.id, toolCall.functionName, JSONObject().put("requestId", request.id).put("state", "DISPATCHED").toString(),
                        com.halo.runtime.cognition.agent.ActionTruth.DISPATCHED)
                }
                "halo_text_create" -> { exact(setOf("title", "format"), setOf("text")); success(document(tools.text.create(string("title"), WorkspaceTextFormat.valueOf(string("format")), if ("text" in args) string("text") else ""))) }
                "halo_text_import" -> { exact(setOf("objectId", "version")); success(document(tools.text.import(id("objectId"), string("version")))) }
                "halo_text_get" -> {
                    exact(setOf("noteId"), setOf("offset", "length")); val current = requireNotNull(tools.text.get(id("noteId")))
                    val offset = number("offset", 0, current.text.length, 0); val length = number("length", 1, 4000, 3500)
                    val end = minOf(current.text.length, offset + length)
                    success(document(current).put("offset", offset).put("text", current.text.substring(offset, end)).put("totalChars", current.text.length).put("hasMore", end < current.text.length))
                }
                "halo_text_edit" -> {
                    exact(setOf("noteId", "revision", "operation", "fields")); val note = id("noteId"); val revision = number("revision").toLong(); val fields = objectFields(args["fields"])
                    fun fields(vararg keys: String) = require(fields.keys == keys.toSet())
                    fun value(key: String) = fields[key] as? String ?: error("Expected text")
                    fun index(key: String) = exactInteger(fields[key], 0, 200000)
                    val changed = when (string("operation")) {
                        "undo" -> { fields(); tools.text.undo(note, revision) }
                        "redo" -> { fields(); tools.text.redo(note, revision) }
                        "replace" -> { fields("text"); tools.text.replace(note, revision, value("text")) }
                        "replace_range" -> { fields("start", "end", "expectedText", "replacement"); tools.text.replaceRange(note, revision, index("start"), index("end"), value("expectedText"), value("replacement")) }
                        else -> {
                            val command = when (string("operation")) {
                                "set_cell" -> { fields("row", "column", "value"); WorkspaceCsvCommand.SetCell(index("row"), index("column"), value("value")) }
                                "insert_row" -> { fields("index"); WorkspaceCsvCommand.InsertRow(index("index")) }
                                "delete_row" -> { fields("index"); WorkspaceCsvCommand.DeleteRow(index("index")) }
                                "insert_column" -> { fields("index"); WorkspaceCsvCommand.InsertColumn(index("index")) }
                                "delete_column" -> { fields("index"); WorkspaceCsvCommand.DeleteColumn(index("index")) }
                                else -> error("Unsupported edit")
                            }; tools.text.editCsv(note, revision, command)
                        }
                    }; success(document(changed))
                }
                "halo_text_export" -> { exact(setOf("noteId", "revision", "format")); success(file(tools.text.exportNote(id("noteId"), number("revision").toLong(), WorkspaceTextFormat.valueOf(string("format"))))) }
                "halo_image_transform" -> { exact(setOf("objectId", "version", "recipe")); success(file(tools.media.transform(source(), imageRecipe(objectFields(args["recipe"]))))) }
                "halo_image_batch" -> { exact(setOf("title", "sources", "recipe")); success(receipt(tools.media.batch(string("title"), sources(args["sources"]), imageRecipe(objectFields(args["recipe"]))))) }
                "halo_image_collage" -> { exact(setOf("sources", "recipe")); success(file(tools.media.collage(sources(args["sources"]), stitchRecipe(objectFields(args["recipe"]))))) }
                "halo_file_batch_status" -> { exact(setOf("noteId")); success(receipt(tools.receipts.get(id("noteId")))) }
                "halo_pdf_page_info" -> { exact(setOf("objectId", "version")); success(JSONObject().put("pages", tools.media.pdfPageCount(source())).put("nativePdfEditing", false)) }
                "halo_pdf_extract_page_image" -> { exact(setOf("objectId", "version", "page")); success(file(tools.media.extractPdfPageImage(source(), number("page", 1, 9999)))) }
                "halo_pdf_annotation_create" -> { exact(setOf("objectId", "version", "title")); success(annotation(tools.annotations.create(source(), string("title")))) }
                "halo_pdf_annotation_get" -> {
                    exact(setOf("noteId"), setOf("page")); val note = requireNotNull(tools.annotations.get(id("noteId"))); val result = annotation(note)
                    if ("page" in args) {
                        val ink = note.markup.pages[number("page", 1, 9999)].orEmpty()
                        result.put("inkChars", ink.length).put("inkOmitted", ink.length > 6000)
                        if (ink.length <= 6000) result.put("ink", ink)
                    }; success(result)
                }
                "halo_pdf_annotation_edit" -> { exact(setOf("noteId", "revision", "page", "ink")); success(annotation(tools.annotations.setPage(id("noteId"), number("revision").toLong(), number("page", 1, 9999), string("ink")))) }
                "halo_pdf_annotation_export_page_image" -> { exact(setOf("noteId", "revision", "page")); success(file(tools.annotations.exportPageImage(id("noteId"), number("revision").toLong(), number("page", 1, 9999)))) }
                "halo_file_attach_canvas" -> { exact(setOf("canvasId", "canvasVersion", "objectId", "version")); success(JSONObject().put("nodeId", tools.attachToCanvas(id("canvasId"), id("objectId"), string("version"), string("canvasVersion")))) }
                else -> ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, "Unsupported file action", ToolRejectionKind.TOOL_UNKNOWN)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { return ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName,
            "File tools could not complete this action. Check the exact source version, supported format and limits; completed batch outputs remain in the receipt.", ToolRejectionKind.INVALID_ARGUMENTS) }
    }
    private fun document(value: WorkspaceTextDocument) = JSONObject().put("noteId", value.id).put("revision", value.revision).put("format", value.format.name).put("title", value.document.title)
        .put("canUndo", tools.text.historyStatus(value.id, value.revision).canUndo).put("canRedo", tools.text.historyStatus(value.id, value.revision).canRedo)
    private fun file(value: WorkspaceFile) = JSONObject().put("objectId", value.objectId.value).put("version", value.version).put("name", value.displayName).put("mimeType", value.mimeType)
    private fun annotation(value: WorkspacePdfAnnotations) = JSONObject().put("noteId", value.id).put("revision", value.revision)
        .put("pdfSourceId", value.source.objectId).put("pdfSourceVersion", value.source.version).put("markedPages", JSONArray(value.markup.pages.keys.sorted())).put("nativePdfEditing", false)
    private fun receipt(value: WorkspaceBatchReceipt) = JSONObject().put("noteId", value.id).put("revision", value.revision).put("state", value.phase.name)
        .put("items", JSONArray().apply { value.items.forEach { put(JSONObject().put("sourceId", it.source.objectId).put("state", it.phase.name)
            .put("outputId", it.output?.objectId ?: JSONObject.NULL).put("outputVersion", it.output?.version ?: JSONObject.NULL)) } })
    private fun objectFields(value: Any?): Map<String, Any?> {
        require(value is Map<*, *> && value.keys.all { it is String })
        return value.entries.associate { it.key as String to it.value }
    }
    private fun exactInteger(value: Any?, min: Int, max: Int): Int {
        require(value is Number); val number = value.toDouble()
        require(number.isFinite() && number % 1.0 == 0.0 && number in min.toDouble()..max.toDouble())
        return number.toInt()
    }
    private fun sources(value: Any?): List<WorkspaceFileVersion> {
        require(value is List<*> && value.size in 1..6)
        return value.map { row -> val fields = objectFields(row); require(fields.keys == setOf("objectId", "version"))
            WorkspaceFileVersion(fields["objectId"] as String, fields["version"] as String) }
    }
    private fun imageRecipe(fields: Map<String, Any?>): CanvasImageRecipe {
        require(fields.values.none { it == null })
        require(fields.keys.all { it in setOf("quarterTurns", "mirror", "squareCrop", "maxEdge", "format", "crop") })
        fun flag(key: String) = if (key in fields) fields[key] as Boolean else false
        val crop = fields["crop"]?.let { value -> val rect = objectFields(value); require(rect.keys == setOf("x", "y", "width", "height"))
            CanvasImageCrop(exactInteger(rect["x"], 0, 999), exactInteger(rect["y"], 0, 999), exactInteger(rect["width"], 1, 1000), exactInteger(rect["height"], 1, 1000)) }
        return CanvasImageRecipe(if ("quarterTurns" in fields) exactInteger(fields["quarterTurns"], 0, 3) else 0,
            flag("mirror"), flag("squareCrop"), if ("maxEdge" in fields) exactInteger(fields["maxEdge"], 512, 4096) else 2048,
            fields["format"]?.let { CanvasImageFormat.valueOf(it as String) } ?: CanvasImageFormat.PNG, crop)
    }
    private fun stitchRecipe(fields: Map<String, Any?>): CanvasStitchRecipe {
        require(fields.values.none { it == null })
        require(fields.keys.all { it in setOf("mode", "gap", "maxEdge", "format", "background") })
        return CanvasStitchRecipe(fields["mode"]?.let { CanvasStitchMode.valueOf(it as String) } ?: CanvasStitchMode.GRID,
            if ("gap" in fields) exactInteger(fields["gap"], 0, 24) else 8,
            if ("maxEdge" in fields) exactInteger(fields["maxEdge"], 1024, 4096) else 2048,
            fields["format"]?.let { CanvasImageFormat.valueOf(it as String) } ?: CanvasImageFormat.PNG,
            fields["background"]?.let { CanvasStitchBackground.valueOf(it as String) } ?: CanvasStitchBackground.WHITE)
    }
}
