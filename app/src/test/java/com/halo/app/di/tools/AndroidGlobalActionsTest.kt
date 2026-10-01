package com.halo.app.di.tools

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import com.halo.host.android.accessibility.AndroidGlobalActions
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidGlobalActionsTest {
    @Test fun `modern actions require platform advertisement and minimum SDK`() {
        val ids = setOf(1, 2, 21, 22, 999)
        assertEquals(setOf("back", "home"), AndroidGlobalActions.forDevice(33, ids).keys)
        assertEquals(setOf("back", "home", "menu", "media_play_pause"), AndroidGlobalActions.forDevice(36, ids).keys)
        assertTrue(AndroidGlobalActions.supported(null, 37).isEmpty())
    }
    @Test fun `API 26 never offers screenshot lock or newer system actions`() {
        val actions = AndroidGlobalActions.forDevice(26, null)
        assertTrue("back" in actions && "quick_settings" in actions)
        assertFalse("lock_screen" in actions || "take_screenshot" in actions || "menu" in actions)
    }
}
