package com.halo.app.ui.screens.activity

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.halo.app.ui.theme.HaloTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ActivityRound2PillTest {
    @get:Rule val compose = createComposeRule()

    @Test fun segmentedTabsSelectTheExistingActivityFilter() {
        var selected = ActivityFilter.REVIEW
        compose.setContent {
            HaloTheme(darkTheme = true) {
                ActivitySegmentedPill(
                    selected = selected,
                    counts = mapOf(ActivityFilter.REVIEW to 2),
                    onSelect = { selected = it },
                )
            }
        }
        compose.onNodeWithText("Needs you").assertExists()
        compose.onNodeWithText("Running").performClick()
        assertEquals(ActivityFilter.RUNNING, selected)
        compose.onNodeWithText("Done").performClick()
        assertEquals(ActivityFilter.HISTORY, selected)
    }
}
