package com.halo.app.ui.presence

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val LocalUtilityOwner = staticCompositionLocalOf { "shell" }

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ShellUtilityBandTest {
    @get:Rule val compose = createComposeRule()

    @Test fun relocatedContentKeepsPageLocalsAndDropsDisposedProjection() {
        val shown = mutableStateOf(true)
        compose.setContent {
            val holder = remember { mutableStateOf<ShellUtilityProjection?>(null) }
            CompositionLocalProvider(LocalShellUtilityBand provides holder) {
                if (shown.value) CompositionLocalProvider(LocalUtilityOwner provides "document-A") {
                    BindShellUtilityBand(ShellPageOwner("note", "A", navigationEntryId = "entry-A")) {
                        Text(LocalUtilityOwner.current)
                    }
                }
                holder.value?.let { RenderShellUtilityBand(it) }
            }
        }
        compose.onNodeWithText("document-A").assertExists()
        compose.onNodeWithText("shell").assertDoesNotExist()
        compose.runOnIdle { shown.value = false }
        compose.onNodeWithText("document-A").assertDoesNotExist()
    }
}
