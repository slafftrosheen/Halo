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

    @Test fun previewIsOneRowWhileLibraryRetainsEveryItem() {
        val files = listOf("Note", "Canvas", "File")
        assertEquals(listOf("Note"), notebookItemsForSurface(files, library = false))
        assertEquals(files, notebookItemsForSurface(files, library = true))
        assertEquals(emptyList<String>(), notebookItemsForSurface(emptyList<String>(), false))
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
