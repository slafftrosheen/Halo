package com.halo.host.android.resolver

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ResolvedApp(
    val packageName: String,
    val label: String
)

class AndroidApplicationResolver(private val context: Context) {
    private var cachedApps: List<ResolvedApp>? = null

    suspend fun resolve(query: String): ResolvedApp? = withContext(Dispatchers.IO) {
        val apps = cachedApps ?: queryInstalledApps().also { cachedApps = it }
        val normalized = query.trim().lowercase()

        if (normalized.isEmpty()) return@withContext null

        // 1. Exact label match (case-insensitive)
        apps.firstOrNull { it.label.lowercase() == normalized }
            // 2. Contains match (e.g. "youtube" in "YouTube")
            ?: apps.firstOrNull { it.label.lowercase().contains(normalized) || normalized.contains(it.label.lowercase()) }
            // 3. Package name match (e.g. "com.google.android.youtube")
            ?: apps.firstOrNull { it.packageName.lowercase().contains(normalized) }
    }

    private fun queryInstalledApps(): List<ResolvedApp> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
        return resolveInfos.mapNotNull { info ->
            val pkg = info.activityInfo.packageName
            val label = info.loadLabel(pm).toString()
            if (pkg.isNullOrBlank() || label.isBlank()) null else ResolvedApp(pkg, label)
        }
    }
}
