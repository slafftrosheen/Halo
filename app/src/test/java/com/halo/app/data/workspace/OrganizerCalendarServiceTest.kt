package com.halo.app.data.workspace

import com.halo.app.data.everyday.*
import com.halo.app.everyday.organizer.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=android.app.Application::class)
class OrganizerCalendarServiceTest {
    @Test fun reviewedSubsetDoesNotExecuteEmbeddedContentOrImportUnselectedEventsAndRejectsChangedBytes()=runBlocking {
        WorkspaceFileToolsFixture().use {f ->
            val clock=Clock.fixed(Instant.parse("2026-11-03T12:00:00Z"),ZoneOffset.UTC)
            val repository=RoomOrganizerRepository(f.database,clock,{ZoneOffset.UTC})
            val organizer=OrganizerUseCases(repository,clock,{ZoneOffset.UTC},{1000},{"boot"})
            val service=OrganizerCalendarService(f.database,organizer,f.files,f.workspace,f.notes,f.context.contentResolver,clock)
            val note=f.notes.createNote(title="Source")
            val raw=("BEGIN:VCALENDAR\r\n"+(1..2).joinToString("") {n ->
                "BEGIN:VEVENT\r\nUID:$n\r\nDTSTART:20261104T090000Z\r\nDTEND:20261104T100000Z\r\nSUMMARY:Event $n\r\nBEGIN:VALARM\r\nACTION:DISPLAY\r\nEND:VALARM\r\nEND:VEVENT\r\n"
            }+"END:VCALENDAR\r\n").toByteArray()
            val file=f.files.createManagedFile("events.ics","text/calendar",raw,note.id,note.revision.toString(),"{}")
            val source=f.reference(file)
            val review=service.preview(source,ZoneOffset.UTC)
            assertEquals(2,review.preview.records.size)
            assertTrue(review.preview.issues.all {it.warning})
            val selected=review.preview.records.first().id
            assertEquals(listOf(selected),service.import(source,ZoneOffset.UTC,review.token,listOf(selected)).added)
            assertEquals(listOf(selected),repository.records().map {it.id})
            assertTrue(repository.receipts(selected).isEmpty())
            assertTrue(runCatching {service.import(source,ZoneOffset.UTC,review.token,listOf(review.preview.records.last().id))}.exceptionOrNull() is OrganizerConflict)
            val next=service.preview(source,ZoneOffset.UTC)
            val path=java.io.File(f.database.workspaceFileDao().getFile(file.objectId.value)!!.ownedCopyPath!!)
            path.writeBytes(raw+"changed".toByteArray())
            assertTrue(runCatching {service.import(source,ZoneOffset.UTC,next.token,listOf(review.preview.records.last().id))}.isFailure)
            assertEquals(1,repository.records().size)
        }
    }
    @Test fun localOwnershipLifecycleAndRecurrenceExportUseExactCanonicalOwners()=runBlocking {
        WorkspaceFileToolsFixture().use {f ->
            val clock=Clock.fixed(Instant.parse("2026-11-03T12:00:00Z"),ZoneOffset.UTC)
            val repository=RoomOrganizerRepository(f.database,clock,{ZoneOffset.UTC})
            val organizer=OrganizerUseCases(repository,clock,{ZoneOffset.UTC},{1000},{"boot"})
            val service=OrganizerCalendarService(f.database,organizer,f.files,f.workspace,f.notes,f.context.contentResolver,clock)
            val event=organizer.create(OrganizerKind.EVENT,"Recurring",time=OrganizerTime(LocalDateTime.of(2026,11,4,9,0),LocalDateTime.of(2026,11,4,10,0),"UTC"),
                repeat=RepeatRule(RepeatFrequency.DAILY,count=3))
            val original=event.time!!.instant(ZoneOffset.UTC).plusSeconds(86400)
            val changed=repository.saveOverride(OccurrenceOverride(event.id,original,OccurrenceDisposition.CANCELLED),event.version)
            assertTrue(runCatching {service.prepareExport(mapOf(event.id to event.version))}.exceptionOrNull() is OrganizerConflict)
            val file=service.prepareExport(mapOf(event.id to changed.version))
            val parsed=OrganizerIcs.preview(f.bytes(file),"round-trip",ZoneOffset.UTC,clock.instant())
            assertEquals(original,parsed.exceptions.single().originalStart)
            assertEquals(OccurrenceDisposition.CANCELLED,parsed.exceptions.single().disposition)
            val source=f.reference(file)
            f.workspace.trash(file.objectId)
            assertTrue(runCatching {service.preview(source,ZoneOffset.UTC)}.isFailure)
            f.workspace.restore(file.objectId)
            val owned=f.database.workspaceFileDao().getFile(file.objectId.value)!!
            f.database.workspaceFileDao().insertFile(owned.copy(ownership=ContentOwnership.EXTERNAL_REFERENCE.name,availability=WorkspaceFileAvailability.AVAILABLE.name))
            org.robolectric.Shadows.shadowOf(f.context.contentResolver).registerInputStreamSupplier(file.uri) {error("Unowned content must not be opened")}
            assertTrue(runCatching {service.preview(source,ZoneOffset.UTC)}.exceptionOrNull() is IllegalArgumentException)
            assertEquals(1,repository.records().size)
        }
    }
}
