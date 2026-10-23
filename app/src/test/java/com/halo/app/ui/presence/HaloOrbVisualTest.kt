package com.halo.app.ui.presence

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.ui.theme.HostlessHaloTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h800dp")
class HaloOrbVisualTest {
    @get:Rule val compose = createComposeRule()

    @Test fun collapsed360() = capture(false)
    @Test @Config(qualifiers = "ru-rRU-w360dp-h800dp") fun russianOpen360LargeText() = capture(true, scale = 2f)
    @Test @Config(qualifiers = "lv-rLV-w412dp-h800dp") fun latvianCapture412LargeText() = capture(true, HaloSignalState.CAPTURING, 2f)
    @Test fun approval360() = capture(true, HaloSignalState.WAITING)
    @Test fun offline360() = capture(true, HaloSignalState.OFFLINE)
    @Test fun failure360() = capture(true, HaloSignalState.ERROR)
    @Test @Config(qualifiers = "w800dp-h900dp") fun tabletInlineSatellites() = capture(true, reduced = false)
    @Test @Config(qualifiers = "w800dp-h900dp") fun tabletReducedMotionUsesList() = capture(true)

    private fun capture(open: Boolean, state: HaloSignalState = HaloSignalState.IDLE,
        scale: Float = 1f, reduced: Boolean = true) {
        val primary = when (state) {
            HaloSignalState.CAPTURING -> HaloOrbPrimary.STOP
            HaloSignalState.WAITING -> HaloOrbPrimary.REVIEW
            else -> HaloOrbPrimary.TALK
        }
        compose.setContent { HostlessHaloTheme(fontScale = scale) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box {
                HaloOrbLauncher(HaloSignalPresentation(state = state, label = when (state) {
                    HaloSignalState.CAPTURING -> "Listening…"
                    HaloSignalState.WAITING -> "Needs your review"
                    HaloSignalState.OFFLINE -> "Halo is offline"
                    HaloSignalState.ERROR -> "Needs attention"
                    else -> "Ready when you are"
                }, reducedMotion = reduced), HaloOrbInteraction(primary, primary != HaloOrbPrimary.TALK),
                    HaloOrbShortcut.entries.toSet(), {}, {}, {}, {})
            }
        } } }
        if (open) compose.onNodeWithContentDescription(when (org.robolectric.RuntimeEnvironment.getApplication().resources.configuration.locales[0].language) {
            "ru" -> "Показать ярлыки Halo"
            "lv" -> "Rādīt Halo saīsnes"
            else -> "Show Halo shortcuts"
        }).performClick()
        compose.waitForIdle()
        if (open && !reduced) compose.onAllNodes(isRoot()).assertCountEquals(1)
        if (open && reduced) org.junit.Assert.assertTrue(compose.onAllNodes(isRoot()).fetchSemanticsNodes().size > 1)
        compose.onAllNodes(isRoot()).onLast().captureRoboImage()
    }
}
