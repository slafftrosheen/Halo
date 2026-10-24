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
        require(kind in setOf("sketch", "annotation") && page in 1..9999 && pages.size <= CanvasFileMarkupCodec.MAX_PAGES)
        require(receiptId == null || RECEIPT_PATTERN.matches(receiptId))
        return JSONObject().put("format", if (receiptId == null) "canvas-drawing:v1" else "canvas-drawing:v2")
            .put("receiptId", receiptId).put("nodeId", nodeId).put("kind", kind)
            .put("fileVersion", fileVersion).put("original", original).put("sketch", sketch).put("page", page)
            .put("pages", JSONObject(pages.mapKeys { it.key.toString() })).toString().also { require(it.length <= 3_000_000) }
    }
    companion object {
        private val RECEIPT_PATTERN = Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
        fun decode(encoded: String): CanvasDrawingDraft? = runCatching {
            require(encoded.length <= 3_000_000)
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
