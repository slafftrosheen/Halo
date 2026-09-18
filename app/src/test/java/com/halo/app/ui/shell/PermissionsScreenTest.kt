package com.halo.app.ui.shell

import android.Manifest
import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.halo.app.ui.theme.HaloTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class)
class PermissionsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun grantMicrophoneButtonInvokesHostPermissionAction() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(application).denyPermissions(Manifest.permission.RECORD_AUDIO)
        var requests = 0

        composeRule.setContent {
            HaloTheme(darkTheme = false) {
                PermissionsScreen(
                    onRequestPermissions = { requests++ }
                )
            }
        }

        composeRule
            .onNodeWithText("Grant microphone access")
            .performClick()

        assertEquals(1, requests)
    }
}
