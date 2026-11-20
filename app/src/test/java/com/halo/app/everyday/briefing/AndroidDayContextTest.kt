package com.halo.app.everyday.briefing

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.everyday.RoomCollectorRepository
import com.halo.app.data.everyday.RoomOrganizerRepository
import com.halo.app.data.notebook.NotebookDocumentRepository
import com.halo.app.data.notebook.RoomNotebookRepository
import com.halo.app.data.notification.*
import com.halo.app.everyday.collectors.*
import com.halo.app.everyday.organizer.OrganizerUseCases
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AndroidDayContextTest {
    @Test fun canonicalBodiesAndPermittedNotificationsAreAvailableAndRevocationInvalidatesPages() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val now = Instant.now()
            val sources = RoomCollectorRepository(db)
            for (kind in listOf(CollectorKind.LOCAL_WORKSPACE, CollectorKind.LOCAL_NOTIFICATIONS))
                sources.save(CollectorSource(kind.name, kind.name, "QA", kind, SourceCategory.PERSONAL, "en", selected = true), null)
            val notes = RoomNotebookRepository(db.notebookDao(), documents = NotebookDocumentRepository(db.notebookDocumentDao(), db))
            val full = "Saved note detail. ".repeat(1300) + "FINAL DETAIL"
            val note = notes.createNote(full, title = "QA full source")
            val policies = NotificationPolicyManager(app)
            policies.setPolicyForApp("qa.allowed", NotificationAccessPolicy.ALLOW)
            policies.setPolicyForApp("qa.hidden", NotificationAccessPolicy.HIDE_CONTENT)
            policies.setPolicyForApp("qa.blocked", NotificationAccessPolicy.BLOCK)
            val notifications = RoomNotificationRepository(db.notificationDao(), policies, "qa.halo")
            for (pkg in listOf("qa.allowed", "qa.hidden", "qa.blocked")) notifications.ingest(IncomingNotification(
                pkg, pkg, "QA", "Headline $pkg", "Notification detail $pkg", null, now, publicContent = true))
            var access = true
            val reader = AndroidDayContext(db, sources, OrganizerUseCases(RoomOrganizerRepository(db), elapsedMs = { 0L }, bootId = { "qa" }),
                notifications, { access }, { policies.changes.value }).reader
            val args = mapOf<String, Any>("from" to now.minusSeconds(3600).toString(), "until" to now.plusSeconds(3600).toString(), "zoneId" to "Europe/Riga")
            val version = reader.read(args).getString("sourceVersion")
            val notices = reader.read(args + mapOf("section" to "NOTIFICATIONS", "sourceVersion" to version))
            assertEquals(1, notices.getJSONArray("items").length())
            val notice = reader.read(args + mapOf("section" to "NOTIFICATIONS", "sourceVersion" to version, "itemId" to "qa.allowed"))
            assertTrue(notice.toString().contains("Notification detail qa.allowed"))
            assertFalse(notice.toString().contains("qa.hidden"))
            val text = StringBuilder(); var offset = 0
            do {
                val page = reader.read(args + mapOf("section" to "WORKSPACE", "sourceVersion" to version, "itemId" to note.id, "textOffset" to offset))
                    .getJSONArray("items").getJSONObject(0)
                text.append(page.getString("text"))
                offset = if (page.isNull("nextTextOffset")) -1 else page.getInt("nextTextOffset")
            } while (offset >= 0)
            assertEquals(full, text.toString())
            val header = requireNotNull(db.notebookDao().getWorkspaceObject(note.id))
            assertTrue(notes.saveNote(note.id, "Revised canonical text"))
            db.notebookDao().insertWorkspaceObject(header) // Same timestamp still has a different note revision.
            assertNotEquals(version, reader.read(args).getString("sourceVersion"))
            val current = reader.read(args).getString("sourceVersion")
            access = false
            assertTrue(runCatching { reader.read(args + mapOf("section" to "NOTIFICATIONS", "sourceVersion" to current)) }.exceptionOrNull() is CollectorConflict)
            val latest = reader.read(args).getString("sourceVersion")
            assertEquals(0, reader.read(args + mapOf("section" to "NOTIFICATIONS", "sourceVersion" to latest)).getJSONArray("items").length())
        } finally { db.close() }
    }
}
