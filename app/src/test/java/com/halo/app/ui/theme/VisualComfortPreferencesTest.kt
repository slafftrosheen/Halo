package com.halo.app.ui.theme

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class VisualComfortPreferencesTest {
    @Test fun guidanceDoesNotChangeMotionContrastOrTheIndependentGuideLight() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        // Each Robolectric application has its own preferences; do not inherit another fixture's singleton.
        val owner = VisualPreferences(context)
        val original = owner.persistedSnapshot()
        try {
            owner.update { it.copy(higherContrast = true, lessMotion = true, guideLight = false) }
            HaloGuidanceAmount.entries.forEach { amount ->
                owner.update { it.copy(guidanceAmount = amount) }
                val freshOwner = VisualPreferences(context)
                val restored = freshOwner.persistedSnapshot()
                assertEquals(amount, restored.guidanceAmount)
                assertTrue(restored.higherContrast); assertTrue(restored.lessMotion); assertFalse(restored.guideLight)
                assertEquals(restored, freshOwner.state.value)
                assertEquals(amount.name, context.getSharedPreferences("halo_visual_options", 0).getString("guidanceAmount", null))
            }
        } finally { owner.update { original } }
    }
}
