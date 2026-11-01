package com.halo.app.ui.zone5

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.R
import com.halo.app.language.uiText
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.presence.*
import com.halo.app.ui.shell.HaloZone
import com.halo.app.ui.theme.HostlessHaloTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "en-rUS-w360dp-h800dp")
class Zone5PredictableHelpTest {
    @get:Rule val compose = createComposeRule()
    @Test fun compactGuidedContinuationRemainsVisible() = show(1f)
    @Test fun cancellingInspectorResumesGuidanceForTheIdenticalOwner() = show(1f, cancelInspector = true)
    @Test fun inspectorClosingInBackgroundResumesGuidanceOnlyOnReturn() = show(1f, cancelInspector = true, background = true)
    @Test @Config(qualifiers = "ru-rRU-w360dp-h800dp") fun russian200() = show(2f)
    @Test @Config(qualifiers = "lv-rLV-w360dp-h800dp") fun latvian200() = show(2f)
    @Test @Config(qualifiers = "pl-rPL-w360dp-h800dp") fun polish200() = show(2f)
    private fun show(scale: Float, cancelInspector: Boolean = false, background: Boolean = false) {
        val holder = mutableStateOf<ShellPageProjection?>(null)
        val session = Zone5GuideSession()
        var step by mutableStateOf(0)
        var blocked by mutableStateOf(false)
        lateinit var lifecycle: androidx.lifecycle.Lifecycle
        var allActions = ""; var help = ""; var guide = ""; var next = ""; var second = ""
        compose.setContent { HostlessHaloTheme(fontScale = scale) {
            lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
            allActions = stringResource(R.string.zone5_all_actions)
            help = stringResource(R.string.zone5_help_title)
            guide = stringResource(R.string.zone5_help_guide)
            next = uiText("Continue")
            second = stringResource(R.string.zone5_help_study_material)
            CompositionLocalProvider(LocalZone5GuideSession provides session, LocalShellPageActions provides holder) {
                BindShellPageActions(ShellPageOwner("study", sourceIdentity = step.toString(), guidanceBlocked = blocked,
                    helpDescription = stringResource(R.string.zone5_help_study),
                    helpSteps = listOf(ShellGuideStep("step-$step", "page.study.next",
                        stringResource(if (step == 0) R.string.zone5_help_study_goal else R.string.zone5_help_study_material)))),
                    listOf(ShellPageAction("page.study.next", next, HaloGlyph.NEXT) { if (cancelInspector) blocked = true else step++ }))
                if (blocked) androidx.compose.material3.AlertDialog(onDismissRequest = { blocked = false },
                    title = { Text("Inspector draft") }, confirmButton = {
                        androidx.compose.material3.TextButton(onClick = { blocked = false }) { Text("Cancel inspector") }
                    })
                val actions = holder.value?.actions.orEmpty().map { HaloOrbContextAction("page:${it.id}", it.title, it.glyph) }
                Column(Modifier.fillMaxSize()) {
                    Text("Synthetic lesson", Modifier.weight(1f))
                    HaloZone("study", 800.dp, true, actions, { holder.value?.invoke(it.id.removePrefix("page:")) }, {}, 0,
                        utility = {}, backdrop = {}, guideIdle = true, entryId = "study-entry", orb = { size ->
                            HaloOrbLauncher(HaloSignalPresentation(state = HaloSignalState.IDLE, label = "Ready", reducedMotion = true),
                                HaloOrbInteraction(HaloOrbPrimary.TALK, false), emptySet(), {}, {}, {}, {}, size = size,
                                contextActions = actions, onContextAction = { holder.value?.invoke(it.id.removePrefix("page:")) })
                        })
                }
            }
        } }
        compose.onNodeWithText(allActions).performClick()
        compose.onNodeWithText(help).performScrollTo().performClick()
        compose.onNodeWithText(guide).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0, step) }
        compose.onNodeWithText(next).performScrollTo().performClick()
        if (cancelInspector) {
            compose.onNodeWithText("Inspector draft").assertIsDisplayed()
            if (background) {
                compose.runOnIdle { (lifecycle as androidx.lifecycle.LifecycleRegistry).currentState = androidx.lifecycle.Lifecycle.State.CREATED }
                compose.runOnIdle { blocked = false }
                compose.onNodeWithText(help).assertDoesNotExist()
                compose.runOnIdle { (lifecycle as androidx.lifecycle.LifecycleRegistry).currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
            } else compose.onNodeWithText("Cancel inspector").performClick()
            compose.runOnIdle { assertEquals(0, step) }
            compose.onNodeWithText(help).assertExists()
            compose.onNodeWithText(next).performScrollTo().assertIsDisplayed()
        } else {
            compose.runOnIdle { assertEquals(1, step) }
            compose.onNodeWithText(help).assertExists()
            compose.onNodeWithText(second, substring = true).performScrollTo().assertIsDisplayed()
            compose.onRoot().captureRoboImage()
        }
    }
}
