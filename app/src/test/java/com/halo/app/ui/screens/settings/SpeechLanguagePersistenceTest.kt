package com.halo.app.ui.screens.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.settings.HaloSettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SpeechLanguagePersistenceTest {
    @Test fun latvianAndExplicitAutoPersistAcrossRepositoryRecreation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = HaloSettingsRepository(context)
        val first = SpeechLanguageSettingsRepository(settings)
        first.loadAndMigrate(); first.select("lv")
        val restored = SpeechLanguageSettingsRepository(settings)
        restored.loadAndMigrate()
        assertEquals("lv", restored.languageMode.value)
        restored.select("auto")
        val automatic = SpeechLanguageSettingsRepository(settings)
        automatic.loadAndMigrate()
        assertEquals("auto", automatic.languageMode.value)
        assertTrue(VoiceSettingsUiState().availableLocales.any { it.tag == "lv" && it.displayName == "Latviešu" })
    }
}
