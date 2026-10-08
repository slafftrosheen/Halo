package com.halo.app.controls

import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import com.halo.sdk.host.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

/** Platform session for actual TTS playback only. No receiver, service, idle session, or fake player. */
class ScopedSpeechMediaControls(private val context: Context, private val controls: AccessoryControls,
    private val storage: Storage, private val scope: CoroutineScope, private val speaking: StateFlow<Boolean>) {
    private val main = Handler(Looper.getMainLooper())
    private var session: MediaSession? = null
    val active: Boolean get() = session?.isActive == true
    private var endpoint: ControlEndpoint? = null
    val enabled = MutableStateFlow(storage.getBoolean("accessory_scoped_media_enabled", false))
    init { scope.launch { combine(speaking, enabled) { active, optIn -> active && optIn }.collect { active ->
        withContext(Dispatchers.Main.immediate) { if (active) acquire() else release() }
    } } }
    fun configure(action: AccessoryAction) {
        if (action !in allowed + AccessoryAction.SystemDefault) return
        val row = endpoint ?: descriptor("settings", false)
        if (action != AccessoryAction.SystemDefault && !controls.mappings.save(row.copy(connected = true),
                ControlBinding(AccessoryControl.PLAY_PAUSE, AccessoryControlAction.SINGLE, AccessoryActionSelection(action)), classDefault = true)) return
        enabled.value = action != AccessoryAction.SystemDefault
        storage.putBoolean("accessory_scoped_media_enabled", enabled.value)
    }
    private fun descriptor(epoch: String, connected: Boolean) = ControlEndpoint("scoped-media", "SCOPED_MEDIA", epoch,
        false, connected, setOf(AccessoryControl.PLAY_PAUSE to AccessoryControlAction.SINGLE), allowed + AccessoryAction.SystemDefault)
    private suspend fun acquire() {
        if (session != null) return
        val current = descriptor(UUID.randomUUID().toString(), true)
        endpoint = current; controls.update(current)
        session = MediaSession(context, "Halo speech controls").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onMediaButtonEvent(intent: Intent): Boolean {
                    @Suppress("DEPRECATION") val key = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return false
                    if (!speaking.value || !enabled.value || key.keyCode !in setOf(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK) || key.repeatCount != 0) return false
                    if (key.action == KeyEvent.ACTION_UP && !key.isCanceled) deliver(current, "${key.downTime}-${key.eventTime}")
                    return key.action in setOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)
                }
                override fun onPause() { if (speaking.value && enabled.value) deliver(current, UUID.randomUUID().toString()) }
                override fun onStop() { if (speaking.value && enabled.value) deliver(current, UUID.randomUUID().toString()) }
            }, main)
            setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP)
                .setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f).build())
            isActive = true
        }
    }
    private fun deliver(current: ControlEndpoint, id: String) {
        scope.launch { controls.receive(AccessoryControlEvent.Button(AccessoryControl.PLAY_PAUSE, AccessoryControlAction.SINGLE,
            System.currentTimeMillis(), ControlDelivery(current.id, current.epoch, id, ControlOrigin.SCOPED_MEDIA, ControlEvidence.VERIFIED_PLATFORM))) }
    }
    private suspend fun release() {
        session?.apply { isActive = false; release() }; session = null
        endpoint?.let { controls.update(it.copy(connected = false)) }; endpoint = null
    }
    companion object { val allowed = setOf(AccessoryAction.StopSpeaking, AccessoryAction.RepeatLastAnswer, AccessoryAction.DoNothing) }
}
