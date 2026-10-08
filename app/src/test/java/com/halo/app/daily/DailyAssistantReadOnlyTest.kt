package com.halo.app.daily

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.routine.*
import com.halo.app.ui.screens.notifications.*
import com.halo.sdk.runtime.embodiment.InMemoryDeviceRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class DailyAssistantReadOnlyTest {
    @Test fun malformedRoutineObservationDoesNotDisableOrRewriteStoredSchedule() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        try {
            val row = RoutineEntity("bad", "Bad", true, "invalid json", "[]", "[]", createdAt = 1,
                updatedAt = 2, lastRunAt = 10, nextRunAt = 20)
            db.routineDao().upsert(row)
            val repository = RoomRoutineRepository(db.routineDao())
            repeat(3) { assertTrue(repository.observeRoutinesReadOnly().first().isEmpty()) }
            assertEquals(row, db.routineDao().get("bad"))
            assertTrue(repository.migrationErrors.value.isEmpty())
        } finally { db.close() }
    }

    @Test fun controllerCollectRefreshAndSheetSnapshotUseCountsWithoutChangingSources() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val now = Instant.parse("2026-10-08T09:00:00Z")
        val privateItem = NotificationInboxItem("key", "package", "PRIVATE APP", "PRIVATE TITLE", "PRIVATE BODY", "Messages", now,
            true, true, ReplyCapability.Available("Reply", "fingerprint"))
        val inbox = MutableStateFlow(NotificationInboxState(listOf(privateItem), true))
        val pendingLearning = MutableStateFlow(2)
        var allowed = false
        val logs = mutableListOf<String>()
        try {
            val controller = DailyAssistantController(flowOf(emptyList()), flowOf(emptyList()), inbox,
                flowOf(emptyList()), flowOf(emptyList()), flowOf(emptyList()), flowOf(emptyList()), pendingLearning,
                flowOf(emptyMap()), flowOf(null), InMemoryDeviceRegistry(), { allowed }, scope, { now }, { synchronized(logs) { logs += it } })
            val first = withTimeout(5000) { controller.snapshot.first { it.counts.notifications == 1 } }
            assertEquals(0, first.counts.learningSuggestions)
            assertFalse(first.toString().contains("PRIVATE"))
            allowed = true
            controller.refresh()
            val refreshed = withTimeout(5000) { controller.snapshot.first { it.counts.learningSuggestions == 2 } }
            controller.recordAction(DailyAssistantAction.OpenInbox)
            assertEquals(listOf(privateItem), inbox.value.items)
            assertEquals(2, pendingLearning.value)
            assertEquals(1, refreshed.counts.replyableNotifications)
            assertFalse(synchronized(logs) { logs.joinToString() }.contains("PRIVATE"))
            assertFalse(synchronized(logs) { logs.joinToString() }.contains("fingerprint"))
        } finally { scope.cancel() }
    }
}
