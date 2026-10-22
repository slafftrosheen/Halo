package com.halo.app.data.notebook

import com.halo.sdk.runtime.ink.InkCodec
import com.halo.sdk.runtime.ink.InkSelectionBounds
import com.halo.sdk.runtime.ink.InkSelectionContext
import com.halo.sdk.runtime.ink.InkSelectionOrigin
import com.halo.sdk.runtime.ink.InkSelections
import com.halo.sdk.runtime.ink.InkStroke
import java.security.MessageDigest

/** Stable JSON representation of a Canvas selection pointer; stroke data stays in its source. */
object InkSelectionBookmarkCodec {
    fun encode(context: InkSelectionContext): String = org.json.JSONObject().apply {
        put("schemaVersion", context.schemaVersion)
        put("workspaceObjectId", context.workspaceObjectId)
        put("pageId", context.pageId)
        put("left", context.bounds.left); put("top", context.bounds.top)
        put("right", context.bounds.right); put("bottom", context.bounds.bottom)
        put("strokeIds", org.json.JSONArray(context.selectedStrokeIds))
        put("sourceRevision", context.sourceRevision)
        put("origin", context.origin.name)
        put("createdAtMs", context.createdAtMs)
        put("updatedAtMs", context.updatedAtMs)
    }.toString().also { require(it.length <= MAX_ENCODED_CHARS) }

    fun decode(encoded: String): InkSelectionContext? {
        if (encoded.length !in 1..MAX_ENCODED_CHARS) return null
        return runCatching {
            val json = org.json.JSONObject(encoded)
            val ids = json.getJSONArray("strokeIds").let { array ->
                (0 until array.length()).map(array::getString)
            }
            InkSelectionContext(
                workspaceObjectId = json.getString("workspaceObjectId"),
                pageId = json.optString("pageId").takeIf(String::isNotEmpty),
                bounds = InkSelectionBounds(json.getInt("left"), json.getInt("top"),
                    json.getInt("right"), json.getInt("bottom")),
                selectedStrokeIds = ids,
                sourceRevision = json.getString("sourceRevision"),
                origin = InkSelectionOrigin.valueOf(json.getString("origin")),
                createdAtMs = json.getLong("createdAtMs"),
                updatedAtMs = json.getLong("updatedAtMs"),
                schemaVersion = json.getInt("schemaVersion"),
            )
        }.getOrNull()
    }

    /** Re-resolves an anchor against the current canonical source revision. */
    fun resolve(
        context: InkSelectionContext,
        canvasId: String,
        sourceType: CanvasNodeType,
        sourceNodeId: String,
        sourceObjectId: String?,
        sourceText: String,
        sourceVersion: String? = null,
    ): List<InkStroke>? {
        val (ownerId, pageId, encoded, revisionBase) = when (sourceType) {
            CanvasNodeType.SKETCH -> {
                if (context.workspaceObjectId != canvasId || context.pageId != sourceNodeId) return null
                listOf(canvasId, sourceNodeId, sourceText, "")
            }
            CanvasNodeType.FILE, CanvasNodeType.IMAGE -> {
                val sourceId = sourceObjectId ?: return null
                val pageNumber = context.pageId?.removePrefix("page-")?.toIntOrNull()
                    ?.takeIf { it in 1..9999 } ?: return null
                if (context.workspaceObjectId != sourceId || sourceVersion.isNullOrBlank()) return null
                val pageText = runCatching { CanvasFileMarkupCodec.decode(sourceText).pages[pageNumber].orEmpty() }.getOrNull() ?: return null
                listOf(sourceId, "page-$pageNumber", pageText, sourceVersion)
            }
            else -> return null
        }
        val document = InkCodec.decode(encoded) ?: return null
        val revision = MessageDigest.getInstance("SHA-256")
            .digest((revisionBase + "\n" + InkCodec.encode(document)).toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
        return InkSelections.resolve(document, context, ownerId, pageId, revision)
    }

    /** Safe, inspectable reference text; deliberately contains no ink content or stroke IDs. */
    fun referenceText(context: InkSelectionContext): String =
        "User-selected visual region reference; recognitionStatus=NOT_AVAILABLE; visualContentIncluded=false; separateExplicitCropMayBeAttached=true. " +
            "Source=${context.workspaceObjectId}; page=${context.pageId ?: "canvas"}; " +
            "bounds=${context.bounds.left},${context.bounds.top},${context.bounds.right},${context.bounds.bottom}/1000; " +
            "selectedStrokeCount=${context.selectedStrokeIds.size}; sourceRevision=${context.sourceRevision}."

    const val MAX_ENCODED_CHARS = 65_536
}

/** Visible Study material wrapper that keeps the exact canonical selection reference inspectable. */
object InkSelectionStudyMaterial {
    private const val PREFIX = "User-selected visual region reference; recognitionStatus=NOT_AVAILABLE; " +
        "visualContentIncluded=false; separateExplicitCropMayBeAttached=true. "
    private val reference = Regex(
        "Source=([^;]+); page=([^;]+); bounds=([^;]+); selectedStrokeCount=(\\d+); sourceRevision=(.+)\\."
    )

    fun body(context: com.halo.sdk.runtime.ink.InkSelectionContext, canvasId: String, selectionNodeId: String): String =
        InkSelectionBookmarkCodec.referenceText(context) +
            "\ncanvasObjectId=$canvasId\nselectionBookmarkId=$selectionNodeId"

    /** Keep model/citation locator concise; the full bounds and revision remain in the note body. */
    fun sourceLocator(body: String): String? {
        if (!body.startsWith(PREFIX)) return null
        val match = reference.find(body) ?: return null
        val source = match.groupValues[1]
        val pageId = match.groupValues[2]
        val page = Regex("page-(\\d+)").matchEntire(pageId)?.groupValues?.get(1)
            ?.let { "page $it" } ?: "Canvas region"
        val revision = match.groupValues[5]
        return "${source.take(120)} · $page · ink revision ${revision.take(16)}"
    }

    fun canvasTarget(body: String): Pair<String, String>? {
        if (sourceLocator(body) == null) return null
        val canvasId = Regex("(?:^|\\n)canvasObjectId=([^\\s]{1,200})(?:\\n|$)")
            .find(body)?.groupValues?.get(1) ?: return null
        val selectionId = Regex("(?:^|\\n)selectionBookmarkId=([^\\s]{1,200})(?:\\n|$)")
            .find(body)?.groupValues?.get(1) ?: return null
        return canvasId to selectionId
    }

}
