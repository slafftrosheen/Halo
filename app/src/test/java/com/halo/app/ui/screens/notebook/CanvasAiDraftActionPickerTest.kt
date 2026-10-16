package com.halo.app.ui.screens.notebook

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
@Config(sdk = [33], application = Application::class)
class CanvasAiDraftActionPickerTest {
    @get:Rule val compose = createComposeRule()
    @Test fun choicesOnlyChangeTheChosenDraftTask() {
        var current = "Review"
        var changes = 0
        compose.setContent {
            HaloTheme {
                CanvasAiActionPicker(current) { current = it; changes++ }
            }
        }
        assertEquals(canvasAiActions, listOf("Review", "Expand", "Rewrite"))
        assertEquals(0, changes)
        compose.onNodeWithText("Rewrite").performClick()
        assertEquals("Rewrite", current)
        assertEquals(1, changes)
    }
    @Test fun aiDraftIsStillAnUncommittedProposal() {
        var kept = false
        val draft = CanvasAiDraft("Review", null, null, "Text to review")
        compose.setContent {
            HaloTheme {
                CanvasInlineDraftCard(draft, busy = false, onReview = {},
                    onKeep = { kept = true }, onDiscard = {})
            }
        }
        compose.onNodeWithText("Proposed by halo · not saved").assertExists()
        assertEquals(false, kept)
    }
}
