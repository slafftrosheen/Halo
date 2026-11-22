package com.halo.app.data.toolkit

import androidx.room.withTransaction
import com.halo.app.data.ObjectProvenance
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.everyday.organizer.OrganizerKind
import com.halo.app.everyday.organizer.OrganizerOccurrence
import com.halo.app.everyday.organizer.OrganizerStatus
import com.halo.app.everyday.organizer.PlannerCalendarPreferences
import java.text.Normalizer
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import kotlin.math.*

internal data class HaloMapCoordinate(val latitude: Double, val longitude: Double) {
    init { require(latitude in -90.0..90.0 && longitude in -180.0..180.0) }
    override fun toString(): String = latitude.toString() + ", " + longitude.toString()
}

internal data class HaloSavedPlace(val id: String, val version: String, val title: String, val coordinate: HaloMapCoordinate)
internal data class HaloMapDeparture(
    val eventId: String,
    val eventVersion: Long,
    val title: String,
    val departAt: Instant,
    val destination: String?,
)
internal data class HaloMapArrival(val upcoming: List<OrganizerOccurrence>, val previous: List<OrganizerOccurrence>)

internal object HaloMapsStore {
    private const val source = "halo-maps-place"
    private const val homePlaceKey = "halo_maps_home_place_v1"
    private val number = """[+-]?\d{1,3}(?:[.,]\d{1,10})?"""
    private val coordinates = Regex("""^\s*($number)\s*[,;\s]\s*($number)\s*$""")

    fun coordinate(text: String): HaloMapCoordinate {
        val parts = requireNotNull(coordinates.matchEntire(text.takeIf { it.length <= 48 }.orEmpty())) { "Invalid coordinates" }
        return HaloMapCoordinate(parts.groupValues[1].replace(',', '.').toDouble(), parts.groupValues[2].replace(',', '.').toDouble())
    }

    fun straightLineKm(from: HaloMapCoordinate, to: HaloMapCoordinate): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val lat = Math.toRadians(to.latitude - from.latitude) / 2.0
        val lon = Math.toRadians(to.longitude - from.longitude) / 2.0
        val a = sin(lat).pow(2) + cos(lat1) * cos(lat2) * sin(lon).pow(2)
        return 2 * 6371.0088 * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    fun observePlaces() = Graph.appDatabase.workspaceContextDao().observeLibraryOriginIds(source).combine(
        Graph.notebookRepository.observeNotes(),
    ) { ids, _ -> currentPlaces(ids) }

    suspend fun currentPlaces(): List<HaloSavedPlace> =
        currentPlaces(Graph.appDatabase.workspaceContextDao().observeLibraryOriginIds(source).first())

    private suspend fun currentPlaces(ids: List<String>) = Graph.appDatabase.withTransaction {
        val approval = Graph.kidsPreferences.adultActionRevision()
        val places = ids.asSequence().distinct().take(1000).toList().mapNotNull { place(it, approval) }
        Graph.kidsPreferences.requireAdultAction(approval)
        places.sortedBy { it.title.lowercase(Locale.ROOT) }
    }

    suspend fun readPlace(id: String): HaloSavedPlace? = Graph.appDatabase.withTransaction {
        place(id, Graph.kidsPreferences.adultActionRevision())
    }

    suspend fun homePlace(): HaloSavedPlace? {
        val id = Graph.storage.getString(homePlaceKey) ?: return null
        return readPlace(id)
    }

    suspend fun setHomePlace(expected: HaloSavedPlace, verify: () -> Unit) {
        val family = Graph.kidsPreferences
        val authority = family.adultActionRevision()
        Graph.appDatabase.withTransaction {
            require(place(expected.id, authority) == expected)
            family.requireAdultAction(authority)
            Graph.haloPause.requireActive()
            verify()
            Graph.storage.putString(homePlaceKey, expected.id)
        }
    }

    private suspend fun place(id: String, approval: Long): HaloSavedPlace? {
        Graph.kidsPreferences.requireAdultAction(approval)
        val db = Graph.appDatabase
        val row = db.notebookDao().getWorkspaceObject(id)?.takeIf { it.lifecycle == "ACTIVE" && it.type == "NOTE" } ?: return null
        val provenance = db.workspaceContextDao().provenanceFor(id)?.takeIf { it.sourceId == source } ?: return null
        val point = runCatching {
            val json = JSONObject(provenance.metadata)
            HaloMapCoordinate(json.getDouble("latitude"), json.getDouble("longitude"))
        }.getOrNull() ?: return null
        return HaloSavedPlace(id, row.updatedAt.toString(), row.title, point)
    }

