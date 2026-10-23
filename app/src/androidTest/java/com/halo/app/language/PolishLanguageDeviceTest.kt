package com.halo.app.language

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.halo.app.R
import com.halo.app.ui.theme.HaloTheme
import com.halo.sdk.runtime.Storage
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated locale fixture: no user preference changes, providers or voice claims. */
@RunWith(AndroidJUnit4::class)
class PolishLanguageDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun polishSwitchAtLargeTextPreservesDraftAndPickerContext() {
        val preferences = AppLanguagePreferences(MemoryStorage(), "en")
        lateinit var draftFocus: FocusManager
        compose.setContent {
            draftFocus = LocalFocusManager.current
            val activity = LocalContext.current
            val picker = LocalActivityResultRegistryOwner.current
            AppLanguageProvider(preferences) {
                assertSame(activity, LocalContext.current)
                assertSame(picker, LocalActivityResultRegistryOwner.current)
                var draft by remember { mutableStateOf("") }
                HaloTheme(fontScale = 2f) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Button({ preferences.select(AppLanguage.POLISH) }, Modifier.testTag("polish")) {
                            Text("Polski")
                        }
                        Text(uiText("Study"))
                        Text(uiText("Workspace"))
                        Text(stringResource(R.string.file52_title))
                        Text(stringResource(R.string.canvas50_page, 2, 8))
                        Text(stringResource(R.string.canvas50_zoom_level, 150))
                        OutlinedTextField(draft, { draft = it }, Modifier.testTag("draft"))
                    }
                }
            }
        }
        compose.onNodeWithTag("draft").performScrollTo().performTextInput("Synthetic question")
        // At 200% in landscape the IME can keep bringing the focused draft back
        // into view while the test scrolls to translated labels. End editing,
        // preserving its text, before checking those separate viewport positions.
        compose.runOnIdle { draftFocus.clearFocus() }
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("polish").performScrollTo().performClick()
        for (label in listOf("Nauka", "Przestrzeń robocza", "Narzędzia plików", "Strona 2 z 8", "Powiększenie: 150%")) {
            compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithTag("draft").performScrollTo().assertTextContains("Synthetic question")
    }

    private class MemoryStorage : Storage {
        private val values = mutableMapOf<String, Any>()
        override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
        override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        override fun clear() { values.clear() }
    }
}
