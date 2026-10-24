package com.halo.app.ui.screens.home

import android.app.Application
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h800dp")
class HomeFeedFollowingTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var scroll: ScrollState
    private lateinit var following: MutableState<Boolean>
    private var rows by mutableIntStateOf(12)
    private var recoveryRequest by mutableStateOf<String?>(null)
    private var retries = 0

    private fun show() {
        compose.setContent {
            scroll = rememberScrollState()
            following = rememberHomeFeedFollowing(scroll, "chat", recoveryRequest)
            Column(Modifier.width(320.dp).height(120.dp).testTag("feed").verticalScroll(scroll)) {
                Column(Modifier.height(60.dp)) {
                    Text("Orb header")
                    if (recoveryRequest != null) Text("Retry", Modifier.clickable { retries++ })
                }
                repeat(rows) { Text("Turn $it", Modifier.height(60.dp)) }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(scroll.maxValue, scroll.value) }
    }

    @Test fun failureRevealsRecoveryAndRetryRearmsFollowing() {
        show()
        compose.runOnIdle { recoveryRequest = "failed-request" }
        compose.waitForIdle()
        compose.onNodeWithText("Retry").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, retries); assertFalse(following.value); rows += 2 }
        compose.waitForIdle()
        compose.onNodeWithText("Retry").assertIsDisplayed()
        compose.runOnIdle { recoveryRequest = null }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(following.value); assertEquals(scroll.maxValue, scroll.value) }
    }

    @Test fun successiveSmallGesturesKeepTheirProgressTowardTheOrb() {
        show()
        var previous = scroll.value
        repeat(3) {
            compose.onNodeWithTag("feed").performTouchInput { swipeDown(durationMillis = 600) }
            compose.waitForIdle()
            compose.runOnIdle {
                assertFalse(following.value)
                assertTrue("Upward progress must survive gesture completion", scroll.value < previous)
                previous = scroll.value
            }
        }
        repeat(12) { compose.onNodeWithTag("feed").performTouchInput { swipeDown(durationMillis = 600) } }
        compose.onNodeWithText("Orb header").assertIsDisplayed()
    }

    @Test fun replyGrowthFollowsUntilAnUpwardGestureReleasesIt() {
        show()
        compose.runOnIdle { rows += 2 }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(scroll.maxValue, scroll.value) }
        compose.onNodeWithTag("feed").performTouchInput { swipeDown(durationMillis = 600) }
        compose.waitForIdle()
        val releasedPosition = scroll.value
        compose.runOnIdle { rows += 2 }
        compose.waitForIdle()
        compose.runOnIdle {
            assertFalse(following.value)
            assertEquals(releasedPosition, scroll.value)
        }
    }

    @Test fun returningToTheBottomRearmsFollowingForTheNextReply() {
        show()
        compose.onNodeWithTag("feed").performTouchInput { swipeDown(durationMillis = 600) }
        compose.runOnIdle { assertFalse(following.value) }
        compose.runOnIdle { runBlocking { scroll.scrollTo(scroll.maxValue) } }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(following.value); rows += 2 }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(scroll.maxValue, scroll.value) }
    }
}
