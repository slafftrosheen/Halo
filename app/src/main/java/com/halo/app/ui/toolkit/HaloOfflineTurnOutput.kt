package com.halo.app.ui.toolkit

import android.app.KeyguardManager
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.sdk.runtime.AudioDeviceKind
import com.halo.sdk.runtime.SpeechOutputFallback
import com.halo.sdk.runtime.SpeechOutputTarget
import com.halo.sdk.runtime.TtsRequested
import java.util.UUID

internal data class OfflineTurn(
    val arrow: String, val distance: String, val meters: Double, val key: String, val spoken: String,
)

@Composable
internal fun HaloOfflineTurnOutput(turn: OfflineTurn, language: String, allowed: () -> Boolean) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
    val presenter = Graph.externalDisplayPresenter
    val display by presenter.state.collectAsStateWithLifecycle()
    val registry = Graph.audioAccessoryRegistry
    val accessories by registry.accessories.collectAsStateWithLifecycle()
    val headphones = accessories.filter { it.canOutput && it.kind in setOf(AudioDeviceKind.HEADSET, AudioDeviceKind.EARBUDS) }
    val caption = remember(presenter) { HaloRecordCaptionDisplay(presenter, Graph.infra.scope) }
    val shown by caption.state.collectAsStateWithLifecycle()
    val latestAllowed by rememberUpdatedState(allowed)
    var choosingDisplay by remember { mutableStateOf(false) }
    var choosingAudio by remember { mutableStateOf(false) }
    var review by remember { mutableStateOf<HaloCaptionDisplayChoice?>(null) }
    var displayRequest by remember { mutableStateOf<HaloCaptionDisplayChoice?>(null) }
    var speechDevice by remember { mutableStateOf<String?>(null) }
    var speech by remember { mutableStateOf<String?>(null) }
    var heard by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var changed by remember { mutableStateOf(false) }
    fun authorized() = latestAllowed() && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
        !Graph.haloPause.isPaused() && Graph.kidsPreferences.loadedState.value && !Graph.kidsPreferences.enabled.value &&
        context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == false
    fun stopOutputs() {
        caption.revoke()
        speech?.let(Graph.haloSpeechOutputCoordinator::cancelScopedTransaction)
        speech = null
        speechDevice = null
        heard = null
        review = null
        displayRequest = null
        choosingDisplay = false
        choosingAudio = false
    }
    DisposableEffect(lifecycle, caption) {
        val events = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) stopOutputs() }
        lifecycle.addObserver(events)
        onDispose {
            lifecycle.removeObserver(events)
            stopOutputs()
        }
    }
    val captionText = "${turn.arrow}  ${turn.distance}"
    LaunchedEffect(displayRequest) {
        val target = displayRequest ?: return@LaunchedEffect
        val lease = runCatching { Graph.kidsPreferences.adultActionRevision() }.getOrNull() ?: return@LaunchedEffect
        changed = !caption.start(target, captionText) {
            authorized() && runCatching { Graph.kidsPreferences.requireAdultAction(lease) }.isSuccess
        }
        displayRequest = null
    }
    LaunchedEffect(captionText, shown?.token) {
        if (shown != null && (!authorized() || !caption.offer(captionText))) {
            caption.revoke()
            changed = true
        }
    }
    val step = when {
        turn.meters < 45 -> 2
        turn.meters < 170 -> 1
        else -> 0
    }
    LaunchedEffect(turn.key, step, speechDevice, headphones) {
        val requested = speechDevice ?: return@LaunchedEffect
        val device = headphones.firstOrNull { it.id == requested }
        if (device == null || !authorized()) {
            speech?.let(Graph.haloSpeechOutputCoordinator::cancelScopedTransaction)
            speech = null
            speechDevice = null
            return@LaunchedEffect
        }
        if (heard == (turn.key to step) || registry.find(device.id) != device) return@LaunchedEffect
        speech?.let(Graph.haloSpeechOutputCoordinator::cancelScopedTransaction)
        val id = UUID.randomUUID().toString()
        speech = id
        heard = turn.key to step
        Graph.eventBus.publish(TtsRequested(text = turn.spoken, transactionId = id,
            target = SpeechOutputTarget.Device(device.id, device.outputDeviceId, device.label, device.kind),
            fallback = SpeechOutputFallback.DISALLOW_PHONE, language = language, localOnly = true))
    }
    Column {
        if (display.endpoints.isNotEmpty() && shown == null) TextButton({ choosingDisplay = true }) {
            Text(stringResource(R.string.halo11_offline_show_turns))
        }
        if (headphones.isNotEmpty() && speechDevice == null) TextButton({ choosingAudio = true }) {
            Text(stringResource(R.string.halo11_offline_speak_turns))
        }
        if (shown != null || speechDevice != null) TextButton(::stopOutputs) {
            Text(stringResource(R.string.halo11_offline_stop_outputs))
        }
        if (changed) Text(stringResource(R.string.halo11_offline_output_changed), style = MaterialTheme.typography.bodySmall)
    }
    if (choosingDisplay) HaloRecordDisplayChooser(display, onDismiss = { choosingDisplay = false }, onChoose = {
        choosingDisplay = false
        review = it
    })
    review?.let { target ->
        AlertDialog(onDismissRequest = { review = null },
            title = { Text(stringResource(R.string.halo11_offline_show_turns)) },
            text = { Text(stringResource(R.string.halo11_offline_display_review, target.name)) },
            confirmButton = { TextButton({
                review = null
                if (authorized()) displayRequest = target
            }) { Text(stringResource(android.R.string.ok)) } },
            dismissButton = { TextButton({ review = null }) { Text(stringResource(android.R.string.cancel)) } })
    }
    if (choosingAudio) AlertDialog(onDismissRequest = { choosingAudio = false },
        title = { Text(stringResource(R.string.halo11_offline_speak_turns)) },
        text = { Column {
            Text(stringResource(R.string.halo11_offline_output_review))
            for (device in headphones) TextButton({
                choosingAudio = false
                if (authorized() && registry.find(device.id) == device) speechDevice = device.id
            }) { Text(device.label) }
        } },
        confirmButton = { TextButton({ choosingAudio = false }) { Text(stringResource(android.R.string.cancel)) } })
}
