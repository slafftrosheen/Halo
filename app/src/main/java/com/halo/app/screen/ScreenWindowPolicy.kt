package com.halo.app.screen

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.view.WindowManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import java.time.Instant
import java.time.ZoneId

/** Window changes are scoped to the visible Screen and restored when leaving it. */
@Composable
internal fun ScreenWindowPolicy(fullScreen: Boolean, keepCharging: Boolean, dimAtNight: Boolean, now: Instant) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = com.halo.app.ui.mini.miniHaloActivityOwner(context)
    val window = activity?.window ?: return
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    var charging by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        fun accept(intent: Intent?) {
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        }
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, intent: Intent?) = accept(intent)
        }
        accept(context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
        onDispose { context.unregisterReceiver(receiver) }
    }
    val hour = now.atZone(ZoneId.systemDefault()).hour
    val visible = fullScreen && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    DisposableEffect(window, view, visible, charging, keepCharging, dimAtNight, hour) {
        val originalBrightness = window.attributes.screenBrightness
        val originallyKeptOn = window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        val controller = WindowCompat.getInsetsController(window, view)
        val previousBehavior = controller.systemBarsBehavior
        if (visible) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            if (keepCharging && charging) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (dimAtNight && (hour >= 22 || hour < 7)) {
                window.attributes = window.attributes.apply { screenBrightness = .1f }
            }
        }
        onDispose {
            if (!originallyKeptOn) window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.attributes = window.attributes.apply { screenBrightness = originalBrightness }
            controller.systemBarsBehavior = previousBehavior
            if (visible) controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
