package com.halo.app.ui.screens.kids

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.halo.app.ui.screens.settings.BrandBook3PowerManager
import com.halo.app.ui.screens.study.HomeworkWordingReview
import com.halo.app.ui.screens.study.HomeworkPageNavigation
import com.halo.app.ui.theme.HostlessHaloTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "en-w360dp-h780dp",
    shadows = [BrandBook3PowerManager::class])
class HomeworkIntakeBrandBookTest {
    @get:Rule val compose = createComposeRule()

    @Test fun photoPreviewRequiresUseAndRetakeDoesNotAcceptIt() {
        var uses = 0; var retakes = 0; var cancels = 0
        val image = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        compose.setContent { HostlessHaloTheme { KidsBrandTheme {
            HomeworkPhotoReviewContent(image, false, false, { uses++ }, { retakes++ }, { cancels++ })
        } } }
        compose.onNodeWithText("Use this").assertHeightIsAtLeast(56.dp)
        assertEquals(0, uses)
        compose.onNodeWithText("Take again").performScrollTo().performClick()
        assertEquals(1, retakes); assertEquals(0, uses)
        compose.onNodeWithText("Use this").performScrollTo().performClick()
        assertEquals(1, uses)
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        assertEquals(1, cancels)
    }

    @Test fun unreadablePhotoCannotBeAccepted() {
        compose.setContent { HostlessHaloTheme { KidsBrandTheme {
            HomeworkPhotoReviewContent(null, true, false, { error("Unreadable photo accepted") }, {}, {})
        } } }
        compose.onNodeWithText("Use this").assertIsNotEnabled()
        compose.onNodeWithText("Take again").assertIsEnabled()
    }

    @Test fun wordingPreviewEditsBeforeExplicitConfirmationAtLargeText() {
        var text by mutableStateOf("A box is 12 cm long and 7 cm wide.")
        var accepted: String? = null
        compose.setContent { HostlessHaloTheme(fontScale = 2f) { KidsBrandTheme {
            Column(Modifier.width(360.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                HomeworkWordingReview(text, "Please check the second number.", true, true,
                    { text = it }, { accepted = text })
            }
        } } }
        assertNull(accepted)
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithText("Fix it").performScrollTo().assertHeightIsAtLeast(56.dp).performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("A box is 12 cm long and 9 cm wide.")
        assertNull(accepted)
        compose.onNodeWithText("It’s right").performScrollTo().performClick()
        assertEquals("A box is 12 cm long and 9 cm wide.", accepted)
    }

    @Test fun blankWordingCannotStartAnExercise() {
        compose.setContent { HostlessHaloTheme {
            HomeworkWordingReview("", null, false, true, {}, { error("Empty wording confirmed") })
        } }
        compose.onNodeWithText("Looks right").assertIsNotEnabled()
    }

    @Test fun pageNavigationWrapsAtLargeTextAndRespectsBothBounds() {
        var page by mutableIntStateOf(1)
        compose.setContent { HostlessHaloTheme(fontScale = 2f) { KidsBrandTheme {
            Column(Modifier.width(360.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                HomeworkPageNavigation(page, 2, true) { page = it }
            }
        } } }
        compose.onNodeWithText("Previous page").assertIsNotEnabled()
        compose.onNodeWithText("Next page").performScrollTo().performClick()
        assertEquals(2, page)
        compose.onNodeWithText("Page 2 of 2").assertExists()
        compose.onNodeWithText("Next page").assertIsNotEnabled()
        compose.onNodeWithText("Previous page").performScrollTo().performClick()
        assertEquals(1, page)
    }

    @Test fun previewDecodeRejectsInvalidLocalFileWithoutPublishingAnything() = runBlocking {
        val file = java.io.File.createTempFile("homework-invalid", ".jpg")
        try {
            file.writeText("Not an image")
            assertTrue(runCatching { loadHomeworkPhotoPreview(file.absolutePath) }.isFailure)
        } finally { file.delete() }
    }
}
