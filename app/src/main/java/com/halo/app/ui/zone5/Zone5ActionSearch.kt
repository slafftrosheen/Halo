package com.halo.app.ui.zone5

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.halo.app.R
import com.halo.app.language.uiText
import com.halo.app.ui.presence.HaloOrbContextAction

/** Search text only over already offered actions. This cannot introduce or dispatch a command. */
@Composable
internal fun actionMatchesQuery(action: HaloOrbContextAction, query: String): Boolean {
    if (query.isBlank()) return true
    val actualId = action.id.substringAfterLast('.')
    val id = when {
        actualId.startsWith("export-") -> "export"
        actualId.startsWith("add-") -> "add"
        actualId.startsWith("new-") -> "new"
        actualId.startsWith("save-") -> "save"
        else -> actualId
    }
    val aliases = when (id) {
        "save", "keep", "checkpoint", "save-draft" -> R.string.zone5_search_keep
        "export", "export-copy", "download" -> R.string.zone5_search_export
        "add", "insert", "new", "new-canvas", "new-note", "import" -> R.string.zone5_search_insert
        "undo" -> R.string.zone5_search_undo
        "redo" -> R.string.zone5_search_redo
        "rename" -> R.string.zone5_search_name
        "find", "search", "find-replace" -> R.string.zone5_search_find
        "properties", "adjust", "format", "appearance", "customize", "arrange" -> R.string.zone5_search_properties
        "layers" -> R.string.zone5_search_layers
        "draw", "ink" -> R.string.zone5_search_draw
        else -> null
    }
    val searchable = uiText(action.title) + " " + (aliases?.let { stringResource(it) } ?: "")
    return query.trim().split(Regex("\\s+")).all { searchable.contains(it, ignoreCase = true) }
}
