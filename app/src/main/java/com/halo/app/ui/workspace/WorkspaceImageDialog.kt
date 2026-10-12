package com.halo.app.ui.workspace

import com.halo.app.language.uiText
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun WorkspaceImageDialog(studio: WorkspaceImageStudio, onDismiss: () -> Unit) {
    val state by studio.state.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    LaunchedEffect(studio) { studio.refresh() }
    AlertDialog(onDismissRequest = { if (!state.saving) { studio.cancel(); onDismiss() } },
        title = { Text(if (state.referenceLabel == null) "Create an image" else "Edit an image") },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(uiText("Describe it, preview it, then save. The original stays intact."), style = MaterialTheme.typography.bodySmall)
                Box {
                    OutlinedButton(onClick = { menu = true }, enabled = !state.busy && !state.saving && !state.loading) {
                        Text(state.selected?.let { "${it.providerName} / ${it.model.displayName}" } ?: "Choose an image model", maxLines = 2)
                    }
                    DropdownMenu(menu, { menu = false }, modifier = Modifier.heightIn(max = 300.dp)) {
                        OutlinedTextField(search, { search = it }, label = { Text(uiText("Search image models")) })
                        state.choices.filter { "${it.providerName} ${it.model.displayName}".contains(search, true) }.forEach { choice ->
                            DropdownMenuItem(text = { Text("${choice.providerName} / ${choice.model.displayName}") }, onClick = { studio.select(choice); menu = false })
                        }
                    }
                }
                TextButton(onClick = studio::refresh, enabled = !state.loading && !state.busy && !state.saving) { Text(uiText("Refresh image models")) }
                state.referenceLabel?.let { Text("Reference: $it", style = MaterialTheme.typography.bodySmall) }
                OutlinedTextField(state.prompt, studio::prompt, modifier = Modifier.fillMaxWidth(), minLines = 3,
                    label = { Text(if (state.result == null) "Describe your image or edit" else "Prompt or changes for this preview") }, enabled = !state.busy && !state.saving)
                if (state.loading || state.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(if (state.loading) "Loading image models..." else "Creating your image...") }
                state.result?.let { image ->
                    GeneratedImagePreview(image.bytes, Modifier.fillMaxWidth().height(220.dp))
                    Text("${image.providerId} / ${image.modelId}", style = MaterialTheme.typography.labelSmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { studio.generate() }, enabled = !state.busy && !state.saving) { Text(uiText("Regenerate")) }
                        TextButton(onClick = { studio.generate(editPreview = true) }, enabled = !state.busy && !state.saving && state.selected?.model?.supportsEditing == true) { Text(uiText("Edit preview")) }
                    }
                }
                state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                Text(uiText("Uses the provider credentials in Settings. Your prompt and any reference image are sent to that provider."), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            if (state.result != null) Button(onClick = { studio.saveResult(onDismiss) }, enabled = !state.busy && !state.saving) { Text(if (state.saving) "Saving..." else "Save image") }
            else Button(onClick = { studio.generate() }, enabled = state.selected != null && state.prompt.isNotBlank() && !state.busy && !state.loading) { Text(uiText("Generate")) }
        }, dismissButton = { TextButton(onClick = { studio.cancel(); onDismiss() }, enabled = !state.saving) { Text(if (state.busy) "Cancel generation" else "Close") } })
}

@Composable
fun GeneratedImagePreview(bytes: ByteArray, modifier: Modifier = Modifier) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, bytes) {
        value = withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().also { it.inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val options = BitmapFactory.Options()
            while (bounds.outWidth / options.inSampleSize > 1024 || bounds.outHeight / options.inSampleSize > 1024) options.inSampleSize *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }
    }
    bitmap?.let { Image(it.asImageBitmap(), "Generated image preview", modifier, contentScale = ContentScale.Fit) }
}

@Composable
fun WorkspaceAttachmentPreview(attachment: com.halo.app.data.workspace.NoteAttachment, modifier: Modifier = Modifier) {
    val resolver = androidx.compose.ui.platform.LocalContext.current.contentResolver
    val bitmap by produceState<android.graphics.Bitmap?>(null, attachment.pathOrUri) {
        value = withContext(Dispatchers.IO) {
            fun stream() = if (attachment.storageType == "MANAGED_FILE") java.io.File(attachment.pathOrUri).inputStream()
                else resolver.openInputStream(android.net.Uri.parse(attachment.pathOrUri))
            try {
                val bounds = BitmapFactory.Options().also { it.inJustDecodeBounds = true }
                stream()?.use { BitmapFactory.decodeStream(it, null, bounds) }
                val options = BitmapFactory.Options()
                while (bounds.outWidth / options.inSampleSize > 512 || bounds.outHeight / options.inSampleSize > 512) options.inSampleSize *= 2
                stream()?.use { BitmapFactory.decodeStream(it, null, options) }
            } catch (_: Exception) { null }
        }
    }
    bitmap?.let { Image(it.asImageBitmap(), attachment.displayName ?: "Workspace image", modifier, contentScale = ContentScale.Fit) }
}
