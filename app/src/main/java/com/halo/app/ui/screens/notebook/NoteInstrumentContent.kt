package com.halo.app.ui.screens.notebook

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText
import com.halo.app.ui.zone6.Zone6ButtonSheet

@Composable
internal fun NoteInstrumentTableDialog(enabled: Boolean, onInsert: (String) -> Unit, onDismiss: () -> Unit) {
    var columns by remember { mutableStateOf("2") }
    var rows by remember { mutableStateOf("2") }
    val countX = columns.toIntOrNull()?.takeIf { it in 1..20 }
    val countY = rows.toIntOrNull()?.takeIf { it in 1..50 }
    val columnLabel = uiText("Column")
    AlertDialog(onDismissRequest = onDismiss, title = { Text(uiText("Insert table")) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(columns, { columns = it.take(2) }, singleLine = true, label = { Text(uiText("Columns (1–20)")) })
            OutlinedTextField(rows, { rows = it.take(2) }, singleLine = true, label = { Text(uiText("Rows (1–50)")) })
            if (!enabled) Text(uiText("The note changed. Open Insert table again."))
        }
    }, confirmButton = {
        TextButton(onClick = {
            val width = requireNotNull(countX)
            val height = requireNotNull(countY)
            fun row(cells: List<String>) = cells.joinToString(" | ", "| ", " |")
            onInsert(buildList {
                add(row(List(width) { "$columnLabel ${it + 1}" }))
                add(row(List(width) { "---" }))
                repeat(height) { add(row(List(width) { " " })) }
            }.joinToString("\n"))
        }, enabled = enabled && countX != null && countY != null) { Text(uiText("Insert")) }
    }, dismissButton = { TextButton(onDismiss) { Text(uiText("Cancel")) } })
}

internal fun isNoteInstrumentTable(text: String): Boolean {
    val lines = text.lines()
    if (lines.size < 2 || lines.any { !it.trim().let { line -> line.startsWith('|') && line.endsWith('|') } }) return false
    val fields = noteInstrumentTableCells(lines[1])
    return fields.isNotEmpty() && fields.all { it.trim(':').let { marks -> marks.length >= 3 && marks.all { char -> char == '-' } } } &&
        lines.all { noteInstrumentTableCells(it).size == fields.size }
}

private fun noteInstrumentTableCells(line: String): List<String> = line.trim().removePrefix("|").removeSuffix("|")
    .split('|').map(String::trim)

@Composable
internal fun NoteInstrumentTable(text: String) {
    val rows = remember(text) { text.lines().filterIndexed { index, _ -> index != 1 }.map(::noteInstrumentTableCells) }
    Column(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
        rows.forEachIndexed { index, row ->
            Row(Modifier.background(if (index == 0) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface)) {
                row.forEach { value ->
                    Text(value.ifEmpty { " " }, Modifier.width(120.dp).heightIn(min = 40.dp).padding(8.dp),
                        fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
internal fun NoteInstrumentTemplates(onChoose: (String?) -> Unit) {
    val letter = uiText("Letter")
    val cv = uiText("CV")
    val recipe = uiText("Recipe card")
    val meeting = uiText("Meeting notes")
    val invite = uiText("Invitation")
    val templates = listOf(
        letter to "# $letter\n\n${uiText("Dear …,")}\n\n${uiText("Write your message here.")}\n\n${uiText("Sincerely,")}",
        cv to "# $cv\n\n## ${uiText("Contact details")}\n\n## ${uiText("Experience")}\n\n## ${uiText("Education")}\n\n## ${uiText("Skills")}",
        recipe to "# $recipe\n\n## ${uiText("Ingredients")}\n- …\n\n## ${uiText("Steps")}\n1. …",
        meeting to "# $meeting\n\n## ${uiText("Attendees")}\n\n## ${uiText("Decisions")}\n\n## ${uiText("Next steps")}\n- [ ] …",
        invite to "# $invite\n\n## ${uiText("What")}\n\n## ${uiText("When")}\n\n## ${uiText("Where")}\n\n## RSVP",
    )
    Zone6ButtonSheet(uiText("Insert a template"), { onChoose(null) }) {
        templates.forEach { (label, text) -> TextButton(onClick = { onChoose(text) }) { Text(label) } }
    }
}
