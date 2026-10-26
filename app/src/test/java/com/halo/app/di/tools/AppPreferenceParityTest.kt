package com.halo.app.di.tools

import android.content.Context
import android.content.ContextWrapper
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
        val visual = VisualPreferences.get(context)
        val tools = AssistantTools(context, AppearancePreferences(context), AssistantPreferences(context), { _, _ -> }, {}, { true },
            palettes = palettes, visual = visual, languages = languages,
            languageReadback = { languageStorage.getString(AppLanguagePreferences.KEY) })
        val changed = tools.execute(ToolCall("appearance", "halo_set_preferences", mapOf(
            "fontScale" to 2.0, "paletteTheme" to "dark", "accent" to "LILAC", "tone" to "COOL", "background" to "STILL",
            "higherContrast" to true, "lessMotion" to true, "guideLight" to false, "language" to "lv")))
        assertEquals(ActionTruth.CONFIRMED, (changed as ToolExecutionResult.Success).actionTruth)
        assertEquals(2f, AssistantPreferences(context).get().fontScale)
        assertEquals(HaloPalette(HaloAccent.LILAC, HaloTone.COOL, HaloBackgroundStyle.STILL), palettes.state.value.dark)
        assertEquals(AppLanguage.LATVIAN, languages.language.value)
        assertEquals("lv", languageStorage.getString(AppLanguagePreferences.KEY))
        val restored = PalettePreferences(context); restored.awaitReady()
        assertEquals(palettes.state.value.dark, restored.state.value.dark)
        val read = tools.execute(ToolCall("read", "halo_get_preferences", emptyMap())) as ToolExecutionResult.Success
        assertTrue("Readback=${read.output}; injectedOwner=${visual.state.value}; currentContextContrast=" +
            context.getSharedPreferences("halo_visual_options", Context.MODE_PRIVATE).getBoolean("contrast", false),
            read.output.contains("higherContrast=true"))
        assertTrue(read.output.contains("language=lv"))
        assertTrue(read.output.contains("darkBackground=STILL"))
    }

    @Test fun injectedVisualOwnerReadbackUsesTheSameNamespaceAsItsConfirmedWrite() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val visual = VisualPreferences.get(context)
        // A real, independent preference namespace reproduces a recreated host context's
        // storage view without depending on test order or resetting the singleton owner.
        val otherContext = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int) =
                context.getSharedPreferences(if (name == "halo_visual_options") "$name.readback-fixture" else name, mode)
        }
        val otherDisk = otherContext.getSharedPreferences("halo_visual_options", Context.MODE_PRIVATE)
        check(otherDisk.edit().clear().commit())
        val tools = AssistantTools(otherContext, AppearancePreferences(context), AssistantPreferences(context),
            { _, _ -> }, {}, { true }, visual = visual)
        val changed = tools.execute(ToolCall("owner-write", "halo_set_preferences", mapOf("higherContrast" to true)))
        assertEquals(ActionTruth.CONFIRMED, (changed as ToolExecutionResult.Success).actionTruth)
        assertTrue("The injected canonical owner completed the write", visual.state.value.higherContrast)
        assertFalse("The independent context must remain a distinct namespace", otherDisk.getBoolean("contrast", false))
        val read = tools.execute(ToolCall("owner-read", "halo_get_preferences", emptyMap())) as ToolExecutionResult.Success
        assertTrue("Confirmed write and subsequent read must use the injected owner; readback=${read.output}; " +
            "injectedOwner=${visual.state.value}; otherContextContrast=${otherDisk.getBoolean("contrast", false)}",
            read.output.contains("higherContrast=true"))
    }

    @Test fun malformedOrUnavailableOptionsNeverPartiallyApply() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tools = AssistantTools(context, AppearancePreferences(context), AssistantPreferences(context), { _, _ -> }, {}, { true })
        listOf(mapOf("fontScale" to 2.1), mapOf("mode" to "dark", "accent" to "RED"),
            mapOf("fontScale" to 1.25, "language" to "zz"), mapOf("lessMotion" to "true"),
            mapOf("mode" to "dark", "unknown" to true), mapOf("background" to "STILL"),
            mapOf("paletteTheme" to "light", "background" to "UNKNOWN")).forEach {
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
