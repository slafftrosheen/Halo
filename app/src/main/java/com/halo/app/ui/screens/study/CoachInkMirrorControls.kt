package com.halo.app.ui.screens.study

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
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.ink.CoachInkHostState
import com.halo.app.ink.CoachInkLocalNetworkAccess
import com.halo.app.ink.CoachInkMirrorHost
import com.halo.app.ink.coachInkHasLocalNetwork
import com.halo.host.ink.CoachInkContent
import com.halo.sdk.runtime.embodiment.DeviceRecord
import com.halo.sdk.runtime.ink.InkDocument
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Only adult, structured worksheet pages can reach the paired Android companion. */
@Composable
internal fun CoachInkMirrorControls(content: CoachInkContent, allowed: () -> Boolean): InkDocument? {
    val context = LocalContext.current
    val ink = Graph.crossDeviceInk
    if (!ink.enabled) {
        Text(stringResource(R.string.halo11_coach_ink_mirror_pending), style = MaterialTheme.typography.bodySmall)
        return null
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val family = Graph.kidsPreferences
    val isKid by family.enabled.collectAsStateWithLifecycle()
    val familyReady by family.loadedState.collectAsStateWithLifecycle()
    val pause = Graph.haloPause
    val pauseState by pause.state.collectAsStateWithLifecycle()
    val privacyMode by Graph.privacyModes.mode.collectAsStateWithLifecycle()
    val localNetwork = CoachInkLocalNetworkAccess(familyReady && !isKid && !pauseState.paused && allowed())
    val currentAccess by rememberUpdatedState(allowed)
    val latestContent by rememberUpdatedState(content)
    val mounted = remember { AtomicBoolean(true) }
    val scope = rememberCoroutineScope()
    var choices by remember { mutableStateOf<List<DeviceRecord>?>(null) }
    var connection by remember { mutableStateOf<CoachInkMirrorHost?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val current = connection
    val connectionState = if (current == null) null else {
        val observed by current.state.collectAsStateWithLifecycle()
        observed
    }
    val writing = if (current == null) null else {
        val observed by current.writing.collectAsStateWithLifecycle()
        observed
    }
    fun stop() {
        choices = null
        connection?.close()
        connection = null
    }
    fun eligible() = mounted.get() && currentAccess() && family.loadedState.value && !family.enabled.value &&
        coachInkHasLocalNetwork(context) &&
        !pause.isPaused() && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
        context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == false

    DisposableEffect(lifecycle) {
        val monitor = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) stop() }
        lifecycle.addObserver(monitor)
        onDispose {
            mounted.set(false)
            lifecycle.removeObserver(monitor)
            stop()
        }
    }
    LaunchedEffect(familyReady, isKid, pauseState.version, localNetwork) {
        if (!eligible()) stop()
    }
    LaunchedEffect(privacyMode) { if (connection != null) stop() }
    SideEffect { current?.update(content) }

    fun choose() {
        if (!eligible() || busy) return
        failed = false
        busy = true
        scope.launch {
            try {
                val devices = ink.devices()
                if (eligible()) choices = devices
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }
    fun start(device: DeviceRecord) {
        if (!eligible() || busy) return
        val certificate = device.inkAssociation?.certificateSha256 ?: return
        choices = null
        busy = true
        scope.launch {
            try {
                val approval = family.adultActionRevision()
                val privacyRevision = Graph.privacyModes.revision
                val pauseRevision = pause.state.value.version
                fun authorized() = eligible() && Graph.privacyModes.revision == privacyRevision &&
                    pause.state.value.version == pauseRevision && runCatching { family.requireAdultAction(approval) }.isSuccess
                val result = CoachInkMirrorHost.start(context.applicationContext, ink, device.identity.deviceId,
                    device.identity.displayName, certificate, latestContent, scope, ::authorized)
                if (authorized()) connection = result else result.close()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }
    if (current == null) {
        TextButton(::choose, enabled = !busy && familyReady && !isKid && !pauseState.paused && allowed() && localNetwork) {
            Text(stringResource(R.string.halo11_coach_ink_mirror))
        }
    } else {
        when (connectionState) {
            CoachInkHostState.WAITING -> {
                Text(stringResource(R.string.halo11_coach_ink_join_from))
                Text(stringResource(R.string.halo11_coach_ink_join_details, current.address, current.port, current.code),
                    style = MaterialTheme.typography.titleMedium)
            }
            CoachInkHostState.CONNECTED -> Text(stringResource(R.string.halo11_coach_ink_mirror_connected, current.name))
            CoachInkHostState.ENDED -> Text(stringResource(R.string.halo11_coach_ink_mirror_ended))
            null -> Unit
        }
        TextButton(::stop) { Text(stringResource(R.string.halo11_coach_ink_mirror_stop)) }
    }
    if (failed) Text(stringResource(R.string.halo11_coach_ink_mirror_failed), color = MaterialTheme.colorScheme.error)
    choices?.let { devices ->
        AlertDialog(onDismissRequest = { choices = null },
            properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
            title = { Text(stringResource(R.string.halo11_coach_ink_mirror)) },
            text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.halo11_coach_ink_mirror_review))
                if (devices.isEmpty()) Text(stringResource(R.string.halo11_coach_ink_mirror_no_devices))
                devices.take(12).forEach { device ->
                    TextButton({ start(device) }) { Text(device.identity.displayName) }
                }
            } },
            confirmButton = {
                TextButton({ choices = null }) { Text(stringResource(android.R.string.cancel)) }
            })
    }
    return writing?.takeIf { connectionState == CoachInkHostState.CONNECTED && it.content == content && eligible() }?.document
}
