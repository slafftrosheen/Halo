package com.halo.app.ui.toolkit

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.halo.app.data.workspace.ContentOwnership
import com.halo.app.data.workspace.WorkspaceFile
import com.halo.app.di.ManualDependencyGraph as Graph
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal data class HaloAlbumPhoto(
    val id: String,
    val name: String,
    val date: LocalDate?,
    val words: String,
    val tags: String,
    val uri: Uri,
    val version: String?,
    val nearbyCity: String = "",
    val placeSearch: String = "",
    val suggestedPeople: List<String> = emptyList(),
)

private const val albumLimit = 120
private const val peopleLimit = 20
private val saveIndexLock = Mutex()
internal class AlbumReferenceFaceUnavailable : IllegalArgumentException()
private data class AlbumLocation(val city: String, val search: String)
private data class AlbumPhotoDetails(val date: LocalDate?, val words: String, val location: AlbumLocation?)

internal class HaloAlbumIndex(private val context: Context) {
    private val storage = context.getSharedPreferences("halo_album_v1", Context.MODE_PRIVATE)

    suspend fun photos(): List<HaloAlbumPhoto> = withContext(Dispatchers.IO) {
        val data = readRows()
        val people = readPeople()
        val available = (0 until data.length()).mapNotNull { index ->
            val row = data.optJSONObject(index) ?: return@mapNotNull null
            val file = Graph.workspaceUseCases.getFile(row.optString("id")) ?: return@mapNotNull null
            if (file.ownership != ContentOwnership.HALO_OWNED || !file.mimeType.startsWith("image/")) null else row to file
        }
        val versions = available.associate { (_, file) -> file.objectId.value to file.version }
        val peopleById = people.filter { versions[it.referenceId] == it.referenceVersion }.associateBy(AlbumPerson::id)
        available.map { (row, file) ->
            val version = file.version
            val photo = HaloAlbumPhoto(
                file.objectId.value, file.displayName, runCatching { LocalDate.parse(row.optString("date")) }.getOrNull(),
                row.optString("words"), row.optString("tags"), file.uri, version, row.optString("city"), row.optString("placeSearch"),
            )
            val derivedMatches = row.optJSONArray("people")
                .takeIf { row.optString("peopleVersion") == version.orEmpty() }
            val candidates = derivedMatches?.let { values ->
                (0 until values.length()).mapNotNull { peopleById[values.optString(it)]?.name }
            }.orEmpty()
            val explicit = peopleById.values.filter { it.referenceId == photo.id }.map(AlbumPerson::name)
            photo.copy(suggestedPeople = (explicit + candidates).distinct())
        }
    }

    suspend fun add(uri: Uri, adultRevision: Long) {
        Graph.kidsPreferences.requireAdultAction(adultRevision)
        check(readRows().length() < albumLimit) { "Album is full" }
        val file = Graph.workspaceUseCases.importForCanvas(uri)
        check(file.mimeType.startsWith("image/"))
        val details = localDetails(file)
        withContext(Dispatchers.IO) {
            saveIndexLock.withLock {
                Graph.kidsPreferences.requireAdultAction(adultRevision)
                val data = readRows()
                check(data.length() < albumLimit)
                val existing = (0 until data.length()).any { data.optJSONObject(it)?.optString("id") == file.objectId.value }
                if (!existing) data.put(JSONObject().put("id", file.objectId.value)
                    .put("date", details.date?.toString().orEmpty()).put("words", details.words)
                    .put("city", details.location?.city.orEmpty()).put("placeSearch", details.location?.search.orEmpty())
                    .put("placeIndexed", true))
                save(data)
            }
        }
    }

    suspend fun putTags(id: String, value: String, adultRevision: Long) = withContext(Dispatchers.IO) {
        saveIndexLock.withLock {
            Graph.kidsPreferences.requireAdultAction(adultRevision)
            val data = readRows()
            val row = (0 until data.length()).mapNotNull(data::optJSONObject).single { it.optString("id") == id }
            row.put("tags", value.trim().take(500))
            save(data)
        }
    }

