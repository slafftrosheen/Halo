package com.halo.app.data.search

import com.halo.app.data.notebook.*
import java.text.Normalizer
import java.util.Locale

/** Authoritative Room projection supplements AppSearch; no stale binary or model draft hits. */
internal object WorkspaceLocalContentSearch {
    fun folded(text: String) = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
    fun matches(text: String, query: String) = folded(text).contains(folded(query.trim()))
    fun snippet(text: String, query: String): String {
        val index = text.indexOf(query.trim(), ignoreCase = true).coerceAtLeast(0)
        val start = (index - 40).coerceAtLeast(0)
        return text.substring(start, minOf(start + 180, text.length))
    }
    fun safe(text: String) = com.halo.runtime.cognition.memory.MemorySensitivityClassifier.classify(text) != com.halo.runtime.cognition.memory.MemorySensitivity.SECRET
    fun cards(rows: List<CanvasSearchRow>, query: WorkspaceSearchQuery): List<WorkspaceSearchHit> = rows.mapNotNull { row ->
        if (query.canvasId != null && row.canvasId != query.canvasId) return@mapNotNull null
        if (query.types.isNotEmpty() && WorkspaceObjectType.CANVAS !in query.types) return@mapNotNull null
        val sourceType = row.sourceType?.let { runCatching { WorkspaceObjectType.valueOf(it) }.getOrNull() } ?: WorkspaceObjectType.CANVAS
        if (query.sourceTypes.isNotEmpty() && sourceType !in query.sourceTypes) return@mapNotNull null
        val text = listOfNotNull(row.sourceTitle, row.content, row.tags).joinToString("\n")
        if (!safe(text)) return@mapNotNull null
        if (row.content?.startsWith("sketch:v1|") == true || row.content?.startsWith("halo-markup:v1|") == true) return@mapNotNull null
        if (!matches(text, query.query)) return@mapNotNull null
        WorkspaceSearchHit(WorkspaceObjectId(row.canvasId), WorkspaceObjectType.CANVAS, row.boardTitle,
            snippet(text, query.query), 1f, row.nodeId, row.refObjectId)
    }
}
