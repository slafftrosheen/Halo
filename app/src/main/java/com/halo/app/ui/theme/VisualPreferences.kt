package com.halo.app.ui.theme

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class HaloGuidanceAmount { OFF, CUES, EXPLANATIONS }
enum class HaloViewMode { EASY, STANDARD, COMPACT }
data class HaloVisualOptions(val higherContrast: Boolean = false, val lessMotion: Boolean = false, val guideLight: Boolean = true,
    val guidanceAmount: HaloGuidanceAmount = HaloGuidanceAmount.EXPLANATIONS,
    val motionLevel: HaloLookMotion = if (lessMotion) HaloLookMotion.STILL else HaloLookMotion.GENTLE,
    val haptics: Boolean = true,
    val edgeLight: Boolean = true,
    val buttonLabels: HaloButtonLabels = HaloButtonLabels.ALWAYS,
    val iconStyle: HaloIconStyle = HaloIconStyle.LINE,
    val appIcon: HaloAppIconMode = HaloAppIconMode.MATCH_LOOK,
    val viewMode: HaloViewMode = HaloViewMode.STANDARD,
) {
    internal fun withViewMode(mode: HaloViewMode): HaloVisualOptions = when (mode) {
        HaloViewMode.EASY -> copy(viewMode = mode, buttonLabels = HaloButtonLabels.ALWAYS,
            motionLevel = if (motionLevel == HaloLookMotion.STILL) motionLevel else HaloLookMotion.GENTLE,
            guideLight = true, guidanceAmount = HaloGuidanceAmount.EXPLANATIONS)
        HaloViewMode.STANDARD -> copy(viewMode = mode, buttonLabels = HaloButtonLabels.ALWAYS,
            guideLight = true, guidanceAmount = HaloGuidanceAmount.EXPLANATIONS)
        HaloViewMode.COMPACT -> copy(viewMode = mode, buttonLabels = HaloButtonLabels.SELECTED_ONLY,
            guideLight = false, guidanceAmount = HaloGuidanceAmount.OFF)
    }
}
val LocalHaloAccentInk = staticCompositionLocalOf { Color(0xFF047A5E) }

/** App-wide options are restored on IO; writes cannot race the initial restore. */
class VisualPreferences internal constructor(context: Context) {
    private val contextForWidgets = context.applicationContext
    private val disk = context.applicationContext.getSharedPreferences("halo_visual_options", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(HaloVisualOptions())
    val state = mutable.asStateFlow()
    private val loaded = scope.async {
        mutable.value = readOptions()
    }
    /** Read the same persisted namespace as update, after restore and any owned write. */
    suspend fun persistedSnapshot(): HaloVisualOptions = withContext(Dispatchers.IO) {
        loaded.await()
        mutex.withLock {
            readOptions()
        }
    }
    private fun readOptions(): HaloVisualOptions {
        val legacyReduced = disk.getBoolean("motion", false)
        val motion = HaloLookMotion.entries.find { it.name == disk.getString("motionLevel", null) }
            ?: if (legacyReduced) HaloLookMotion.STILL else HaloLookMotion.GENTLE
        return HaloVisualOptions(
            higherContrast = disk.getBoolean("contrast", false),
            lessMotion = motion == HaloLookMotion.STILL,
            guideLight = disk.getBoolean("guide", true),
            guidanceAmount = HaloGuidanceAmount.entries.find { it.name == disk.getString("guidanceAmount", null) }
                ?: HaloGuidanceAmount.EXPLANATIONS,
            motionLevel = motion,
            haptics = disk.getBoolean("haptics", true),
            edgeLight = disk.getBoolean("edgeLight", true),
            buttonLabels = HaloButtonLabels.entries.find { it.name == disk.getString("buttonLabels", null) }
                ?: HaloButtonLabels.ALWAYS,
            iconStyle = HaloIconStyle.entries.find { it.name == disk.getString("iconStyle", null) } ?: HaloIconStyle.LINE,
            appIcon = HaloAppIconMode.entries.find { it.name == disk.getString("appIcon", null) } ?: HaloAppIconMode.MATCH_LOOK,
            viewMode = HaloViewMode.entries.find { it.name == disk.getString("viewMode", null) } ?: HaloViewMode.STANDARD,
        )
    }
    suspend fun update(transform: (HaloVisualOptions) -> HaloVisualOptions) = withContext(Dispatchers.IO) {
        loaded.await()
        mutex.withLock {
            val before = mutable.value
            val requested = transform(before)
            if (requested.copy(higherContrast = before.higherContrast) != before)
                HaloLayoutLock.get(contextForWidgets).requireUnlocked()
            val motion = when {
                requested.motionLevel != before.motionLevel -> requested.motionLevel
                requested.lessMotion != before.lessMotion -> if (requested.lessMotion) HaloLookMotion.STILL else HaloLookMotion.GENTLE
                else -> requested.motionLevel
            }
            val next = requested.copy(motionLevel = motion, lessMotion = motion == HaloLookMotion.STILL)
            check(disk.edit().putBoolean("contrast", next.higherContrast).putBoolean("motion", next.lessMotion)
                .putBoolean("guide", next.guideLight).putString("guidanceAmount", next.guidanceAmount.name)
                .putString("motionLevel", next.motionLevel.name).putBoolean("haptics", next.haptics)
                .putBoolean("edgeLight", next.edgeLight).putString("buttonLabels", next.buttonLabels.name)
                .putString("iconStyle", next.iconStyle.name).putString("appIcon", next.appIcon.name)
                .putString("viewMode", next.viewMode.name).commit()) {
                "Unable to save visual preferences"
            }
            mutable.value = next
            com.halo.app.nativeactions.HaloNativeWidget.refreshAsync(contextForWidgets)
        }
    }
    companion object {
        @Volatile private var instance: VisualPreferences? = null
        fun get(context: Context): VisualPreferences = instance ?: synchronized(this) {
            instance ?: VisualPreferences(context).also { instance = it }
        }
    }
}

@Composable fun rememberVisualPreferences(): HaloVisualOptions {
    LocalHaloVisualOverride.current?.let { return it }
    val context = LocalContext.current
    // Alarm UI is available before first unlock; private preference storage is not.
    if (!context.getSystemService(android.os.UserManager::class.java).isUserUnlocked)
        return HaloVisualOptions(lessMotion = true, guideLight = false)
    val preferences = remember(context.applicationContext) { VisualPreferences.get(context) }
    return preferences.state.collectAsStateWithLifecycle().value
}
