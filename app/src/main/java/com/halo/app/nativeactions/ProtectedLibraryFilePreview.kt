package com.halo.app.nativeactions

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.media.MediaDataSource
import android.media.MediaPlayer
import android.os.Handler
import android.os.HandlerThread
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import android.system.ErrnoException
import android.system.OsConstants
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private sealed interface ProtectedPreview {
    data class Picture(val bitmap: Bitmap, val documentPages: Int? = null) : ProtectedPreview
    data class Document(val text: String, val truncated: Boolean) : ProtectedPreview
}

@Composable
internal fun ProtectedLibraryFilePreview(
    store: PrivateLibraryStore,
    access: VaultSession,
    envelopeId: String,
    itemId: String,
    entry: PrivateLibraryMediaEntry,
) {
    val mime = entry.mimeType.substringBefore(';').trim().lowercase()
    if (mime.startsWith("audio/")) {
        ProtectedLibraryAudioPreview(store, access, envelopeId, itemId, entry.id)
        return
    }
    if (mime !in pictureTypes && mime !in documentTypes && mime != "application/pdf") {
        Text(stringResource(R.string.halo11_private_preview_restore), style = MaterialTheme.typography.bodySmall)
        return
    }
    val context = LocalContext.current.applicationContext
    var requested by remember(access, envelopeId, itemId, entry.id) { mutableStateOf(false) }
    var preview by remember(access, envelopeId, itemId, entry.id) { mutableStateOf<ProtectedPreview?>(null) }
    var failed by remember(access, envelopeId, itemId, entry.id) { mutableStateOf(false) }
    LaunchedEffect(access, envelopeId, itemId, entry.id, requested) {
        if (!requested) return@LaunchedEffect
        try {
            preview = withContext(Dispatchers.IO) {
                if (mime in pictureTypes) {
                    val media = store.media(access, envelopeId, itemId, entry.id)
                    if (media != null) {
                        try { decodePicture(media.bytes) } finally { media.bytes.fill(0) }
                    } else {
                        store.openMedia(access, envelopeId, itemId, entry.id).use(::decodeLargePicture)
                    }
                } else {
                    store.openMedia(access, envelopeId, itemId, entry.id).use { source ->
                        if (mime == "application/pdf") renderFirstPage(context, source) else readDocumentStart(source)
                    }
                }
            }.also { access.requireOpen() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        }
    }
    when (val result = preview) {
        is ProtectedPreview.Picture -> {
            Image(
                bitmap = result.bitmap.asImageBitmap(),
                contentDescription = entry.title.ifBlank { stringResource(R.string.halo11_private_preview) },
                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                contentScale = ContentScale.Fit,
            )
            result.documentPages?.takeIf { it > 1 }?.let { pages ->
                Text(stringResource(R.string.halo11_private_preview_first_page, pages))
            }
        }
        is ProtectedPreview.Document -> {
            Text(result.text.ifBlank { stringResource(R.string.halo11_private_preview_empty) })
            if (result.truncated) Text(stringResource(R.string.halo11_private_preview_truncated))
        }
        null -> when {
            failed -> Text(stringResource(R.string.halo11_private_preview_restore), style = MaterialTheme.typography.bodySmall)
            requested -> Text(stringResource(R.string.halo11_private_preview_loading))
            else -> TextButton(onClick = { requested = true }) { Text(stringResource(R.string.halo11_private_preview)) }
        }
    }
}

@Composable
private fun ProtectedLibraryAudioPreview(
    store: PrivateLibraryStore,
    access: VaultSession,
    envelopeId: String,
    itemId: String,
    mediaId: String,
) {
    var playing by remember(access, envelopeId, itemId, mediaId) { mutableStateOf(false) }
    var failed by remember(access, envelopeId, itemId, mediaId) { mutableStateOf(false) }
    LaunchedEffect(access, envelopeId, itemId, mediaId, playing) {
        if (!playing) return@LaunchedEffect
        var source: ProtectedAudioSource? = null
        var player: MediaPlayer? = null
        try {
            val data = ProtectedAudioSource(store.openMedia(access, envelopeId, itemId, mediaId)).also { source = it }
            access.requireOpen()
            val playback = MediaPlayer().also { player = it }
            playback.setAudioAttributes(
                android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build(),
            )
            playback.setDataSource(data)
            suspendCancellableCoroutine<Unit> { continuation ->
                playback.setOnPreparedListener {
                    if (continuation.isActive) try {
                        access.requireOpen()
                        it.start()
                    } catch (error: Exception) {
                        continuation.resumeWithException(error)
                    }
                }
                playback.setOnCompletionListener { if (continuation.isActive) continuation.resume(Unit) }
                playback.setOnErrorListener { _, _, _ ->
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Audio preview failed"))
                    true
                }
                playback.prepareAsync()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        } finally {
            try { player?.release() } finally { source?.close() }
        }
        playing = false
    }
    TextButton(onClick = { failed = false; playing = !playing }) {
        Text(stringResource(if (playing) R.string.halo11_private_preview_stop else R.string.halo11_private_preview_play))
    }
    if (failed) Text(stringResource(R.string.halo11_private_preview_restore), style = MaterialTheme.typography.bodySmall)
}

private fun readDocumentStart(source: PrivateLibraryReadSource): ProtectedPreview.Document {
    val bytes = ByteArray(minOf(source.size, 64 * 1024L).toInt())
    return try {
        val count = source.readAt(0, bytes, 0, bytes.size).coerceAtLeast(0)
        val text = String(bytes, 0, count, Charsets.UTF_8)
        ProtectedPreview.Document(text.take(16_000), count < source.size || text.length > 16_000)
    } finally {
        bytes.fill(0)
    }
}

private fun renderFirstPage(context: Context, source: PrivateLibraryReadSource): ProtectedPreview.Picture {
    val storage = requireNotNull(context.getSystemService(StorageManager::class.java))
    val callbacks = HandlerThread("Private document preview").apply { start() }
    try {
        val descriptor = storage.openProxyFileDescriptor(ParcelFileDescriptor.MODE_READ_ONLY,
            object : ProxyFileDescriptorCallback() {
                override fun onGetSize() = source.size
                override fun onRead(offset: Long, size: Int, data: ByteArray): Int = try {
                    source.readAt(offset, data, 0, size).coerceAtLeast(0)
                } catch (error: Exception) {
                    throw ErrnoException("read", OsConstants.EIO, error)
                }
                override fun onRelease() = Unit
            }, Handler(callbacks.looper))
        val renderer = try { PdfRenderer(descriptor) } catch (error: Exception) {
            descriptor.close()
            throw error
        }
        renderer.use { pdf ->
            check(pdf.pageCount > 0)
            pdf.openPage(0).use { page ->
                val edge = maxOf(page.width, page.height)
                check(edge > 0)
                val scale = minOf(2f, 1_600f / edge)
                val bitmap = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1),
                    (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                try {
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return ProtectedPreview.Picture(bitmap, pdf.pageCount)
                } catch (failure: Exception) {
                    bitmap.recycle()
                    throw failure
                }
            }
        }
    } finally {
        callbacks.quitSafely()
    }
}

/** MediaPlayer receives authenticated chunks and no public URI. */
private class ProtectedAudioSource(private val reader: PrivateLibraryReadSource) : MediaDataSource() {
    override fun getSize(): Long = reader.size
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int = try {
        reader.readAt(position, buffer, offset, size)
    } catch (error: Exception) {
        throw IOException("Private media is unavailable", error)
    }
    override fun close() = reader.close()
}

private fun decodeLargePicture(source: PrivateLibraryReadSource): ProtectedPreview.Picture {
    fun stream() = object : InputStream() {
        private var position = 0L
        private val oneByte = ByteArray(1)
        override fun read(): Int = if (read(oneByte, 0, 1) == 1) oneByte[0].toInt() and 0xff else -1
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val count = source.readAt(position, buffer, offset, length)
            if (count > 0) position += count
            return count
        }
    }.buffered()
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    stream().use { BitmapFactory.decodeStream(it, null, bounds) }
    val options = pictureSampling(bounds)
    val bitmap = stream().use { BitmapFactory.decodeStream(it, null, options) }
    return ProtectedPreview.Picture(requireNotNull(bitmap))
}

private fun decodePicture(bytes: ByteArray): ProtectedPreview.Picture {
    if (Build.VERSION.SDK_INT >= 28) {
        val decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
            val dimensions = info.size
            val edge = maxOf(dimensions.width, dimensions.height)
            require(dimensions.width > 0 && dimensions.height > 0 && edge <= 48_000)
            val ratio = (1_600f / edge).coerceAtMost(1f)
            if (ratio < 1f) decoder.setTargetSize((dimensions.width * ratio).toInt().coerceAtLeast(1),
                (dimensions.height * ratio).toInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        return ProtectedPreview.Picture(decoded)
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    return ProtectedPreview.Picture(checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, pictureSampling(bounds))))
}

private fun pictureSampling(bounds: BitmapFactory.Options): BitmapFactory.Options {
    val edge = maxOf(bounds.outWidth, bounds.outHeight)
    require(bounds.outWidth > 0 && bounds.outHeight > 0 && edge <= 48_000)
    var sample = 1
    while (edge / sample > 1_600) sample *= 2
    return BitmapFactory.Options().apply { inSampleSize = sample }
}

private val pictureTypes = setOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif", "image/gif", "image/bmp", "image/avif")
private val documentTypes = setOf("text/plain", "text/csv", "text/tab-separated-values", "text/markdown", "application/json")
