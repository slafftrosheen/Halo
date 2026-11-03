package com.halo.app.data.everyday

import android.content.ContentResolver
import androidx.room.withTransaction
import com.halo.app.data.AppDatabase
import com.halo.app.data.notebook.*
import com.halo.app.data.workspace.*
import com.halo.app.everyday.EverydayWriteAuthority
import com.halo.app.everyday.organizer.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.time.*

data class OrganizerCalendarReview(val preview: CalendarImportPreview,val token: String)

/** Bounded interchange over existing Organizer/Workspace owners. No URI supplied by a model is opened. */
class OrganizerCalendarService(private val database: AppDatabase,private val organizer: OrganizerUseCases,
    private val files: RoomWorkspaceFileRepository,private val workspace: WorkspaceRepository,
    private val notes: NotebookRepository,private val resolver: ContentResolver,private val clock: Clock=Clock.systemUTC()) {
    private val versions=WorkspaceFileVersions(files,workspace)
    private fun digest(bytes: ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it)}

    /** Only an already local, immutable, version-bound Workspace copy is eligible. SAF grants stay human-owned. */
    private suspend fun bytes(source: WorkspaceFileVersion): ByteArray {
        val file=versions.current(source)
        require(file.ownership==ContentOwnership.HALO_OWNED && file.availability==WorkspaceFileAvailability.OWNED_LOCAL && file.sha256==source.version) {
            "Choose an already imported local calendar copy in Workspace"
        }
        require(file.mimeType in setOf("text/calendar","application/ics") || file.displayName.endsWith(".ics",true))
        require(file.sizeBytes?.let {it in 1..MAX_BYTES.toLong()}==true)
        val bytes=requireNotNull(resolver.openInputStream(file.uri)).use {input ->
            val output=ByteArrayOutputStream();val buffer=ByteArray(8192)
            while(true) {
                currentCoroutineContext().ensureActive()
                val size=input.read(buffer);if(size<0) break
                require(output.size()+size<=MAX_BYTES);output.write(buffer,0,size)
            }
            output.toByteArray()
        }
        require(digest(bytes)==source.version) {"Calendar bytes changed"}
        versions.current(source)
        return bytes
    }

    suspend fun preview(source: WorkspaceFileVersion,zone: ZoneId): OrganizerCalendarReview = database.withTransaction {
        val bytes=bytes(source)
        val preview=OrganizerIcs.preview(bytes,"halo:manual-import",zone,clock.instant())
        val candidates=preview.records.map {it.id}.toSet()
        val existing=organizer.repository.records(true).filter {it.id in candidates}.sortedBy {it.id}
            .joinToString("\n") {"${it.id}:${it.version}:${it.deletedAt}"}
        // A deterministic review token binds source bytes, zone, importer version and current add-only identities.
        val token=digest("calendar-review-v1\u0000${source.objectId}\u0000${source.version}\u0000${zone.id}\u0000$existing".toByteArray())
        OrganizerCalendarReview(preview,token)
    }

    suspend fun import(source: WorkspaceFileVersion,zone: ZoneId,token: String,recordIds: List<String>): CalendarImportResult = database.withTransaction {
        require(recordIds.isNotEmpty() && recordIds.size<=50 && recordIds.distinct()==recordIds)
        val current=preview(source,zone)
        if(current.token!=token) throw OrganizerConflict("Calendar review changed; preview again")
        val selected=current.preview.records.filter {it.id in recordIds}
        require(selected.size==recordIds.size) {"Choose supported event IDs from the reviewed calendar"}
        EverydayWriteAuthority.requireCurrent()
        organizer.repository.importCalendar(current.preview.copy(records=selected,
            exceptions=current.preview.exceptions.filter {it.recordId in recordIds}))
    }

    suspend fun prepareExport(expected: Map<String,Long>): WorkspaceFile = withContext(Dispatchers.IO) {
        require(expected.isNotEmpty() && expected.size<=2000)
        val rows=expected.map { (id,version) -> organizer.requireCurrent(id,version).also {require(it.kind==OrganizerKind.EVENT)} }
        val exceptions=rows.flatMap {organizer.repository.overrides(it.id)}
        val bytes=OrganizerIcs.export(rows,exceptions,clock.instant())
        EverydayWriteAuthority.requireCurrent()
        files.createManagedFileWithPreparedSource("Halo-calendar.ics","text/calendar",bytes,
            metadata=JSONObject().put("kind","organizer-calendar-export").put("records",JSONObject(expected)).toString(),
            validateSource={
                EverydayWriteAuthority.requireCurrent()
                expected.forEach { (id,version) -> organizer.requireCurrent(id,version) }
            },prepareSource={
                EverydayWriteAuthority.requireCurrent()
                notes.createNote(title="iCalendar · ${LocalDate.now(clock)}",source=WorkspaceSource.USER_CREATED,
                    body=rows.joinToString("\n") {"${it.title} · ${it.id} · ${it.version}"}).id
            })
    }

    companion object { const val MAX_BYTES=2*1024*1024 }
}
