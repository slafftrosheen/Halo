package com.halo.app.ui.screens.study

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.language.uiText
import com.halo.app.study.ResolvedStudySource
import com.halo.sdk.runtime.multimodal.ImageInputLimits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Local bounded preview only: no OCR, model load or inference is performed here. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeworkSourcePreview(source: ResolvedStudySource, locator: String?, kids: Boolean,
    onPage: (Int) -> Unit) {
    if (source !is ResolvedStudySource.Visual && source !is ResolvedStudySource.Pdf) return
    val pdf = source as? ResolvedStudySource.Pdf
    var page by rememberSaveable(source.objectId, locator) { mutableIntStateOf(
        (locator?.removePrefix("Page ")?.toIntOrNull() ?: pdf?.selectedPage ?: 1)
            .coerceIn(1, pdf?.pageCount?.coerceAtLeast(1) ?: 1)) }
    var notice by remember(source.objectId, page) { mutableStateOf<String?>(null) }
    val bitmap by produceState<Bitmap?>(null, source.objectId, source.revision, page) {
        value = null
        try {
            value = withContext(Dispatchers.IO) {
                val prepared = when (source) {
                    is ResolvedStudySource.Visual -> Graph.imageInputPreparer.prepare(
                        Graph.mediaAssetResolver.open(source.asset), source.asset,
                        ImageInputLimits(maxWidth = 1200, maxHeight = 1200, maxPixels = 1_440_000))
                    is ResolvedStudySource.Pdf -> Graph.studySourceResolver.pdf.render(
                        requireNotNull(Graph.workspaceFileRepository.getFile(com.halo.app.data.notebook.WorkspaceObjectId(source.objectId))), page)
                    else -> error("Not visual material")
                }
                BitmapFactory.decodeByteArray(prepared.content.bytes, 0, prepared.content.bytes.size)
                    ?: error("Unreadable preview")
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: OutOfMemoryError) { notice = "Not enough memory for the preview. Check the original material instead." }
        catch (_: Exception) { notice = "Could not open the source preview. Check the original material and edit the wording yourself." }
    }
    var zoom by remember(source.objectId, page) { mutableFloatStateOf(1f) }
    var pan by remember(source.objectId, page) { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { scale, offset, _ ->
        zoom = (zoom * scale).coerceIn(1f, 4f)
        pan = if (zoom > 1f) pan + offset else Offset.Zero
    }
    val target = if (kids) 56.dp else 48.dp
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(uiText("Original material"), style = MaterialTheme.typography.titleSmall)
        Text(uiText("Preview does not use AI or upload your material. Pinch to zoom; drag to move."),
            style = MaterialTheme.typography.bodySmall)
        bitmap?.let { image ->
            Box(Modifier.fillMaxWidth().height(320.dp).clipToBounds().transformable(transform)) {
                Image(image.asImageBitmap(), contentDescription = source.title,
                    modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = zoom, scaleY = zoom,
                        translationX = pan.x, translationY = pan.y), contentScale = ContentScale.Fit)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { zoom = (zoom + .5f).coerceAtMost(4f) }, modifier = Modifier.heightIn(min = target)) {
                    Text(uiText("Zoom in"))
                }
                TextButton(onClick = { zoom = 1f; pan = Offset.Zero }, modifier = Modifier.heightIn(min = target)) {
                    Text(uiText("Reset view"))
                }
            }
        } ?: if (notice == null) LinearProgressIndicator(Modifier.fillMaxWidth()) else Unit
        notice?.let { Text(uiText(it)) }
        if (pdf != null) HomeworkPageNavigation(page, pdf.pageCount, kids) { selected ->
            page = selected; onPage(selected)
        }
    }
}
