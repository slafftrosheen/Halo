package com.halo.app.ui.toolkit

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.room.withTransaction
import com.halo.app.data.toolkit.AlbumPeopleFormat
import com.halo.app.data.toolkit.AlbumPeopleStore
import com.halo.app.data.toolkit.AlbumPersonEntity
import com.halo.app.data.toolkit.AlbumPersonMatch
import com.halo.app.data.toolkit.albumIndexLimit
import com.halo.app.data.toolkit.albumIndexWriteLock as saveIndexLock
import com.halo.app.data.toolkit.albumPeopleLimit
import com.halo.app.data.toolkit.albumPreferenceName
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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
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
    val sourceCredit: String = "",
    val sourceLink: String = "",
    val sourceLicense: String = "",
    val editedFromId: String? = null,
    val suggestedPeopleOwners: List<AlbumPerson> = emptyList(),
) {
    fun withVisiblePeople(current: Set<AlbumPerson>): HaloAlbumPhoto {
        val eligible = suggestedPeopleOwners.filter { it in current }
        return copy(suggestedPeople = eligible.map(AlbumPerson::name).distinct(), suggestedPeopleOwners = eligible)
    }
}

internal class AlbumReferenceFaceUnavailable : IllegalArgumentException()
private data class AlbumLocation(val city: String, val search: String)
private data class AlbumPhotoDetails(val date: LocalDate?, val words: String, val location: AlbumLocation?)
internal data class AlbumNamingFacts(val id: String, val version: String, val date: LocalDate?, val city: String?)

internal class HaloAlbumIndex(private val context: Context) {
    private val storage = context.getSharedPreferences(albumPreferenceName, Context.MODE_PRIVATE)
    private val database get() = Graph.appDatabase
    private val peopleStore by lazy { AlbumPeopleStore(context, database) }

    /** Older edits recorded their original photo in Album before the managed-file publisher had versioned lineage. */
    suspend fun backfillLegacyEditOrigins() = withContext(Dispatchers.IO) {
        saveIndexLock.withLock {
            val data = readRows()
            database.withTransaction {
                val origins = database.workspaceContextDao()
                for (index in 0 until data.length()) {
                    val row = data.optJSONObject(index) ?: continue
                    val original = row.optString("editedFromId").takeIf(String::isNotBlank) ?: continue
                    val id = row.optString("id")
                    if (id.isBlank() || id == original) continue
                    val previous = origins.provenanceFor(id) ?: continue
                    val source = Uri.parse(previous.sourceId)
                    val path = source.pathSegments
                    if (source.scheme != "content" || source.authority != context.packageName + ".captures" ||
                        path.size != 3 || path[0] != "workspace-owned" || path[1] != "scan-staging" ||
                        !path[2].matches(Regex("Edited-photo-[a-f0-9-]{36}\\.png"))) continue
                    val owner = database.notebookDao().getWorkspaceObject(id) ?: continue
                    if (owner.lifecycle != "ACTIVE" || owner.type != "FILE") continue
                    val metadata = JSONObject(previous.metadata).put("kind", "ALBUM_PHOTO_EDIT_LEGACY")
                        .put("originalPhotoId", original).put("sources", JSONArray().put(JSONObject().put("id", original)))
                    // Preserve the original provenance revision. A legacy Album record does not
                    // attest the source version used for this copy.
                    origins.provenance(previous.copy(sourceId = original, metadata = metadata.toString()))
                }
            }
        }
    }

    suspend fun photos(): List<HaloAlbumPhoto> = withContext(Dispatchers.IO) {
        val peopleReady = albumTry { peopleStore.ensureReady() } != null
        val data = readRows()
        val available = (0 until data.length()).mapNotNull { index ->
            val row = data.optJSONObject(index) ?: return@mapNotNull null
            val file = Graph.workspaceUseCases.getFile(row.optString("id")) ?: return@mapNotNull null
            if (file.ownership != ContentOwnership.HALO_OWNED || !file.mimeType.startsWith("image/")) null else row to file
        }
        val versions = available.associate { (_, file) -> file.objectId.value to file.version }
        // Read the canonical owner after the photo versions so a withdrawn profile cannot
        // regain a name from a legacy photo row or from a stale derived match.
        val people = if (peopleReady) database.albumPeopleDao().people().filter { versions[it.referenceId] == it.referenceVersion }
            else emptyList()
        val matchesByPhoto = people.flatMap { person ->
            runCatching { AlbumPeopleFormat.matches(person) }.getOrDefault(emptyList())
                .filter { versions[it.photoId].orEmpty() == it.photoVersion }
                .map { it.photoId to (it.position to person) }
        }.groupBy({ it.first }, { it.second })
        available.map { (row, file) ->
            val version = file.version
            val photo = HaloAlbumPhoto(
                file.objectId.value, file.displayName, runCatching { LocalDate.parse(row.optString("date")) }.getOrNull(),
                row.optString("words"), row.optString("tags"), file.uri, version, row.optString("city"), row.optString("placeSearch"),
                sourceCredit = row.optString("sourceCredit"), sourceLink = row.optString("sourceLink"),
                sourceLicense = row.optString("sourceLicense"),
                editedFromId = row.optString("editedFromId").takeIf(String::isNotBlank),
            )
            val candidates = matchesByPhoto[photo.id].orEmpty().sortedBy { it.first }.map { it.second }
            val explicit = people.filter { it.referenceId == photo.id }
            val names = (explicit + candidates).distinctBy(AlbumPersonEntity::id)
            photo.copy(suggestedPeople = names.map(AlbumPersonEntity::name),
                suggestedPeopleOwners = names.map(AlbumPersonEntity::toAlbumPerson))
        }
    }

