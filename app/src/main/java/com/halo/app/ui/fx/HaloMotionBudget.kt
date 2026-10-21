package com.halo.app.ui.fx

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Decorative frame clocks stop under battery saver, screen-off, and thermal pressure. */
@Composable
internal fun rememberHaloMotionConstrained(): Boolean {
    val context = LocalContext.current
    val power = remember(context) { context.getSystemService(PowerManager::class.java) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var thermalUnavailable by remember(power) { mutableStateOf(false) }
    fun constrained() = thermalUnavailable || power.isPowerSaveMode || !power.isInteractive ||
        (Build.VERSION.SDK_INT >= 29 && runCatching { power.currentThermalStatus }
            .getOrDefault(PowerManager.THERMAL_STATUS_MODERATE) >= PowerManager.THERMAL_STATUS_MODERATE)
    var limited by remember(power) { mutableStateOf(constrained()) }
    DisposableEffect(context, lifecycle, power) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { limited = constrained() }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) limited = constrained()
        }
        lifecycle.addObserver(observer)
        val thermal = if (Build.VERSION.SDK_INT >= 29) PowerManager.OnThermalStatusChangedListener {
            limited = constrained()
        }.let { listener ->
            try {
                power.addThermalStatusListener(ContextCompat.getMainExecutor(context), listener)
                listener
            } catch (_: RuntimeException) {
                // Some hosts have no thermal service. Decorative motion must not
                // prevent the screen from opening; retain the static artwork.
                thermalUnavailable = true
                limited = true
                null
            }
        } else null
        onDispose {
            context.unregisterReceiver(receiver)
            lifecycle.removeObserver(observer)
            if (Build.VERSION.SDK_INT >= 29 && thermal != null) {
                runCatching { power.removeThermalStatusListener(thermal) }
            }
        }
    }
    return limited
}
