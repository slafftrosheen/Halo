package com.halo.app.language

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.halo.sdk.runtime.Storage
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class AppLanguageTest {
    @Test fun explicitSelectionPersistsAndUnsupportedDeviceLanguageFallsBackSafely() {
        val storage = MemoryStorage()
        val preferences = AppLanguagePreferences(storage, "lv-LV")
        assertEquals(AppLanguage.LATVIAN, preferences.language.value)
        preferences.select(AppLanguage.RUSSIAN)
        assertEquals(AppLanguage.RUSSIAN, AppLanguagePreferences(storage, "en").language.value)
        assertEquals(AppLanguage.ENGLISH, AppLanguagePreferences(MemoryStorage(), "ja").language.value)
    }
    @Test fun compiledResourcesKeepEnglishAndRenderCyrillicAndLatvianDiacritics() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        fun localized(tag: String) = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(tag))
        }).resources
        val english = localized("en")
        uiCatalogue.forEach { (label, id) -> assertEquals(Regex("""\bHalo\b""").replace(label, "halo"), english.getString(id)) }
        assertEquals("Учёба", localized("ru").getString(uiCatalogue.getValue("Study")))
        assertEquals("Mācības", localized("lv").getString(uiCatalogue.getValue("Study")))
        assertEquals("Источники: 2 · карточки: 3 · сохранённые ответы: 4", localized("ru").getString(
            uiCatalogue.getValue("%1\$d sources · %2\$d cards · %3\$d saved replies"), 2, 3, 4))
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
