package com.halo.app.screen

import android.view.WindowManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.ViewCompat
import java.time.Instant
import java.time.ZoneId

/** Window changes are scoped to the visible Screen and restored when leaving it. */
@Composable
internal fun ScreenWindowPolicy(fullScreen: Boolean, keepCharging: Boolean, dimAtNight: Boolean, now: Instant,
    environment: ScreenEnvironment) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = com.halo.app.ui.mini.miniHaloActivityOwner(context)
    val window = activity?.window ?: return
    val hour = now.atZone(ZoneId.systemDefault()).hour
    val decision = screenAmbientDecision(fullScreen, keepCharging, dimAtNight, hour >= 22 || hour < 7, environment)
    val visible = fullScreen && environment.resumed && environment.interactive && !environment.locked
    // Keep ambient snapshots protected even when the activity pauses before a mask frame draws.
    DisposableEffect(window, fullScreen) {
        val originallySecure = window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        if (fullScreen) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (fullScreen && !originallySecure) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    DisposableEffect(window, view, visible, decision) {
        val originalBrightness = window.attributes.screenBrightness
        val originallyKeptOn = window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        val controller = WindowCompat.getInsetsController(window, view)
        val previousBehavior = controller.systemBarsBehavior
        val insets = ViewCompat.getRootWindowInsets(view)
        val statusVisible = insets?.isVisible(WindowInsetsCompat.Type.statusBars()) ?: true
        val navigationVisible = insets?.isVisible(WindowInsetsCompat.Type.navigationBars()) ?: true
        if (visible) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            if (decision.keepAwake) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            decision.brightnessCap?.let { cap ->
                val brightness = if (originalBrightness >= 0) minOf(originalBrightness, cap) else cap
                window.attributes = window.attributes.apply { screenBrightness = brightness }
            }
        }
        onDispose {
            if (visible) {
                if (decision.keepAwake && !originallyKeptOn) window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                if (decision.brightnessCap != null) window.attributes = window.attributes.apply { screenBrightness = originalBrightness }
                controller.systemBarsBehavior = previousBehavior
                if (statusVisible) controller.show(WindowInsetsCompat.Type.statusBars()) else controller.hide(WindowInsetsCompat.Type.statusBars())
                if (navigationVisible) controller.show(WindowInsetsCompat.Type.navigationBars()) else controller.hide(WindowInsetsCompat.Type.navigationBars())
            }
        }
    }
}
