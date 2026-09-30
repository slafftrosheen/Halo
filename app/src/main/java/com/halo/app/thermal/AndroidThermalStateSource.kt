package com.halo.app.thermal

import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.halo.runtime.cognition.agent.AgentRunThermalGate
import com.halo.sdk.runtime.thermal.ThermalSeverity
import com.halo.sdk.runtime.thermal.ThermalState
import com.halo.sdk.runtime.thermal.ThermalStateSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * MINING-P1-001 host side of the thermal governor.
 *
 * `android.os.PowerManager` lives here and nowhere in `runtime-sdk` / `runtime:cognition`. The
 * Runtime only ever sees [ThermalSeverity]; the platform status code stays opaque.
 *
 * Registered lazily by the app graph and never polled at startup beyond one cheap read
 * (`getCurrentThermalStatus()`), which is a property read, not work.
 */
class AndroidThermalStateSource(private val context: Context) : ThermalStateSource {

    private val _state = MutableStateFlow(
        ThermalState(severity = ThermalSeverity.UNKNOWN, source = "power_manager")
    )
    override val state: StateFlow<ThermalState> = _state

    private val powerManager: PowerManager? =
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private val listener: PowerManager.OnThermalStatusChangedListener? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            PowerManager.OnThermalStatusChangedListener { status -> publish(status) }
        } else {
            null
        }

    private var listening = false

    fun start() {
        if (listening) return
        val pm = powerManager ?: return
        listening = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            listener?.let { pm.addThermalStatusListener(it) }
            publish(pm.currentThermalStatus)
        } else {
            // Pre-Q has no thermal API. Reporting UNKNOWN is honest; policy treats it as
            // permissive and says so, rather than inventing a reading.
            _state.value = ThermalState(severity = ThermalSeverity.UNKNOWN, source = "unavailable_pre_q")
        }
    }

    fun stop() {
        val pm = powerManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && listening) {
            listener?.let { runCatching { pm.removeThermalStatusListener(it) } }
        }
        listening = false
    }

    /** One cheap property read; safe to call before every guarded round. */
    fun currentSeverity(): ThermalSeverity {
        val pm = powerManager ?: return ThermalSeverity.UNKNOWN
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return ThermalSeverity.UNKNOWN
        return severityOf(runCatching { pm.currentThermalStatus }.getOrNull())
    }

    private fun publish(status: Int) {
        _state.value = ThermalState(
            severity = severityOf(status),
            platformStatus = status,
            recordedAtMs = System.currentTimeMillis(),
            source = "power_manager",
        )
    }

    /** Exposed for the gate so the runtime never reasons about platform codes. */
    fun asAgentRunGate(): AgentRunThermalGate = AgentRunThermalGate { currentSeverity() }

    companion object {
        /**
         * Android `PowerManager.THERMAL_STATUS_*`:
         * 0 NONE, 1 LIGHT, 2 MODERATE, 3 SEVERE, 4 CRITICAL, 5 EMERGENCY, 6 SHUTDOWN.
         * `SHUTDOWN` is reported as EMERGENCY: the device is about to power off, and no new local
         * generation may start.
         */
        fun severityOf(platformStatus: Int?): ThermalSeverity = when (platformStatus) {
            0 -> ThermalSeverity.NORMAL
            1 -> ThermalSeverity.LIGHT
            2 -> ThermalSeverity.MODERATE
            3 -> ThermalSeverity.SEVERE
            4 -> ThermalSeverity.CRITICAL
            5, 6 -> ThermalSeverity.EMERGENCY
            else -> ThermalSeverity.UNKNOWN
        }
    }
}
