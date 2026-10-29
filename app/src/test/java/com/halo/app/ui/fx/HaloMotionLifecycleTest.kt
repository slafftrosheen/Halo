package com.halo.app.ui.fx

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HaloMotionLifecycleTest {
    @get:Rule val compose = createComposeRule()

    @Test fun idleGuideAuraFramesStayStillWhileActiveRuntimeFeedbackChanges() {
        val light = mutableStateOf(HaloLight.IDLE)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            com.halo.app.ui.theme.HostlessHaloTheme {
                Box(Modifier.size(120.dp).testTag("guide-aura")
                    .haloStateAura(light.value, Color.Blue, Color.Yellow, Color.Red))
            }
        }
        // Advancing Compose's clock does not itself drive Android drawing.
        // Explicitly render each frame; this proves visual motion, not CPU cost.
        fun frame(): IntArray {
            val image = compose.onNodeWithTag("guide-aura").captureToImage()
            return IntArray(image.width * image.height).also { image.readPixels(it) }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        val idleFrame = frame()
        assertTrue("The actual guide surface must contain rendered pixels", idleFrame.any { it != 0 })
        compose.mainClock.advanceTimeBy(5_000)
        compose.waitForIdle()
        assertTrue("Settled idle must retain the same guide frame", idleFrame.contentEquals(frame()))
        compose.runOnIdle { light.value = HaloLight.THINKING }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        val activeFrame = frame()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertFalse("Real active feedback must retain its pulse", activeFrame.contentEquals(frame()))
    }

    @Test fun decorativePlaybackTracksTheSurfaceLifecycleWithoutRemountingIt() {
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry.createUnsafe(this)
            override val lifecycle: Lifecycle get() = registry
        }
        owner.registry.currentState = Lifecycle.State.RESUMED
        var foreground = false
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                val active = rememberHaloMotionForeground()
                SideEffect { foreground = active }
            }
        }
        compose.runOnIdle { assertTrue(foreground) }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.runOnIdle { assertFalse(foreground) }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        compose.runOnIdle { assertTrue(foreground) }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.DESTROYED }
        compose.runOnIdle { assertFalse(foreground) }
    }
}
