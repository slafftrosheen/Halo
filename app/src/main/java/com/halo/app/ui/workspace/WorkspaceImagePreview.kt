package com.halo.app.ui.workspace

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.halo.app.data.workspace.NoteAttachment
import com.halo.app.language.uiText
import com.halo.app.ui.design.HaloShapes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

private data class ImagePreviewState(val bitmap: Bitmap? = null, val loading: Boolean = true)

/** Reopen the source for bounds and pixels, keeping large originals off the main thread. */
internal fun decodeWorkspacePreview(open: () -> InputStream?): Bitmap? {
    val bounds = BitmapFactory.Options().also { it.inJustDecodeBounds = true }
    open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val options = BitmapFactory.Options().also { it.inSampleSize = 1 }
    while (bounds.outWidth / options.inSampleSize > 1024 || bounds.outHeight / options.inSampleSize > 1024) options.inSampleSize *= 2
    return open()?.use { BitmapFactory.decodeStream(it, null, options) }
}

@Composable
fun GeneratedImagePreview(bytes: ByteArray, modifier: Modifier = Modifier, label: String = uiText("Generated image preview")) {
    ImageSourcePreview(bytes, label, modifier) { bytes.inputStream() }
}

@Composable
fun WorkspaceAttachmentPreview(attachment: NoteAttachment, modifier: Modifier = Modifier, onOpen: (() -> Unit)? = null) {
    val resolver = androidx.compose.ui.platform.LocalContext.current.contentResolver
    ImageSourcePreview(attachment.pathOrUri, attachment.displayName ?: uiText("Image"), modifier, onOpen) {
        if (attachment.storageType == "MANAGED_FILE") java.io.File(attachment.pathOrUri).inputStream()
        else resolver.openInputStream(android.net.Uri.parse(attachment.pathOrUri))
    }
}

@Composable
private fun ImageSourcePreview(key: Any, label: String, modifier: Modifier, onOpen: (() -> Unit)? = null, open: () -> InputStream?) {
    var retry by remember(key) { mutableIntStateOf(0) }
    val state by produceState(ImagePreviewState(), key, retry) {
        value = ImagePreviewState()
        val bitmap = withContext(Dispatchers.IO) {
            try { decodeWorkspacePreview(open) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        }
        value = ImagePreviewState(bitmap, loading = false)
    }
    Surface(modifier, shape = HaloShapes.control, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val bitmap = state.bitmap
            when {
                bitmap != null -> InspectableImage(bitmap, label, Modifier.fillMaxSize(), onOpen)
                state.loading -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                else -> Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(uiText("Preview unavailable"), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { retry++ }) { Text(uiText("Try again")) }
                }
            }
        }
    }
}

@Composable
fun InspectableImage(bitmap: Bitmap, label: String, modifier: Modifier = Modifier, onOpen: (() -> Unit)? = null) {
    var expanded by remember(bitmap) { mutableStateOf(false) }
    Image(bitmap.asImageBitmap(), label,
        modifier.clickable(onClickLabel = uiText("View image")) { if (onOpen != null) onOpen() else expanded = true },
        contentScale = ContentScale.Fit)
    if (expanded) ImageViewer(bitmap, label) { expanded = false }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImageViewer(bitmap: Bitmap, label: String, onDismiss: () -> Unit) {
    var scale by remember(bitmap) { mutableFloatStateOf(1f) }
    var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    TextButton(onClick = onDismiss) { Text(uiText("Close")) }
                }
                Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().pointerInput(bitmap) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 4f)
                        val limitX = size.width * (scale - 1f) / 2f
                        val limitY = size.height * (scale - 1f) / 2f
                        offset = Offset((offset.x + pan.x).coerceIn(-limitX, limitX), (offset.y + pan.y).coerceIn(-limitY, limitY))
                    }
                }, contentAlignment = Alignment.Center) {
                    Image(bitmap.asImageBitmap(), label, Modifier.fillMaxSize().graphicsLayer {
                        scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
                    }, contentScale = ContentScale.Fit)
                }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { scale = (scale / 1.5f).coerceAtLeast(1f); offset = Offset.Zero }, enabled = scale > 1f) { Text(uiText("Zoom out")) }
                    TextButton(onClick = { scale = 1f; offset = Offset.Zero }, enabled = scale != 1f) { Text(uiText("Fit image")) }
                    TextButton(onClick = { scale = (scale * 1.5f).coerceAtMost(4f) }, enabled = scale < 4f) { Text(uiText("Zoom in")) }
                }
            }
        }
    }
}
