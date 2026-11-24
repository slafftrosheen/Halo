package com.halo.app.data.notebook

import org.json.JSONObject
import java.util.UUID

data class CanvasDrawingDraft(val nodeId: String?, val kind: String, val original: String,
    val sketch: String = "", val pages: Map<Int, String> = emptyMap(), val page: Int = 1, val fileVersion: String? = null,
    /** A new completed editor draft has its own identity even when its ink repeats an older edit. */
    val receiptId: String? = UUID.randomUUID().toString()) {
    /** A draft cannot acquire a newer source identity merely by reopening its editor. */
    fun matchesSource(sourceNodeId: String?, sourceKind: String, sourceOriginal: String,
        sourceFileVersion: String? = null): Boolean =
        nodeId == sourceNodeId && kind == sourceKind && original == sourceOriginal && fileVersion == sourceFileVersion

    fun encode(): String {
        val maxPages = if (kind == "sketch") com.halo.app.ui.canvas.v13.InkSheets.MAX_SHEETS - 1 else CanvasFileMarkupCodec.MAX_PAGES
        require(kind in setOf("sketch", "annotation") && page in 1..9999 && pages.size <= maxPages)
        if (kind == "sketch" && pages.isNotEmpty()) {
            require(pages.keys == (2..pages.size + 1).toSet())
            require(sketch.length <= MAX_SHEET_CHARS && original.length <= MAX_SHEET_CHARS)
            require(pages.values.all { it.length <= MAX_SHEET_CHARS })
        }
        require(receiptId == null || RECEIPT_PATTERN.matches(receiptId))
        return JSONObject().put("format", if (receiptId == null) "canvas-drawing:v1" else "canvas-drawing:v2")
            .put("receiptId", receiptId).put("nodeId", nodeId).put("kind", kind)
            .put("fileVersion", fileVersion).put("original", original).put("sketch", sketch).put("page", page)
            .put("pages", JSONObject(pages.mapKeys { it.key.toString() })).toString()
            .also { require(it.length <= if (kind == "sketch" && pages.isNotEmpty()) MAX_STORED_CHARS else LEGACY_MAX_CHARS) }
    }
    companion object {
        private val RECEIPT_PATTERN = Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
        private const val MAX_SHEET_CHARS = com.halo.sdk.runtime.ink.InkLimits.MAX_ENCODED_CHARS
        private const val LEGACY_MAX_CHARS = 3_000_000
        internal const val MAX_STORED_CHARS = MAX_SHEET_CHARS * (com.halo.app.ui.canvas.v13.InkSheets.MAX_SHEETS + 1) + 8_192

        fun decode(encoded: String): CanvasDrawingDraft? = runCatching {
            require(encoded.length <= MAX_STORED_CHARS)
            val json = JSONObject(encoded)
            val format = json.getString("format")
            require(format in setOf("canvas-drawing:v1", "canvas-drawing:v2"))
            // Never mint an identity while reading legacy bytes: repeated recovery reads
            // must compare equal. New editor callbacks construct a fresh v2 draft.
            val receipt = if (format == "canvas-drawing:v2") json.getString("receiptId") else null
            val pages = json.getJSONObject("pages")
            CanvasDrawingDraft(json.optString("nodeId").takeIf { it.isNotBlank() && it != "null" }, json.getString("kind"),
                json.getString("original"), json.getString("sketch"), pages.keys().asSequence().associate { it.toInt() to pages.getString(it) }, json.getInt("page"), json.optString("fileVersion").takeIf { it.isNotBlank() && it != "null" }, receipt)
                .also { it.encode() }
        }.getOrNull()
    }
}
