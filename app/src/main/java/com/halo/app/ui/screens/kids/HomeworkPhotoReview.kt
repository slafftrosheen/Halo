package com.halo.app.ui.screens.kids

import android.graphics.Bitmap
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
import com.halo.app.capture.AndroidImageInputPreparer
import com.halo.app.language.uiText
import com.halo.sdk.runtime.multimodal.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Decode only. Pending camera files do not enter Workspace or inference until Use this. */
internal suspend fun loadHomeworkPhotoPreview(path: String): Bitmap = withContext(Dispatchers.IO) {
    val file = File(path)
    require(file.length() in 1..(32L * 1024 * 1024))
    val prepared = AndroidImageInputPreparer().prepare(MediaAssetContent(file.readBytes(), "image/jpeg"), null,
        ImageInputLimits(maxWidth = 1200, maxHeight = 1200, maxPixels = 1_440_000,
            maxSourceBytes = 32 * 1024 * 1024))
    BitmapFactory.decodeByteArray(prepared.content.bytes, 0, prepared.content.bytes.size)
        ?: error("Unreadable photo")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeworkPhotoReview(path: String, busy: Boolean, onUse: () -> Unit,
    onRetake: () -> Unit, onCancel: () -> Unit) {
    var failed by remember(path) { mutableStateOf(false) }
    val bitmap by produceState<Bitmap?>(null, path) {
        try { value = loadHomeworkPhotoPreview(path) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    ModalBottomSheet(onDismissRequest = { if (!busy) onCancel() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        HomeworkPhotoReviewContent(bitmap, failed, busy, onUse, onRetake, onCancel)
    }
}

@Composable
internal fun HomeworkPhotoReviewContent(bitmap: Bitmap?, failed: Boolean, busy: Boolean,
    onUse: () -> Unit, onRetake: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(max = 700.dp).verticalScroll(rememberScrollState())
        .navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(uiText("Check your homework photo"), style = MaterialTheme.typography.titleLarge)
        Text(uiText("Make sure the whole question is clear. Nothing is added to your lesson until you choose Use this."),
            style = MaterialTheme.typography.bodyMedium)
        if (bitmap != null) com.halo.app.ui.workspace.InspectableImage(bitmap, uiText("Homework photo"),
            modifier = Modifier.fillMaxWidth().height(260.dp))
        else if (failed) Text(uiText("This photo could not be opened. Take another photo or type the question instead."),
            color = MaterialTheme.colorScheme.error)
        else LinearProgressIndicator(Modifier.fillMaxWidth())
        KidsPrimaryButton(onUse, enabled = !busy && bitmap != null && !failed,
            modifier = Modifier.fillMaxWidth()) { Text(uiText("Use this")) }
        KidsSecondaryButton(onRetake, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(uiText("Take again"))
        }
        TextButton(onCancel, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(uiText("Cancel"))
        }
    }
}
