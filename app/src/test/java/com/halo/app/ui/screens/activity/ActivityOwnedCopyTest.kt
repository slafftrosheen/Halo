package com.halo.app.ui.screens.activity

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.halo.app.language.*
import com.halo.app.ui.theme.HostlessHaloTheme
import com.halo.sdk.runtime.Storage
import com.halo.app.interaction.StructuredPresentationRenderer
import com.halo.runtime.cognition.interaction.*
import java.util.Locale
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class ActivityOwnedCopyTest {
    @get:Rule val compose = createComposeRule()

    @Test fun typedCountsAndOwnedLabelsLocalizeWhileIdenticalExternalTextStaysLiteral() {
        val language = AppLanguagePreferences(MemoryStorage(), "en")
        compose.setContent { AppLanguageProvider(language) { HostlessHaloTheme {
            Column {
                Text(localizedActivityProgress(ActivityPlanProgress(2, 3, true)), Modifier.testTag("progress"))
                Text(uiText("Plan"), Modifier.testTag("owned-plan"))
                StructuredPresentationRenderer(PresentationTemplates.checklist("Plan",
                    listOf(PresentationDirectiveItem("Arbitrary model content", PresentationStepState.COMPLETED))))
                ActivityRecordedStepRow(ActivityStep("owned", "Updated your task", "Your answer is ready", hostOwnedCopy = true), null)
                ActivityRecordedStepRow(ActivityStep("source", "Updated your task", "Your answer is ready"), null)
            }
        } } }
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (tag in listOf("en", "ru", "lv", "pl")) {
            compose.runOnIdle { language.select(AppLanguage.fromTag(tag)) }
            val resources = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(tag))
            }).resources
            compose.onNodeWithTag("owned-plan").assertTextEquals(resolveUiText(resources, "Plan"))
            compose.onAllNodesWithText("Plan").assertCountEquals(if (resolveUiText(resources, "Plan") == "Plan") 2 else 1)
            val counts = resolveUiText(resources, "%1\$d / %2\$d steps complete", 2, 3)
            compose.onNodeWithTag("progress").assertTextEquals("$counts · ${resolveUiText(resources, "Waiting for approval")}")
            compose.onAllNodesWithText("Updated your task").assertCountEquals(if (resolveUiText(resources, "Updated your task") == "Updated your task") 2 else 1)
            compose.onAllNodesWithText("Your answer is ready").assertCountEquals(if (resolveUiText(resources, "Your answer is ready") == "Your answer is ready") 2 else 1)
            compose.onAllNodesWithText(resolveUiText(resources, "Updated your task")).assertCountEquals(if (resolveUiText(resources, "Updated your task") == "Updated your task") 2 else 1)
        }
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
