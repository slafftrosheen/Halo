package com.halo.app.automation

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.routine.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class RoutineHistoryPersistenceTest {
    @Test fun successSurvivesFailuresAndReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Keep the complete legacy SQLite path below Windows MAX_PATH.
        val name = "h-${java.util.UUID.randomUUID().toString().take(8)}.db"
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name).setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE).build()
        var db = open()
        try {
            val dao = db.routineDao()
            dao.insertRun(RoutineRunEntity("success", "routine", "agent-success", 1, "COMPLETED",
                reason = "The previous useful result", actionsCompleted = 1, actionsTotal = 1))
            repeat(120) { i -> dao.insertRun(RoutineRunEntity("failure-$i", "routine", "agent-$i", i + 2L, "FAILED")) }
            db.close()
            db = open()
            val history = RoomRoutineRepository(db.routineDao()).observeRecentRuns().first()
            assertEquals("failure-119", history.first().id)
            assertEquals("The previous useful result", history.single { it.id == "success" }.reason)
            assertEquals(101, history.size)
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun newestSuccessIsDeterministicAndNeverCrossesRoutineOrTrialIdentity() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.routineDao()
            listOf(
                RoutineRunEntity("old", "one", "agent", 1, "COMPLETED"),
                RoutineRunEntity("success-a", "one", "agent", 2, "COMPLETED"),
                RoutineRunEntity("success-b", "one", "agent", 2, "COMPLETED"),
                RoutineRunEntity("other", "two", "agent", 1, "COMPLETED"),
                RoutineRunEntity("trial", "one", "agent", 3, "COMPLETED", source = "TRIAL"),
            ).forEach { dao.insertRun(it) }
            repeat(120) { i -> dao.insertRun(RoutineRunEntity("fail-$i", "one", "agent", i + 10L, "FAILED")) }
            val ids = dao.observeRecentRuns().first().map { it.id }
            assertEquals(102, ids.size)
            assertTrue("success-b" in ids)
            assertTrue("other" in ids)
            assertFalse("success-a" in ids)
            assertFalse("old" in ids)
            assertFalse("trial" in ids)
        } finally { db.close() }
    }
}
