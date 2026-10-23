package com.halo.app.ui.screens.notebook

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.halo.app.ui.theme.HaloTheme
import com.halo.sdk.runtime.ink.InkCodec
import com.halo.sdk.runtime.ink.InkDocument
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class CanvasErasedSketchUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun recoveredEmptyExistingSketchCanBeSavedExactlyOnce() {
        val erased = InkCodec.encode(InkDocument())
        val saves = mutableListOf<String>()
        show(initial = "sketch:v1|100,100;200,200", recovery = erased, existing = true, onSave = saves::add)
        compose.onNodeWithText("Save").assertIsEnabled().performClick()
        assertEquals(listOf(erased), saves)
    }

    @Test fun emptyNewSketchRemainsUnsavable() {
        show(initial = "", existing = false)
        compose.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test fun corruptExistingSketchRemainsUnsavableAndCancelDoesNotPublish() {
        val saves = mutableListOf<String>()
        var dismissed = 0
        show(initial = "sketch:v3|broken", existing = true, onSave = saves::add, onDismiss = { dismissed++ })
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(emptyList<String>(), saves)
        assertEquals(1, dismissed)
    }

    private fun show(initial: String, recovery: String? = null, existing: Boolean,
        onSave: (String) -> Unit = {}, onDismiss: () -> Unit = {}) {
        compose.setContent {
            HaloTheme {
                CanvasSketchPadDialog(initial = initial, recovery = recovery, editingExisting = existing,
                    onSave = onSave, onDismiss = onDismiss)
            }
        }
    }
}
