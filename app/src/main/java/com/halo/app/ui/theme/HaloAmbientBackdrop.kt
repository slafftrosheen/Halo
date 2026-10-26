package com.halo.app.ui.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
 * Draw reads the phase directly: only this Canvas invalidates, never screen content.
 * Ten tiny position updates/second, paused outside RESUMED or under power/thermal limits.
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
    val moving = animate && !reduced && !contrast && powerAllowed && palette.background != HaloBackgroundStyle.STILL
    LaunchedEffect(lifecycle, moving) {
        if (moving) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(100)
                phase.floatValue = (phase.floatValue + .003f) % (2f * Math.PI.toFloat())
            }
        }
    }
    val base = baseColor ?: MaterialTheme.colorScheme.background
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        drawRect(base)
        if (contrast || palette.background == HaloBackgroundStyle.STILL) return@Canvas
        val t = phase.floatValue
        val driftX = sin(t) * size.width * .018f
        val driftY = cos(t * 2f) * size.height * .012f
        val radius = size.width * .73f
        val origin = Offset(size.width * .96f + driftX, size.height * .22f + driftY)
        if (palette.background == HaloBackgroundStyle.MIST) {
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = .055f), Color.Transparent),
                center = origin, radius = radius), radius, origin)
            val other = Offset(-size.width * .14f - driftX, size.height * .88f - driftY)
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = .035f), Color.Transparent),
                center = other, radius = radius * .8f), radius * .8f, other)
        } else {
            // Oversized cropped fragments cannot be confused with the compact interactive Orb.
            drawArc(accent.copy(alpha = .035f), 32f, 272f, false,
                topLeft = origin - Offset(radius, radius), size = Size(radius * 2, radius * 2),
                style = Stroke(size.width * .07f))
            val r = radius * .63f
            drawArc(accent.copy(alpha = .025f), 210f, 235f, false,
                topLeft = Offset(-r * 1.4f - driftX, size.height * .72f - driftY), size = Size(r * 2, r * 2),
                style = Stroke(size.width * .04f))
        }
    }
}
