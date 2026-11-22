package com.halo.app.nativeactions

import android.content.Context
import com.halo.app.data.AppDatabase
import com.halo.app.data.notebook.WorkspaceObjectEntity
import com.halo.app.data.toolkit.AlbumPeopleFormat
import com.halo.app.data.toolkit.AlbumPeopleStore
import com.halo.app.data.toolkit.AlbumPersonEntity
import com.halo.app.data.toolkit.albumPeopleLimit
import org.json.JSONArray
import org.json.JSONObject

/** The encrypted record owns the name, exact reference and every versioned match while Private. */
internal class PrivateLibraryPersonAdapter(private val database: AppDatabase, context: Context) {
    private val store = AlbumPeopleStore(context, database)
    private val dao get() = database.albumPeopleDao()

    /** Run before opening the Vault transaction, as the one-time import also touches preferences. */
    suspend fun prepareStorage() = store.ensureReady()

    suspend fun capture(target: PrivateLibraryTarget.Person): PrivateLibraryItem {
        store.requireReady()
        val row = requireNotNull(dao.person(target.id))
        check(row.name == target.name && row.referenceId == target.photoId && row.referenceVersion == target.photoVersion)
        return snapshot(row)
    }

    suspend fun captureLinked(id: String): PrivateLibraryItem {
        store.requireReady()
        return snapshot(requireNotNull(dao.person(id)))
    }

    private fun snapshot(row: AlbumPersonEntity): PrivateLibraryItem {
        AlbumPeopleFormat.requireValidPerson(row)
        val snapshot = JSONObject().put("referenceId", row.referenceId).put("referenceVersion", row.referenceVersion)
            .put("sortOrder", row.sortOrder).put("matches", JSONArray(row.matchesJson))
        return PrivateLibraryItem(
            header = WorkspaceObjectEntity(
                id = row.id, type = "PRIVATE_ALBUM_PERSON", title = row.name, createdAt = 0L, updatedAt = 0L,
                source = "ALBUM", lifecycle = "ACTIVE", inInbox = false, indexingState = "NONE",
                contentRef = "album-person:" + row.id,
            ),
            note = null, blocks = emptyList(), memberships = emptyList(), favorite = null,
            alias = null, file = null, person = snapshot,
        )
    }

    suspend fun withdraw(item: PrivateLibraryItem) {
        store.requireReady()
        check(item.header.type == "PRIVATE_ALBUM_PERSON" && item.person != null)
        check(dao.deletePerson(item.header.id) == 1)
    }

    suspend fun restore(item: PrivateLibraryItem) {
        store.requireReady()
        val header = item.header
        check(header.type == "PRIVATE_ALBUM_PERSON" && dao.people().size < albumPeopleLimit)
        val payload = requireNotNull(item.person)
        val restored = AlbumPersonEntity(header.id, header.title, AlbumPeopleFormat.nameKey(header.title),
            payload.getString("referenceId"), payload.getString("referenceVersion"),
            payload.getInt("sortOrder"), payload.getJSONArray("matches").toString())
        AlbumPeopleFormat.requireValidPerson(restored)
        // ABORT on a conflicting public name or reference. The enclosing transaction keeps
        // the complete encrypted entry available for retry and never replaces a public profile.
        dao.insertPeople(listOf(restored))
    }
}
