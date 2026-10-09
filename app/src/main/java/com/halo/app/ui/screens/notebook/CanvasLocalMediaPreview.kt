package com.halo.app.ui.screens.notebook

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.halo.app.data.workspace.WorkspaceFile
import com.halo.app.data.workspace.WorkspaceFileAvailability
import com.halo.app.language.uiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decode a small local thumbnail; never put full-resolution images or file bytes
 * into CanvasEditorUiState (which is recomposed during board dragging).
 */
@Composable
internal fun CanvasLocalFileThumbnail(file: WorkspaceFile, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val preview by produceState<ImageBitmap?>(null, file.uri.toString()) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(file.uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                if (bounds.outWidth !in 1..32768 || bounds.outHeight !in 1..32768) return@runCatching null
                var sample = 1
                while ((bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) && sample < 1024) sample *= 2
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                context.contentResolver.openInputStream(file.uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    if (preview == null) {
        Text(uiText("Image preview unavailable"), modifier, style = MaterialTheme.typography.bodySmall)
    } else {
        Image(preview!!, contentDescription = null, modifier = modifier,
            contentScale = ContentScale.Fit)
    }
}

/** Local file audio playback is opt-in and released when its card leaves composition. */
@Composable
internal fun CanvasLocalAudioPlayer(file: WorkspaceFile) {
    val context = LocalContext.current
    var playing by remember(file.uri.toString()) { mutableStateOf(false) }
    var error by remember(file.uri.toString()) { mutableStateOf(false) }
    val player = remember(file.uri.toString()) { LocalMemoPlayer(context) }
    DisposableEffect(player) { onDispose { player.release() } }
    TextButton(
        onClick = {
            error = false
            runCatching { player.toggle(file.uri) { playing = false } }
                .onSuccess { playing = it }
                .onFailure { error = true; playing = false }
        },
        enabled = file.availability == WorkspaceFileAvailability.AVAILABLE ||
            file.availability == WorkspaceFileAvailability.OWNED_LOCAL,
    ) { Text(uiText(if (playing) "Stop playback" else "Play memo")) }
    if (error) Text(uiText("Audio playback unavailable"), style = MaterialTheme.typography.bodySmall)
}

private class LocalMemoPlayer(private val context: android.content.Context) {
    private var player: MediaPlayer? = null

    fun toggle(uri: android.net.Uri, onEnd: () -> Unit): Boolean {
        if (player != null) {
            release()
            return false
        }
        val created = MediaPlayer()
        try {
            created.setDataSource(context, uri)
            created.setOnCompletionListener {
                release()
                onEnd()
            }
            created.prepare()
            created.start()
            player = created
            return true
        } catch (error: Exception) {
            created.release()
            throw error
        }
    }

    fun release() {
        player?.release()
        player = null
    }
}
