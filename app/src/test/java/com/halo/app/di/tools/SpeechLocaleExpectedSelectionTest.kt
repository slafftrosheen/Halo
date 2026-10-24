package com.halo.app.di.tools

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.settings.HaloSettingsRepository
import com.halo.app.ui.screens.settings.SpeechLanguageSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SpeechLocaleExpectedSelectionTest {
    @Test fun exactLocaleSelectionRejectsAStaleUiSnapshotAndRetainsThePersistedWinner() = runBlocking {
        val settings = HaloSettingsRepository(ApplicationProvider.getApplicationContext<Context>())
        val owner = SpeechLanguageSettingsRepository(settings)
        owner.select("lv")
        owner.selectExpected("ru", "lv")
        assertTrue(runCatching { owner.selectExpected("en", "lv") }.isFailure)
        assertEquals("ru", owner.languageMode.value)
        assertEquals("ru", settings.settings.first().speechLanguageMode)
    }
}
