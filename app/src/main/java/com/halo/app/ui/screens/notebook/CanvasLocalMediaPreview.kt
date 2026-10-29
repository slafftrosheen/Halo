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
    var loading by remember(file.uri.toString(), file.version) { mutableStateOf(true) }
    var retry by remember(file.uri.toString(), file.version) { mutableIntStateOf(0) }
    val preview by produceState<Bitmap?>(null, file.uri.toString(), file.version, retry) {
        loading = true
        value = null
        value = try { renderInkPage(context, file, 1, maxDimension = 512)?.bitmap }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        finally { loading = false }
    }

    // Published preview pixels must outlive any retained Android display list.
    if (preview == null) {
        Box(modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
            if (loading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            else Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Text(uiText("Image preview unavailable"), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { retry++ }) { Text(uiText("Try again")) }
            }
        }
    } else {
        Image(preview!!.asImageBitmap(), contentDescription = null, modifier = modifier,
            contentScale = ContentScale.Fit)
    }
}

/** Local file audio playback is opt-in and released when its card leaves composition. */
@Composable
internal fun CanvasLocalAudioPlayer(file: WorkspaceFile) {
    val context = LocalContext.current
    var playing by remember(file.uri.toString()) { mutableStateOf(false) }
    var error by remember(file.uri.toString()) { mutableStateOf(false) }
    val player = remember(file.uri.toString()) { LocalMemoPlayer(context, com.halo.app.di.ManualDependencyGraph.infra.resourceLeaseManager) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(player, lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) { player.release(); playing = false } }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); player.release() }
    }
    TextButton(
        onClick = {
            error = false
            runCatching { player.toggle(file.uri, onError = { error = true; playing = false }) { playing = false } }
                .onSuccess { playing = it }
                .onFailure { error = true; playing = false }
        },
        enabled = file.availability == WorkspaceFileAvailability.AVAILABLE ||
            file.availability == WorkspaceFileAvailability.OWNED_LOCAL,
    ) { Text(uiText(if (playing) "Stop playback" else "Play memo")) }
    if (error) Text(uiText("Audio playback unavailable"), style = MaterialTheme.typography.bodySmall)
}

internal class LocalMemoPlayer(private val context: android.content.Context, private val resources: com.halo.sdk.runtime.resource.ResourceLeaseManager) {
    private var player: MediaPlayer? = null
    private var lease: com.halo.sdk.runtime.resource.ResourceLease? = null

    fun toggle(uri: android.net.Uri, onError: () -> Unit, onEnd: () -> Unit): Boolean {
        if (player != null) {
            release()
            return false
        }
        // Never fall back to an unqualified output route on older Android.
        if (android.os.Build.VERSION.SDK_INT < 28) throw IllegalStateException("Phone speaker routing requires Android 9 or newer")
        val id = "memo-playback:${java.util.UUID.randomUUID()}"
        val granted = resources.tryAcquire(com.halo.sdk.runtime.resource.ResourceLeaseRequest(id,
            com.halo.sdk.runtime.resource.ResourceHolder(id), setOf(
                com.halo.sdk.runtime.resource.ResourceClaim(com.halo.sdk.runtime.resource.ResourceKey(com.halo.sdk.runtime.resource.HaloResourceIds.AudioOutput, com.halo.sdk.runtime.resource.ResourceEndpointId("host:primary"))),
                com.halo.sdk.runtime.resource.ResourceClaim(com.halo.sdk.runtime.resource.ResourceKey(com.halo.sdk.runtime.resource.HaloResourceIds.AudioSession, com.halo.sdk.runtime.resource.ResourceEndpointId("host:android-audio"))),
            ), preemption = com.halo.sdk.runtime.resource.ResourcePreemptionPolicy.NEVER))
        check(granted is com.halo.sdk.runtime.resource.ResourceAcquireResult.Granted) { "Audio is busy" }
        lease = granted.lease
        var pending: MediaPlayer? = null
        try {
            val created = MediaPlayer()
            pending = created
            created.setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
            val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
            val phoneSpeaker = audio.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            check(phoneSpeaker != null && created.setPreferredDevice(phoneSpeaker)) { "Phone speaker unavailable" }
            created.setDataSource(context, uri)
            created.setOnCompletionListener {
                release()
                onEnd()
            }
            created.setOnErrorListener { _, _, _ -> release(); onError(); true }
            created.setOnPreparedListener { if (player === created) created.start() }
            player = created
            created.prepareAsync()
            return true
        } catch (error: Exception) {
            runCatching { pending?.release() }; player = null; lease?.release(); lease = null
            throw error
        }
    }

    fun release() {
        try { player?.release() } finally { player = null; lease?.release(); lease = null }
    }
}
