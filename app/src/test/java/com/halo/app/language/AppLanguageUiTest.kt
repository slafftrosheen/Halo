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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.halo.sdk.runtime.Storage
import com.halo.app.interaction.StructuredPresentationRenderer
import com.halo.app.interaction.PresentationDensity
import com.halo.app.interaction.SavePresentationToNotebookAction
import com.halo.runtime.cognition.interaction.*
import org.junit.Assert.assertSame
import org.junit.Assert.assertEquals
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

    @Test fun polishStructuredResultLocalizesHostSemanticsAndPreservesSourceAndCallbacks() {
        val preferences = AppLanguagePreferences(MemoryStorage(), "pl")
        var saves = 0
        val presentation = StructuredPresentation(root = PresentationNode.Column("root", listOf(
            PresentationNode.Status("status", "Private source", PresentationStatusState.INFO),
            PresentationNode.Progress("progress", "Private progress"),
            PresentationNode.Checklist("checklist", listOf(PresentationChecklistItem("step", "Private task", PresentationStepState.SKIPPED))),
            PresentationNode.ImageReference("image", "private-asset-id"),
            // This is also a registered host label; model/source content must still stay literal.
            PresentationNode.Text("source", "Study"),
        )))
        compose.setContent {
            AppLanguageProvider(preferences) {
                Column {
                    StructuredPresentationRenderer(presentation)
                    SavePresentationToNotebookAction { saves++ }
                }
            }
        }
        compose.onNodeWithContentDescription("Private source: W toku").assertExists()
        compose.onNodeWithContentDescription("Private progress: w toku, postęp nieokreślony").assertExists()
        compose.onNodeWithText("Private task").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Pominięto"))
        compose.onNodeWithText("Obraz jest dostępny w halo").assertExists()
        compose.onNodeWithText("Study").assertExists()
        compose.onNodeWithText("private-asset-id").assertDoesNotExist()
        compose.onNodeWithText("Zapisz w przestrzeni roboczej").performClick()
        assertEquals(1, saves)
    }

    @Test fun compactPolishComparisonUsesHostCopyWithoutTranslatingTheModelPayload() {
        val preferences = AppLanguagePreferences(MemoryStorage(), "pl")
        val presentation = StructuredPresentation(root = PresentationNode.Comparison("comparison", listOf("Original heading"), listOf(listOf("Original value"))))
        compose.setContent {
            AppLanguageProvider(preferences) {
                StructuredPresentationRenderer(presentation, density = PresentationDensity.COMPACT)
            }
        }
        compose.onNodeWithText("Porównanie jest dostępne w halo").assertExists()
        compose.onNodeWithText("Original value").assertDoesNotExist()
    }

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
