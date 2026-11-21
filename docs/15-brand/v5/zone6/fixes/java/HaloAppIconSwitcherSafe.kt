package com.halo.app.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * halo 7.4 · safe replacement for HaloAppIconSwitcher.apply().
 * Fixes: early return left two icons enabled; a failed enable could leave no launcher at all.
 * Order is always: enable the new alias → confirm → disable the others. Classic is the fallback.
 */
object HaloAppIconSwitcherSafe {
    const val CLASSIC = "com.halo.app.icon.Classic"

    fun apply(context: Context, component: String, allAliases: List<String>) {
        val pm = context.packageManager
        val pkg = context.packageName
        val target = ComponentName(pkg, component)

        pm.setComponentEnabledSetting(target, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        if (!isOn(pm, target, component)) return // keep whatever works now; never end up with zero icons

        allAliases.filter { it != component }.forEach {
            pm.setComponentEnabledSetting(ComponentName(pkg, it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }

    /** Call once from Application.onCreate(): if nothing can launch halo, turn Classic back on. */
    fun repair(context: Context, allAliases: List<String>) {
        val pm = context.packageManager
        val anyOn = allAliases.any { isOn(pm, ComponentName(context.packageName, it), it) }
        if (!anyOn) pm.setComponentEnabledSetting(ComponentName(context.packageName, CLASSIC),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
    }

    private fun isOn(pm: PackageManager, cn: ComponentName, name: String): Boolean =
        when (pm.getComponentEnabledSetting(cn)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> name == CLASSIC // manifest default: only Classic ships enabled
            else -> false
        }
}
