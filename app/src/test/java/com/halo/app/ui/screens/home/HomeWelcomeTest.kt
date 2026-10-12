package com.halo.app.ui.screens.home

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.halo.app.ui.theme.HaloTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w320dp-h800dp")
class HomeWelcomeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun suggestionOnlyPreparesAnEditableDraft() {
        val drafts = mutableListOf<String>()
        compose.setContent { HaloTheme(darkTheme = false) { HomeWelcome { drafts += it } } }
        compose.onNodeWithText("Make a plan").performClick()
        assertEquals(1, drafts.size)
        assertEquals("Help me plan my day. Ask me what I need to get done.", drafts.single())
    }
}
