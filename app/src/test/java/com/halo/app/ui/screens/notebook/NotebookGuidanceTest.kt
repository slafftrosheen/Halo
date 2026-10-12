package com.halo.app.ui.screens.notebook

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.halo.app.ui.theme.HaloTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class NotebookGuidanceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyCollectionRecoversWithoutCreatingOrDeletingAnything() {
        var creates = 0
        var recoveries = 0
        compose.setContent {
            HaloTheme(darkTheme = false, fontScale = 2f) {
                Box(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                    NotebookEmptyGuidance("", NotebookFilter.ALL, true, true,
                        { creates++ }, { creates++ }, { recoveries++ })
                }
            }
        }
        compose.onNodeWithText("This collection is empty").assertIsDisplayed()
        compose.onNodeWithText("Show whole library").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, recoveries)
        assertEquals(0, creates)
    }

    @Test fun canvasGuidanceInvokesCanvasAndHonorsBusyState() {
        var notes = 0
        var canvases = 0
        val enabled = mutableStateOf(true)
        compose.setContent {
            HaloTheme(darkTheme = true) {
                NotebookEmptyGuidance("", NotebookFilter.CANVASES, false, enabled.value,
                    { notes++ }, { canvases++; enabled.value = false }, {})
            }
        }
        compose.onNodeWithText("New canvas").performClick()
        compose.onNodeWithText("New canvas").assertIsNotEnabled().performClick()
        assertEquals(1, canvases)
        assertEquals(0, notes)
    }

    @Test fun searchAndTrashDoNotSuggestCreatingReplacementData() {
        assertEquals(NotebookEmptyAction.SHOW_LIBRARY, notebookEmptyAction("missing", NotebookFilter.CANVASES, false))
        assertEquals(NotebookEmptyAction.SHOW_LIBRARY, notebookEmptyAction("", NotebookFilter.TRASH, false))
        assertEquals(NotebookEmptyAction.CREATE_NOTE, notebookEmptyAction("", NotebookFilter.NOTES, false))
        assertEquals(NotebookEmptyAction.CREATE_CANVAS, notebookEmptyAction("", NotebookFilter.CANVASES, false))
    }
}
