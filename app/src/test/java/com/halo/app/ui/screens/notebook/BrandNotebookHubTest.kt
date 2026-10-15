package com.halo.app.ui.screens.notebook

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.halo.app.data.notebook.*
import com.halo.app.ui.theme.HaloTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class BrandNotebookHubTest {
    @get:Rule val compose = createComposeRule()


    @Test fun round2CreationCardsPreserveAllFourDestinations() {
        val calls = mutableListOf<String>()
        compose.setContent {
            HaloTheme(darkTheme = false) {
                androidx.compose.foundation.layout.Column {
                    NotebookCreateAction("Note", com.halo.app.R.drawable.halo_ic_new_note, true,
                        { calls += "note" }, emphasized = true)
                    NotebookCreateAction("Canvas", com.halo.app.R.drawable.halo_ic_canvas, true,
                        { calls += "canvas" })
                    NotebookCreateAction("Import", com.halo.app.R.drawable.halo_ic_import, true,
                        { calls += "import" })
                    NotebookCreateAction("Inbox", com.halo.app.R.drawable.halo_ic_inbox, true,
                        { calls += "inbox" })
                }
            }
        }
        listOf("Note", "Canvas", "Import", "Inbox").forEach { name ->
            compose.onNodeWithText(name).assertExists().performClick()
        }
        assertEquals(listOf("note", "canvas", "import", "inbox"), calls)
    }

    @Test fun studyRemainsAnExplicitNotebookToolAlongsideMemory() {
        val actions = mutableListOf<String>()
        compose.setContent {
            HaloTheme(darkTheme = false) {
                NotebookLearningTools(
                    onStudy = { actions += "study" },
                    onMemory = { actions += "memory" },
                )
            }
        }
        compose.onNodeWithText("Study").assertExists()
        compose.onNodeWithText("Open Study").performClick()
        compose.onNodeWithText("Memory").performClick()
        assertEquals(listOf("study", "memory"), actions)
    }

    @Test fun previewIsOneRowWhileLibraryRetainsEveryItem() {
        val files = listOf("Note", "Canvas", "File")
        assertEquals(listOf("Note"), notebookItemsForSurface(files, library = false))
        assertEquals(files, notebookItemsForSurface(files, library = true))
        assertEquals(emptyList<String>(), notebookItemsForSurface(emptyList<String>(), false))
    }

    @Test fun studySourceActionsIncludeReadableFilesButNeverTrashedItems() {
        assertEquals("Study this note", notebookStudySourceAction(
            WorkspaceObjectType.NOTE, WorkspaceObjectLifecycle.ACTIVE))
        assertEquals("Study this material", notebookStudySourceAction(
            WorkspaceObjectType.FILE, WorkspaceObjectLifecycle.ACTIVE))
        assertEquals("Study this material", notebookStudySourceAction(
            WorkspaceObjectType.MEDIA, WorkspaceObjectLifecycle.ACTIVE))
        assertNull(notebookStudySourceAction(
            WorkspaceObjectType.NOTE, WorkspaceObjectLifecycle.TRASHED))
        assertNull(notebookStudySourceAction(
            WorkspaceObjectType.CANVAS, WorkspaceObjectLifecycle.ACTIVE))
    }

    @Test fun longPressEntersSelectionInsteadOfOpeningNoteAndFavoriteIsDirect() {
        var opened = 0
        var selected = 0
        var favorited = 0
        val note = NotebookWorkspaceItem(
            WorkspaceObject(
                WorkspaceObjectId("brand-preview-note"),
                WorkspaceObjectType.NOTE,
                "Photosynthesis notes",
                0L, 0L,
                WorkspaceObjectSource.USER_CREATED,
                WorkspaceObjectLifecycle.ACTIVE,
                true, "preview"
            ), "Plants turn light into food."
        )
        compose.setContent {
            HaloTheme(darkTheme = true) {
                WorkspaceObjectRow(
                    item = note, selected = true, favorite = false,
                    collections = emptyList(), inSelectedCollection = false,
                    onOpen = { opened++ },
                    onStudy = {},
                    onToggleFavorite = { favorited++ },
                    onAddToCollection = {},
                    onRemoveFromSelectedCollection = {},
                    onToggleContext = {},
                    onMultiSelect = { selected++ }
                )
            }
        }
        compose.onNodeWithText("Photosynthesis notes").performTouchInput { longClick() }
        assertEquals(1, selected)
        assertEquals(0, opened)
        compose.onNodeWithContentDescription("Add favorite").performClick()
        assertEquals(1, favorited)
        compose.onNodeWithText("In chat").assertExists()
        compose.onNodeWithText("Plants turn light into food.").assertExists()
    }
}
