package com.halo.app.ui.screens.settings

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
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
class BackupExportOptionsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun protectedExportCannotBecomeUnprotectedAfterPasswordIsLost() {
        val restoration = StateRestorationTester(compose)
        var exports = 0
        restoration.setContent { HaloTheme(darkTheme = true, fontScale = 2f) {
            var files by rememberSaveable { mutableStateOf(true) }
            var protect by rememberSaveable { mutableStateOf(true) }
            var password by remember { mutableStateOf("") }
            Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                BackupExportOptions(files, protect, password, false, false, { files = it }, { protect = it }, { password = it }, { exports++ })
            }
        } }
        compose.onNodeWithText("Export Brain Dump").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Password").performScrollTo().performTextInput("temporary-password")
        compose.onNodeWithText("Export Brain Dump").performScrollTo().assertIsEnabled()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Password").performScrollTo().assertTextContains("Password")
        compose.onNodeWithText("Export Brain Dump").performScrollTo().assertIsNotEnabled()
        assertEquals(0, exports)
    }

    @Test fun busyAndPendingPickerSuppressDuplicateExports() {
        val pending = mutableStateOf(false)
        var exports = 0
        compose.setContent { HaloTheme {
            BackupExportOptions(false, false, "", false, pending.value, {}, {}, {}, { exports++; pending.value = true })
        } }
        assertEquals(0, exports)
        compose.onNodeWithText("Export Brain Dump").performClick()
        compose.onNodeWithText("Export Brain Dump").assertIsNotEnabled().performClick()
        assertEquals(1, exports)
        assertFalse(backupExportAllowed(true, false, false, ""))
        assertFalse(backupExportAllowed(false, true, false, ""))
        assertFalse(backupExportAllowed(false, false, true, ""))
    }
}
