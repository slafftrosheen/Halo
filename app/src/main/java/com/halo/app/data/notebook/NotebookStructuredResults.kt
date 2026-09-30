package com.halo.app.data.notebook

import com.halo.app.data.WorkspaceContextDao
import com.halo.runtime.cognition.interaction.PresentationMetadata
import com.halo.runtime.cognition.interaction.StructuredPresentation
import org.json.JSONObject

/**
 * MINING-P2-001 Part O.
 *
 * A saved structured answer is an ordinary workspace object: the note body carries the assistant's
 * **text fallback**, and the bounded presentation payload rides in the object's existing provenance
 * metadata (written by the host when the user taps "Save to Notebook"). No new table and no EAV
 * component database — the presentation stays a bounded JSON payload with an explicit schema version.
 *
 * Reading is deliberately defensive: an unsupported schema version, a truncated payload or corrupt
 * metadata returns `null`, and the notebook simply shows the note it always had. The presentation is
 * never merged into the editable Markdown document; the object stays a normal, editable note, and the
 * card is a read-only view of how the answer was originally presented.
 */
class NotebookStructuredResults(private val dao: WorkspaceContextDao) {

    suspend fun presentationFor(objectId: String): StructuredPresentation? {
        val raw = dao.provenanceMetadata(objectId) ?: return null
        return decode(raw)
    }

    /** Metadata JSON is a flat string map, so the codec's keys survive a round trip unchanged. */
    fun decode(rawMetadataJson: String): StructuredPresentation? = runCatching {
        val json = JSONObject(rawMetadataJson)
        val keys = json.keys()
        val map = LinkedHashMap<String, String>()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = json.optString(key)
        }
        PresentationMetadata.decode(map)
    }.getOrNull()
}