    suspend fun people(): List<AlbumPerson> = withContext(Dispatchers.IO) { readPeople() }

    suspend fun refreshPlaces(adultRevision: Long) = withContext(Dispatchers.IO) {
        val snapshot = readRows()
        val need = (0 until snapshot.length()).mapNotNull { snapshot.optJSONObject(it) }
            .filterNot { it.optBoolean("placeIndexed") }.map { it.getString("id") }
        if (need.isEmpty()) return@withContext
        val locations = need.associateWith { id ->
            ensureActive()
            Graph.kidsPreferences.requireAdultAction(adultRevision)
            Graph.workspaceUseCases.getFile(id)?.let { location(readAlbumExif(context, it.uri)) }
        }
        saveIndexLock.withLock {
            Graph.kidsPreferences.requireAdultAction(adultRevision)
            val rows = readRows()
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                val id = row.getString("id")
                if (id in locations && !row.optBoolean("placeIndexed")) {
                    row.put("city", locations[id]?.city.orEmpty()).put("placeSearch", locations[id]?.search.orEmpty())
                        .put("placeIndexed", true)
                }
            }
            save(rows)
        }
    }

    suspend fun enroll(photo: HaloAlbumPhoto, name: String, adultRevision: Long, progress: (Int, Int) -> Unit) {
        require(name.isNotBlank() && name.length <= 60 && name.none(Char::isISOControl))
        val previous = people()
        val existing = previous.firstOrNull { normalizeAlbumText(it.name) == normalizeAlbumText(name) }
        require(existing != null || previous.size < peopleLimit)
        val person = AlbumPerson(existing?.id ?: UUID.randomUUID().toString(), name, photo.id,
            photo.version ?: throw AlbumReferenceFaceUnavailable())
        val next = previous.filterNot { it.id == person.id } + person
        require(next.count { it.referenceId == photo.id } == 1)
        calculatePeople(previous, next, reference = photo, adultRevision, progress)
    }

    suspend fun refreshPeople(adultRevision: Long, progress: (Int, Int) -> Unit) {
        val previous = people()
        if (previous.isNotEmpty()) calculatePeople(previous, previous, reference = null, adultRevision, progress)
    }

    suspend fun removePerson(id: String, adultRevision: Long) = withContext(Dispatchers.IO) {
        saveIndexLock.withLock {
            Graph.kidsPreferences.requireAdultAction(adultRevision)
            val keep = readPeople().filterNot { it.id == id }
            val rows = readRows()
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                val matches = row.optJSONArray("people") ?: continue
                row.put("people", JSONArray((0 until matches.length()).map(matches::getString).filterNot { it == id }))
            }
            save(rows, keep)
        }
    }

    private suspend fun calculatePeople(
        expected: List<AlbumPerson>, wanted: List<AlbumPerson>, reference: HaloAlbumPhoto?,
        adultRevision: Long, progress: (Int, Int) -> Unit,
    ) {
        Graph.kidsPreferences.requireAdultAction(adultRevision)
        val images = photos()
        if (reference != null && images.none { it.id == reference.id && it.version == reference.version && it.uri == reference.uri })
            throw AlbumReferenceFaceUnavailable()
        val photosById = images.associateBy(HaloAlbumPhoto::id)
        val descriptors = mutableMapOf<String, List<FloatArray>>()
        for (person in wanted) {
            Graph.kidsPreferences.requireAdultAction(adultRevision)
            val image = photosById[person.referenceId]?.takeIf { it.version == person.referenceVersion } ?: continue
            val faces = HaloAlbumFaceMatcher.descriptors(context, image.uri)
            if (image.id == reference?.id && faces.size != 1) throw AlbumReferenceFaceUnavailable()
            descriptors[image.id] = faces
        }
        val enrolled = wanted.mapNotNull { person -> descriptors[person.referenceId]?.singleOrNull()?.let { person to it } }.toMap()
        val evaluated = mutableMapOf<String, Pair<String?, Set<String>>>()
        images.forEachIndexed { index, photo ->
            Graph.kidsPreferences.requireAdultAction(adultRevision)
            val faces = if (enrolled.isEmpty()) emptyList() else descriptors[photo.id]
                ?: albumTry { HaloAlbumFaceMatcher.descriptors(context, photo.uri) }.orEmpty()
            evaluated[photo.id] = photo.version to HaloAlbumFaceMatcher.match(faces, enrolled)
            progress(index + 1, images.size)
        }
        withContext(Dispatchers.IO) {
            saveIndexLock.withLock {
                Graph.kidsPreferences.requireAdultAction(adultRevision)
                check(readPeople() == expected) { "Album people changed during matching" }
                val rows = readRows()
                for (index in 0 until rows.length()) {
                    val row = rows.getJSONObject(index)
                    val id = row.getString("id")
                    val (version, matches) = evaluated[id] ?: continue
                    val currentFile = Graph.workspaceUseCases.getFile(id)
                    if (currentFile != null && currentFile.version == version) {
                        row.put("peopleVersion", version.orEmpty()).put("people", JSONArray(matches.toList()))
                    }
                }
                save(rows, wanted)
            }
        }
    }

    private fun readRows() = JSONArray(storage.getString("photos", null).orEmpty().ifEmpty { "[]" })
    private fun readPeople(): List<AlbumPerson> {
        val rows = JSONArray(storage.getString("people", "[]"))
        return (0 until rows.length().coerceAtMost(peopleLimit)).map { index ->
            val item = rows.getJSONObject(index)
            AlbumPerson(item.getString("id"), item.getString("name"), item.getString("referenceId"), item.getString("referenceVersion"))
        }
    }
    private fun save(rows: JSONArray, people: List<AlbumPerson>? = null) {
        val update = storage.edit().putString("photos", rows.toString())
        if (people != null) update.putString("people", JSONArray(people.map {
            JSONObject().put("id", it.id).put("name", it.name).put("referenceId", it.referenceId).put("referenceVersion", it.referenceVersion)
        }).toString())
        check(update.commit())
    }

    private suspend fun location(exif: ExifInterface?): AlbumLocation? {
        val points = exif?.latLong ?: return null
        val city = albumTry { Graph.offlineCities.nearest(points[0], points[1]) } ?: return null
        val country = Locale.Builder().setRegion(city.countryCode).build()
        return AlbumLocation(city.name,
            listOf(city.name, city.countryCode, country.getDisplayCountry(Locale.ENGLISH), country.displayCountry).distinct().joinToString(" "))
    }

    private suspend fun localDetails(file: WorkspaceFile): AlbumPhotoDetails = withContext(Dispatchers.IO) {
        val exif = readAlbumExif(context, file.uri)
        val date = exif?.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.take(10)?.let {
            runCatching { LocalDate.parse(it, DateTimeFormatter.ofPattern("yyyy:MM:dd", Locale.ROOT)) }.getOrNull()
        }
        val caption = exif?.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION).orEmpty().take(240)
        val location = location(exif)
        val bitmap = albumTry { loadAlbumBitmap(context, file.uri, maxEdge = 1400) }
            ?: return@withContext AlbumPhotoDetails(date, caption, location)
        try {
            val text = albumTry { scanFrameText(bitmap).joinToString(" ") { it.text }.take(3000) }.orEmpty()
            val label = albumTry { labelFrame(bitmap) }.orEmpty()
            AlbumPhotoDetails(date, listOf(caption, label, text).filter(String::isNotBlank).joinToString(" "), location)
        } finally { bitmap.recycle() }
    }
}

