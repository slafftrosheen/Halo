package com.halo.app.data.models

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.security.MessageDigest

/** Coarse environment identity; never reads installation, account or hardware identifiers. */
data class DeviceRuntimeIdentity(
    val fingerprint: String,
    val manufacturer: String,
    val model: String,
    val sdk: Int,
    val memoryClassMb: Int,
) {
    companion object {
        fun current(context: Context): DeviceRuntimeIdentity {
            val memory = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).memoryClass
            val soc = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else "unknown"
            val coarse = listOf(Build.MANUFACTURER, Build.MODEL, soc, Build.VERSION.SDK_INT.toString(),
                Build.SUPPORTED_64_BIT_ABIS.joinToString(","), memory.toString())
            return DeviceRuntimeIdentity(hash(coarse.joinToString("\n")), Build.MANUFACTURER, Build.MODEL, Build.VERSION.SDK_INT, memory)
        }
        internal fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
