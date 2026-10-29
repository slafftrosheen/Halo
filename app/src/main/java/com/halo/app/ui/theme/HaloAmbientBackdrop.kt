package com.halo.app.ui.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/** Decorative, non-interactive Open Ring fragments. No session state or input handling.
 * Only retained motif-layer translations read phase; motif drawing and base stay static.
 * One tiny position update/second, paused outside RESUMED or under power/thermal limits.
 */
@Composable
fun HaloAmbientBackdrop(modifier: Modifier = Modifier, palette: HaloPalette = LocalHaloPalette.current,
    animate: Boolean = true, baseColor: Color? = null) {
    val reduced by rememberReducedMotion()
    val contrast = rememberVisualPreferences().higherContrast
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
    val power = remember(context) { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    fun allowed() = power?.let {
        it.isInteractive && !it.isPowerSaveMode &&
            (Build.VERSION.SDK_INT < 29 || it.currentThermalStatus < PowerManager.THERMAL_STATUS_MODERATE)
    } ?: false
    var powerAllowed by remember(power) { mutableStateOf(allowed()) }
    DisposableEffect(context, power) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { powerAllowed = allowed() }
        }
        androidx.core.content.ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        val thermal = if (Build.VERSION.SDK_INT >= 29 && power != null) {
            PowerManager.OnThermalStatusChangedListener { powerAllowed = allowed() }.also {
                power.addThermalStatusListener(it)
            }
        } else null
        onDispose {
            context.unregisterReceiver(receiver)
            if (Build.VERSION.SDK_INT >= 29 && thermal != null) power?.removeThermalStatusListener(thermal)
        }
    }
    val phase = remember { mutableFloatStateOf(0f) }
    val moving = haloAmbientMotionAllowed(animate, reduced, contrast, powerAllowed, palette.background)
    LaunchedEffect(lifecycle, moving) { advanceHaloAmbientPhase(lifecycle, moving, phase) }
    val base = baseColor ?: MaterialTheme.colorScheme.background
    val accent = MaterialTheme.colorScheme.primary
    HaloAmbientBackdropLayers(modifier, palette.background, base, accent, phase, contrast)
}

/** Static motif recording; phase is observed only by retained layer-property blocks. */
@Composable
internal fun HaloAmbientBackdropLayers(
    modifier: Modifier,
    style: HaloBackgroundStyle,
    base: Color,
    accent: Color,
    phase: FloatState,
    contrast: Boolean,
) {
    // Clip once at the viewport, never within a moving motif. Oversized fragments
    // remain recorded outside the layer bounds so translation exposes no cut edge.
    Box(modifier.clipToBounds().background(base)) {
        if (!contrast && style != HaloBackgroundStyle.STILL) {
            repeat(2) { index ->
                Canvas(Modifier.matchParentSize().graphicsLayer {
                    val drift = haloAmbientDrift(phase.floatValue, size.width, size.height, index)
                    translationX = drift.x
                    translationY = drift.y
                    clip = false
                }) {
                    val radius = size.width * .73f
                    if (style == HaloBackgroundStyle.MIST) {
                        val center = if (index == 0) Offset(size.width * .96f, size.height * .22f)
                            else Offset(-size.width * .14f, size.height * .88f)
                        val r = if (index == 0) radius else radius * .8f
                        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = if (index == 0) .055f else .035f), Color.Transparent),
                            center = center, radius = r), r, center)
                    } else if (index == 0) {
                        val center = Offset(size.width * .96f, size.height * .22f)
                        drawArc(accent.copy(alpha = .035f), 32f, 272f, false,
                            topLeft = center - Offset(radius, radius), size = Size(radius * 2, radius * 2),
                            style = Stroke(size.width * .07f))
                    } else {
                        val r = radius * .63f
                        drawArc(accent.copy(alpha = .025f), 210f, 235f, false,
                            topLeft = Offset(-r * 1.4f, size.height * .72f), size = Size(r * 2, r * 2),
                            style = Stroke(size.width * .04f))
                    }
                }
            }
        }
    }
}

internal fun haloAmbientMotionAllowed(animate: Boolean, reduced: Boolean, contrast: Boolean,
    powerAllowed: Boolean, style: HaloBackgroundStyle): Boolean =
    animate && !reduced && !contrast && powerAllowed && style != HaloBackgroundStyle.STILL

internal fun haloAmbientDrift(phase: Float, width: Float, height: Float, motif: Int): Offset {
    val direction = if (motif == 0) 1f else -1f
    return Offset(direction * sin(phase) * width * .018f, direction * cos(phase * 2f) * height * .012f)
}

internal suspend fun advanceHaloAmbientPhase(lifecycle: Lifecycle, moving: Boolean, phase: MutableFloatState) {
    if (moving) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        while (true) {
            delay(1_000)
            phase.floatValue = (phase.floatValue + .03f) % (2f * Math.PI.toFloat())
        }
    }
}
