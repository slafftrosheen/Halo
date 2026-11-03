package com.halo.app.ui.presence

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.ui.screens.kids.FamilyModeMenuRow
import com.halo.app.ui.theme.HostlessHaloTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class FamilyModeMenuVisibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun globalFamilySwitchSurvivesPageChangesAndBothFamilyPresentations() {
        val route = mutableStateOf("home")
        val kids = mutableStateOf(false)
        var menuLabel = ""
        compose.setContent { HostlessHaloTheme {
            menuLabel = stringResource(R.string.zone5_menu)
            HaloZone5OrbLauncher(HaloSignalPresentation(reducedMotion = true), HaloOrbInteraction(HaloOrbPrimary.TALK, false),
                {}, {}, emptyList(), {}, 48.dp, Modifier,
                HaloZone5Navigation(route.value, emptyList(), {}, false, {}, {}, entryId = "${route.value}:${kids.value}",
                    compact = true, kids = kids.value, familyControl = { FamilyModeMenuRow(kids.value, true) {} }))
        } }
        for ((child, page) in listOf(false to "home", false to "studio", false to "screen/organizer",
            false to "screen/brief", false to "screen/sources", true to "home", true to "study", true to "screen/clocks")) {
            compose.runOnIdle { route.value = page; kids.value = child }
            compose.onNodeWithTag("family-mode-switch").assertDoesNotExist()
            compose.onNodeWithText(menuLabel).performClick()
            compose.onNodeWithTag("family-mode-switch").assertIsDisplayed()
        }
    }
}
