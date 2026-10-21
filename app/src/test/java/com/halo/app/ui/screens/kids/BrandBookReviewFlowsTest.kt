package com.halo.app.ui.screens.kids

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.halo.app.ui.screens.settings.BrandBook3PowerManager
import com.halo.app.ui.screens.study.StudyReviewQuestionFace
import com.halo.app.ui.theme.HostlessHaloTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "en-w360dp-h780dp",
    shadows = [BrandBook3PowerManager::class])
class BrandBookReviewFlowsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun previewDoesNotSaveAndChangeEditsActualDraftAtLargeText() {
        var keeps = 0
        var draft by mutableStateOf("A fox finds a star.")
        compose.setContent { HostlessHaloTheme(fontScale = 2f) { KidsBrandTheme {
            Column(Modifier.width(360.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                KidsProjectReview(KidsProjectState(loading = false, idea = "Fox and the Star", draft = draft,
                    editing = true), false, { draft = it }, { keeps++ }, {}, {})
            }
        } } }
        compose.onNodeWithText("Not saved yet").assertExists()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithText("Change").performScrollTo().assertHeightIsAtLeast(56.dp).performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("The fox helps the star.")
        compose.onNodeWithText("Preview").performScrollTo().performClick()
        assertEquals(0, keeps)
        assertEquals("The fox helps the star.", draft)
        compose.onNodeWithText("Keep", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals(1, keeps)
    }

    @Test fun discardNeedsExplicitConfirmation() {
        var discards = 0
        compose.setContent { HostlessHaloTheme { KidsBrandTheme {
            KidsProjectReview(KidsProjectState(loading = false, draft = "Unsaved text", editing = true),
                false, {}, {}, { discards++ }, {})
        } } }
        compose.onNodeWithText("Discard").performClick()
        assertEquals(0, discards)
        compose.onNodeWithText("Keep editing").performClick()
        assertEquals(0, discards)
        compose.onNodeWithText("Discard").performClick()
        compose.onNodeWithText("Discard unkept changes?").assertExists()
        compose.onAllNodesWithText("Discard").onLast().performClick()
        assertEquals(1, discards)
    }

    @Test fun savedProjectHasNoDiscardOrDuplicateKeepAction() {
        compose.setContent { HostlessHaloTheme { KidsBrandTheme {
            KidsProjectReview(KidsProjectState(loading = false, draft = "Kept text", noteId = "saved"),
                true, {}, {}, {}, {})
        } } }
        compose.onNodeWithText("Saved project").assertExists()
        compose.onNodeWithText("Discard").assertDoesNotExist()
        compose.onNodeWithText("Keep").assertDoesNotExist()
        compose.onNodeWithText("Change").assertExists()
    }

    @Test fun answerFaceUsesSavedAnswerAndStoryPageKeepsLongTextAtTwoHundredPercent() {
        compose.setContent { HostlessHaloTheme(fontScale = 2f) { KidsBrandTheme {
            Column(Modifier.width(360.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                StudyReviewQuestionFace("comí", "I ate")
                KidsStoryPage(AnnotatedString("One night a little star fell into the snow. Pip ran to help it."),
                    large = true, spacing = true, onWordClick = {})
            }
        } } }
        compose.onNodeWithText("I ate").assertExists()
        compose.onNodeWithText("One night a little star fell into the snow. Pip ran to help it.")
            .performScrollTo().assertIsDisplayed()
    }
}
