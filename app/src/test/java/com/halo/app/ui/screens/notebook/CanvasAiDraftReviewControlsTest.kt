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
class CanvasAiDraftReviewControlsTest {
    @get:Rule val compose = createComposeRule()

    private val draft = CanvasAiDraft(
        action = "Review", nodeId = null, original = null,
        text = "A proposed summary that has not been saved.",
    )

    @Test fun inlineDraftDoesNotKeepBeforeExplicitAction() {
        var keeps = 0
        var discards = 0
        var reviews = 0
        compose.setContent {
            HaloTheme {
                CanvasInlineDraftCard(
                    draft = draft, busy = false,
                    onReview = { reviews++ },
                    onKeep = { keeps++ },
                    onDiscard = { discards++ },
                )
            }
        }
        compose.onNodeWithText(draft.text).assertExists()
        assertEquals(0, keeps)
        compose.onNodeWithText("Review AI draft").performClick()
        assertEquals(1, reviews)
        assertEquals(0, keeps)
        compose.onNodeWithText("Discard").performClick()
        assertEquals(1, discards)
        assertEquals(0, keeps)
    }

    @Test fun keepAndDiscardDisabledWhileRegenerationInProgress() {
        compose.setContent {
            HaloTheme {
                CanvasInlineDraftCard(
                    draft = draft, busy = true,
                    onReview = {},
                    onKeep = { error("Busy draft must never be kept") },
                    onDiscard = { error("Busy draft must never be discarded") },
                )
            }
        }
        compose.onNodeWithText("Keep").assertIsNotEnabled()
        compose.onNodeWithText("Discard").assertIsNotEnabled()
    }

    @Test fun emptyDraftCannotBeCommittedFromReviewSheet() {
        compose.setContent {
            HaloTheme {
                CanvasAiDraftReview(
                    draft = draft.copy(text = ""), busy = false,
                    onEdit = {}, onKeep = { error("Empty draft was kept") },
                    onDiscard = {}, onRegenerate = {},
                )
            }
        }
        compose.onNodeWithText("Keep").assertIsNotEnabled()
    }
}
