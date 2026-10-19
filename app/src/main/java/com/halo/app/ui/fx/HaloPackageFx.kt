package com.halo.app.ui.fx

import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.airbnb.lottie.compose.*
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon

/** Marker names and reduced-motion plates come directly from the supplied packages. */
enum class HaloPackageMoment(
    val asset: String, val marker: String, val still: String,
    val glyph: HaloGlyph, val loop: Boolean = false,
) {
    STEP_RUNNING("task_step", "running", "task_step_running", HaloGlyph.ACTIVITY, true),
    STEP_DONE("task_step", "done", "task_step_done", HaloGlyph.SUCCESS),
    APPROVAL("approval_breathe", "needs_you", "approval_breathe_needs_you", HaloGlyph.APPROVAL, true),
    CONFIRMED("receipt", "confirmed", "receipt_confirmed", HaloGlyph.SUCCESS),
    UNCONFIRMED("receipt", "unconfirmed", "receipt_unconfirmed", HaloGlyph.TOOL),
    STOP("stop_cancel", "stop", "stop_cancel_stop", HaloGlyph.STOP),
    ASSIST_INVOKE("assist_invoke", "invoke", "assist_invoke_listening", HaloGlyph.LISTEN),
    APPLY_DRAFT("apply_draft", "apply", "apply_draft_applied", HaloGlyph.NOTE),
    FORGET_MEMORY("memory_forget", "forget", "memory_forget_forgotten", HaloGlyph.MEMORY),
    KIDS_LOCK("kids_gate", "lock", "kids_gate_locked", HaloGlyph.SECURITY),
    KIDS_UNLOCK("kids_gate", "unlock", "kids_gate_locked", HaloGlyph.SECURITY),
    CARD_UPDATE("card_update", "update", "card_update_peak", HaloGlyph.ACTIVITY),
}

/** The event owner supplies the key. Changing theme never invents a new event. */
@Composable
fun HaloPackageFx(moment: HaloPackageMoment, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val reduced = rememberHaloReducedMotion()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val theme = if (dark) "dark" else "light"
    val composition = if (enabled && !reduced) rememberLottieComposition(
        LottieCompositionSpec.Asset("halo/fx/halo_fx_${moment.asset}_$theme.json")
    ).value else null
    if (composition != null) {
        LottieAnimation(composition, modifier = modifier,
            clipSpec = LottieClipSpec.Marker(moment.marker),
            iterations = if (moment.loop) LottieConstants.IterateForever else 1,
            isPlaying = enabled && lifecycle.isAtLeast(Lifecycle.State.STARTED),
            restartOnPlay = false)
    } else {
        val context = LocalContext.current
        val request = remember(context, moment, theme) {
            ImageRequest.Builder(context)
                .data("file:///android_asset/halo/fx/static/halo_fx_${moment.still}_$theme.svg")
                .decoderFactory(SvgDecoder.Factory()).build()
        }
        val painter = rememberAsyncImagePainter(request)
        if (painter.state is AsyncImagePainter.State.Success)
            Image(painter, contentDescription = null, modifier = modifier)
        else HaloIcon(moment.glyph, contentDescription = null, modifier = modifier)
    }
}

/** Shared Stop epoch suppresses delayed receipts and moments until a new interaction. */
object HaloMotionEvents {
    private val _stopEpoch = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val stopEpoch: kotlinx.coroutines.flow.StateFlow<Long> = _stopEpoch
    private val cancelled = linkedSetOf<String>()
    @Synchronized fun stop(interactionId: String? = null) {
        interactionId?.let { cancelled.add(it) }
        while (cancelled.size > 64) cancelled.remove(cancelled.first())
        _stopEpoch.value += 1L
    }
    @Synchronized fun isStopped(interactionId: String?): Boolean = interactionId != null && interactionId in cancelled
}

/** Arrival/restoration does not replay an old effect; Stop also dismisses an active one. */
@Composable
fun HaloEventMoment(event: Long, moment: HaloPackageMoment, modifier: Modifier = Modifier) {
    var previous by remember { mutableLongStateOf(event) }
    var visible by remember { mutableStateOf(false) }
    val stop by HaloMotionEvents.stopEpoch.collectAsState()
    var previousStop by remember { mutableLongStateOf(stop) }
    LaunchedEffect(event, stop) {
        if (previousStop != stop) {
            previousStop = stop; previous = event; visible = false
        } else if (event != previous) {
            previous = event; visible = true
            kotlinx.coroutines.delay(1000)
            visible = false
        }
    }
    if (visible) key(event) { HaloPackageFx(moment, modifier) }
}
