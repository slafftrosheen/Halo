package com.halo.app.ui.toolkit

import android.app.Activity
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.halo.app.R
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.everyday.organizer.OrganizerKind
import com.halo.app.ui.components.HaloVoiceTextActions
import com.halo.app.ui.components.LocalPrivateSpeech
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.presence.ShellPageAction
import com.halo.app.ui.zone6.Zone6ButtonSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
internal fun HaloWindDownScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val recordsFlow = remember { ManualDependencyGraph.organizerRepository.observe() }
    val records by recordsFlow.collectAsStateWithLifecycle(emptyList())
    val now = LocalDate.now()
    val agenda by produceState<Result<String?>?>(null, now, records) {
        value = null
        val zone = java.time.ZoneId.systemDefault()
        val tomorrow = now.plusDays(1)
        val start = tomorrow.atStartOfDay(zone).toInstant()
        val end = tomorrow.plusDays(1).atStartOfDay(zone).toInstant()
        value = withContext(Dispatchers.IO) {
            try { Result.success(ManualDependencyGraph.organizerUseCases.agenda(start, end, setOf(OrganizerKind.EVENT))
                .firstOrNull { !it.completed && !it.start.isBefore(start) }?.record?.title) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) { Result.failure(error) }
        }
    }
    val scope = rememberCoroutineScope()
    val owner = remember(context) { HaloFocusOwner.get(context) }
    val focus by owner.state.collectAsStateWithLifecycle()
    var setQuiet by remember { mutableStateOf(false) }
    var controls by remember { mutableStateOf(false) }
    var soundMinutes by remember { mutableIntStateOf(15) }
    var sound by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    val dark = isSystemInDarkTheme()
    LaunchedEffect(sound, soundMinutes, lifecycle) {
        if (!sound) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                val track = withContext(Dispatchers.Default) { makeSleepTrack() }
                try {
                    track.play()
                    playing = true
                    delay(soundMinutes * 60_000L)
                    sound = false
                } finally {
                    playing = false
                    runCatching(track::stop)
                    track.release()
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                sound = false
                throw cancelled
            } catch (_: Exception) {
                error = true
                sound = false
            }
        }
    }
    DisposableEffect(context) {
        val window = (context as? Activity)?.window
        val old = window?.attributes?.screenBrightness
        if (window != null) window.attributes = window.attributes.apply { screenBrightness = .18f }
        onDispose { if (window != null && old != null) window.attributes = window.attributes.apply { screenBrightness = old } }
    }
    val title = stringResource(R.string.halo_winddown_title)
    val explanation = stringResource(R.string.halo_winddown_help)
    val tomorrowTitle = agenda?.getOrNull()
    val tomorrowLine = when {
        agenda == null -> stringResource(R.string.action_loading)
        agenda?.isFailure == true -> stringResource(R.string.halo_winddown_unavailable)
        tomorrowTitle != null -> stringResource(R.string.halo_winddown_tomorrow, tomorrowTitle)
        else -> stringResource(R.string.halo_winddown_clear)
    }
    Surface(color = if (dark) Color(0xFF1E1820) else Color(0xFFF6E8D5),
        contentColor = if (dark) Color(0xFFEFE0CC) else Color(0xFF3E2D22), modifier = Modifier.fillMaxSize()) {
        LifePage("winddown", title, explanation,
            stringResource(if (sound) R.string.halo_winddown_stop else R.string.halo_winddown_play),
            true, { sound = !sound }, onBack, guidanceBlocked = controls,
            extra = listOf(ShellPageAction("page.winddown.appearance", stringResource(R.string.coach_edit_page), HaloGlyph.SETTINGS) {
                controls = true
            })) {
            Text(tomorrowLine, style = MaterialTheme.typography.titleLarge)
            CompositionLocalProvider(LocalPrivateSpeech provides true) {
                HaloVoiceTextActions(tomorrowLine, {}, allowDictation = false,
                    readActionLabel = stringResource(R.string.halo_winddown_read))
            }
            if (playing) Text(stringResource(R.string.halo_winddown_playing, soundMinutes))
            TextButton({ controls = true }) { Text(stringResource(R.string.halo_winddown_settings)) }
            TextButton({
                if (focus != null) onOpen("focus")
                else {
                    val manager = context.getSystemService(android.app.NotificationManager::class.java)
                    if (android.os.Build.VERSION.SDK_INT >= 29 && manager?.isNotificationPolicyAccessGranted == true) setQuiet = true
                    else onOpen("focus")
                }
            }) { Text(stringResource(R.string.halo_focus_quiet)) }
            if (error) Text(stringResource(R.string.page_layout_error))
        }
    }
    if (controls) Zone6ButtonSheet(stringResource(R.string.halo_winddown_settings), { controls = false }) {
        listOf(5, 15, 30).forEach { minutes ->
            TextButton({ soundMinutes = minutes; controls = false }) {
                Text(stringResource(R.string.halo_winddown_minutes, minutes))
            }
        }
    }
    if (setQuiet) AlertDialog(onDismissRequest = { setQuiet = false },
        title = { Text(stringResource(R.string.halo_focus_quiet)) },
        text = { Text(stringResource(R.string.halo_focus_quiet_help)) },
        confirmButton = { Button({
            setQuiet = false
            scope.launch {
                try { owner.start(soundMinutes, quiet = true) }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { error = true }
            }
        }) { Text(stringResource(R.string.halo_focus_start)) } },
        dismissButton = { TextButton({ setQuiet = false }) { Text(stringResource(android.R.string.cancel)) } })
}

private fun makeSleepTrack(): AudioTrack {
    val rate = 16_000
    val frames = rate * 8
    var filtered = 0.0
    val samples = ShortArray(frames) { frame ->
        filtered = .96 * filtered + .04 * (kotlin.random.Random.nextDouble() * 2.0 - 1.0)
        val distance = minOf(frame, frames - 1 - frame)
        (filtered * 4700 * (distance / 600f).coerceAtMost(1f)).toInt().toShort()
    }
    val track = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
        .setBufferSizeInBytes(samples.size * 2).setTransferMode(AudioTrack.MODE_STATIC).build()
    if (track.state != AudioTrack.STATE_INITIALIZED || track.write(samples, 0, samples.size) != samples.size ||
        track.setLoopPoints(0, frames, -1) != AudioTrack.SUCCESS) {
        track.release()
        error("Sleep sound is unavailable")
    }
    return track
}
