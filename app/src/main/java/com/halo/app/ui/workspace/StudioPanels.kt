package com.halo.app.ui.workspace

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.halo.app.R
import com.halo.app.data.studio.StudioDocument
import com.halo.app.data.studio.StudioService
import kotlinx.coroutines.CancellationException

/** Dialog windows retain the app's accessibility density and keep controls outside the scrolling body. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StudioAlertDialog(onDismissRequest: () -> Unit, title: @Composable () -> Unit,
    text: @Composable () -> Unit, confirmButton: @Composable () -> Unit, dismissButton: @Composable () -> Unit) {
    val density = LocalDensity.current
    Dialog(onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalDensity provides density) {
            Surface(Modifier.imePadding().safeDrawingPadding().padding(16.dp).widthIn(max = 640.dp)
                .fillMaxWidth().heightIn(max = 720.dp).testTag("studio-dialog"), shape = MaterialTheme.shapes.large) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { title() }
                        IconButton(onClick = onDismissRequest) { Icon(Icons.Default.Close, stringResource(R.string.studio_close)) }
                    }
                    Box(Modifier.weight(1f, fill = false).fillMaxWidth().padding(horizontal = 16.dp)) { text() }
                    FlowRow(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
                        dismissButton(); confirmButton()
                    }
                }
            }
        }
    }
}

@Composable
internal fun StudioDocumentPreview(service: StudioService, document: StudioDocument, modifier: Modifier = Modifier) {
    val pixels = document.copy(selectedLayerId = null)
    key(pixels) {
        var failed by remember { mutableStateOf(false) }
        val bitmap by produceState<Bitmap?>(null) {
            try { value = service.previewDocument(pixels) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
        }
        DisposableEffect(bitmap) { onDispose { bitmap?.takeUnless { it.isRecycled }?.recycle() } }
        Box(modifier.testTag("studio-preview"), contentAlignment = Alignment.Center) {
            bitmap?.let { InspectableImage(it, stringResource(R.string.studio_title), Modifier.fillMaxSize()) }
                ?: if (failed) Text(stringResource(R.string.studio_preview_error), Modifier.padding(16.dp))
                else CircularProgressIndicator()
        }
    }
}
