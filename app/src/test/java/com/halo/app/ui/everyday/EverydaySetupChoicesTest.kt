package com.halo.app.ui.everyday

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.everyday.RoomCollectorRepository
import com.halo.app.everyday.EverydayWriteAuthority
import com.halo.app.everyday.collectors.*
import com.halo.app.screen.LocalScreenStore
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33],application=Application::class)
class EverydaySetupChoicesTest {
    private val app=ApplicationProvider.getApplicationContext<Application>()
    private val db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build()
    private val sources=RoomCollectorRepository(db)
    private val screen=LocalScreenStore(app)
    private val city=OfflineCity("456172","Riga","LV",56.95,24.1,"Europe/Riga",600000)
    @After fun close() {db.close();app.getSharedPreferences("halo_screen",0).edit().clear().commit()}
    @Test fun firstRunIsLocalAndResumableAndASecondSaveUsesItsOwnCommittedRevision()=runBlocking {
        val first=sources.save(StarterSourceCatalogue.external().first(),null)
        val second=sources.save(StarterSourceCatalogue.external().last(),null)
        val reviewed=sources.states()
        val draft=EverydaySetupDraft(sourceIds=setOf(first.id),cards=listOf("clock","weather"),cityId=city.id,
            versions=reviewed.associate {it.source.id to it.source.version},screenRevision=screen.withToolState {it.revision})
        val state=SavedStateHandle(mapOf("draft" to draft.encode()))
        val resumed=EverydaySetupDraft.decode(state.get<String>("draft")!!)
        val saved=EverydayWriteAuthority.run({}) {EverydaySetupChoices.apply(db,sources,screen,resumed,reviewed,city)}
        assertTrue(sources.state(first.id)!!.source.selected);assertFalse(sources.state(second.id)!!.source.selected)
        assertTrue(saved.sources.all {it.snapshot==null && it.attempt.state==CollectorAttemptState.NEVER})
        assertEquals("weather:setup:${city.id}",screen.layout.value!!.cards.last().config["sourceId"])
        val revision=saved.draft.screenRevision
        val again=EverydayWriteAuthority.run({}) {EverydaySetupChoices.apply(db,sources,screen,saved.draft.copy(minutes=180,cards=listOf("clock","briefing")),saved.sources,city)}
        assertNotEquals(revision,again.draft.screenRevision)
        assertEquals(180,sources.state(first.id)!!.source.refreshMinutes)
        assertEquals(180,sources.state("weather:setup:${city.id}")!!.source.refreshMinutes)
        assertEquals(listOf("clock","briefing"),LocalScreenStore(app).layout.value!!.cards.map {it.widgetType})
    }
    @Test fun staleSourceReviewRollsBackEveryChoiceAndDoesNotChangeScreen()=runBlocking {
        val source=sources.save(StarterSourceCatalogue.external().first(),null)
        val reviewed=sources.states();val before=screen.portableSnapshot()
        val draft=EverydaySetupDraft(sourceIds=setOf(source.id),versions=mapOf(source.id to source.version),screenRevision=screen.withToolState {it.revision})
        sources.save(source.copy(title="Changed elsewhere"),source.version)
        assertTrue(runCatching {EverydaySetupChoices.apply(db,sources,screen,draft,reviewed,null)}.exceptionOrNull() is CollectorConflict)
        assertFalse(sources.state(source.id)!!.source.selected);assertEquals(before,screen.portableSnapshot())
    }
    @Test fun malformedDraftCannotAddAnUnknownCardOrExpandItsSourceScope()=runBlocking {
        assertTrue(runCatching {EverydaySetupDraft.decode(EverydaySetupDraft(cards=listOf("execute")).encode())}.isFailure)
        assertTrue(runCatching {EverydaySetupChoices.apply(db,sources,screen,EverydaySetupDraft(sourceIds=setOf("unknown")),emptyList(),null)}.isFailure)
        assertTrue(sources.states().isEmpty());assertNull(screen.layout.value)
    }
    @Test fun removingAndReplacingSavedWeatherCityUpdatesActualSelectedSources()=runBlocking {
        val source=sources.save(StarterSourceCatalogue.external().first(),null)
        val draft=EverydaySetupDraft(sourceIds=setOf(source.id),cityId=city.id,applyScreen=false,
            versions=mapOf(source.id to source.version),screenRevision=screen.withToolState {it.revision})
        val first=EverydaySetupChoices.apply(db,sources,screen,draft,sources.states(),city)
        val removed=EverydaySetupChoices.apply(db,sources,screen,first.draft.withWeatherCity(null),first.sources,null)
        assertFalse(sources.state("weather:setup:${city.id}")!!.source.selected)
        assertTrue(sources.state(source.id)!!.source.selected)
        val again=EverydaySetupChoices.apply(db,sources,screen,removed.draft.withWeatherCity(city.id),removed.sources,city)
        val next=city.copy(id="524901",name="Moscow",zoneId="Europe/Moscow")
        EverydaySetupChoices.apply(db,sources,screen,again.draft.withWeatherCity(next.id),again.sources,next)
        assertFalse(sources.state("weather:setup:${city.id}")!!.source.selected)
        assertTrue(sources.state("weather:setup:${next.id}")!!.source.selected)
        assertTrue(sources.state(source.id)!!.source.selected)
    }
}
