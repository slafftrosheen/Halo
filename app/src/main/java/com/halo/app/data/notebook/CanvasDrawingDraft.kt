package com.halo.app.data.notebook

import org.json.JSONObject

data class CanvasDrawingDraft(val nodeId: String?, val kind: String, val original: String,
    val sketch: String = "", val pages: Map<Int, String> = emptyMap(), val page: Int = 1, val fileVersion: String? = null) {
    /** A draft cannot acquire a newer source identity merely by reopening its editor. */
    fun matchesSource(sourceNodeId: String?, sourceKind: String, sourceOriginal: String,
        sourceFileVersion: String? = null): Boolean =
        nodeId == sourceNodeId && kind == sourceKind && original == sourceOriginal && fileVersion == sourceFileVersion

    fun encode(): String {
        require(kind in setOf("sketch", "annotation") && page in 1..9999 && pages.size <= CanvasFileMarkupCodec.MAX_PAGES)
        return JSONObject().put("format", "canvas-drawing:v1").put("nodeId", nodeId).put("kind", kind)
            .put("fileVersion", fileVersion).put("original", original).put("sketch", sketch).put("page", page)
            .put("pages", JSONObject(pages.mapKeys { it.key.toString() })).toString().also { require(it.length <= 3_000_000) }
    }
    companion object {
        fun decode(encoded: String): CanvasDrawingDraft? = runCatching {
            require(encoded.length <= 3_000_000)
            val json = JSONObject(encoded)
            require(json.getString("format") == "canvas-drawing:v1")
            val pages = json.getJSONObject("pages")
            CanvasDrawingDraft(json.optString("nodeId").takeIf { it.isNotBlank() && it != "null" }, json.getString("kind"),
                json.getString("original"), json.getString("sketch"), pages.keys().asSequence().associate { it.toInt() to pages.getString(it) }, json.getInt("page"), json.optString("fileVersion").takeIf { it.isNotBlank() && it != "null" })
                .also { it.encode() }
        }.getOrNull()
    }
}
