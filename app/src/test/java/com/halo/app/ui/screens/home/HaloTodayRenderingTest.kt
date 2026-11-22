package com.halo.app.ui.screens.home

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.R
import com.halo.app.ui.shell.HaloHomeTab
import com.halo.app.ui.shell.HaloHomeTabs
import com.halo.app.ui.theme.HostlessHaloTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "en-rUS-w360dp-h800dp")
class HaloTodayRenderingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun oldNextEventChoiceMigratesOnceToItsOccurrence() {
        val storage = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("today-next-event-migration", Context.MODE_PRIVATE)
        storage.edit().putStringSet("pinned", setOf("day", "reads")).commit()
        HaloBlockChoices(storage).retarget("day", "event:first")
        val restored = HaloBlockChoices(storage)
        restored.retarget("day", "event:second")
        assertEquals(setOf("event:first", "reads"), restored.pinned)
    }

    @Test fun eventMapAndChatOpenFromTheirOwnTargets() {
        val storage = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("today-rendering-test", Context.MODE_PRIVATE)
        val choices = HaloBlockChoices(storage)
        val routeHistory = mutableListOf<String>()
        val cards = listOf(
            HaloHomeBlock("day", R.string.today_block_day, "Coffee with Anna · 10:30", "Planner",
                R.string.today_block_why_day, "event",
                departure = HaloBlockDeparture("Leave by 10:15", "map")),
            HaloHomeBlock("study", R.string.today_block_study, "8 cards due", "Library",
                R.string.today_block_why_study, "study"),
        )
        compose.setContent {
            HostlessHaloTheme(darkTheme = true) {
                Surface(Modifier.fillMaxSize()) {
                    Column {
                        HaloHomeTabs(HaloHomeTab.TODAY, routeHistory::add)
                        HaloTodayContent(9, cards, choices, routeHistory::add)
                    }
                }
            }
        }
        compose.onNodeWithText("Today").assertIsSelected()
        compose.onNodeWithText("Good morning").assertIsDisplayed()
        compose.onRoot().captureRoboImage()
        compose.onNodeWithText("Coffee with Anna · 10:30").performClick()
        compose.onNodeWithText("Leave by 10:15").assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText("Chat").assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle { assertEquals(listOf("event", "map", "home"), routeHistory) }
        compose.onAllNodesWithContentDescription("Block options").onFirst().performClick()
        compose.onNodeWithText("Why am I seeing this?").performClick()
        compose.onNodeWithText("You set a departure reminder on this event in Planner.").assertIsDisplayed()
        compose.onNodeWithText("OK", ignoreCase = true).performClick()
        compose.onAllNodesWithContentDescription("Block options").onFirst().performClick()
        compose.onNodeWithText("Less like this").performClick()
        compose.onNodeWithText("Coffee with Anna · 10:30").assertDoesNotExist()
        compose.onNodeWithText("Show hidden blocks again").performClick()
        compose.onNodeWithText("Coffee with Anna · 10:30").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "ru-rRU-w320dp-h800dp")
    fun russianChatActionsWrapAndRemainAccessibleAt200Percent() {
        var new = 0
        var clear = 0
        var history = 0
        compose.setContent {
            HostlessHaloTheme(darkTheme = true, fontScale = 2f) {
                HomeChatActions(true, true, { new++ }, { clear++ }, { history++ })
            }
        }
        compose.onNodeWithText("Новый чат").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText("Очистить чат").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithContentDescription("История разговора").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf(1, 1, 1), listOf(new, clear, history)) }
        compose.onRoot().captureRoboImage()
    }
}
