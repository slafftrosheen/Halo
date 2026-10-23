package com.halo.app.screen

import android.os.PowerManager
import org.junit.Assert.*
import org.junit.Test

class ScreenAmbientPolicyTest {
    private val normal = ScreenEnvironment(true, true, false, true, 80, false, PowerManager.THERMAL_STATUS_NONE)

    @Test fun privateCardsAreMaskedByDefaultInDeskModeButMayBeExplicitlyRevealedWhileUnlocked() {
        assertTrue(screenMasksSensitive(true, true, normal))
        assertFalse(screenMasksSensitive(true, false, normal))
        assertFalse(screenMasksSensitive(false, true, normal))
    }

    @Test fun lockBackgroundAndScreenOffAlwaysMaskEvenAfterPrivacyWasExplicitlyDisabled() {
        listOf(normal.copy(locked = true), normal.copy(resumed = false), normal.copy(interactive = false)).forEach { state ->
            for (full in listOf(false, true)) for (hide in listOf(false, true)) assertTrue(screenMasksSensitive(full, hide, state))
        }
    }

    @Test fun chargedKeepAwakeRequiresExplicitChoiceAndVisibleUnlockedScreen() {
        assertTrue(screenAmbientDecision(true, true, false, false, normal).keepAwake)
        assertFalse(screenAmbientDecision(false, true, false, false, normal).keepAwake)
        assertFalse(screenAmbientDecision(true, false, false, false, normal).keepAwake)
        listOf(normal.copy(charging = false), normal.copy(resumed = false), normal.copy(interactive = false), normal.copy(locked = true)).forEach {
            assertFalse(screenAmbientDecision(true, true, false, false, it).keepAwake)
        }
    }

    @Test fun lowBatterySaverHeatAndUnavailableObservationsPreventKeepAwakeAndCapBrightness() {
        listOf(normal.copy(batteryPercent = 15), normal.copy(powerSave = true),
            normal.copy(thermalStatus = PowerManager.THERMAL_STATUS_SEVERE), normal.copy(thermalStatus = null),
            normal.copy(batteryPercent = null)).forEach {
            val policy = screenAmbientDecision(true, true, false, false, it)
            assertFalse(policy.keepAwake); assertTrue(policy.resting); assertEquals(.1f, policy.brightnessCap!!, 0f)
        }
    }

    @Test fun nightDimmingIsScopedAndNeverStartsOutsideDeskMode() {
        assertEquals(.1f, screenAmbientDecision(true, true, true, true, normal).brightnessCap!!, 0f)
        assertNull(screenAmbientDecision(true, true, false, true, normal).brightnessCap)
        assertNull(screenAmbientDecision(true, true, true, false, normal).brightnessCap)
        assertNull(screenAmbientDecision(false, true, true, true, normal).brightnessCap)
        assertNull(screenAmbientDecision(true, true, true, true, normal.copy(resumed = false)).brightnessCap)
    }
}
