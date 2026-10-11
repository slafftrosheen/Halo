package com.halo.app.ui.screens.notebook

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.halo.app.data.workspace.CanvasPdfPageTools
import com.halo.app.data.workspace.WorkspaceFile
import com.halo.app.language.uiText
import kotlinx.coroutines.CancellationException

/** Single-page extraction; no page removal or rewrite of the source document. */
@Composable
internal fun CanvasPdfPageDialog(
    file: WorkspaceFile,
    busy: Boolean,
    onSave: (List<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val key = file.objectId.value + "|" + file.version
    var pageInput by remember(key) { mutableStateOf("1") }
    var countError by remember(key) { mutableStateOf(false) }
    val pages by produceState<Int?>(null, key) {
        try { value = CanvasPdfPageTools.pageCount(context, file) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { countError = true }
    }
    val selection = pages?.let { count ->
        com.halo.app.data.workspace.CanvasPdfPageSelection.parse(pageInput, count)
    }
    val number = selection?.firstOrNull()
    val valid = selection != null
    var previewError by remember(key, number) { mutableStateOf(false) }
    val bitmap by produceState<Bitmap?>(null, key, number, pages) {
        if (number != null) {
            try { value = CanvasPdfPageTools.preview(context, file, number) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { previewError = true }
        }
    }
    DisposableEffect(bitmap) { onDispose { bitmap?.recycle() } }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(uiText("Extract PDF pages")) },
        text = {
            Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(uiText("Select up to six PDF pages to save in one Notebook note. Original unchanged."),
                    style = MaterialTheme.typography.bodySmall)
                Text(file.displayName.take(100), style = MaterialTheme.typography.labelSmall)
                if (pages != null) Text(uiText("Pages") + ": " + pages)
                OutlinedTextField(pageInput, { updated ->
                    if (!busy) pageInput = updated.filter { it.isDigit() || it in ",-– " }.take(90)
                }, label = { Text(uiText("Pages to extract (e.g. 1,3-5)")) },
                    singleLine = true, enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), isError = !valid && pageInput.isNotEmpty())
                when {
                    countError -> Text(uiText("PDF preview unavailable"), color = MaterialTheme.colorScheme.error)
                    !valid -> Text(uiText("Choose 1–6 valid, unique pages."), style = MaterialTheme.typography.bodySmall)
                    bitmap != null -> Image(bitmap!!.asImageBitmap(), contentDescription = null,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp, max = 260.dp),
                        contentScale = ContentScale.Fit)
                    previewError -> Text(uiText("PDF preview unavailable"), color = MaterialTheme.colorScheme.error)
                    else -> Text(uiText("Loading preview…"), style = MaterialTheme.typography.bodySmall)
                }
                if (selection != null) Text(uiText("Pages selected") + ": " +
                    selection.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onSave(requireNotNull(selection)) }, enabled = !busy && valid &&
            bitmap != null && !previewError) { Text(uiText("Save pages as images")) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(uiText("Cancel")) } },
    )
}
