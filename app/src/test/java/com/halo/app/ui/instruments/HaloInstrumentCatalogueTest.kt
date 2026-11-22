package com.halo.app.ui.instruments

import android.app.Application
import android.content.res.Configuration
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import com.halo.app.language.resolveUiText
import com.halo.app.ui.theme.HaloLookMotion
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HaloInstrumentCatalogueTest {
    private val resources get() = ApplicationProvider.getApplicationContext<Application>().resources

    @Test fun suppliedCatalogueResolvesEveryIconAndBilingualName() {
        val tools = mapOf("studio" to 22, "notes" to 20, "sheets" to 16, "canvas" to 15,
            "scan" to 12, "record" to 12, "planner" to 11, "study" to 10, "memory" to 9,
            "album" to 11, "vault" to 9)
        val specs = tools.flatMap { (tool, count) ->
            HaloInstrumentCatalogue.entries(resources, tool).also { assertEquals(count, it.size) }
        }
        assertEquals(147, specs.map(InstrumentSpec::id).toSet().size)
        assertTrue(specs.all { it.icon != 0 && it.russianName.isNotBlank() && it.isVisible(InstrumentLevel.PRO) })
        assertEquals(setOf("sheets.remove_duplicates", "album.share", "vault.share_a_copy"),
            specs.filter(InstrumentSpec::hardStop).map(InstrumentSpec::id).toSet())
        assertTrue(specs.single { it.id == "studio.brush" }.matches("кисть"))
        assertFalse(specs.single { it.id == "studio.lasso" }.isVisible(InstrumentLevel.EASY))
    }

    @Test fun alternativeAndModifierShortcutsMatchOnlyTheRequestedKey() {
        val specs = HaloInstrumentCatalogue.entries(resources, "notes")
        val emphasis = specs.single { it.id == "notes.bold_italic_underline" }
        assertTrue(emphasis.matchesShortcut(key(KeyEvent.KEYCODE_I, KeyEvent.META_CTRL_ON)))
        assertFalse(emphasis.matchesShortcut(key(KeyEvent.KEYCODE_I, 0)))
        assertFalse(emphasis.matchesShortcut(key(KeyEvent.KEYCODE_I, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)))
        val styles = specs.single { it.id == "notes.paragraph_styles" }
        assertTrue(styles.matchesShortcut(key(KeyEvent.KEYCODE_3, KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON)))
    }

    @Test fun reducedMotionUsesOneFadeDuration() {
        assertEquals(360, HaloInstrumentMotion.duration(HaloInstrumentMotion.CALM, HaloLookMotion.FULL))
        assertEquals(468, HaloInstrumentMotion.duration(HaloInstrumentMotion.CALM, HaloLookMotion.GENTLE))
        assertEquals(150, HaloInstrumentMotion.duration(HaloInstrumentMotion.QUICK, HaloLookMotion.STILL))
        assertEquals(150, HaloInstrumentMotion.duration(HaloInstrumentMotion.CALM, HaloLookMotion.STILL))
    }

    @Test fun russianScreensLocalizeReportPlannerAndInstrumentCopyWithFormatting() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val russian = app.createConfigurationContext(Configuration(app.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("ru"))
        }).resources
        assertEquals("На этой неделе", resolveUiText(russian, "This week"))
        assertEquals("Что включить в отчёт", resolveUiText(russian, "What goes in your day"))
        assertEquals("Вид на месяц", resolveUiText(russian, "Month view"))
        assertEquals("Задание с подсказками", resolveUiText(russian, "Guided task"))
        assertEquals("Карта: страниц 4, связей 3", resolveUiText(russian, "Map of %1\$d pages and %2\$d links", 4, 3))
        assertEquals("Допуск 20%", resolveUiText(russian, "Tolerance %1\$d%%", 20))
        val magicWand = HaloInstrumentCatalogue.entries(russian, "studio").single { it.id == "studio.magic_wand" }
        assertTrue(resolveUiText(russian, magicWand.parameters).contains("Допуск"))
    }

    private fun key(code: Int, modifiers: Int) = KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, 0, modifiers)
}
