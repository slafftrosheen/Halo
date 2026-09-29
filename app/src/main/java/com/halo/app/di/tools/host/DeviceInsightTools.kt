package com.halo.app.di.tools.host

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolExecutor
import com.halo.runtime.cognition.tools.ToolFailureKind
import com.halo.runtime.cognition.tools.ToolResultEnvelope
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * RUNTIME-056 native ability batch, harvested from the OneBox agent tool catalogue
 * (`get_device_info`, network status): the assistant could act on the phone but could
 * not describe it.
 *
 * Everything here is observation only. Nothing reads user content, nothing changes
 * state, and no field is invented when the platform will not answer: an unavailable
 * value is reported as `unknown` with the reason, never guessed.
 */
class DeviceInsightTools(private val context: Context) : ToolExecutor {

    override fun definitions(): List<ToolDefinition> = listOf(
        ToolDefinition(
            id = "halo_device_info",
            name = "halo_device_info",
            description = "Describe the phone Halo is running on: model, Android version, screen, memory and locale. " +
                "Use this to explain compatibility or performance questions instead of guessing about the device.",
            parameters = mapOf(
                "type" to "object",
                "properties" to emptyMap<String, Any>(),
                "additionalProperties" to false,
            ),
        ),
        ToolDefinition(
            id = "halo_battery_status",
            name = "halo_battery_status",
            description = "Read the current battery level, charging state, power-save state and temperature.",
            parameters = mapOf(
                "type" to "object",
                "properties" to emptyMap<String, Any>(),
                "additionalProperties" to false,
            ),
        ),
        ToolDefinition(
            id = "halo_network_status",
            name = "halo_network_status",
            description = "Read the active network type, whether the connection is validated or metered, and the local IP " +
                "addresses. Wi-Fi network name is included only when Halo is allowed to read location.",
            parameters = mapOf(
                "type" to "object",
                "properties" to emptyMap<String, Any>(),
                "additionalProperties" to false,
            ),
        ),
    )

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        fun reject(reason: String, kind: com.halo.runtime.cognition.tools.ToolRejectionKind) =
            ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason, kind)

        if (toolCall.arguments.isNotEmpty()) {
            return reject("This action takes no arguments", com.halo.runtime.cognition.tools.ToolRejectionKind.INVALID_ARGUMENTS)
        }

        return when (toolCall.functionName) {
            "halo_device_info" -> success(toolCall, deviceInfo())
            "halo_battery_status" -> batteryStatus(toolCall)
            "halo_network_status" -> success(toolCall, networkStatus())
            else -> reject("Unknown tool", com.halo.runtime.cognition.tools.ToolRejectionKind.TOOL_UNKNOWN)
        }
    }

    private fun success(toolCall: ToolCall, body: String) = ToolExecutionResult.Success(
        toolCallId = toolCall.id,
        toolId = toolCall.functionName,
        output = ToolResultEnvelope.success(actionTruth = ActionTruth.CONFIRMED, details = body),
        actionTruth = ActionTruth.CONFIRMED,
    )

    private fun deviceInfo(): String = buildString {
        appendLine("manufacturer=${Build.MANUFACTURER}")
        appendLine("brand=${Build.BRAND}")
        appendLine("model=${Build.MODEL}")
        appendLine("device=${Build.DEVICE}")
        appendLine("android_release=${Build.VERSION.RELEASE}")
        appendLine("sdk_int=${Build.VERSION.SDK_INT}")
        appendLine("supported_abis=${Build.SUPPORTED_ABIS.joinToString(",")}")
        val metrics = displayMetrics()
        if (metrics == null) {
            appendLine("screen=unknown (display metrics unavailable)")
        } else {
            appendLine("screen_pixels=${metrics.widthPixels}x${metrics.heightPixels}")
            appendLine("screen_density_dpi=${metrics.densityDpi}")
            appendLine("screen_density_scale=${"%.2f".format(Locale.US, metrics.density)}")
        }
        appendLine("screen_refresh_hz=${refreshRate() ?: "unknown"}")
        appendLine("total_ram_mb=${totalRamMb() ?: "unknown"}")
        appendLine("app_heap_max_mb=${Runtime.getRuntime().maxMemory() / MEGABYTE}")
        appendLine("uptime_hours=${TimeUnit.MILLISECONDS.toHours(SystemClock.elapsedRealtime())}")
        appendLine("locale=${Locale.getDefault().toLanguageTag()}")
        appendLine("timezone=${java.util.TimeZone.getDefault().id}")
        append("airplane_mode=${airplaneModeOn() ?: "unknown (restricted to system apps on this Android version)"}")
    }

    /**
     * Battery truth is read from the sticky broadcast so the answer is the same one
     * Android itself publishes, including `not_charging` versus `discharging`.
     */
    private fun batteryStatus(toolCall: ToolCall): ToolExecutionResult {
        val battery = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (battery == null && sticky == null) {
            return ToolExecutionResult.Failed(
                toolCallId = toolCall.id,
                toolId = toolCall.functionName,
                message = "Android did not report any battery state for this device",
                failureKind = ToolFailureKind.UNAVAILABLE,
                code = "BATTERY_UNAVAILABLE",
                hint = "This device exposes no battery service; tell the user Halo cannot read battery state here.",
            )
        }

        val percentage = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)?.takeIf { it >= 0 }
            ?.let { level ->
                val scale = sticky.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                level * 100 / scale
            }
            ?: battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }

        val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val temperatureTenths = sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?.takeUnless { it == Int.MIN_VALUE || it <= 0 }

        val body = buildString {
            appendLine("level_percent=${percentage ?: "unknown"}")
            appendLine("charging_state=${chargeState(status)}")
            appendLine("plugged=${plugType(plugged)}")
            temperatureTenths?.let { appendLine("temperature_c=${"%.1f".format(Locale.US, it / 10.0)}") }
            sticky?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)?.takeIf { it > 0 }
                ?.let { appendLine("voltage_v=${"%.2f".format(Locale.US, it / 1000.0)}") }
            sticky?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.takeIf { it.isNotBlank() }
                ?.let { appendLine("technology=$it") }
            appendLine("health=${healthLabel(sticky?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1)}")
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            appendLine("power_save_enabled=${powerManager?.isPowerSaveMode ?: "unknown"}")
            append("is_charging=${status == BatteryManager.BATTERY_STATUS_CHARGING || plugged != 0}")
        }
        return success(toolCall, body)
    }

    private fun networkStatus(): String {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return "network=unknown (no connectivity service)"
        val active = manager.activeNetwork
        val capabilities = active?.let { manager.getNetworkCapabilities(it) }
        val link = active?.let { manager.getLinkProperties(it) }

        return buildString {
            appendLine("transports=${transportLabel(capabilities)}")
            appendLine("connected=${capabilities != null}")
            appendLine("validated_internet=${capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ?: false}")
            appendLine("metered=${manager.isActiveNetworkMetered}")
            appendLine("roaming=${capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)?.let { !it } ?: "unknown"}")
            appendLine("vpn=${capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ?: false}")
            capabilities?.linkDownstreamBandwidthKbps?.let { appendLine("downstream_kbps=$it") }
            capabilities?.linkUpstreamBandwidthKbps?.let { appendLine("upstream_kbps=$it") }
            link?.interfaceName?.let { appendLine("interface=$it") }
            appendLine("ip_addresses=${localAddresses(link)}")
            append(wifiName())
        }
    }

    private fun localAddresses(link: LinkProperties?): String {
        val addresses = link?.linkAddresses.orEmpty()
            .mapNotNull { it.address?.hostAddress }
            .filterNot { it.contains(':') }
            .take(MAX_ADDRESSES)
        return addresses.joinToString(",").ifEmpty { "unknown" }
    }

    private fun wifiName(): String {
        val granted = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return "wifi_ssid=withheld (reading the Wi-Fi network name requires location access)\n"
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return "wifi_ssid=unknown (no Wi-Fi service)\n"
        @Suppress("DEPRECATION")
        val ssid = runCatching { wifi.connectionInfo?.ssid }.getOrNull()
            ?.takeUnless { it == android.net.wifi.WifiManager.UNKNOWN_SSID || it.isBlank() }
            ?.trim('"')
        return if (ssid == null) "wifi_ssid=unknown (not on Wi-Fi)\n" else "wifi_ssid=$ssid\n"
    }

    private fun transportLabel(capabilities: NetworkCapabilities?): String {
        if (capabilities == null) return "none"
        val transports = buildList {
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("wifi")
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("cellular")
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("ethernet")
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) add("bluetooth")
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("vpn")
        }
        return transports.joinToString(",").ifEmpty { "none" }
    }

    private fun displayMetrics(): DisplayMetrics? = runCatching {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        DisplayMetrics().also { metrics ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = windowManager.currentWindowMetrics.bounds
                metrics.widthPixels = bounds.width()
                metrics.heightPixels = bounds.height()
                metrics.densityDpi = context.resources.displayMetrics.densityDpi
                metrics.density = context.resources.displayMetrics.density
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getRealMetrics(metrics)
            }
        }
    }.getOrNull()

    private fun refreshRate(): String? = runCatching {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        val display = windowManager.defaultDisplay
        "%.1f".format(Locale.US, display.refreshRate)
    }.getOrNull()

    private fun totalRamMb(): Long? {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return null
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        return info.totalMem / MEGABYTE
    }

    private fun airplaneModeOn(): Boolean? = runCatching {
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON) == 1
    }.getOrNull()

    private fun chargeState(status: Int): String = when (status) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
        BatteryManager.BATTERY_STATUS_FULL -> "full"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not_charging"
        else -> "unknown"
    }

    private fun plugType(plugged: Int): String = when (plugged) {
        BatteryManager.BATTERY_PLUGGED_AC -> "ac"
        BatteryManager.BATTERY_PLUGGED_USB -> "usb"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
        0 -> "none"
        else -> "other"
    }

    private fun healthLabel(health: Int): String = when (health) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
        BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over_voltage"
        BatteryManager.BATTERY_HEALTH_COLD -> "cold"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "failure"
        else -> "unknown"
    }

    private companion object {
        const val MEGABYTE = 1024L * 1024L
        const val MAX_ADDRESSES = 4
    }
}
