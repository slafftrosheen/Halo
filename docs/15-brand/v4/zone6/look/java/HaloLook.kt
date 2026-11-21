package com.halo.app.ui.theme

/*
 * halo "Make it yours" — new look options (Zone 6 · 6.4). Extends the EXISTING owners; nothing replaces them:
 *   PalettePreferences  (accent 6 × tone 5 × background 3, separate light/dark)   — unchanged
 *   VisualPreferences   (higherContrast, lessMotion, guideLight, guidanceAmount) — lessMotion is derived from HaloMotion
 * New values persist in "halo_look" SharedPreferences with the same preview → Apply pattern (selectPersisted).
 */

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class HaloIconStyle { LINE, SPOT, TILE }
enum class HaloAppIconMode { MATCH_LOOK, CLASSIC, LIGHT, SYSTEM }
enum class HaloMotion { FULL, GENTLE, STILL }          // STILL ⇒ VisualPreferences.lessMotion = true
enum class HaloButtonLabels { ALWAYS, SELECTED_ONLY }

data class HaloLook(
    val iconStyle: HaloIconStyle = HaloIconStyle.LINE,
    val appIcon: HaloAppIconMode = HaloAppIconMode.MATCH_LOOK,
    val motion: HaloMotion = HaloMotion.GENTLE,
    val haptics: Boolean = true,
    val edgeLight: Boolean = true,
    val labels: HaloButtonLabels = HaloButtonLabels.ALWAYS,
)
val LocalHaloLook = staticCompositionLocalOf { HaloLook() }

/** Six ready-made looks = real PalettePreferences values. */
enum class HaloPreset(val dark: Boolean, val accent: HaloAccent, val tone: HaloTone, val background: HaloBackgroundStyle) {
    NIGHT_MINT(true, HaloAccent.MINT, HaloTone.NATURAL, HaloBackgroundStyle.HALO),
    PAPER_SAGE(true, HaloAccent.SAGE, HaloTone.PAPER, HaloBackgroundStyle.STILL),
    SLATE_BLUE(true, HaloAccent.BLUE, HaloTone.SLATE, HaloBackgroundStyle.MIST),
    WARM_ROSE(false, HaloAccent.ROSE, HaloTone.WARM, HaloBackgroundStyle.HALO),
    COOL_LILAC(false, HaloAccent.LILAC, HaloTone.COOL, HaloBackgroundStyle.MIST),
    CORAL_DAY(false, HaloAccent.CORAL, HaloTone.NATURAL, HaloBackgroundStyle.STILL),
}

/** Motion level → existing timings. GENTLE = no overshoot, shorter loops; STILL = reduced-motion path everywhere. */
fun HaloMotion.springDamping() = when (this) { HaloMotion.FULL -> 0.62f; HaloMotion.GENTLE -> 0.9f; HaloMotion.STILL -> 1f }
fun HaloMotion.lessMotion() = this == HaloMotion.STILL

/* ---------------- icons follow the theme ---------------- */

/**
 * One call site for every halo icon. Colours come ONLY from the theme roles, so all 60 looks repaint together.
 * Meaning colours are never overridden: pass [semantic] for Apricot (needs you) / error icons.
 */
@Composable
fun HaloThemedIcon(icon: ImageVector, contentDescription: String?, size: Dp = 24.dp,
                   semantic: androidx.compose.ui.graphics.Color? = null, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val accentInk = LocalHaloAccentInk.current
    val contrast = rememberVisualPreferences().higherContrast
    when (if (semantic != null || contrast) HaloIconStyle.LINE else LocalHaloLook.current.iconStyle) {
        HaloIconStyle.LINE -> Icon(icon, contentDescription, modifier.size(size), tint = semantic ?: if (contrast) c.onSurface else accentInk)
        HaloIconStyle.SPOT -> Box(modifier.size(size + 12.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.align(Alignment.TopEnd).size(size * .55f).clip(CircleShape).background(c.primary.copy(alpha = .55f)))
            Icon(icon, contentDescription, Modifier.size(size), tint = c.onSurface)
        }
        HaloIconStyle.TILE -> Box(modifier.size(size + 16.dp).clip(RoundedCornerShape(size * .55f)).background(accentInk.copy(alpha = .15f)),
            contentAlignment = Alignment.Center) { Icon(icon, contentDescription, Modifier.size(size), tint = accentInk) }
    }
}

/* ---------------- app icon follows the look ---------------- */

object HaloAppIconSwitcher {
    private val accents = mapOf(HaloAccent.MINT to "Mint", HaloAccent.BLUE to "Blue", HaloAccent.LILAC to "Lilac",
        HaloAccent.CORAL to "Coral", HaloAccent.SAGE to "Sage", HaloAccent.ROSE to "Rose")
    /** All launcher aliases: Classic + 12 from AndroidManifest.icon-aliases.xml. MainActivity is never toggled. */
    private fun all(context: Context) = listOf("com.halo.app.icon.Classic") +
        listOf("Night", "Cream").flatMap { b -> accents.values.map { a -> "com.halo.app.icon.$b$a" } }

    fun target(mode: HaloAppIconMode, appIsDark: Boolean, palette: HaloPalette): String = when (mode) {
        HaloAppIconMode.CLASSIC, HaloAppIconMode.SYSTEM -> "com.halo.app.icon.Classic"   // SYSTEM: launcher applies the monochrome layer
        HaloAppIconMode.LIGHT -> "com.halo.app.icon.Cream${accents.getValue(palette.accent)}"
        HaloAppIconMode.MATCH_LOOK -> "com.halo.app.icon.${if (appIsDark) "Night" else "Cream"}${accents.getValue(palette.accent)}"
    }

    /**
     * Enable the target FIRST, then disable the others (never zero launcher entries). DONT_KILL_APP.
     * Call only from Apply (never on every recomposition); skip when the target is already enabled.
     */
    fun apply(context: Context, component: String) {
        val pm = context.packageManager
        val target = ComponentName(context.packageName, component)
        if (pm.getComponentEnabledSetting(target) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) return
        pm.setComponentEnabledSetting(target, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        all(context).filter { it != component }.forEach {
            pm.setComponentEnabledSetting(ComponentName(context.packageName, it),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }
}
