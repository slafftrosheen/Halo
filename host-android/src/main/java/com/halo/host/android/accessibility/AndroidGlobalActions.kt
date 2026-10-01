package com.halo.host.android.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Build
import com.halo.runtime.cognition.ui.UiOperation

/** Public platform actions, filtered against the connected service on API 30+. */
object AndroidGlobalActions {
    private data class Action(val operation: UiOperation, val id: Int, val minSdk: Int)
    // IDs are the public AccessibilityService GLOBAL_ACTION_* constants. Keep newer
    // constants behind SDK guards without introducing Android dependencies in Runtime.
    private val actions = listOf(
        Action(UiOperation.BACK, AccessibilityService.GLOBAL_ACTION_BACK, 16), Action(UiOperation.HOME, AccessibilityService.GLOBAL_ACTION_HOME, 16),
        Action(UiOperation.RECENTS, AccessibilityService.GLOBAL_ACTION_RECENTS, 16), Action(UiOperation.NOTIFICATIONS, AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS, 16),
        Action(UiOperation.QUICK_SETTINGS, AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS, 17), Action(UiOperation.POWER_DIALOG, AccessibilityService.GLOBAL_ACTION_POWER_DIALOG, 21),
        Action(UiOperation.TOGGLE_SPLIT_SCREEN, AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN, 24), Action(UiOperation.LOCK_SCREEN, AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN, 28),
        Action(UiOperation.TAKE_SCREENSHOT, AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT, 28), Action(UiOperation.HEADSET_HOOK, AccessibilityService.GLOBAL_ACTION_KEYCODE_HEADSETHOOK, 31),
        Action(UiOperation.ACCESSIBILITY_BUTTON, AccessibilityService.GLOBAL_ACTION_ACCESSIBILITY_BUTTON, 31), Action(UiOperation.ACCESSIBILITY_BUTTON_CHOOSER, AccessibilityService.GLOBAL_ACTION_ACCESSIBILITY_BUTTON_CHOOSER, 31),
        Action(UiOperation.ACCESSIBILITY_SHORTCUT, AccessibilityService.GLOBAL_ACTION_ACCESSIBILITY_SHORTCUT, 31), Action(UiOperation.ALL_APPS, AccessibilityService.GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS, 31),
        Action(UiOperation.DPAD_UP, AccessibilityService.GLOBAL_ACTION_DPAD_UP, 33), Action(UiOperation.DPAD_DOWN, AccessibilityService.GLOBAL_ACTION_DPAD_DOWN, 33),
        Action(UiOperation.DPAD_LEFT, AccessibilityService.GLOBAL_ACTION_DPAD_LEFT, 33), Action(UiOperation.DPAD_RIGHT, AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT, 33),
        Action(UiOperation.DPAD_CENTER, AccessibilityService.GLOBAL_ACTION_DPAD_CENTER, 33), Action(UiOperation.MENU, AccessibilityService.GLOBAL_ACTION_MENU, 36),
        Action(UiOperation.DISMISS_NOTIFICATION_SHADE, AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE, 31), Action(UiOperation.MEDIA_PLAY_PAUSE, AccessibilityService.GLOBAL_ACTION_MEDIA_PLAY_PAUSE, 36),
    )
    fun supported(service: AccessibilityService?, sdk: Int = Build.VERSION.SDK_INT): Map<String, UiOperation> {
        if (service == null) return emptyMap()
        val advertised = if (sdk >= 30) service.systemActions.map { it.id }.toSet() else null
        return forDevice(sdk, advertised)
    }
    fun forDevice(sdk: Int, advertised: Set<Int>?): Map<String, UiOperation> {
        // Modern Android must provide actual service evidence, including an empty set.
        if (sdk >= 30 && advertised == null) return emptyMap()
        return actions.filter { sdk >= it.minSdk && (advertised == null || it.id in advertised) }
            .associate { it.operation.name.lowercase() to it.operation }
    }
    fun id(service: AccessibilityService, operation: UiOperation): Int? =
        actions.firstOrNull { it.operation == operation && operation in supported(service).values }?.id
}
