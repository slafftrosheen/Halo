package com.halo.app.data.notebook

import com.halo.app.data.workspace.NoteAttachment
import com.halo.app.data.workspace.WorkspaceAssetEntity
import org.json.JSONArray
import java.security.MessageDigest

/** Content identities shared by saved-note controls and the canonical purge owner. */
object NotebookNoteIdentity {
    fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    fun asset(item: WorkspaceAssetEntity): String = digest(JSONArray().put(item.id).put(item.objectId).put(item.type)
        .put(item.mimeType).put(item.storageType).put(item.pathOrUri).put(item.permissionPersisted).put(item.displayName)
        .put(item.bytes).put(item.width).put(item.height).put(item.durationMs).put(item.createdAtEpochMs).toString())
    fun content(note: NotebookDocument, inInbox: Boolean, attachments: List<NoteAttachment>, assets: List<WorkspaceAssetEntity>, legacyBody: String? = null): String {
        val value = JSONArray().put(note.noteId).put(note.title).put(note.revision).put(note.lifecycle.name)
            .put(note.updatedAt).put(note.createdAt).put(note.schemaVersion).put(inInbox).put(legacyBody)
            .put(JSONArray(note.blocks.map { block -> JSONArray().put(block.id).put(block.position).put(block.type.name)
                .put(block.text).put(block.checked).put(block.indent).put(block.refObjectId).put(block.language) }))
            .put(JSONArray(attachments.sortedBy { it.id }.map { item -> JSONArray().put(item.id).put(item.assetId).put(item.position)
                .put(item.caption).put(item.type).put(item.mimeType).put(item.storageType).put(item.pathOrUri).put(item.permissionPersisted)
                .put(item.displayName).put(item.bytes).put(item.width).put(item.height).put(item.durationMs) }))
            .put(JSONArray(assets.sortedBy { it.id }.map(::asset))).toString()
        return "note:" + digest(value)
    }
    fun recovery(draft: NoteDraftEntity?): String = if (draft == null) "note-draft:none" else "note-draft:" + digest(
        JSONArray().put(draft.objectId).put(draft.body).put(draft.updatedAt).put(draft.baseRevision).put(draft.sequence).put(draft.structureJson).toString())
}
