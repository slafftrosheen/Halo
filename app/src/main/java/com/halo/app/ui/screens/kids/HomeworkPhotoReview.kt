package com.halo.app.ui.screens.kids

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText

@Composable
internal fun HomeworkPhotoReviewContent(bitmap: Bitmap?, failed: Boolean, busy: Boolean,
    onUse: () -> Unit, onRetake: () -> Unit, onCancel: () -> Unit,
    reviewTitle: String? = null, reviewCaption: String? = null, useLabel: String? = null,
    retakeLabel: String? = null, photoLabel: String? = null, embedded: Boolean = false,
) {
    val layout = if (embedded) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().heightIn(max = 700.dp)
        .verticalScroll(rememberScrollState()).navigationBarsPadding().padding(20.dp)
    Column(layout, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(reviewTitle ?: uiText("Check your homework photo"), style = MaterialTheme.typography.titleLarge)
        Text(reviewCaption ?: uiText("Make sure the whole question is clear. Nothing is added to your lesson until you choose Use this."),
            style = MaterialTheme.typography.bodyMedium)
        if (bitmap != null) com.halo.app.ui.workspace.InspectableImage(bitmap, photoLabel ?: uiText("Homework photo"),
            modifier = Modifier.fillMaxWidth().height(260.dp))
        else if (failed) Text(uiText("This photo could not be opened. Take another photo or type the question instead."),
            color = MaterialTheme.colorScheme.error)
        else LinearProgressIndicator(Modifier.fillMaxWidth())
        KidsPrimaryButton(onUse, enabled = !busy && bitmap != null && !failed,
            modifier = Modifier.fillMaxWidth()) { Text(useLabel ?: uiText("Use this")) }
        KidsSecondaryButton(onRetake, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(retakeLabel ?: uiText("Take again"))
        }
        TextButton(onCancel, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(uiText("Cancel"))
        }
    }
}
