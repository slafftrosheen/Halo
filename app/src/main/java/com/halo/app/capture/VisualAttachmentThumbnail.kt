package com.halo.app.capture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import com.halo.sdk.runtime.multimodal.ImageInputLimits

@Composable
fun VisualAttachmentThumbnail(attachment: VisualAttachment, modifier: Modifier = Modifier) {
    val thumbnail by produceState<android.graphics.Bitmap?>(null, attachment.ref.id) {
        value = try {
            val content = com.halo.app.di.ManualDependencyGraph.mediaAssetStore.open(attachment.ref)
            val prepared = AndroidImageInputPreparer().prepare(content, attachment.ref,
                ImageInputLimits(maxWidth = 128, maxHeight = 128, maxPixels = 16_384, maxBytes = 64 * 1024))
            android.graphics.BitmapFactory.decodeByteArray(prepared.content.bytes, 0, prepared.content.bytes.size)
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { null }
    }
    thumbnail?.let { androidx.compose.foundation.Image(it.asImageBitmap(), "${attachment.source} attachment", modifier) }
}
