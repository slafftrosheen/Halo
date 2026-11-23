package com.halo.app.ui.instruments

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.R

internal enum class InstrumentMenu(@StringRes val label: Int) {
    FILE(R.string.shell_menu_file), EDIT(R.string.shell_menu_edit), INSERT(R.string.shell_menu_insert),
    FORMAT(R.string.shell_menu_format), VIEW(R.string.shell_menu_view), TOOLS(R.string.shell_menu_tools), HELP(R.string.shell_menu_help);

    val easy get() = this == FILE || this == EDIT || this == INSERT || this == HELP

    @Composable
    fun title(tool: String): String = stringResource(if (this == FORMAT) when (tool) {
        "studio" -> R.string.shell_format_image
        "canvas" -> R.string.shell_format_path
        "sheets" -> R.string.shell_format_data
        "planner" -> R.string.shell_format_event
        else -> label
    } else label)
}

internal fun instrumentMenu(group: String, id: String): InstrumentMenu {
    val final = id.substringAfterLast('.').removePrefix("view-")
    return when {
        final in setOf("save", "open", "new", "version", "print", "close", "restore_version") ||
            final.startsWith("export") || final.startsWith("import") -> InstrumentMenu.FILE
        final.startsWith("add") || final.startsWith("insert") -> InstrumentMenu.INSERT
        final in setOf("undo", "redo", "rename", "delete", "copy", "cut", "paste", "duplicate", "discard") -> InstrumentMenu.EDIT
        group in setOf("File", "Export", "Keep") -> InstrumentMenu.FILE
        group in setOf("Edit", "Select", "Arrange", "Retouch", "Correct") -> InstrumentMenu.EDIT
        group in setOf("Insert", "Add", "Make", "Write") -> InstrumentMenu.INSERT
        group in setOf("Format", "Text", "Data", "Image", "Path", "Colour", "Style", "Adjust") -> InstrumentMenu.FORMAT
        group in setOf("View", "Browse", "Find", "Layers", "Zoom") -> InstrumentMenu.VIEW
        group in setOf("Help", "Try", "Guide") -> InstrumentMenu.HELP
        else -> InstrumentMenu.TOOLS
    }
}

@Composable
internal fun <T> OrderedToolMenus(
    tool: String,
    easy: Boolean,
    actions: List<T>,
    menuOf: (T) -> InstrumentMenu,
    labelOf: @Composable (T) -> String,
    enabled: (T) -> Boolean,
    onAction: (T) -> Unit,
    onHelp: () -> Unit,
) {
    var open by remember(tool) { mutableStateOf<InstrumentMenu?>(null) }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        InstrumentMenu.entries.filter { !easy || it.easy }.forEach { menu ->
            val entries = actions.filter { menuOf(it) == menu }
            Box {
                TextButton(onClick = { open = menu }, enabled = entries.isNotEmpty() || menu == InstrumentMenu.HELP,
                    modifier = Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 10.dp)) {
                    Text(menu.title(tool))
                }
                DropdownMenu(open == menu, { open = null }) {
                    entries.forEach { action ->
                        DropdownMenuItem(text = { Text(labelOf(action)) }, enabled = enabled(action),
                            modifier = Modifier.heightIn(min = 48.dp), onClick = { open = null; onAction(action) })
                    }
                    if (menu == InstrumentMenu.HELP) DropdownMenuItem(
                        text = { Text(stringResource(R.string.shell_find_action)) },
                        onClick = { open = null; onHelp() })
                }
            }
        }
    }
}
