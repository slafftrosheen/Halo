package com.halo.app.ui.screens.notebook

import androidx.annotation.StringRes
import com.halo.app.R
import com.halo.app.data.LibraryCaptureOrigin
import com.halo.app.data.notebook.WorkspaceObject
import com.halo.app.data.notebook.WorkspaceObjectSource
import com.halo.app.data.notebook.WorkspaceObjectType
import com.halo.app.data.study.LibraryPracticeCard
import com.halo.app.data.workspace.ContentOwnership
import com.halo.app.data.workspace.WorkspaceFile
import com.halo.app.capture.glassesPhotoOrigins
import com.halo.app.ui.toolkit.AlbumNamingFacts
import java.time.LocalDate
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/** A presentation kind for an existing owner, independent of its persistence format. */
enum class HaloLibraryKind(@StringRes val label: Int, @StringRes val group: Int) {
    NOTE(R.string.halo11_library_note, R.string.halo11_library_notes),
    PHOTO(R.string.halo11_library_photo, R.string.halo11_library_photos),
    FILE(R.string.halo11_library_file, R.string.halo11_library_files),
    SCAN(R.string.halo11_library_scan, R.string.halo11_library_scans),
    CARD(R.string.halo11_library_card, R.string.halo11_library_cards),
    PLACE(R.string.halo11_library_place, R.string.halo11_library_places),
    RECORDING(R.string.halo11_library_recording, R.string.halo11_library_recordings),
    PLAN(R.string.halo11_library_plan, R.string.halo11_library_plans),
    PERSON(R.string.halo11_library_person, R.string.halo11_library_people),
}

data class LibraryPersonReference(val id: String, val name: String, val photoId: String, val photoVersion: String)
data class LibraryNameFacts(val date: LocalDate?, val city: String?)

internal const val LibraryPlaceOrigin = "halo-maps-place"

data class HaloLibrarySources(
    val files: Map<String, WorkspaceFile> = emptyMap(),
    val places: Set<String> = emptySet(),
    val scans: Set<String> = emptySet(),
    val photographs: Set<String> = emptySet(),
    val glassesPhotos: Set<String> = emptySet(),
    val cards: List<LibraryPracticeCard> = emptyList(),
    val people: List<LibraryPersonReference> = emptyList(),
    val nameFacts: Map<String, LibraryNameFacts> = emptyMap(),
) {
    fun kind(item: WorkspaceObject): HaloLibraryKind? {
        val id = item.id.value
        if (id in places && item.type == WorkspaceObjectType.NOTE) return HaloLibraryKind.PLACE
        if (id in scans && item.type == WorkspaceObjectType.FILE) return HaloLibraryKind.SCAN
        if (id in photographs && item.source == WorkspaceObjectSource.DEVICE_CAPTURE) return HaloLibraryKind.PHOTO
        val file = files[id]
        if (file != null) return when {
            file.mimeType.startsWith("image/") -> HaloLibraryKind.PHOTO
            file.mimeType.startsWith("audio/") -> HaloLibraryKind.RECORDING
            else -> HaloLibraryKind.FILE
        }
        return when (item.type) {
            WorkspaceObjectType.NOTE -> HaloLibraryKind.NOTE
            WorkspaceObjectType.FILE, WorkspaceObjectType.MEDIA, WorkspaceObjectType.CANVAS, WorkspaceObjectType.ARTIFACT ->
                HaloLibraryKind.FILE
            WorkspaceObjectType.MEMORY, WorkspaceObjectType.CONVERSATION -> null
        }
    }
}

internal fun observeHaloLibrarySources(
    files: Flow<List<WorkspaceFile>>,
    places: Flow<List<String>>,
    scans: Flow<List<String>>,
    captures: Flow<List<LibraryCaptureOrigin>>,
    cards: Flow<List<LibraryPracticeCard>>,
    people: Flow<List<LibraryPersonReference>>,
    albumFacts: Flow<List<AlbumNamingFacts>>,
    glassesDeviceIds: suspend () -> Set<String>,
): Flow<HaloLibrarySources> = combine(files, combine(places, scans, ::Pair), captures, cards, combine(people, albumFacts, ::Pair)) {
        savedFiles, origins, photos, studyCards, album ->
    val byId = savedFiles.associateBy { it.objectId.value }
    val wearablePhotos = if (photos.any { it.sourceId != "phone" })
        glassesPhotoOrigins(photos, glassesDeviceIds()) else emptyList()
    HaloLibrarySources(files = byId, places = origins.first.toSet(), scans = origins.second.toSet(),
        photographs = photos.mapTo(hashSetOf(), LibraryCaptureOrigin::objectId),
        glassesPhotos = wearablePhotos.mapTo(hashSetOf(), LibraryCaptureOrigin::objectId), cards = studyCards,
        people = album.first.filter { person -> byId[person.photoId]?.let { photo ->
            photo.ownership == ContentOwnership.HALO_OWNED && photo.mimeType.startsWith("image/") && photo.version == person.photoVersion
        } == true }, nameFacts = album.second.filter { byId[it.id]?.version == it.version }
            .associate { it.id to LibraryNameFacts(it.date, it.city) })
}

