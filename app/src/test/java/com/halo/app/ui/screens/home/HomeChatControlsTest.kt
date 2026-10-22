package com.halo.app.ui.screens.home

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.halo.app.ui.theme.HostlessHaloTheme as HaloTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class HomeChatControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun headerNavigationRemainsReachableAtNarrowWidthAndLargeText() {
        var history = 0
        var newChat = 0
        var updates = 0
        var modes = 0
        compose.setContent {
            HaloTheme(fontScale = 2f) {
                Box(Modifier.width(320.dp)) {
                    HomeChatHeader("Chat", true, { modes++ }, { history++ }, { newChat++ },
                        { updates++ }, {}, {})
                }
            }
        }
        compose.onNodeWithContentDescription("Conversation history").assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp).performClick()
        compose.onNodeWithContentDescription("New conversation").assertIsDisplayed().performClick()
        compose.onNodeWithText("Chat").assertIsDisplayed().performClick()
        compose.onNodeWithText("Updates").assertDoesNotExist()
        compose.onNodeWithContentDescription("Chat options").performClick()
        compose.onNodeWithText("Updates").performClick()
        assertEquals(1, history)
        assertEquals(1, newChat)
        assertEquals(1, updates)
        assertEquals(1, modes)
    }

    @Test fun messageActionsStayQuietAndFollowTheExactMessageIdentity() {
        val identity = mutableStateOf("first")
        var copied = 0
        var saved = 0
        var read = 0
        compose.setContent {
            HaloTheme {
                HomeMessageActions({ copied++ }, {}, { saved++ }, { read++ }, {}, identity.value)
            }
        }
        compose.onNodeWithText("Copy").assertDoesNotExist()
        compose.onNodeWithContentDescription("Message actions").performClick()
        compose.onNodeWithText("Copy").performClick()
        assertEquals(1, copied)
        assertEquals(0, saved)
        assertEquals(0, read)
        compose.onNodeWithContentDescription("Message actions").performClick()
        compose.runOnIdle { identity.value = "second" }
        compose.onNodeWithText("Read aloud").assertDoesNotExist()
        compose.onNodeWithContentDescription("Message actions").performClick()
        compose.onNodeWithText("Read aloud").performClick()
        assertEquals(1, read)
        assertEquals(0, saved)
    }

    @Test fun historyUsesStoredTitlesAndSelectsTheExactConversation() {
        var selected: String? = null
        compose.setContent {
            HaloTheme {
                HomeConversationHistory(listOf(
                    com.halo.app.data.PersistedConversation("one", 1, true, "First chat"),
                    com.halo.app.data.PersistedConversation("two", 2, false, "Second chat")),
                    "one", { selected = it }, {}, {})
            }
        }
        compose.onNodeWithContentDescription("Current conversation").assertExists()
        compose.onNodeWithText("Second chat").performClick()
        assertEquals("two", selected)
    }
}
