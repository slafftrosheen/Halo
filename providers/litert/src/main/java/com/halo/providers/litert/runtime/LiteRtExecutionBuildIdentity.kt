package com.halo.providers.litert.runtime

import android.content.Context
import android.os.Build
import java.security.MessageDigest

/** Installed execution build, not an account, hardware serial or model-quality assertion. */
object LiteRtExecutionBuildIdentity {
    fun current(context: Context): String? = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val installedAt = info.lastUpdateTime.takeIf { it > 0 } ?: return@runCatching null
        val version = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        val identity = "${context.packageName}\n$version\n$installedAt\n${Build.ID}"
        MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }.getOrNull()
}
