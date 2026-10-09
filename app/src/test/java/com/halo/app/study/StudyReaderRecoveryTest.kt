package com.halo.app.study

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.study.StudyRepository
import com.halo.app.ui.screens.study.StudyViewModel
import com.halo.sdk.runtime.study.StudyLanguagePreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],manifest=Config.NONE,application=Application::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@OptIn(ExperimentalCoroutinesApi::class)
class StudyReaderRecoveryTest {
    private class Narrator : StudyNarrationController {
        override val available=true
        val receipts=MutableSharedFlow<NarrationEvent>(extraBufferCapacity=16)
        override val events=receipts.asSharedFlow()
        var last=""
        override fun narrate(text: String,sessionId: String,generationId: String) { last=generationId }
        override fun stop(reason: String)=Unit
    }
    @Test fun pauseKeepsPositionAndLateCompletionCannotAdvanceResumedReader() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).allowMainThreadQueries().build()
        val store=ViewModelStore()
        try {
            val repository=StudyRepository(db,db.studyDao())
            val session=repository.createSession(listOf("source"),7,StudyLanguagePreferences(),"Learn")
            val narrator=Narrator()
            val resolver=StudySourceResolver { _,_ -> ResolvedStudySource.Text("source","7","Source",
                listOf(StudyPassage("First. Second.",StudySourceLocator.NoteBlocks(1,1))),14,SourceCoverage.FULL) }
            val vm=StudyViewModel(session.id,StudyUseCases(repository,narration=narrator,sourceResolver=resolver))
            store.put("reader",vm)
            withTimeout(5000) { vm.uiState.filter { !it.loading && it.sources.isNotEmpty() }.first() }
            vm.readSourceAloud()
            val first=narrator.last
            assertTrue(vm.uiState.value.reader!!.speaking)
            vm.toggleReaderPlayback()
            assertNotNull(vm.uiState.value.reader)
            assertFalse(vm.uiState.value.reader!!.speaking)
            narrator.receipts.emit(NarrationEvent.Completed(first))
            assertEquals(0,vm.uiState.value.reader!!.index)
            vm.toggleReaderPlayback()
            val resumed=narrator.last
            assertNotEquals(first,resumed)
            narrator.receipts.emit(NarrationEvent.Completed(first))
            assertEquals(0,vm.uiState.value.reader!!.index)
            narrator.receipts.emit(NarrationEvent.Range(first,0,3))
            assertNull(vm.uiState.value.reader!!.wordRange)
            narrator.receipts.emit(NarrationEvent.Range(resumed,0,3))
            assertEquals(0..2,vm.uiState.value.reader!!.wordRange)
            narrator.receipts.emit(NarrationEvent.Range(resumed,0,999))
            assertEquals(0..2,vm.uiState.value.reader!!.wordRange)
            narrator.receipts.emit(NarrationEvent.Completed(resumed))
            assertEquals(1,vm.uiState.value.reader!!.index)
            vm.closeReader()
            assertNull(vm.uiState.value.reader)
        } finally { store.clear(); db.close(); Dispatchers.resetMain() }
    }
}
