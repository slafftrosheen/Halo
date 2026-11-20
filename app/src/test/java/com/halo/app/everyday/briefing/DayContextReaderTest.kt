package com.halo.app.everyday.briefing

import android.app.Application
import com.halo.app.everyday.collectors.CollectorConflict
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class DayContextReaderTest {
    private val from = Instant.parse("2026-11-15T22:00:00Z")
    private val args = mapOf<String, Any>("from" to from.toString(), "until" to "2026-11-16T22:00:00Z", "zoneId" to "Europe/Riga")
    @Test fun fullDayPagesIncludeAllSectionsAndUnclippedTextWithStableVersions() = runBlocking {
        val items = (0..24).map { DayContextItem("note-$it", "Note $it", from.plusSeconds(it.toLong()), "1") }
        val snapshot = DayContextSnapshot(mapOf("WORKSPACE" to items, "NOTIFICATIONS" to listOf(DayContextItem("notice", "Allowed notice", from, "1"))), mapOf("notifications" to "24-hour retention"))
        val full = "Body beyond headings. ".repeat(1800) + "LAST DETAIL"
        val reader = DayContextReader({ _, _ -> snapshot }, { _, _ -> full })
        val manifest = reader.read(args)
        val version = manifest.getString("sourceVersion")
        assertEquals(5, manifest.getJSONArray("sections").length())
        val base = args + mapOf("sourceVersion" to version, "section" to "WORKSPACE")
        val seen = mutableSetOf<String>(); var offset = 0
        while (true) {
            val page = reader.read(base + ("offset" to offset)); val rows = page.getJSONArray("items")
            repeat(rows.length()) { seen += rows.getJSONObject(it).getString("id") }
            if (page.isNull("nextOffset")) break
            offset = page.getInt("nextOffset")
        }
        assertEquals(items.map { it.id }.toSet(), seen)
        val text = StringBuilder(); offset = 0
        while (true) {
            val item = reader.read(base + mapOf("itemId" to "note-0", "textOffset" to offset)).getJSONArray("items").getJSONObject(0)
            text.append(item.getString("text"))
            if (item.isNull("nextTextOffset")) break
            offset = item.getInt("nextTextOffset")
        }
        assertEquals(full, text.toString())
    }
    @Test fun changedPermissionsOrContentInvalidateReadBeforeDisclosure() = runBlocking {
        var revision = "1"
        fun snapshot() = DayContextSnapshot(mapOf("WORKSPACE" to listOf(DayContextItem("one", "Note", from, revision))), mapOf("permissions" to revision))
        val reader = DayContextReader({ _, _ -> snapshot() }, { _, _ -> revision = "2"; "PRIVATE CONTENT" })
        val version = reader.read(args).getString("sourceVersion")
        try { reader.read(args + mapOf("section" to "WORKSPACE", "sourceVersion" to version, "itemId" to "one")); fail("Changed authority must fail") }
        catch (_: CollectorConflict) { }
        try { reader.read(args + mapOf("section" to "WORKSPACE")); fail("Continuation needs a version") }
        catch (_: IllegalArgumentException) { }
    }
}