private suspend inline fun <T> albumTry(block: () -> T): T? = try { block() }
catch (cancel: CancellationException) { throw cancel }
catch (_: Exception) { null }

internal fun findAlbumPhotos(photos: List<HaloAlbumPhoto>, spoken: String, today: LocalDate = LocalDate.now()): List<HaloAlbumPhoto> {
    var words = normalizeAlbumText(spoken)
    val dateRange = when {
        albumSummer.containsMatchIn(words) -> {
            words = albumSummer.replace(words, "")
            val year = if (today.monthValue >= 9) today.year else today.year - 1
            LocalDate.of(year, 6, 1)..LocalDate.of(year, 8, 31)
        }
        albumLastYear.containsMatchIn(words) -> {
            words = albumLastYear.replace(words, "")
            LocalDate.of(today.year - 1, 1, 1)..LocalDate.of(today.year - 1, 12, 31)
        }
        albumYesterday.containsMatchIn(words) -> {
            words = albumYesterday.replace(words, "")
            today.minusDays(1)..today.minusDays(1)
        }
        albumToday.containsMatchIn(words) -> {
            words = albumToday.replace(words, "")
            today..today
        }
        else -> null
    }
    val phrase = words.replace(Regex("(?:’|')s\\b"), "")
    val tokens = albumWords.findAll(phrase).map { it.value }
        .filter { it !in albumFillers }.take(18).toList()
    val keys = albumWords.findAll(phrase).joinToString(" ") { it.value }
    fun mentions(value: String): Boolean {
        val normalized = albumWords.findAll(normalizeAlbumText(value)).joinToString(" ") { it.value }
        return normalized.isNotBlank() && " $normalized " in " $keys "
    }
    val namedPeople = photos.asSequence().flatMap { it.suggestedPeople.asSequence() }.distinct().filter(::mentions).toSet()
    val namedPlaces = photos.asSequence().map(HaloAlbumPhoto::nearbyCity).distinct().filter(::mentions).toSet()
    return photos.asSequence().filter { dateRange == null || it.date?.let(dateRange::contains) == true }.map { photo ->
        val text = normalizeAlbumText(listOf(photo.name, photo.tags, photo.words, photo.placeSearch,
            photo.suggestedPeople.joinToString(" "), photo.date?.toString().orEmpty(),
            photo.date?.month?.let { month -> albumQueryLocales.joinToString(" ") {
                month.getDisplayName(TextStyle.FULL, it)
            } }.orEmpty()).joinToString(" "))
        val peopleMatch = namedPeople.isEmpty() || photo.suggestedPeople.any { it in namedPeople } ||
            namedPeople.any { normalizeAlbumText(it) in normalizeAlbumText(photo.tags) }
        val placeMatch = namedPlaces.isEmpty() || photo.nearbyCity in namedPlaces ||
            namedPlaces.any { normalizeAlbumText(it) in normalizeAlbumText(photo.tags) }
        photo to if (peopleMatch && placeMatch) tokens.count { it in text } else -1
    }.filter { it.second >= 0 && (tokens.isEmpty() || it.second > 0) }
        .sortedWith(compareByDescending<Pair<HaloAlbumPhoto, Int>> { it.second }.thenByDescending { it.first.date })
        .map { it.first }.toList()
}

