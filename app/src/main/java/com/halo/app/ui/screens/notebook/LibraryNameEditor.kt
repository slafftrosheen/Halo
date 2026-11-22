package com.halo.app.ui.screens.notebook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.data.notebook.WorkspaceObjectLifecycle
import com.halo.app.data.notebook.WorkspaceObjectType
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal fun canRenameLibraryItem(item: NotebookWorkspaceItem): Boolean =
    item.objectRef.lifecycle == WorkspaceObjectLifecycle.ACTIVE &&
        !item.objectRef.contentRef.startsWith("file-tools:") && item.libraryKind != HaloLibraryKind.PLACE &&
        when (item.objectRef.type) {
            WorkspaceObjectType.NOTE -> true
            WorkspaceObjectType.FILE, WorkspaceObjectType.MEDIA -> item.renamableFileVersion != null
            else -> false
        }

@Composable
internal fun LibraryNameEditor(item: NotebookWorkspaceItem, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val oldName = item.objectRef.title
    var title by remember(item.objectRef.id) { mutableStateOf(oldName) }
    val locale = LocalConfiguration.current.locales[0]
    val kind = item.libraryKind
    val suggested = if (kind == HaloLibraryKind.PHOTO || kind == HaloLibraryKind.SCAN || kind == HaloLibraryKind.RECORDING) {
        suggestLibraryName(item, stringResource(kind.label), locale)
    } else null
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.halo11_library_name_item)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HaloTextField(title, { title = it.take(160) }, label = { Text(stringResource(R.string.halo11_library_name_label)) },
                kind = VoiceFieldKind.SHORT, maxChars = 160, singleLine = true)
            suggested?.takeIf { it != title }?.let { name ->
                TextButton(onClick = { title = name }) { Text(stringResource(R.string.halo11_library_use_name, name)) }
            }
        } },
        confirmButton = { TextButton(onClick = { onSave(title) }, enabled = title.isNotBlank() && title.trim() != oldName) {
            Text(uiText("Save"))
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(uiText("Cancel")) } })
}

internal fun suggestLibraryName(item: NotebookWorkspaceItem, what: String, locale: Locale): String? {
    val source = item.objectRef
    val date = item.nameFacts?.date ?: source.createdAt.takeIf { it > 0 }?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
    }
    val dateLabel = date?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    val location = item.nameFacts?.city?.takeIf { it.none { c -> c == '/' || c == '\\' } }
    val suffix = if (source.type == WorkspaceObjectType.NOTE) "" else
        source.title.substringAfterLast('.', "").takeIf(String::isNotBlank)?.let { ".$it" }.orEmpty()
    if (dateLabel == null && location == null) return null
    return (listOfNotNull(what, location, dateLabel).joinToString(" · ") + suffix).takeIf { it.length <= 160 }
}
