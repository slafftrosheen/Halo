package com.halo.app.ui.toolkit

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.delay

@Composable
internal fun rememberHaloRecordTransport(
    lifecycle: Lifecycle,
    draft: HaloRecordDraft?,
    audio: HaloRecordAudio?,
    selection: HaloRecordWindow?,
    allowed: () -> Boolean,
    onFailure: (String) -> Unit,
): HaloRecordTransport {
    val context: Context = LocalContext.current
    val speed: MutableFloatState = rememberSaveable { mutableFloatStateOf(1f) }
    val inputs: State<HaloRecordPlaybackInputs> = rememberUpdatedState(HaloRecordPlaybackInputs(draft, audio, selection, allowed, onFailure))
    val transport: HaloRecordTransport = remember(context, lifecycle) { HaloRecordTransport(context, lifecycle, inputs, speed) }
    val trim: HaloRecordWindow? = selection?.takeUnless { it == audio?.fullWindow }
    LaunchedEffect(transport.playing, trim) {
        while (transport.playing) {
            transport.updatePosition(trim)
            delay(if (trim == null) 250 else 35)
        }
    }
    DisposableEffect(transport) { onDispose { transport.release() } }
    return transport
}

internal data class HaloRecordPlaybackInputs(val draft: HaloRecordDraft?, val audio: HaloRecordAudio?, val selection: HaloRecordWindow?,
    val allowed: () -> Boolean, val onFailure: (String) -> Unit) {
    val trim: HaloRecordWindow? get() = selection?.takeUnless { it == audio?.fullWindow }
}

@Stable
internal class HaloRecordTransport(
    private val context: Context,
    private val lifecycle: Lifecycle,
    private val inputs: State<HaloRecordPlaybackInputs>,
    private val playbackSpeed: MutableFloatState,
) {
    private var player: MediaPlayer? = null
    private var queuedPosition: Int? = null
    var playing: Boolean by mutableStateOf(false)
        private set
    var preparing: Boolean by mutableStateOf(false)
        private set
    var position: Int by mutableIntStateOf(0)
        private set
    val speed: Float get() = playbackSpeed.floatValue

    fun changeSpeed(value: Float) {
        playbackSpeed.floatValue = value
        if (playing) runCatching { player?.let { it.playbackParams = it.playbackParams.setSpeed(value) } }
            .onFailure { inputs.value.onFailure("Could not change playback speed.") }
    }

    fun toggle() {
        val audio = inputs.value.draft?.file ?: return
        if (preparing || !inputs.value.allowed()) return
        try {
            val current: MediaPlayer? = player
            when {
                current == null -> {
                    val media = MediaPlayer()
                    player = media
                    preparing = true
                    media.setDataSource(context, captureFileUri(context, audio))
                    media.setOnPreparedListener(::prepared)
                    media.setOnCompletionListener(::completed)
                    media.setOnErrorListener { failed, _, _ -> if (player === failed) failPlayback(); true }
                    media.prepareAsync()
                }
                current.isPlaying -> pause()
                else -> resume(current)
            }
        } catch (_: Exception) { failPlayback() }
    }

    fun seek(requested: Int, play: Boolean = false) {
        val currentInputs: HaloRecordPlaybackInputs = inputs.value
        val draft: HaloRecordDraft = currentInputs.draft ?: return
        val low: Int = currentInputs.selection?.startMs ?: 0
        val high: Int = currentInputs.selection?.endMs ?: currentInputs.audio?.durationMs ?: draft.elapsedSeconds * 1_000
        val target: Int = requested.coerceIn(low, high)
        position = target
        val current: MediaPlayer? = player
        if (current == null || preparing) {
            queuedPosition = target
            if (play && !preparing) toggle()
        } else runCatching {
            if (play && !playing) { queuedPosition = target; toggle() }
            else current.seekTo(target)
        }.onFailure { currentInputs.onFailure("Could not seek in this recording.") }
    }

    fun select(start: Int, listen: Boolean) {
        release()
        position = start
        queuedPosition = start
        if (listen) toggle()
    }

    fun pause() {
        if (playing) runCatching { player?.pause() }
        playing = false
    }

    fun release() {
        player?.release()
        player = null
        queuedPosition = null
        playing = false
        preparing = false
        position = 0
    }

    fun updatePosition(trim: HaloRecordWindow?) {
        position = runCatching { player?.currentPosition }.getOrNull() ?: position
        if (trim != null && position >= trim.endMs) {
            pause()
            runCatching { player?.seekTo(trim.startMs) }
            position = trim.startMs
        }
    }

    private fun prepared(media: MediaPlayer) {
        if (player !== media) return
        preparing = false
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        val start: Int? = queuedPosition ?: inputs.value.trim?.startMs
        queuedPosition = null
        begin(media, start)
    }

    private fun completed(media: MediaPlayer) {
        if (player !== media) return
        playing = false
        val trim: HaloRecordWindow = inputs.value.trim ?: return
        runCatching { media.seekTo(trim.startMs) }
        position = trim.startMs
    }

    private fun resume(media: MediaPlayer) {
        val clip: HaloRecordWindow? = inputs.value.trim
        val start: Int? = queuedPosition ?: clip?.let { range ->
            range.startMs.takeIf { media.currentPosition !in range.startMs until range.endMs }
        }
        queuedPosition = null
        begin(media, start)
    }

    private fun begin(media: MediaPlayer, start: Int?) {
        try {
            if (start == null) play(media)
            else {
                preparing = true
                media.setOnSeekCompleteListener { ready -> ready.setOnSeekCompleteListener(null); play(ready) }
                media.seekTo(start)
            }
        } catch (_: Exception) { failPlayback() }
    }

    private fun play(media: MediaPlayer) {
        if (player !== media) return
        preparing = false
        if (!inputs.value.allowed() || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        try {
            media.start()
            if (speed != 1f) media.playbackParams = media.playbackParams.setSpeed(speed)
            playing = true
        } catch (_: Exception) { failPlayback() }
    }

    private fun failPlayback() {
        release()
        inputs.value.onFailure("Could not play this recording.")
    }
}
