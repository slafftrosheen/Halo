package com.halo.app.ui.workspace

import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.app.language.uiText
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp
import com.halo.app.data.notebook.WorkspaceObject
import com.halo.app.ui.presence.*

@Composable
fun WorkspaceObjectPicker(
    candidates: List<WorkspaceObject>,
    title: String = "Choose Workspace material",
    onPick: (WorkspaceObject) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, candidates) {
        if (query.isBlank()) candidates.take(40)
        else candidates.filter { it.title.contains(query, ignoreCase = true) }.take(40)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                HaloTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text(uiText("Filter")) },
                    modifier = Modifier.fillMaxWidth(), kind = VoiceFieldKind.SEARCH)
                Spacer(Modifier.height(6.dp))
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (filtered.isEmpty()) Text(uiText("No workspace objects to reference."))
                    filtered.forEach { item ->
                        Surface(
                            onClick = { onPick(item) },
                            color = MaterialTheme.colorScheme.background,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                HaloSignalGlyph(kind = HaloSignalKind.FILE, size = 20.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(item.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(uiText("Cancel")) } },
    )
}
