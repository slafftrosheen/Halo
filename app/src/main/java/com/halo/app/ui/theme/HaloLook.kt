package com.halo.app.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.runtime.staticCompositionLocalOf
import com.halo.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class HaloIconStyle { LINE, SPOT, TILE }
enum class HaloAppIconMode { MATCH_LOOK, CLASSIC, LIGHT, SYSTEM }
enum class HaloButtonLabels { ALWAYS, SELECTED_ONLY }
enum class HaloLookMotion(val damping: Float) { FULL(.62f), GENTLE(.90f), STILL(1f) }

internal enum class HaloLookPreset(val dark: Boolean, val palette: HaloPalette, @StringRes val label: Int,
    val motion: HaloLookMotion = HaloLookMotion.GENTLE, val clear: Boolean = false) {
    NIGHT_MINT(true, HaloPalette(HaloAccent.MINT, HaloTone.NATURAL, HaloBackgroundStyle.HALO), R.string.look_preset_night_mint),
    PAPER_SAGE(true, HaloPalette(HaloAccent.SAGE, HaloTone.PAPER, HaloBackgroundStyle.STILL), R.string.look_preset_paper_sage),
    SLATE_BLUE(true, HaloPalette(HaloAccent.BLUE, HaloTone.SLATE, HaloBackgroundStyle.MIST), R.string.look_preset_slate_blue),
    WARM_ROSE(false, HaloPalette(HaloAccent.ROSE, HaloTone.WARM, HaloBackgroundStyle.HALO), R.string.look_preset_warm_rose),
    COOL_LILAC(false, HaloPalette(HaloAccent.LILAC, HaloTone.COOL, HaloBackgroundStyle.MIST), R.string.look_preset_cool_lilac),
    CORAL_DAY(false, HaloPalette(HaloAccent.CORAL, HaloTone.NATURAL, HaloBackgroundStyle.STILL), R.string.look_preset_coral_day),
    CALM_NIGHT(true, HaloPalette(), R.string.f13_look_calm_night),
    PAPER_MORNING(false, HaloPalette(HaloAccent.SAGE, HaloTone.PAPER, HaloBackgroundStyle.STILL), R.string.f13_look_paper_morning),
    LIVELY(true, HaloPalette(HaloAccent.CORAL, HaloTone.WARM, HaloBackgroundStyle.HALO), R.string.f13_look_lively, HaloLookMotion.FULL),
    WORLD(false, HaloPalette(HaloAccent.BLUE, HaloTone.COOL, HaloBackgroundStyle.MIST), R.string.f13_look_world),
    CLEAR(false, HaloPalette(HaloAccent.BLUE, HaloTone.SLATE, HaloBackgroundStyle.STILL), R.string.f13_look_clear,
        HaloLookMotion.STILL, clear = true),
    NIGHT_GARDEN(true, HaloPalette(HaloAccent.SAGE, HaloTone.NATURAL, HaloBackgroundStyle.MIST), R.string.f13_look_night_garden),
    SUNRISE(false, HaloPalette(HaloAccent.ROSE, HaloTone.WARM, HaloBackgroundStyle.HALO), R.string.f13_look_sunrise);

    companion object {
        val releaseLooks = listOf(CALM_NIGHT, PAPER_MORNING, LIVELY, WORLD, CLEAR, NIGHT_GARDEN, SUNRISE)
    }
}

/** The override lives only while the Appearance page owns a draft. */
internal object HaloLookPreview {
    data class Draft(val owner: Any, val dark: Boolean, val palette: HaloPalette, val visual: HaloVisualOptions)
    private val mutable = MutableStateFlow<Draft?>(null)
    val state = mutable.asStateFlow()
    fun show(draft: Draft) { mutable.value = draft }
    fun clear(owner: Any) {
        if (mutable.value?.owner === owner) mutable.value = null
    }
}

internal val LocalHaloVisualOverride = staticCompositionLocalOf<HaloVisualOptions?> { null }

internal object HaloAppIconSwitcher {
    private val names = listOf("Classic") + listOf("Night", "Cream").flatMap { side ->
        HaloAccent.entries.map { side + it.name.lowercase(Locale.ROOT).replaceFirstChar(Char::uppercaseChar) }
    }

    fun apply(context: Context, mode: HaloAppIconMode, dark: Boolean, accent: HaloAccent) {
        val side = if (mode == HaloAppIconMode.MATCH_LOOK && dark) "Night" else "Cream"
        val target = when (mode) {
            HaloAppIconMode.CLASSIC, HaloAppIconMode.SYSTEM -> "Classic"
            HaloAppIconMode.MATCH_LOOK, HaloAppIconMode.LIGHT ->
                side + accent.name.lowercase(Locale.ROOT).replaceFirstChar(Char::uppercaseChar)
        }
        val manager = context.packageManager
        val changes = names.mapNotNull { alias ->
            val component = ComponentName(context.packageName, "com.halo.app.icon.$alias")
            val current = manager.getComponentEnabledSetting(component)
            val enabled = when (current) {
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> alias == "Classic"
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                else -> false
            }
            val shouldEnable = alias == target
            if (enabled == shouldEnable) null else component to if (shouldEnable)
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        if (changes.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 33) {
            manager.setComponentEnabledSettings(changes.map { (component, setting) ->
                PackageManager.ComponentEnabledSetting(component, setting, PackageManager.DONT_KILL_APP)
            })
        } else {
            // Enable the destination before hiding the current launcher entry.
            changes.sortedBy { (_, setting) -> setting != PackageManager.COMPONENT_ENABLED_STATE_ENABLED }
                .forEach { (component, setting) ->
                    manager.setComponentEnabledSetting(component, setting, PackageManager.DONT_KILL_APP)
                }
        }
    }
}
