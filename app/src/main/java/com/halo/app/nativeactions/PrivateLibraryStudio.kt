package com.halo.app.nativeactions

import com.halo.app.data.AppDatabase
import com.halo.app.data.studio.StudioDocumentCodec
import com.halo.app.data.studio.StudioLayerKind
import com.halo.app.data.studio.StudioProjectEntity
import com.halo.app.data.studio.StudioProjectVersion
import com.halo.app.data.studio.StudioSession
import org.json.JSONArray
import org.json.JSONObject

/** Saves the document, editing session and every stored version as a single protected item. */
internal data class PrivateLibraryStudio(val project: StudioProjectEntity, val versions: List<StudioProjectVersion>) {
    fun encode(): JSONObject = JSONObject().put("revision", project.revision).put("committed", project.committed)
        .put("session", project.session).put("versions", JSONArray(versions.map { version ->
            JSONObject().put("revision", version.revision).put("document", version.document).put("at", version.savedAt)
        }))

    fun visibleText(): String? = StudioSession.decode(project.session).current.layers
        .filter { it.kind == StudioLayerKind.TEXT && it.visible && it.text.isNotBlank() }
        .joinToString("\n\n") { it.text }.takeIf(String::isNotBlank)

    suspend fun restore(database: AppDatabase) {
        val projects = database.studioProjectDao()
        projects.insert(project)
        versions.forEach { projects.insertVersion(it) }
    }

    companion object {
        suspend fun capture(database: AppDatabase, id: String): PrivateLibraryStudio {
            val sizes = database.privateLibraryDao()
            val bytes = sizes.studioEncodedSize(id)
            require(bytes in 1..9_000_000L)
            val projects = database.studioProjectDao()
            val stored = requireNotNull(projects.get(id))
            return PrivateLibraryStudio(stored, sizes.studioVersions(id))
        }

        fun decode(id: String, data: JSONObject): PrivateLibraryStudio {
            val project = StudioProjectEntity(id, data.getLong("revision"), data.getString("committed"), data.getString("session"))
            check(project.revision > 0)
            StudioDocumentCodec.decode(project.committed)
            StudioSession.decode(project.session)
            val rows = data.getJSONArray("versions")
            val versions = List(rows.length()) { index ->
                val row = rows.getJSONObject(index)
                StudioProjectVersion(id, row.getLong("revision"), row.getString("document"), row.getLong("at"))
            }
            check(versions.map { it.revision }.distinct().size == versions.size)
            check(versions.all { it.revision in 1..project.revision })
            return PrivateLibraryStudio(project, versions)
        }
    }
}
