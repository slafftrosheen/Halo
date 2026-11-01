package com.halo.app.data.everyday

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.everyday.collectors.OfflineCityCatalogue
import com.halo.app.everyday.organizer.*
import java.time.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WorldClockAndCitiesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val repository = RoomWorldClockRepository(db)
    @After fun close() { db.close() }
    @Test fun independentClockEditsReorderAndUndoRejectStaleVersions() = runBlocking {
        val first = repository.save(WorldClockRecord("one", "Home", "Europe/Riga"), null)
        val second = repository.save(WorldClockRecord("two", "Family", "Europe/Riga"), null)
        repository.reorder(listOf(second.id, first.id), mapOf(first.id to first.version, second.id to second.version))
        assertEquals(listOf("two", "one"), repository.all().map { it.id })
        assertThrows(OrganizerConflict::class.java) { runBlocking { repository.save(first.copy(label = "Old update"), first.version) } }
        val current = repository.get(first.id)!!
        val deleted = repository.remove(first.id, current.version)
        assertEquals(listOf("two"), repository.all().map { it.id })
        val restored = RoomWorldClockRepository(db).restore(deleted.id, deleted.version)
        assertTrue(restored.version > deleted.version)
        assertEquals("Home", restored.label)
        assertThrows(OrganizerConflict::class.java) { runBlocking { repository.restore(deleted.id, deleted.version) } }
        Unit
    }
    @Test fun worldTimeUsesZoneRulesAcrossDstInsteadOfFixedOffsets() {
        val riga = WorldClockRecord("riga", "Rīga", "Europe/Riga")
        val before = riga.at(Instant.parse("2026-03-29T00:59:00Z"))
        val after = riga.at(Instant.parse("2026-03-29T01:01:00Z"))
        assertEquals(2, before.hour); assertEquals(4, after.hour)
        assertEquals(ZoneOffset.ofHours(2), before.offset); assertEquals(ZoneOffset.ofHours(3), after.offset)
    }
    @Test fun bundledCitySearchSupportsDiacriticsAndRussianWithoutNetwork() = runBlocking {
        val cities = OfflineCityCatalogue { context.assets.open("everyday/everyday-cities.bin") }
        val latin = cities.search("Riga", "LV").first()
        val diacritics = cities.search("Rīga", "LV").first()
        val russian = cities.search("Рига", "LV").first()
        assertEquals(latin.id, diacritics.id); assertEquals(latin.id, russian.id)
        assertEquals("Europe/Riga", latin.zoneId)
        assertEquals(latin, cities.get(latin.id))
        assertTrue(cities.search("Warszawa", "PL").any { it.zoneId == "Europe/Warsaw" })
        assertEquals(10, cities.search("", limit = 10).size)
    }
}
