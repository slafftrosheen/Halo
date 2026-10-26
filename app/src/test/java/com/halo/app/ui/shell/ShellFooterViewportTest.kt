package com.halo.app.ui.shell

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.halo.app.ui.theme.HostlessHaloTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w800dp-h360dp")
class ShellFooterViewportTest {
    @get:Rule val compose = createComposeRule()
    @Test fun exceptionallyShortImeViewportRetainsEveryFullSizeActionByScrolling() {
        val clicked = mutableListOf<String>()
        compose.setContent { HostlessHaloTheme(fontScale = 2f) {
            Box(Modifier.fillMaxWidth().height(77.dp)) {
                ShellFooterViewport {
                    TextField("Draft preserved", {}, Modifier.fillMaxWidth().heightIn(min = 120.dp), label = { Text("Message") })
                    listOf("Send", "Home", "Workspace", "Talk", "Devices", "Settings", "Notifications", "Power").forEach { label ->
                        Button({ clicked += label }, Modifier.heightIn(min = 48.dp)) { Text(label) }
                    }
                }
            }
        } }
        listOf("Send", "Home", "Workspace", "Talk", "Devices", "Settings", "Notifications", "Power").forEach { label ->
            compose.onNodeWithText(label).performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        }
        assertEquals(listOf("Send", "Home", "Workspace", "Talk", "Devices", "Settings", "Notifications", "Power"), clicked)
        compose.onNodeWithText("Draft preserved").assertExists()
    }
    @Test fun crossingImeHeightThresholdPreservesFocusedEditorCaretAndComposition() {
        val height = androidx.compose.runtime.mutableStateOf(280.dp)
        var editor: com.halo.app.ui.screens.home.HomeComposerEditBuffer? = null
        compose.setContent { HostlessHaloTheme {
            Box(Modifier.fillMaxWidth().height(height.value)) {
                ShellFooterViewport {
                    val current = androidx.compose.runtime.remember {
                        com.halo.app.ui.screens.home.HomeComposerEditBuffer("Draft preserved", 3)
                    }
                    editor = current
                    androidx.compose.foundation.text.BasicTextField(current.value, current::edit,
                        Modifier.fillMaxWidth().heightIn(min = 48.dp))
                    Button({}) { Text("Home") }
                }
            }
        } }
        val field = compose.onNode(hasSetTextAction())
        field.performClick().assertIsFocused()
        val original = requireNotNull(editor)
        compose.runOnIdle {
            original.edit(androidx.compose.ui.text.input.TextFieldValue("Draft preserved",
                androidx.compose.ui.text.TextRange(4), androidx.compose.ui.text.TextRange(2, 6)))
        }
        listOf(120.dp, 280.dp, 180.dp, 300.dp).forEach { available ->
            compose.runOnIdle { height.value = available }
            compose.waitForIdle()
            field.assertIsFocused()
            compose.runOnIdle {
                org.junit.Assert.assertSame(original, editor)
                assertEquals(androidx.compose.ui.text.TextRange(4), original.value.selection)
                assertEquals(androidx.compose.ui.text.TextRange(2, 6), original.value.composition)
                assertEquals("Draft preserved", original.value.text)
            }
        }
    }

}
