package com.halo.app.data.toolkit

import android.content.Context
import androidx.room.withTransaction
import com.halo.app.data.AppDatabase
import java.text.Normalizer
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal const val albumPeopleLimit = 20
internal const val albumIndexLimit = 120
internal const val albumPreferenceName = "halo_album_v1"
internal val albumIndexWriteLock = Mutex()

internal data class AlbumPersonMatch(val photoId: String, val photoVersion: String, val position: Int)

internal object AlbumPeopleFormat {
    fun nameKey(name: String): String = Normalizer.normalize(name.lowercase(Locale.ROOT), Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "")

    fun encodeMatches(rows: List<AlbumPersonMatch>): String {
        requireValidMatches(rows)
        return JSONArray(rows.sortedBy(AlbumPersonMatch::photoId).map { match ->
            JSONObject().put("id", match.photoId).put("version", match.photoVersion).put("position", match.position)
        }).toString()
    }

    fun matches(row: AlbumPersonEntity): List<AlbumPersonMatch> {
        val result = JSONArray(row.matchesJson)
        require(result.length() <= albumIndexLimit)
        return (0 until result.length()).map { index ->
            val match = result.getJSONObject(index)
            AlbumPersonMatch(match.getString("id"), match.getString("version"), match.getInt("position"))
        }.also(::requireValidMatches)
    }

    fun requireValidPerson(row: AlbumPersonEntity) {
        require(row.id.length in 1..200 && row.id.none(Char::isISOControl))
        require(row.name.isNotBlank() && row.name.length <= 60 && row.name.none(Char::isISOControl))
        require(row.normalizedName == nameKey(row.name))
        require(row.referenceId.length in 1..200 && row.referenceVersion.length in 1..240)
        require(row.sortOrder in 0 until albumPeopleLimit)
        matches(row)
    }

    private fun requireValidMatches(rows: List<AlbumPersonMatch>) {
        require(rows.size <= albumIndexLimit && rows.map(AlbumPersonMatch::photoId).distinct().size == rows.size)
        require(rows.all { it.photoId.length in 1..200 && it.photoVersion.length <= 240 && it.position in 0 until albumPeopleLimit })
    }
}

/** Migrates once, then verifies the old name/match fields have been removed before allowing public or sealed reads. */
internal class AlbumPeopleStore(context: Context, private val database: AppDatabase) {
    private val preferences = context.applicationContext.getSharedPreferences(albumPreferenceName, Context.MODE_PRIVATE)
    private val dao get() = database.albumPeopleDao()

    suspend fun ensureReady() = withContext(Dispatchers.IO) {
        if (dao.migrationPhase() == scrubbed) return@withContext
        albumIndexWriteLock.withLock {
            if (dao.migrationPhase() == scrubbed) return@withLock
            val photos = JSONArray(preferences.getString("photos", "[]").orEmpty())
            database.withTransaction {
                if (dao.migrationPhase() == null) {
                    val profiles = JSONArray(preferences.getString("people", "[]").orEmpty())
                    val rows = migrate(profiles, photos)
                    if (rows.isNotEmpty()) dao.insertPeople(rows)
                    dao.writeMigrationPhase(AlbumPeopleMigrationEntity(1, imported))
                }
            }
            // Write the scrub before its DB marker. A crash after import retries the scrub,
            // and a failed disk write leaves all person paths unavailable until it succeeds.
            for (index in 0 until photos.length()) {
                photos.getJSONObject(index).apply { remove("people"); remove("peopleVersion") }
            }
            check(preferences.edit().putString("photos", photos.toString()).remove("people")
                .putString("peopleMigrationScrub", UUID.randomUUID().toString()).commit())
            dao.writeMigrationPhase(AlbumPeopleMigrationEntity(1, scrubbed))
        }
    }

    suspend fun requireReady() { check(dao.migrationPhase() == scrubbed) }

    fun observeEntries(): Flow<List<AlbumPersonEntity>> = flow {
        try { ensureReady() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            emit(emptyList())
            return@flow
        }
        emitAll(dao.observePeople())
    }

    private fun migrate(profiles: JSONArray, photos: JSONArray): List<AlbumPersonEntity> {
        require(profiles.length() <= albumPeopleLimit && photos.length() <= albumIndexLimit)
        val matches = linkedMapOf<String, MutableList<AlbumPersonMatch>>()
        for (index in 0 until photos.length()) {
            val photo = photos.getJSONObject(index)
            val ids = photo.optJSONArray("people") ?: continue
            val photoId = photo.getString("id")
            val version = photo.optString("peopleVersion")
            require(ids.length() <= albumPeopleLimit)
            for (position in 0 until ids.length()) {
                val id = ids.getString(position)
                matches.getOrPut(id, ::mutableListOf) += AlbumPersonMatch(photoId, version, position)
            }
        }
        return (0 until profiles.length()).map { index ->
            val row = profiles.getJSONObject(index)
            val id = row.getString("id")
            val name = row.getString("name")
            AlbumPersonEntity(id, name, AlbumPeopleFormat.nameKey(name), row.getString("referenceId"),
                row.getString("referenceVersion"), index, AlbumPeopleFormat.encodeMatches(matches[id].orEmpty()))
                .also(AlbumPeopleFormat::requireValidPerson)
        }
    }

    private companion object {
        const val imported = 1
        const val scrubbed = 2
    }
}
