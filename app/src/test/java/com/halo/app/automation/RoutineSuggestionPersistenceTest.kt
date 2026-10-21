package com.halo.app.automation

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.memory.*
import com.halo.app.data.routine.RoomRoutineRepository
import com.halo.app.ui.screens.settings.RoutineTemplate
import com.halo.runtime.cognition.memory.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class RoutineSuggestionPersistenceTest {
    @Test fun approvalIsAtomicAndBoundToPendingTarget() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val learning = PersonalLearningRepository(db, RoomMemoryRepository(db))
            val routines = RoomRoutineRepository(db.routineDao())
            val routine = RoutineTemplate.BATTERY.buildDraft().routine.copy(enabled = false)
            routines.upsert(routine)
            val now = Instant.now()
            val suggestion = PersonalSuggestion("s", "s", PersonalSuggestionKind.AUTOMATION, "Review", "Reason", 3,
                now, now, .7f, PersonalSuggestionReason.REPEATED_MANUAL_ROUTINE, routine.id.value)
            assertTrue(learning.suggest(suggestion))
            val id = "learning:s"
            assertFalse(requireNotNull(routines.get(routine.id)).enabled)
            assertTrue(runCatching { learning.acceptRoutineReview(id, "other") { error("must not save") } }.isFailure)
            assertEquals("PENDING", db.personalMemoryDao().suggestionById(id)?.state)
            assertTrue(runCatching { learning.acceptRoutineReview(id, routine.id.value) {
                routines.upsert(routine.copy(enabled = true))
                error("save failed")
            } }.isFailure)
            assertFalse(requireNotNull(routines.get(routine.id)).enabled)
            assertEquals("PENDING", db.personalMemoryDao().suggestionById(id)?.state)
            learning.acceptRoutineReview(id, routine.id.value) { routines.upsert(routine.copy(enabled = true)) }
            assertEquals("ACCEPTED", db.personalMemoryDao().suggestionById(id)?.state)
            assertTrue(requireNotNull(routines.get(routine.id)).enabled)
            var repeated = false
            assertTrue(runCatching { learning.acceptRoutineReview(id, routine.id.value) { repeated = true } }.isFailure)
            assertFalse(repeated)
        } finally { db.close() }
    }

    @Test fun dismissalAndRemovedTargetCannotEnable() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val learning = PersonalLearningRepository(db, RoomMemoryRepository(db))
            val routines = RoomRoutineRepository(db.routineDao())
            val routine = RoutineTemplate.BATTERY.buildDraft().routine.copy(enabled = false)
            routines.upsert(routine)
            val now = Instant.now()
            fun suggestion(key: String) = PersonalSuggestion(key, key, PersonalSuggestionKind.AUTOMATION, "Review", "Reason", 3,
                now, now, .7f, PersonalSuggestionReason.REPEATED_MANUAL_ROUTINE, routine.id.value)
            learning.suggest(suggestion("dismissed"))
            learning.dismiss("learning:dismissed")
            var saved = false
            assertTrue(runCatching { learning.acceptRoutineReview("learning:dismissed", routine.id.value) { saved = true } }.isFailure)
            assertEquals("DISMISSED", db.personalMemoryDao().suggestionById("learning:dismissed")?.state)
            assertFalse(requireNotNull(routines.get(routine.id)).enabled)
            learning.suggest(suggestion("removed"))
            routines.delete(routine.id)
            assertTrue(runCatching { learning.acceptRoutineReview("learning:removed", routine.id.value) { saved = true } }.isFailure)
            assertFalse(saved)
        } finally { db.close() }
    }
}
