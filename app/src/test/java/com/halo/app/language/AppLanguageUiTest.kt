package com.halo.app.language

import android.app.Application
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.halo.sdk.runtime.Storage
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],manifest=Config.NONE,application=Application::class)
class AppLanguageUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun switchingLanguagePreservesUnsentDraftAndPickerOwner() =
        checkLanguageSwitch(AppLanguage.RUSSIAN, "Учёба", "Рабочее пространство")

    @Test fun switchingToPolishPreservesUnsentDraftAndPickerOwner() =
        checkLanguageSwitch(AppLanguage.POLISH, "Nauka", "Przestrzeń robocza")

    private fun checkLanguageSwitch(target: AppLanguage, studyLabel: String, workspaceLabel: String) {
        val preferences = AppLanguagePreferences(MemoryStorage(), "en")
        compose.setContent {
            val activityContext = LocalContext.current
            val pickerOwner = LocalActivityResultRegistryOwner.current
            AppLanguageProvider(preferences) {
                assertSame(activityContext, LocalContext.current)
                assertSame(pickerOwner, LocalActivityResultRegistryOwner.current)
                var draft by remember { mutableStateOf("") }
                Column {
                    Text(uiText("Study"))
                    Text(uiText("Workspace"))
                    OutlinedTextField(draft, { draft = it }, Modifier.testTag("draft"))
                    Button({ preferences.select(target) }) { Text("Switch language") }
                }
            }
        }
        compose.onNodeWithTag("draft").performTextInput("My question")
        compose.onNodeWithText("Switch language").performClick()
        compose.onNodeWithText(studyLabel).assertExists()
        compose.onNodeWithText(workspaceLabel).assertExists()
        compose.onNodeWithTag("draft").assertTextContains("My question")
    }
    private class MemoryStorage : Storage {
        private val values = java.util.concurrent.ConcurrentHashMap<String, Any>()
        override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { if(value == null) values.remove(key) else values[key] = value }
        override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        override fun clear() { values.clear() }
    }
}
