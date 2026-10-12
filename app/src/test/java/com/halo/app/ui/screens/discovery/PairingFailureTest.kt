package com.halo.app.ui.screens.discovery

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.halo.app.ui.theme.HaloTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PairingFailureTest {
    @get:Rule val compose = createComposeRule()
    @Test fun scanFailureOffersExplicitRetryAndKeepsExceptionDetailsOutOfTheFlow() {
        var scans = 0
        compose.setContent { HaloTheme(darkTheme = true) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
            PairingGuidance(PairingUiState(statusMessage = "Scan failed: internal detail"), {}, {}, { scans++ }, {}, {})
            }
        } }
        compose.onNodeWithText("Search did not finish").assertExists()
        compose.onNodeWithText("Scan failed: internal detail").assertDoesNotExist()
        assertEquals(0, scans)
        compose.onNodeWithText("Search nearby").performScrollTo().performClick()
        assertEquals(1, scans)
    }
}
