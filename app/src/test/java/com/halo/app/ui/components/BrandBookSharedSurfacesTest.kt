package com.halo.app.ui.components

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.halo.app.ui.glass.components.HaloGlassChip
import com.halo.app.ui.glass.components.HaloGlassDialog
import com.halo.app.ui.theme.HostlessHaloTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "en-w360dp-h780dp")
class BrandBookSharedSurfacesTest {
    @get:Rule val compose = createComposeRule()

    @Test fun compactChipDoesNotInheritCardPadding() {
        compose.setContent {
            HostlessHaloTheme {
                HaloGlassChip(text = "Selected", modifier = Modifier.testTag("chip"))
            }
        }
        compose.onNodeWithTag("chip").assertHeightIsAtLeast(34.dp)
        val bounds = compose.onNodeWithTag("chip").fetchSemanticsNode().boundsInRoot
        assertTrue("Chip bounds: $bounds; density ${compose.density}", bounds.height <= with(compose.density) { 38.dp.toPx() })
        assertTrue(bounds.width < with(compose.density) { 100.dp.toPx() })
    }

    @Test fun largeSectionHeadingAndActionDoNotOverlap() {
        compose.setContent {
            HostlessHaloTheme(fontScale = 2f) {
                Box(Modifier.width(320.dp)) {
                    HaloSection("Learning materials", action = {
                        HaloButton("Add material", {}, Modifier.testTag("action"))
                    }) { Text("Saved locally") }
                }
            }
        }
        val title = compose.onNodeWithText("Learning materials").fetchSemanticsNode().boundsInRoot
        val action = compose.onNodeWithTag("action").fetchSemanticsNode().boundsInRoot
        assertTrue(title.right <= action.left || title.bottom <= action.top)
        compose.onNodeWithText("Add material").assertIsDisplayed()
    }

    @Test fun glassDialogKeepsLongContentReachable() = dialogScrolls(glass = true)
    @Test fun standardDialogKeepsLongContentReachable() = dialogScrolls(glass = false)

    private fun dialogScrolls(glass: Boolean) {
        compose.setContent {
            HostlessHaloTheme(fontScale = 2f) {
                val body: @androidx.compose.runtime.Composable ColumnScope.() -> Unit = {
                    repeat(16) { Text("Review the proposed action before continuing.") }
                    HaloButton("Confirm action", {})
                }
                if (glass) HaloGlassDialog(title = "Review action", onDismissRequest = {}) {
                    Column(content = body)
                } else HaloDialog("Review action", {}, body)
            }
        }
        compose.onNodeWithText("Review action").assertIsDisplayed()
        compose.onNodeWithText("Confirm action").performScrollTo().assertIsDisplayed()
    }
}