    suspend fun keepArticlePicture(name: String, mimeType: String, bytes: ByteArray, sourceId: String,
        contentRevision: String, assetKey: String, credit: String, link: String, license: String,
        validate: suspend () -> Unit) = withContext(Dispatchers.IO) {
        saveIndexLock.withLock {
            validate()
            val rows = readRows()
            if ((0 until rows.length()).any { index ->
                    val row = rows.getJSONObject(index)
                    row.optString("articleAsset") == assetKey && row.optString("sourceLink") == link &&
                        Graph.workspaceUseCases.getFile(row.optString("id")) != null
                }) return@withLock
            check(rows.length() < albumIndexLimit) { "Album is full" }
            val metadata = JSONObject().put("sourceCredit", credit).put("sourceLink", link).put("sourceLicense", license)
            metadata.put("collectorSourceId", sourceId).put("collectorRevision", contentRevision)
            val suffix = when (mimeType) {
                "image/png" -> ".png"
                "image/webp" -> ".webp"
                "image/jpeg" -> ".jpg"
                else -> error("Unsupported article picture")
            }
            val file = Graph.workspaceFileRepository.createManagedFileWithPreparedSource(name.take(114) + suffix, mimeType, bytes,
                metadata.toString(), validateSource = validate, prepareSource = {
                    Graph.notebookRepository.createNote("$credit\n$link\n$license", title = name.take(120)).id
                })
            rows.put(JSONObject().put("id", file.objectId.value).put("placeIndexed", true)
                .put("articleAsset", assetKey).put("sourceCredit", credit).put("sourceLink", link).put("sourceLicense", license))
            save(rows)
            Unit
        }
    }

    suspend fun add(uri: Uri, adultRevision: Long): String {
        Graph.kidsPreferences.requireAdultAction(adultRevision)
        check(readRows().length() < albumIndexLimit) { "Album is full" }
        val file = Graph.workspaceUseCases.importForCanvas(uri)
        check(file.mimeType.startsWith("image/"))
        val details = localDetails(file)
        withContext(Dispatchers.IO) {
            saveIndexLock.withLock {
                Graph.kidsPreferences.requireAdultAction(adultRevision)
                val data = readRows()
                check(data.length() < albumIndexLimit)
                val existing = (0 until data.length()).any { data.optJSONObject(it)?.optString("id") == file.objectId.value }
                if (!existing) {
                    data.put(JSONObject().put("id", file.objectId.value)
                        .put("date", details.date?.toString().orEmpty())
                        .put("words", details.words).put("tags", "")
                        .put("city", details.location?.city.orEmpty())
                        .put("placeSearch", details.location?.search.orEmpty())
                        .put("namingVersion", file.version)
                        .put("placeIndexed", true))
                }
                save(data)
            }
        }
        return file.objectId.value
    }