private val albumWords = Regex("[\\p{L}\\p{N}]+")
private val albumFillers = setOf("a", "and", "at", "find", "from", "in", "me", "of", "photo", "photos", "show", "the", "with")
private val albumQueryLocales = listOf(Locale.ENGLISH, Locale.forLanguageTag("ru"), Locale.forLanguageTag("lv"), Locale.forLanguageTag("pl"))
private val albumSummer = albumTimePhrase("last summer", "прошлым летом", "прошлого лета", "pagājušajā vasarā", "zeszłego lata", "zeszłym latem", "ubiegłego lata", "w zeszłe lato")
private val albumLastYear = albumTimePhrase("last year", "прошлом году", "прошлого года", "pagājušajā gadā", "zeszłym roku", "ubiegłym roku")
private val albumYesterday = albumTimePhrase("yesterday", "вчера", "vakar", "wczoraj")
private val albumToday = albumTimePhrase("today", "сегодня", "šodien", "dzisiaj", "dziś")
private fun albumTimePhrase(vararg values: String) =
    Regex("(?<![\\p{L}\\p{N}])(?:${values.joinToString("|") { Regex.escape(normalizeAlbumText(it)) }})(?![\\p{L}\\p{N}])")
private fun normalizeAlbumText(text: String) =
    Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFKD).replace(Regex("\\p{M}+"), "")
