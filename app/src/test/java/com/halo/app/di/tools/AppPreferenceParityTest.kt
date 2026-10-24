package com.halo.app.di.tools

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.halo.app.di.tools.host.AssistantTools
import com.halo.app.language.*
import com.halo.app.ui.theme.*
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class AppPreferenceParityTest {
    @Test fun allVisibleAppearanceAndLanguageOptionsUseSharedOwnersAndPersist() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val languageStorage = MemoryStorage()
        val palettes = PalettePreferences(context)
        val languages = AppLanguagePreferences(languageStorage)
        val tools = AssistantTools(context, AppearancePreferences(context), AssistantPreferences(context), { _, _ -> }, {}, { true },
            palettes = palettes, visual = VisualPreferences.get(context), languages = languages,
            languageReadback = { languageStorage.getString(AppLanguagePreferences.KEY) })
        val changed = tools.execute(ToolCall("appearance", "halo_set_preferences", mapOf(
            "fontScale" to 2.0, "paletteTheme" to "dark", "accent" to "LILAC", "tone" to "COOL",
            "higherContrast" to true, "lessMotion" to true, "guideLight" to false, "language" to "lv")))
        assertEquals(ActionTruth.CONFIRMED, (changed as ToolExecutionResult.Success).actionTruth)
        assertEquals(2f, AssistantPreferences(context).get().fontScale)
        assertEquals(HaloPalette(HaloAccent.LILAC, HaloTone.COOL), palettes.state.value.dark)
        assertEquals(AppLanguage.LATVIAN, languages.language.value)
        assertEquals("lv", languageStorage.getString(AppLanguagePreferences.KEY))
        val restored = PalettePreferences(context); restored.awaitReady()
        assertEquals(palettes.state.value.dark, restored.state.value.dark)
        val read = tools.execute(ToolCall("read", "halo_get_preferences", emptyMap())) as ToolExecutionResult.Success
        assertTrue(read.output.contains("higherContrast=true"))
        assertTrue(read.output.contains("language=lv"))
    }

    @Test fun malformedOrUnavailableOptionsNeverPartiallyApply() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tools = AssistantTools(context, AppearancePreferences(context), AssistantPreferences(context), { _, _ -> }, {}, { true })
        listOf(mapOf("fontScale" to 2.1), mapOf("mode" to "dark", "accent" to "RED"),
            mapOf("fontScale" to 1.25, "language" to "zz"), mapOf("lessMotion" to "true"),
            mapOf("mode" to "dark", "unknown" to true)).forEach {
            assertTrue(tools.execute(ToolCall("invalid", "halo_set_preferences", it)) is ToolExecutionResult.Rejected)
        }
    }

    private class MemoryStorage : Storage {
        private val values = mutableMapOf<String, Any?>()
        override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { values[key] = value }
        override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        override fun clear() { values.clear() }
    }
}