    suspend fun addEdited(photo: HaloAlbumPhoto, bytes: ByteArray, adultRevision: Long): String = withContext(Dispatchers.IO) {
        saveIndexLock.withLock {
            Graph.kidsPreferences.requireAdultAction(adultRevision)
            val data = readRows()
            check(data.length() < albumIndexLimit)
            val base = photo.name.substringBeforeLast('.').replace(Regex("[\\p{Cntrl}/\\\\]+"), " ")
                .trim().take(100).ifBlank { "Photo" }
            val metadata = albumSourceProvenance("ALBUM_PHOTO_EDIT_V1", listOf(photo))
                .put("originalPhotoId", photo.editedFromId ?: photo.id)
            val file = Graph.workspaceFileRepository.createManagedFile("$base · edited.png", "image/png", bytes,
                photo.id, requireNotNull(photo.version), metadata.toString(), validateSource = {
                    Graph.kidsPreferences.requireAdultAction(adultRevision)
                    requireCurrentAlbumPhoto(photo)
                })
            Graph.kidsPreferences.requireAdultAction(adultRevision)
            data.put(JSONObject().put("id", file.objectId.value).put("date", photo.date?.toString().orEmpty())
                .put("words", photo.words).put("tags", photo.tags).put("city", photo.nearbyCity).put("placeSearch", photo.placeSearch)
                .put("namingVersion", file.version).put("editedFromId", photo.editedFromId ?: photo.id).put("placeIndexed", true))
            save(data)
            file.objectId.value
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

    suspend fun people(): List<AlbumPerson> = withContext(Dispatchers.IO) {
        peopleStore.ensureReady()
        database.albumPeopleDao().people().map(AlbumPersonEntity::toAlbumPerson)
    }

    /** IDs of canonical local images explicitly admitted to Album by the grown-up. */
    fun observeLocalPhotoIds(): Flow<Set<String>> = callbackFlow {
        fun snapshot() = runCatching {
            val rows = readRows()
            (0 until rows.length()).mapNotNull(rows::optJSONObject)
                .filter { it.optString("sourceLink").isEmpty() && it.optString("articleAsset").isEmpty() }
                .mapNotNull { row -> row.optString("id").takeIf(String::isNotEmpty) }.toSet()
        }.fold(onSuccess = { trySend(it); Unit }, onFailure = { close(it); Unit })
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == "photos") snapshot()
        }
        storage.registerOnSharedPreferenceChangeListener(listener)
        snapshot()
        awaitClose { storage.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()

    fun observePeople(): Flow<List<AlbumPerson>> = peopleStore.observeEntries().map { rows ->
        rows.map(AlbumPersonEntity::toAlbumPerson)
    }

    fun observeNamingFacts(): Flow<List<AlbumNamingFacts>> = observePreference("photos") {
        val rows = readRows()
        (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val version = row.optString("namingVersion").takeIf(String::isNotBlank) ?: return@mapNotNull null
            AlbumNamingFacts(row.getString("id"), version, runCatching { LocalDate.parse(row.optString("date")) }.getOrNull(),
                row.optString("city").trim().takeIf { it.isNotBlank() && it.length <= 70 && it.none(Char::isISOControl) })
        }
    }

    private fun <T> observePreference(keyName: String, read: () -> T): Flow<T> = callbackFlow {
        fun snapshot() = runCatching(read).fold(
            onSuccess = { trySend(it); Unit },
            onFailure = { close(it); Unit },
        )
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == keyName) snapshot()
        }
        storage.registerOnSharedPreferenceChangeListener(listener)
        snapshot()
        awaitClose { storage.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()

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
        require(existing != null || previous.size < albumPeopleLimit)
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
        peopleStore.ensureReady()
        database.withTransaction {
            Graph.kidsPreferences.requireAdultAction(adultRevision)
            check(database.albumPeopleDao().deletePerson(id) == 1)
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
                database.withTransaction {
                    Graph.kidsPreferences.requireAdultAction(adultRevision)
                    val dao = database.albumPeopleDao()
                    val current = dao.people()
                    check(current.map(AlbumPersonEntity::toAlbumPerson) == expected) { "Album people changed during matching" }
                    val checkedPhotos = evaluated.filter { (id, result) ->
                        val file = Graph.workspaceUseCases.getFile(id)
                        file != null && file.version == result.first
                    }
                    val next = wanted.mapIndexed { order, person ->
                        val kept = current.firstOrNull { it.id == person.id }?.let(AlbumPeopleFormat::matches).orEmpty()
                            .filterNot { it.photoId in checkedPhotos }
                        val updated = checkedPhotos.mapNotNull { (photoId, result) ->
                            val position = result.second.indexOf(person.id)
                            if (position < 0) null else AlbumPersonMatch(photoId, result.first.orEmpty(), position)
                        }
                        AlbumPersonEntity(person.id, person.name, AlbumPeopleFormat.nameKey(person.name), person.referenceId,
                            person.referenceVersion, order, AlbumPeopleFormat.encodeMatches(kept + updated))
                            .also(AlbumPeopleFormat::requireValidPerson)
                    }
                    dao.deletePeople()
                    if (next.isNotEmpty()) dao.insertPeople(next)
                    Graph.kidsPreferences.requireAdultAction(adultRevision)
                }
            }
        }
    }

    private fun readRows() = JSONArray(storage.getString("photos", null).orEmpty().ifEmpty { "[]" })
    private fun save(rows: JSONArray) { check(storage.edit().putString("photos", rows.toString()).commit()) }

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

private fun AlbumPersonEntity.toAlbumPerson() = AlbumPerson(id, name, referenceId, referenceVersion)

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
