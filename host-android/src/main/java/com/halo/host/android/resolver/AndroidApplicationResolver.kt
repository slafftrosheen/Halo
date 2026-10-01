package com.halo.host.android.resolver

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class ResolvedApp(
    val packageName: String,
    val label: String,
    val systemApp: Boolean = false,
)

enum class AppMatchQuality { EXACT, PREFIX, CONTAINS }
data class AppCandidate(val app: ResolvedApp, val quality: AppMatchQuality)

sealed interface ApplicationResolution {
    data class Resolved(val app: ResolvedApp) : ApplicationResolution
    data class Ambiguous(val candidates: List<ResolvedApp>) : ApplicationResolution
    data object NotFound : ApplicationResolution
}

class AndroidApplicationResolver(private val context: Context) {
    @Volatile private var cachedApps: List<ResolvedApp>? = null
    @Volatile private var cachedAtMs: Long = 0L

    fun invalidate() { cachedApps = null; cachedAtMs = 0L }

    suspend fun search(query: String, includeSystem: Boolean = false, limit: Int = 20): List<AppCandidate> = withContext(Dispatchers.IO) {
        val normalized = query.trim().lowercase(Locale.ROOT)
        if (normalized.isBlank()) return@withContext emptyList()
        val now = System.currentTimeMillis()
        val apps = cachedApps?.takeIf { now - cachedAtMs <= CACHE_TTL_MS }
            ?: queryInstalledApps().also { cachedApps = it; cachedAtMs = now }
        apps.asSequence()
            .filter { includeSystem || !it.systemApp }
            .mapNotNull { app ->
                val label = app.label.lowercase(Locale.ROOT)
                val pkg = app.packageName.lowercase(Locale.ROOT)
                val quality = when {
                    label == normalized || pkg == normalized -> AppMatchQuality.EXACT
                    label.startsWith(normalized) || pkg.startsWith(normalized) -> AppMatchQuality.PREFIX
                    label.contains(normalized) || pkg.contains(normalized) -> AppMatchQuality.CONTAINS
                    else -> null
                }
                quality?.let { AppCandidate(app, it) }
            }
            .sortedWith(compareBy<AppCandidate> { it.quality.ordinal }
                .thenBy { it.app.label.lowercase(Locale.ROOT) }
                .thenBy { it.app.packageName })
            .take(limit.coerceIn(1, 20))
            .toList()
    }

    suspend fun resolve(query: String): ResolvedApp? = withContext(Dispatchers.IO) {
        when (val res = resolveApplication(query)) {
            is ApplicationResolution.Resolved -> res.app
            else -> null
        }
    }

    suspend fun resolveApplication(query: String): ApplicationResolution = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext ApplicationResolution.NotFound
        val candidates = search(trimmed, includeSystem = true)
        if (candidates.isEmpty()) return@withContext ApplicationResolution.NotFound

        // 1. Exact package ID match
        candidates.firstOrNull { it.app.packageName.equals(trimmed, ignoreCase = true) }?.let {
            return@withContext ApplicationResolution.Resolved(it.app)
        }

        // 2. Exact human label match
        val exactLabelMatches = candidates.filter { it.app.label.equals(trimmed, ignoreCase = true) }
        if (exactLabelMatches.size == 1) {
            return@withContext ApplicationResolution.Resolved(exactLabelMatches.first().app)
        } else if (exactLabelMatches.size > 1) {
            return@withContext ApplicationResolution.Ambiguous(exactLabelMatches.map { it.app })
        }

        // 3. Single prefix match
        val prefixMatches = candidates.filter { it.quality == AppMatchQuality.PREFIX }
        if (prefixMatches.size == 1) {
            return@withContext ApplicationResolution.Resolved(prefixMatches.first().app)
        } else if (prefixMatches.size > 1) {
            return@withContext ApplicationResolution.Ambiguous(prefixMatches.map { it.app })
        }

        // 4. Single contains match
        val containsMatches = candidates.filter { it.quality == AppMatchQuality.CONTAINS }
        if (containsMatches.size == 1) {
            return@withContext ApplicationResolution.Resolved(containsMatches.first().app)
        } else if (containsMatches.size > 1) {
            return@withContext ApplicationResolution.Ambiguous(containsMatches.map { it.app })
        }

        ApplicationResolution.Ambiguous(candidates.map { it.app })
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
            val system = (info.activityInfo.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            if (pkg.isNullOrBlank() || label.isBlank()) null else ResolvedApp(pkg, label, system)
        }
    }

    private companion object { const val CACHE_TTL_MS = 60_000L }
}
