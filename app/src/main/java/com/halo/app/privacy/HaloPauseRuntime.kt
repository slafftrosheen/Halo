package com.halo.app.privacy

import android.content.Context
import android.util.Log
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.nativeactions.HaloNativeWidget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Apply the state to process resources. Organizer alarms and timers use their own Android delivery owner. */
internal suspend fun observeHaloPause(context: Context): Unit = coroutineScope {
    val owner = HaloPauseOwner.get(context)
    launch {
        owner.state.collectLatest { value ->
            if (value.paused) {
                while (owner.state.value.version == value.version) {
                    delay(owner.remaining(value).coerceIn(1L, 30_000L))
                    try {
                        owner.expire(value.version)
                    } catch (_: IllegalStateException) {
                        delay(5_000L)
                    }
                }
            }
        }
    }
    var wasPaused = false
    owner.state.collect { value ->
        if (value.paused != wasPaused) {
            wasPaused = value.paused
            if (value.paused) {
                withContext(Dispatchers.Main.immediate) {
                    applyPauseStep("stop voice fields") { com.halo.app.ui.components.VoiceFieldCancellation.stop() }
                    if (Graph.runtime.isStarted()) {
                        applyPauseStep("stop Home interaction") { Graph.homeViewModel.cancelSession() }
                        applyPauseStep("stop speech") { Graph.haloSpeechOutputCoordinator.stop("Halo paused") }
                        applyPauseStep("stop read aloud") { Graph.readAloud.stop() }
                        applyPauseStep("cancel cognition") { Graph.cognitiveSession.cancel() }
                    }
                }
                if (Graph.runtime.isStarted()) {
                    applyPauseStep("cancel active turn") { Graph.activeTurnCoordinator.cancel("Halo paused") }
                }
                applyPauseStep("revoke remote dispatch") { Graph.privacyModes.revokeActiveDispatch() }
                applyPauseStep("cancel collectors") { Graph.collectorCoordinator.cancelAll() }
                applyPauseStep("stop runtime") { Graph.runtime.stop() }
            } else {
                applyPauseStep("resume runtime") { Graph.runtime.resumeAfterPause() }
            }
            applyPauseStep("refresh widgets") { HaloNativeWidget.refreshAsync(context) }
        }
    }
}

private suspend fun applyPauseStep(step: String, action: suspend () -> Unit) {
    try {
        action()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Log.w("HaloPause", "Could not $step", failure)
    }
}
