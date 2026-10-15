package com.halo.app.language

import android.app.Application
import android.content.res.Configuration
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class StudyProgressLocalizationTest {
    private fun message(language: String, key: String, vararg values: Any): String {
        val app = RuntimeEnvironment.getApplication()
        val configuration = Configuration(app.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        }
        val resources = app.createConfigurationContext(configuration).resources
        return resources.getString(requireNotNull(uiCatalogue[key]), *values)
    }

    @Test fun allThreeLanguagesRenderProgressNumbers() {
        val key = "%1\$d cards due · %2\$d mastered"
        for (language in listOf("en", "ru", "lv")) {
            val label = message(language, key, 4, 2)
            assertTrue(label.contains("4"))
            assertTrue(label.contains("2"))
            assertTrue(!label.contains("%1"))
        }
    }

    @Test fun russianAndLatvianDoNotFallBackToEnglish() {
        val key = "%1\$d attempts saved locally · %2\$d without help"
        val en = message("en", key, 3, 1)
        assertNotEquals(en, message("ru", key, 3, 1))
        assertNotEquals(en, message("lv", key, 3, 1))
    }

    @Test fun percentageIsEscapedAndFormattedOnce() {
        for (language in listOf("en", "ru", "lv")) {
            val percent = message(language, "Mastery %1\$d%%", 75)
            assertTrue(percent.contains("75%"))
            assertEquals(1, percent.count { it == '%' })
        }
    }
}
