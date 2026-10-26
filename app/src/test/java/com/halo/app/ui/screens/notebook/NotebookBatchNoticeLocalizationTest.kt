package com.halo.app.ui.screens.notebook

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.halo.app.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class NotebookBatchNoticeLocalizationTest {
    @Test fun partialResultsAndRollbackUncertaintyAreLocalizedWithoutLosingCounts() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val labels = mapOf("en" to "Unconfirmed", "ru" to "Не подтверждено",
            "lv" to "Nav apstiprināts", "pl" to "Niepotwierdzone")
        val warnings = mutableSetOf<String>()
        for ((language, label) in labels) {
            val configuration = Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(language))
            }
            val resources = context.createConfigurationContext(configuration).resources
            val partial = resources.getString(R.string.interface4_library_batch_partial, 2, 1, 3)
            assertTrue("Unconfirmed status must use the requested locale", partial.contains(label))
            listOf("2", "1", "3").forEach { assertTrue(partial.contains(it)) }
            assertFalse("No unexpanded argument markers", partial.contains('%'))
            warnings += resources.getString(R.string.interface4_appearance_rollback_unknown)
            assertTrue(resources.getString(R.string.interface4_library_batch_context, 2).contains("20"))
        }
        assertEquals("Rollback warning must not fall back to English", 4, warnings.size)
    }
}
