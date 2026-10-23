package com.halo.app.backup

import com.halo.app.data.studio.*
import com.squareup.moshi.JsonClass
import org.json.JSONObject

@JsonClass(generateAdapter = true)
data class StudioProjectSnapshot(val objectId: String, val revision: Long, val committed: String, val session: String)
@JsonClass(generateAdapter = true)
data class StudioVersionSnapshot(val objectId: String, val revision: Long, val document: String, val savedAt: Long)

/** Archive adaptation delegates document validation to the canonical Studio codec. */
internal object StudioArchive {
    fun validate(snapshot: BrainSnapshot) {
        val headers = snapshot.workspace.associateBy { it.id }
        val projects = snapshot.studioProjects.associateBy { it.objectId }
        require(projects.size == snapshot.studioProjects.size)
        snapshot.studioProjects.forEach { project ->
            require(project.objectId.matches(Regex("[A-Za-z0-9_-]{1,100}")) && project.revision in 1..StudioProjectRepository.MAX_REVISION)
            require(headers[project.objectId]?.let { it.type == "ARTIFACT" && it.contentRef == "studio:${project.objectId}" && it.title.isNotBlank() && it.title.length <= 120 } == true)
            StudioDocumentCodec.decode(project.committed); StudioSession.decode(project.session)
        }
        require(snapshot.workspace.filter { it.contentRef.startsWith("studio:") }.all { it.id in projects })
        require(snapshot.studioVersions.map { it.objectId to it.revision }.distinct().size == snapshot.studioVersions.size)
        snapshot.studioVersions.forEach { version ->
            require(version.revision > 0 && version.revision <= requireNotNull(projects[version.objectId]).revision && version.savedAt >= 0)
            StudioDocumentCodec.decode(version.document)
        }
    }
    private fun StudioDocument.remap(ids: Map<String, String>) = copy(layers = layers.map { layer ->
        layer.copy(source = layer.source?.let { it.copy(objectId = ids[it.objectId] ?: it.objectId) })
    }).also(StudioDocument::requireValid)
    fun document(encoded: String, ids: Map<String, String>): String = StudioDocumentCodec.encode(StudioDocumentCodec.decode(encoded).remap(ids))
    fun session(encoded: String, ids: Map<String, String>): String = StudioSession.decode(encoded).let { session ->
        session.copy(current = session.current.remap(ids), undo = session.undo.map { it.remap(ids) }, redo = session.redo.map { it.remap(ids) }).encode()
    }
    fun metadata(encoded: String, ids: Map<String, String>): String {
        val objectValue = runCatching { JSONObject(encoded) }.getOrNull() ?: return encoded
        if (!objectValue.optString("operation").startsWith("studio-")) return encoded
        require(encoded.length <= 2_100_000)
        if (objectValue.has("projectId")) {
            val id = objectValue.getString("projectId"); objectValue.put("projectId", ids[id] ?: id)
        }
        objectValue.optJSONObject("document")?.let { value ->
            objectValue.put("document", JSONObject(document(value.toString(), ids)))
        }
        return objectValue.toString()
    }
}
