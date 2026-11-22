package com.halo.app.ui.toolkit

import android.app.KeyguardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.sdk.runtime.AudioDeviceKind
import com.halo.sdk.runtime.SpeechOutputFallback
import com.halo.sdk.runtime.SpeechOutputTarget
import com.halo.sdk.runtime.TtsCancelled
import com.halo.sdk.runtime.TtsCompleted
import com.halo.sdk.runtime.TtsFailed
import com.halo.sdk.runtime.TtsRequested
import com.halo.sdk.runtime.TtsStarted
import com.halo.sdk.runtime.display.ExternalDisplaySession
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Shows or speaks reviewed text on one explicitly selected connected endpoint. */
@Composable
internal fun HaloReviewedDeviceText(text: String, language: String, enabled: Boolean, validate: suspend () -> Boolean) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val family = Graph.kidsPreferences
    val pause = Graph.haloPause
    val presenter = Graph.externalDisplayPresenter
    val display by presenter.state.collectAsStateWithLifecycle()
    val registry = Graph.audioAccessoryRegistry
    val connected by registry.accessories.collectAsStateWithLifecycle()
    val audio = Graph.haloSpeechOutputCoordinator
    val playing by audio.playbackActive.collectAsStateWithLifecycle()
    val headphones = connected.filter { it.canOutput && it.kind in setOf(AudioDeviceKind.HEADSET, AudioDeviceKind.EARBUDS) }
    val displayText = remember(text) { ExternalDisplaySession.paginate(text)?.singleOrNull() }
    val owner = remember(text, language) { UUID.randomUUID().toString() }
    val mounted = remember(owner) { AtomicBoolean(true) }
    var review by remember(owner) { mutableStateOf(false) }
    var dispatching by remember(owner) { mutableStateOf(false) }
    var status by remember(owner) { mutableIntStateOf(0) }
    var displayToken by remember(owner) { mutableStateOf<String?>(null) }
    var speechId by remember(owner) { mutableStateOf<String?>(null) }
    val latestEnabled by rememberUpdatedState(enabled)

    fun eligible() = mounted.get() && latestEnabled && family.loadedState.value && !family.enabled.value && !pause.isPaused() &&
        lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
        context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == false
    fun stopOutputs() {
        speechId?.let(audio::cancelScopedTransaction)
        speechId = null
        displayToken = null
        Graph.infra.scope.launch { presenter.clearLiveCaptions(owner) }
    }
    DisposableEffect(owner) {
        onDispose {
            mounted.set(false)
            stopOutputs()
        }
    }
    LaunchedEffect(enabled) {
        if (!enabled) { review = false; stopOutputs() }
    }
    LaunchedEffect(displayToken) {
        if (displayToken != null) {
            delay(15_000)
            displayToken = null
            presenter.clearLiveCaptions(owner)
        }
    }
    LaunchedEffect(display.activeToken, displayToken) {
        val currentToken = displayToken
        if (currentToken != null && display.activeToken != currentToken) displayToken = null
    }
    LaunchedEffect(owner) {
        Graph.eventBus.events().collect { event ->
            val id = speechId ?: return@collect
            when {
                event is TtsStarted && event.transactionId == id -> status = R.string.halo11_tool_map_summary_playing
                event is TtsCompleted && event.transactionId == id -> {
                    speechId = null
                    status = R.string.halo11_tool_map_summary_played
                }
                event is TtsFailed && event.transactionId == id -> {
                    speechId = null
                    status = R.string.halo11_tool_map_summary_failed
                }
                event is TtsCancelled && event.transactionId == id -> { speechId = null; status = 0 }
            }
        }
    }

    fun dispatch(send: suspend (() -> Boolean) -> Unit) {
        if (dispatching || !eligible()) return
        review = false
        dispatching = true
        status = 0
        scope.launch {
            try {
                val approved = family.adultActionRevision()
                val modeVersion = Graph.privacyModes.revision
                val pauseVersion = pause.state.value.version
                fun authorized() = eligible() && pause.state.value.version == pauseVersion &&
                    Graph.privacyModes.revision == modeVersion && runCatching { family.requireAdultAction(approved) }.isSuccess
                check(validate() && authorized())
                send(::authorized)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { status = R.string.halo11_tool_map_summary_failed }
            finally { dispatching = false }
        }
    }

    val hasOutputs = headphones.isNotEmpty() || displayText != null && display.endpoints.isNotEmpty()
    if (hasOutputs) TextButton({ review = true }, enabled = enabled && !dispatching && speechId == null && displayToken == null) {
        Text(stringResource(R.string.halo11_tool_map_summary_choose))
    }
    if (displayToken != null || speechId != null) TextButton(::stopOutputs) {
        Text(stringResource(R.string.halo11_tool_map_summary_stop))
    }
    if (status != 0 && enabled) Text(stringResource(status), style = MaterialTheme.typography.bodySmall)
    if (!review || !enabled) return
    AlertDialog(
        onDismissRequest = { review = false },
        title = { Text(stringResource(R.string.halo11_tool_map_summary_choose)) },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text)
                for (target in display.endpoints.takeIf { displayText != null }.orEmpty()) {
                    val revision = display.revision
                    TextButton({
                        dispatch { authorized ->
                            val receipt = presenter.showLiveCaptions(target.token, requireNotNull(displayText), revision, owner, true, authorized)
                            check(receipt.status == "DISPATCHED")
                            if (!authorized()) presenter.clearLiveCaptions(owner)
                            check(authorized())
                            displayToken = target.token
                            status = R.string.halo11_tool_map_summary_displayed
                        }
                    }, enabled = display.activeToken == null) {
                        Text(stringResource(R.string.halo11_tool_map_summary_display, target.name))
                    }
                }
                for (target in headphones) {
                    TextButton({
                        dispatch { authorized ->
                            check(authorized() && !audio.playbackActive.value && registry.find(target.id) == target)
                            val id = UUID.randomUUID().toString()
                            speechId = id
                            Graph.eventBus.publish(TtsRequested(text = text, transactionId = id,
                                target = SpeechOutputTarget.Device(target.id, target.outputDeviceId, target.label, target.kind),
                                fallback = SpeechOutputFallback.DISALLOW_PHONE, language = language, localOnly = true))
                        }
                    }, enabled = !playing) { Text(stringResource(R.string.halo11_tool_map_summary_listen, target.label)) }
                }
            }
        },
        confirmButton = { TextButton({ review = false }) { Text(stringResource(R.string.halo11_tool_close)) } },
    )
}
