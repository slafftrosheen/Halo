package com.halo.app.di.tools.host

import android.content.Context
import android.location.LocationManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.ConnectivityManager
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Local evidence only, no background tracking, active speed test, or guessed sensor values. */
class NativeObservationTools(private val context: Context, private val isForeground: () -> Boolean = { true }) : ToolExecutor {
    override fun definitions() = listOf(ToolDefinition("halo_native_observation", "halo_native_observation",
        "Read local location (including fix time/accuracy), one available sensor sample, or advertised network bandwidth. Advertised bandwidth is not measured internet speed.",
        mapOf("type" to "object", "properties" to mapOf("kind" to mapOf("type" to "string", "enum" to listOf("location", "ambient_light", "proximity", "accelerometer", "network_quality"))),
            "required" to listOf("kind"), "additionalProperties" to false)))
    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        fun reject(message: String, kind: ToolRejectionKind) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, message, kind)
        if (toolCall.functionName != "halo_native_observation") return reject("Unknown tool", ToolRejectionKind.TOOL_UNKNOWN)
        if (toolCall.arguments.keys != setOf("kind")) return reject("Only kind accepted", ToolRejectionKind.INVALID_ARGUMENTS)
        if (!isForeground()) return reject("Open Halo to observe location or sensors", ToolRejectionKind.CAPABILITY_UNAVAILABLE)
        return try {
            val evidence = when (val kind = toolCall.arguments["kind"]) {
                "location" -> {
                    val manager = context.getSystemService(LocationManager::class.java)
                    val providers = manager.getProviders(true)
                    val provider = providers.firstOrNull { it == LocationManager.GPS_PROVIDER }
                        ?: providers.firstOrNull { it == LocationManager.NETWORK_PROVIDER }
                        ?: return reject("No enabled location provider", ToolRejectionKind.CAPABILITY_UNAVAILABLE)
                    val location = withTimeoutOrNull(7_000) { suspendCancellableCoroutine<android.location.Location?> { continuation ->
                        if (android.os.Build.VERSION.SDK_INT >= 30) {
                            val cancellation = android.os.CancellationSignal()
                            continuation.invokeOnCancellation { cancellation.cancel() }
                            manager.getCurrentLocation(provider, cancellation, context.mainExecutor) { location ->
                                if (continuation.isActive) continuation.resume(location)
                            }
                        } else {
                            val listener = object : android.location.LocationListener {
                                override fun onLocationChanged(location: android.location.Location) {
                                    manager.removeUpdates(this)
                                    if (continuation.isActive) continuation.resume(location)
                                }
                                override fun onProviderEnabled(provider: String) = Unit
                                override fun onProviderDisabled(provider: String) = Unit
                                @Deprecated("Legacy Android callback")
                                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
                            }
                            continuation.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
                            @Suppress("DEPRECATION")
                            manager.requestSingleUpdate(provider, listener, android.os.Looper.getMainLooper())
                        }
                    } } ?: return reject("No current location received before timeout; no location guessed", ToolRejectionKind.RESOURCE_UNAVAILABLE)
                    val age = ((android.os.SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000).coerceAtLeast(0)
                    if (age > 30_000) return reject("Location provider returned a stale fix; try again outdoors", ToolRejectionKind.RESOURCE_UNAVAILABLE)
                    "latitude=${location.latitude}\nlongitude=${location.longitude}\nfixTimeMs=${location.time}\nageMs=$age\naccuracyMeters=${if (location.hasAccuracy()) location.accuracy else "unknown"}\nsource=${location.provider}\nnote=Observed location fix; accuracy and timestamp are reported, not inferred."
                }
                "network_quality" -> {
                    val manager = context.getSystemService(ConnectivityManager::class.java)
                    val caps = manager.getNetworkCapabilities(manager.activeNetwork)
                        ?: return reject("No active network evidence", ToolRejectionKind.RESOURCE_UNAVAILABLE)
                    "validated=${caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)}\nmetered=${manager.isActiveNetworkMetered}\nadvertisedDownstreamKbps=${caps.linkDownstreamBandwidthKbps}\nadvertisedUpstreamKbps=${caps.linkUpstreamBandwidthKbps}\nnote=OS advertised bandwidth, not measured throughput. No traffic generated."
                }
                "ambient_light", "proximity", "accelerometer" -> {
                    val manager = context.getSystemService(SensorManager::class.java)
                    val type = when (kind) { "ambient_light" -> Sensor.TYPE_LIGHT; "proximity" -> Sensor.TYPE_PROXIMITY; else -> Sensor.TYPE_ACCELEROMETER }
                    val sensor = manager.getDefaultSensor(type) ?: return reject("Sensor not present", ToolRejectionKind.CAPABILITY_UNAVAILABLE)
                    val sample = withTimeoutOrNull(2_000) { suspendCancellableCoroutine<Pair<Long, FloatArray>> { continuation ->
                        val listener = object : android.hardware.SensorEventListener {
                            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                            override fun onSensorChanged(event: android.hardware.SensorEvent) {
                                manager.unregisterListener(this)
                                if (continuation.isActive) continuation.resume(event.timestamp to event.values.copyOf())
                            }
                        }
                        continuation.invokeOnCancellation { manager.unregisterListener(listener) }
                        if (!manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL, android.os.Handler(android.os.Looper.getMainLooper()))) {
                            continuation.resume(0L to FloatArray(0))
                        }
                    } } ?: return reject("Sensor produced no sample", ToolRejectionKind.RESOURCE_UNAVAILABLE)
                    if (sample.second.isEmpty()) return reject("Sensor registration unavailable", ToolRejectionKind.RESOURCE_UNAVAILABLE)
                    "sensor=$kind\ntimestampElapsedNanos=${sample.first}\nvalues=${sample.second.take(3).joinToString(",")}\nunits=${when(kind){"ambient_light" -> "lux"; "proximity" -> "cm"; else -> "m/s2"}}"
                }
                else -> return reject("Unknown observation kind", ToolRejectionKind.INVALID_ARGUMENTS)
            }
            ToolExecutionResult.Success(toolCall.id, toolCall.functionName,
                ToolResultEnvelope.success(ActionTruth.ACKNOWLEDGED, details = evidence), ActionTruth.ACKNOWLEDGED)
        } catch (_: SecurityException) { reject("Android location or sensor permission required", ToolRejectionKind.PERMISSION_REQUIRED) }
    }
}