    suspend fun keep(title: String, point: HaloMapCoordinate): HaloSavedPlace {
        val name = title.trim()
        require(name.length in 1..120 && name.none(Char::isISOControl))
        val family = Graph.kidsPreferences
        val approval = family.adultActionRevision()
        val mode = Graph.privacyModes.revision
        val pause = Graph.haloPause
        val pauseVersion = pause.state.value.version
        fun authorize() {
            family.requireAdultAction(approval)
            check(mode == Graph.privacyModes.revision && pauseVersion == pause.state.value.version && !pause.isPaused())
        }
        return Graph.appDatabase.withTransaction {
            authorize()
            // Keep the exact coordinates in app-local provenance so regular note text and cloud
            // context projections expose only the name the person chose to give this Place.
            val note = Graph.notebookRepository.createNote(name, title = name)
            val data = JSONObject().put("latitude", point.latitude).put("longitude", point.longitude)
            Graph.appDatabase.workspaceContextDao().provenance(ObjectProvenance(note.id, source, note.id, data.toString()))
            authorize()
            requireNotNull(place(note.id, approval))
        }
    }

    suspend fun nextDeparture(now: Instant = Instant.now(), eventId: String? = null,
                              occurrenceStart: Instant? = null): HaloMapDeparture? {
        val family = Graph.kidsPreferences
        val approval = family.adultActionRevision()
        val calendar = PlannerCalendarPreferences.read(Graph.storage)
        val rows = Graph.organizerUseCases.agenda(now, now.plus(Duration.ofDays(14)), setOf(OrganizerKind.EVENT))
        family.requireAdultAction(approval)
        Graph.haloPause.requireActive()
        check(PlannerCalendarPreferences.read(Graph.storage) == calendar)
        return rows.asSequence().filter {
            !it.allDay && !it.completed && it.start >= now && it.record.status == OrganizerStatus.ACTIVE &&
                calendar.appearance(it.record.calendar).visible &&
                (eventId == null || it.record.id == eventId && it.originalStart == occurrenceStart)
        }
            .mapNotNull { row ->
                val stored = row.record.leaveBeforeMinutes ?: return@mapNotNull null
                val departure = row.start.minusSeconds(stored * 60L)
                val destination = row.record.location.trim().take(120)
                    .takeIf { it.isNotBlank() && it.none(Char::isISOControl) }
                HaloMapDeparture(row.record.id, row.record.version, row.record.title, departure, destination)
            }.minByOrNull { it.departAt }
    }

    suspend fun arrived(place: HaloSavedPlace, now: Instant = Instant.now()): HaloMapArrival {
        val authority = Graph.kidsPreferences.adultActionRevision()
        val privacyVersion = Graph.privacyModes.revision
        val pause = Graph.haloPause
        val pauseVersion = pause.state.value.version
        val calendar = PlannerCalendarPreferences.read(Graph.storage)
        check(!pause.isPaused() && readPlace(place.id) == place)
        val label = placeLabel(place.title)
        val useLabel = currentPlaces().count { placeLabel(it.title) == label } == 1
        val events = Graph.organizerUseCases.agenda(now.minus(Duration.ofDays(14)), now.plus(Duration.ofDays(14)), setOf(OrganizerKind.EVENT))
            .filter { occurrence ->
                calendar.appearance(occurrence.record.calendar).visible &&
                    occurrence.record.status != OrganizerStatus.CANCELLED && matchesPlace(
                    place, occurrence.record.location, occurrence.record.links.map { it.objectId }, useLabel,
                )
            }
        Graph.kidsPreferences.requireAdultAction(authority)
        check(privacyVersion == Graph.privacyModes.revision && pauseVersion == pause.state.value.version &&
            !pause.isPaused() && PlannerCalendarPreferences.read(Graph.storage) == calendar && readPlace(place.id) == place)
        return HaloMapArrival(
            events.filter { !it.completed && it.record.status == OrganizerStatus.ACTIVE &&
                (it.end?.isAfter(now) == true || it.start >= now) }.take(8),
            events.filter { (it.end ?: it.start) <= now }.takeLast(8).asReversed(),
        )
    }

    fun matchesPlace(place: HaloSavedPlace, location: String, linkedIds: List<String>, useLabel: Boolean): Boolean {
        if (place.id in linkedIds) return true
        val coordinate = runCatching { coordinate(location) }.getOrNull()
        if (coordinate != null) return straightLineKm(place.coordinate, coordinate) <= .05
        val normalized = placeLabel(location)
        return useLabel && normalized.isNotEmpty() && normalized == placeLabel(place.title)
    }

    private fun placeLabel(name: String) = Normalizer.normalize(name.trim(), Normalizer.Form.NFC)
        .replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
}
