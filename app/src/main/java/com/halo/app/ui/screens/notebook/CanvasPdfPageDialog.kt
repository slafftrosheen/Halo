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
    onSave: (Int) -> Unit,
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
    val number = pageInput.toIntOrNull()
    val valid = pages != null && number != null && number in 1..pages!!
    var previewError by remember(key, number) { mutableStateOf(false) }
    val bitmap by produceState<Bitmap?>(null, key, number, pages) {
        if (valid) {
            try { value = CanvasPdfPageTools.preview(context, file, number!!) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { previewError = true }
        }
    }
    DisposableEffect(bitmap) { onDispose { bitmap?.recycle() } }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(uiText("Extract PDF page")) },
        text = {
            Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(uiText("Save one PDF page as a new Notebook image. Original unchanged."),
                    style = MaterialTheme.typography.bodySmall)
                Text(file.displayName.take(100), style = MaterialTheme.typography.labelSmall)
                if (pages != null) Text(uiText("Pages") + ": " + pages)
                OutlinedTextField(pageInput, { updated ->
                    if (!busy) pageInput = updated.filter(Char::isDigit).take(6)
                }, label = { Text(uiText("Page number")) },
                    singleLine = true, enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), isError = !valid && pageInput.isNotEmpty())
                when {
                    countError -> Text(uiText("PDF preview unavailable"), color = MaterialTheme.colorScheme.error)
                    !valid -> Text(uiText("Choose a page in this PDF."), style = MaterialTheme.typography.bodySmall)
                    bitmap != null -> Image(bitmap!!.asImageBitmap(), contentDescription = null,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp, max = 260.dp),
                        contentScale = ContentScale.Fit)
                    previewError -> Text(uiText("PDF preview unavailable"), color = MaterialTheme.colorScheme.error)
                    else -> Text(uiText("Loading preview…"), style = MaterialTheme.typography.bodySmall)
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onSave(number!!) }, enabled = !busy && valid &&
            bitmap != null && !previewError) { Text(uiText("Save page image")) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(uiText("Cancel")) } },
    )
}
