package com.halo.app.capture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import com.halo.sdk.runtime.multimodal.ImageInputLimits

@Composable
fun VisualAttachmentThumbnail(attachment: VisualAttachment, modifier: Modifier = Modifier) {
    var loaded by remember(attachment.ref.id) { mutableStateOf(false) }
    val thumbnail by produceState<android.graphics.Bitmap?>(null, attachment.ref.id) {
        value = try {
            val content = com.halo.app.di.ManualDependencyGraph.mediaAssetResolver.open(attachment.ref)
            val prepared = AndroidImageInputPreparer().prepare(content, attachment.ref,
                ImageInputLimits(maxWidth = 128, maxHeight = 128, maxPixels = 16_384, maxBytes = 64 * 1024))
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                android.graphics.BitmapFactory.decodeByteArray(prepared.content.bytes, 0, prepared.content.bytes.size)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        finally { loaded = true }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        thumbnail?.let { androidx.compose.foundation.Image(it.asImageBitmap(), com.halo.app.language.uiText("Attached image"), androidx.compose.ui.Modifier.matchParentSize()) }
            ?: if (!loaded) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(androidx.compose.ui.res.painterResource(com.halo.app.R.drawable.halo_ic_image),
                com.halo.app.language.uiText("Preview unavailable"), Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
