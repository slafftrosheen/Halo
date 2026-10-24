package com.halo.app.ui.fx

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class HaloMotionLifecycleTest {
    @get:Rule val compose = createComposeRule()

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
