package com.halo.app.data.workspace

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.workspace.AdaptiveSlot.*
import com.halo.app.everyday.presentation.EverydayCardConfig
import com.halo.app.everyday.presentation.EverydayCardKind
import com.halo.app.ui.adaptive.adaptiveRows
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AdaptivePagesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences("halo_adaptive_pages_v1", Context.MODE_PRIVATE)
    private val slots = AdaptiveSlot.entries.toSet()

    @After fun clear() {
        preferences.edit().clear().commit()
    }

    @Test fun plansMatchWholeWordsInRequestOrderAndFoldAccents() {
        assertTrue(AdaptivePlanner.select("explain what I already have", slots, 7).isEmpty())
        assertEquals(listOf(CLOCK, TASKS, WEATHER),
            AdaptivePlanner.select("pulkstenis, uzdevumi, laikapstākļi", slots, 7))
        assertEquals(listOf(READS, WEATHER), AdaptivePlanner.select("news and weather", slots, 7))
        assertTrue(AdaptivePlanner.suggestions(slots, setOf(READS), 7).none { READS in it })
    }

    @Test fun blockEditsAndWideRowsPreserveOrderAndSize() {
        val layout = AdaptiveLayout(listOf(WEATHER, TASKS, PLANNER, CLOCK, DEVICE), setOf(PLANNER))
        assertEquals(listOf(listOf(WEATHER, TASKS), listOf(PLANNER), listOf(CLOCK, DEVICE)), adaptiveRows(layout, true))
        assertEquals(layout.slots.map { listOf(it) }, adaptiveRows(layout, false))
        val edited = layout.swap(PLANNER, BRIEF).remove(WEATHER).move(BRIEF, -1)
        assertEquals(AdaptiveLayout(listOf(BRIEF, TASKS, CLOCK, DEVICE), setOf(BRIEF)), edited)
    }

    @Test fun launcherExportKeepsPageOrderAndReportsUnsupportedBlocks() {
        val plan = AdaptiveWidgetExport.from(AdaptiveLayout(listOf(LIBRARY, REMINDERS, WEATHER, WIND_DOWN, CLOCK, TASKS)))
        assertEquals(listOf(LIBRARY, WIND_DOWN), plan.unsupported)
        val default = plan.config()
        assertEquals(listOf(EverydayCardKind.REMINDERS, EverydayCardKind.WEATHER, EverydayCardKind.CLOCK), default.sections)
        assertEquals(default, EverydayCardConfig.from(default.fields(), EverydayCardKind.DASHBOARD))
        val selected = plan.config(listOf(EverydayCardKind.TASKS, EverydayCardKind.REMINDERS))
        assertEquals(listOf(EverydayCardKind.REMINDERS, EverydayCardKind.TASKS), selected.sections)
        assertThrows(IllegalArgumentException::class.java) { plan.config(listOf(EverydayCardKind.UPCOMING)) }
        assertThrows(IllegalArgumentException::class.java) {
            AdaptiveWidgetExport.from(AdaptiveLayout(listOf(LIBRARY, STUDY))).config()
        }
    }

    @Test fun widgetReviewRouteRefusesMalformedAndUnboundedRevisions() {
        val request = AdaptiveWidgetEntry(AdaptivePageRef("legacy", 2), 9)
        assertEquals(request, AdaptiveWidgetEntry.parse(request.route()))
        assertNull(AdaptiveWidgetEntry.parse(request.route() + "?unreviewed=true"))
        assertNull(AdaptiveWidgetEntry.parse("home/pages/widget/legacy/0/9"))
        assertNull(AdaptiveWidgetEntry.parse("home/pages/widget/legacy/9007199254740992/9"))
        assertNull(AdaptiveWidgetEntry.parse("home/pages/widget/legacy/2/-1"))
    }

    @Test fun legacyLayoutsKeepTheirOrderAndOnlyExactRevisionsCanReplaceOrDelete() = runBlocking {
        preferences.edit().putString("pinned", "WEATHER,PLANNER,STUDY").commit()
        val store = AdaptivePageStore(context)
        val legacy = store.pages().single()
        assertEquals(listOf(WEATHER, PLANNER, STUDY), legacy.layout.slots)
        val layout = AdaptiveLayout(listOf(TASKS, PLANNER), setOf(TASKS))
        val saved = store.save(legacy.ref, "Morning", layout) {}
        assertEquals(AdaptivePageRef("legacy", 2), saved.ref)
        assertEquals(listOf(saved), AdaptivePageStore(context).pages())
        assertTrue(runCatching { store.save(legacy.ref, "Stale", layout) {} }
            .exceptionOrNull() is AdaptivePageChanged)
        assertTrue(runCatching { store.delete(legacy.ref) {} }.exceptionOrNull() is AdaptivePageChanged)
        assertTrue(runCatching { store.delete(saved.ref) { error("Access changed") } }.isFailure)
        assertEquals(listOf(saved), store.pages())
        store.delete(saved.ref) {}
        assertTrue(store.pages().isEmpty())
        assertTrue(!preferences.contains("pinned"))
    }
}
