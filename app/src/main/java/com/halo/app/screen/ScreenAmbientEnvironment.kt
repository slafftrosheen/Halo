package com.halo.app.screen

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

internal data class ScreenEnvironment(val resumed: Boolean, val interactive: Boolean, val locked: Boolean,
    val charging: Boolean, val batteryPercent: Int?, val powerSave: Boolean, val thermalStatus: Int?)

internal fun screenMasksSensitive(fullScreen: Boolean, hidePrivate: Boolean, environment: ScreenEnvironment): Boolean =
    !environment.resumed || !environment.interactive || environment.locked || fullScreen && hidePrivate

internal data class ScreenAmbientDecision(val keepAwake: Boolean, val brightnessCap: Float?, val resting: Boolean)

internal fun screenAmbientDecision(fullScreen: Boolean, keepCharging: Boolean, dimAtNight: Boolean,
    night: Boolean, environment: ScreenEnvironment): ScreenAmbientDecision {
    val visible = fullScreen && environment.resumed && environment.interactive && !environment.locked
    val limited = environment.powerSave || environment.batteryPercent == null || environment.batteryPercent <= 15 ||
        environment.thermalStatus == null || environment.thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
    return ScreenAmbientDecision(visible && keepCharging && environment.charging && !limited,
        if (!visible) null else if (limited || dimAtNight && night) .1f else null, visible && limited)
}

/** Android observations only; no audio, provider, permission or execution lifecycle is introduced. */
@Composable
internal fun rememberScreenEnvironment(): ScreenEnvironment {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val power = remember(context) { context.getSystemService(PowerManager::class.java) }
    val keyguard = remember(context) { context.getSystemService(KeyguardManager::class.java) }
    var battery by remember(context) { mutableStateOf(context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))) }
    var thermalUnavailable by remember(power) { mutableStateOf(false) }
    fun read(resumed: Boolean = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)): ScreenEnvironment {
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return ScreenEnvironment(resumed, power?.isInteractive == true, keyguard?.isKeyguardLocked != false,
            status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else null,
            power?.isPowerSaveMode != false,
            if (Build.VERSION.SDK_INT >= 29 && !thermalUnavailable) runCatching { power?.currentThermalStatus }.getOrNull() else null)
    }
    var environment by remember(context, lifecycle) { mutableStateOf(read()) }
    DisposableEffect(context, lifecycle, power, keyguard) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) battery = intent
                environment = read()
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED); addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        val observer = LifecycleEventObserver { _, event ->
            environment = read(when (event) {
                Lifecycle.Event.ON_RESUME -> true
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY -> false
                else -> lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            })
        }
        lifecycle.addObserver(observer)
        val thermal = if (Build.VERSION.SDK_INT >= 29 && power != null) {
            val listener = PowerManager.OnThermalStatusChangedListener { environment = read() }
            try { power.addThermalStatusListener(ContextCompat.getMainExecutor(context), listener); listener }
            catch (_: RuntimeException) { thermalUnavailable = true; environment = read(); null }
        } else null
        onDispose {
            context.unregisterReceiver(receiver)
            lifecycle.removeObserver(observer)
            if (Build.VERSION.SDK_INT >= 29 && thermal != null) runCatching { power?.removeThermalStatusListener(thermal) }
        }
    }
    return environment
}
