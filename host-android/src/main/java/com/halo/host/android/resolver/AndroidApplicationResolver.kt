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
    data object VisibilityUnavailable : ApplicationResolution
    data object NotFound : ApplicationResolution
}

class AndroidApplicationResolver(private val context: Context,
    private val catalog: (() -> List<ResolvedApp>)? = null) {
    @Volatile private var cachedApps: List<ResolvedApp>? = null
    @Volatile private var cachedAtMs: Long = 0L

    fun invalidate() { cachedApps = null; cachedAtMs = 0L }

    suspend fun search(query: String, includeSystem: Boolean = false, limit: Int = 20): List<AppCandidate> = withContext(Dispatchers.IO) {
        val normalized = query.trim().lowercase(Locale.ROOT)
        if (normalized.isBlank()) return@withContext emptyList()
        val now = System.currentTimeMillis()
        val apps = cachedApps?.takeIf { now - cachedAtMs <= CACHE_TTL_MS }
            ?: (catalog?.invoke() ?: queryInstalledApps()).distinctBy { it.packageName }.also { cachedApps = it; cachedAtMs = now }
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
        val candidates = try {
            var matches = search(trimmed, includeSystem = true)
            if (matches.isEmpty()) {
                // A miss may be a package installed since the cached launcher scan.
                invalidate()
                matches = search(trimmed, includeSystem = true)
            }
            matches
        } catch (_: SecurityException) {
            return@withContext ApplicationResolution.VisibilityUnavailable
        }
        if (cachedApps?.isEmpty() == true) return@withContext ApplicationResolution.VisibilityUnavailable
        val aliasPackages = aliases[trimmed.lowercase(Locale.ROOT)].orEmpty()
        val aliasesFound = cachedApps.orEmpty().filter { it.packageName in aliasPackages }
        // Aliases only select a package actually returned by Android's launcher catalogue.
        if (candidates.none { it.quality == AppMatchQuality.EXACT } && aliasesFound.isNotEmpty()) {
            return@withContext if (aliasesFound.size == 1) ApplicationResolution.Resolved(aliasesFound.single())
            else ApplicationResolution.Ambiguous(aliasesFound)
        }
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

        // Search suggestions are not execution authority. A partial name must be clarified,
        // even when this device currently returns only one suggestion.
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

    private companion object {
        const val CACHE_TTL_MS = 60_000L
        val aliases = mapOf(
            "google chrome" to setOf("com.android.chrome"),
            "chrome" to setOf("com.android.chrome"),
            "youtube" to setOf("com.google.android.youtube"),
            "ютуб" to setOf("com.google.android.youtube"),
            "whatsapp" to setOf("com.whatsapp"),
            "ватсап" to setOf("com.whatsapp"),
            "telegram" to setOf("org.telegram.messenger", "org.telegram.messenger.web"),
            "телеграм" to setOf("org.telegram.messenger", "org.telegram.messenger.web"),
            "settings" to setOf("com.android.settings"),
            "настройки" to setOf("com.android.settings"),
            "iestatījumi" to setOf("com.android.settings"),
        )
    }
}
