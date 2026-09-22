package com.halo.app.ui.screens.notebook

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.halo.app.notebook.WorkspaceUseCases
import kotlinx.coroutines.launch

@Composable
fun WorkspaceObjectDetailScreen(
    id: String,
    type: String,
    workspace: WorkspaceUseCases,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fileRevision by remember { mutableIntStateOf(0) }
    val relinkLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { workspace.relinkFile(id, uri); fileRevision++ }
    }
    val item by produceState<com.halo.app.data.notebook.WorkspaceObject?>(null, id) {
        value = workspace.getObject(id)
    }
    val file by produceState<com.halo.app.data.workspace.WorkspaceFile?>(null, id, type, fileRevision) {
        value = if (type == "file") workspace.getFile(id) else null
    }
    val memory by produceState<com.halo.app.data.memory.MemoryRecord?>(null, id, type) {
        value = if (type == "memory") workspace.getMemory(id) else null
    }
    var correction by remember(memory?.content) { mutableStateOf(memory?.content.orEmpty()) }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        TextButton(onClick = onBack) { Text("Back") }
        Spacer(Modifier.height(12.dp))
        Text(item?.title ?: file?.displayName ?: "Workspace item",
            style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        if (type == "file") {
            if (file == null) {
                Text("File is unavailable", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(file!!.mimeType)
                Text(file!!.sizeBytes?.let { "${it / 1024} KB" } ?: "Size unknown")
                Text(file!!.availability.name.replace('_', ' ').lowercase())
                Text(if (file!!.ownership == com.halo.app.data.workspace.ContentOwnership.HALO_OWNED)
                    "Stored in Halo" else "Linked from another app")
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW)
                        .setDataAndType(file!!.uri, file!!.mimeType)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    runCatching { context.startActivity(intent) }
                }) { Text("Open file") }
                if (file!!.ownership != com.halo.app.data.workspace.ContentOwnership.HALO_OWNED) {
                    TextButton(onClick = {
                        scope.launch { workspace.keepLocalCopy(id); fileRevision++ }
                    }) { Text("Keep local copy") }
                    TextButton(onClick = { relinkLauncher.launch(arrayOf("*/*")) }) {
                        Text("Relink file")
                    }
                }
            }
        } else if (type == "memory") {
            if (memory == null) {
                Text("Memory is unavailable", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("Source: ${memory!!.sourceObjectId?.value ?: "User"}")
                Text("Revision: ${memory!!.sourceRevision ?: 1}")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = correction, onValueChange = { correction = it },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Memory") })
                Button(onClick = { scope.launch { workspace.correctMemory(id, correction) } },
                    enabled = correction.isNotBlank() && correction != memory!!.content) {
                    Text("Save correction")
                }
                TextButton(onClick = { scope.launch { workspace.forgetMemory(id); onBack() } }) {
                    Text("Forget")
                }
            }
        }
    }
}
