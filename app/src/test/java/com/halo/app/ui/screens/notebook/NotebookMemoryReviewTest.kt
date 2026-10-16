package com.halo.app.ui.screens.notebook

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.halo.app.data.memory.PersonalSuggestionEntity
import com.halo.app.ui.theme.HaloTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class NotebookMemoryReviewTest {
    @get:Rule val compose = createComposeRule()
    private fun row(kind: String, state: String, json: String) =
        PersonalSuggestionEntity("a", "memory:a", kind, json, state, 0L, 0L, 1)

    @Test fun onlyDecodablePendingMemoryProposalsAreCounted() {
        val valid = """{"id":"memory-1","content":"I like short tutorials","kind":"PREFERENCE","scope":"GLOBAL","scopeId":null,"confidence":0.6,"origin":"USER_EXPLICIT","evidence":[],"conflicts":[],"created":1,"sensitivity":"NORMAL","subject":"tutorials"}"""
        // The kind alone is insufficient: invalid/corrupt payloads cannot generate a review count.
        assertEquals(0, notebookReviewableMemoryCount(listOf(
            row("MEMORY", "PENDING", "not-json"),
            row("PROCEDURE", "PENDING", valid),
            row("MEMORY", "ACCEPTED", valid)
        )))
    }

    @Test fun tappingNonEmptyMemoryReviewOpensTheExistingReviewOwner() {
        var opens = 0
        compose.setContent { HaloTheme { NotebookPendingMemoryReview(2) { opens++ } } }
        compose.onNodeWithText("Memory suggestions").assertExists().performClick()
        assertEquals(1, opens)
    }

    @Test fun noReviewBannerForEmptyQueue() {
        compose.setContent { HaloTheme { NotebookPendingMemoryReview(0) {} } }
        compose.onNodeWithText("Memory suggestions").assertDoesNotExist()
    }
}