internal fun NotebookFilter.libraryKind(): HaloLibraryKind? = when (this) {
    NotebookFilter.NOTES -> HaloLibraryKind.NOTE
    NotebookFilter.PHOTOS, NotebookFilter.GLASSES_PHOTOS -> HaloLibraryKind.PHOTO
    NotebookFilter.FILES -> HaloLibraryKind.FILE
    NotebookFilter.SCANS -> HaloLibraryKind.SCAN
    NotebookFilter.CARDS -> HaloLibraryKind.CARD
    NotebookFilter.PLACES -> HaloLibraryKind.PLACE
    NotebookFilter.RECORDINGS -> HaloLibraryKind.RECORDING
    NotebookFilter.ORGANIZER -> HaloLibraryKind.PLAN
    NotebookFilter.PEOPLE -> HaloLibraryKind.PERSON
    else -> null
}

private val libraryWordBoundary = Regex("\\s+")
private val previousReceipt = Regex(
    """^(?:(?:find|show me)\s+)?(?:last month['’]s\s+(receipts?)|(receipts?)\s+(?:from\s+)?last month)[?.!]*$""",
    RegexOption.IGNORE_CASE,
)
private val previousReceiptRu = Regex("""^(?:(?:найди|покажи)\s+)?(чеки?)\s+за\s+прошлый\s+месяц[?.!]*$""", RegexOption.IGNORE_CASE)

internal data class LibrarySearchPrompt(val terms: String, val monthSaved: YearMonth? = null) {
    fun includes(saved: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        monthSaved == null || YearMonth.from(Instant.ofEpochMilli(saved).atZone(zone)) == monthSaved
}

internal fun parseLibrarySearchPrompt(text: String, today: LocalDate = LocalDate.now()): LibrarySearchPrompt {
    val lookup = text.trim()
    val receipt = previousReceipt.matchEntire(lookup)?.groupValues?.drop(1)?.firstOrNull(String::isNotBlank)
        ?: previousReceiptRu.matchEntire(lookup)?.groupValues?.getOrNull(1)
    return if (receipt == null) LibrarySearchPrompt(lookup)
    else LibrarySearchPrompt(if (receipt.startsWith("ч", true)) "чек" else "receipt", YearMonth.from(today).minusMonths(1))
}

internal fun matchesLibraryTerms(query: String, title: String): Boolean =
    query.trim().split(libraryWordBoundary).all { title.contains(it, ignoreCase = true) }

internal fun NotebookUiState.groupsLibrarySearch(): Boolean = resolvedQuery.isNotBlank() && filter == NotebookFilter.ALL

internal fun NotebookUiState.visibleLibraryObjects(): List<NotebookWorkspaceItem> {
    if (filter == NotebookFilter.MEMORY) return emptyList()
    val rows = items.filterNot { it.objectRef.id.value.startsWith("learning:") }
    return if (groupsLibrarySearch()) rows.sortedBy { it.libraryKind?.ordinal ?: Int.MAX_VALUE } else rows
}

internal fun grownUpLibrarySources(kids: Flow<Boolean>, authorityLoaded: Flow<Boolean>,
    sources: () -> Flow<HaloLibrarySources>): Flow<HaloLibrarySources> =
    grownUpLibraryValues(kids, authorityLoaded, HaloLibrarySources(), sources)

@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> grownUpLibraryValues(kids: Flow<Boolean>, authorityLoaded: Flow<Boolean>,
    unavailable: T, sources: () -> Flow<T>): Flow<T> =
    combine(kids, authorityLoaded) { child, loaded -> loaded && !child }
        .flatMapLatest { allowed -> if (allowed) sources() else flowOf(unavailable) }
