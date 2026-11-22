package com.halo.app.nativeactions

import com.halo.app.data.ObjectProvenance
import com.halo.app.data.WorkingSetEntry
import com.halo.app.data.notebook.NoteBlockEntity
import com.halo.app.data.notebook.NoteDraftEntity
import com.halo.app.data.notebook.NoteEntity
import com.halo.app.data.notebook.NotebookCollectionEntity
import com.halo.app.data.notebook.NotebookCollectionMemberEntity
import com.halo.app.data.notebook.NotebookFavoriteEntity
import com.halo.app.data.notebook.WorkspaceObjectEntity
import com.halo.app.data.workspace.WorkspaceLibraryTitleAlias
import com.halo.app.data.workspace.WorkspaceRelationEntity
import org.json.JSONArray
import org.json.JSONObject

internal data class PrivateLibrarySnapshot(
    val collection: NotebookCollectionEntity?,
    val items: List<PrivateLibraryItem>,
    val rootItemId: String? = null,
    val relations: List<WorkspaceRelationEntity> = emptyList(),
    val toolResults: List<PrivateLibraryPayloadBody> = emptyList(),
) {
    val title: String get() = collection?.name ?: (rootItemId?.let { root -> items.single { it.header.id == root } }
        ?: items.single()).header.title
}

internal data class PrivateLibraryItem(
    val header: WorkspaceObjectEntity,
    val note: NoteEntity?,
    val blocks: List<NoteBlockEntity>,
    val memberships: List<NotebookCollectionMemberEntity>,
    val favorite: NotebookFavoriteEntity?,
    val alias: WorkspaceLibraryTitleAlias?,
    val file: JSONObject?,
    val canvas: PrivateLibraryCanvas? = null,
    val provenance: ObjectProvenance? = null,
    val studyCard: JSONObject? = null,
    val organizer: JSONObject? = null,
    val studio: PrivateLibraryStudio? = null,
    val noteAssets: JSONObject? = null,
    val person: JSONObject? = null,
    val working: WorkingSetEntry? = null,
    val draft: NoteDraftEntity? = null,
    val studySession: JSONObject? = null,
    val memory: JSONObject? = null,
    val history: JSONObject? = null,
    val automation: JSONObject? = null,
    val composerDraft: String? = null,
    val composerDraftCaptured: Boolean = false,
)

internal object PrivateLibraryCodec {
    const val maxItems = 500

    fun encode(value: PrivateLibrarySnapshot): ByteArray = JSONObject().put("version", 1)
        .put("collection", value.collection?.let(::encodeCollection))
        .put("rootItem", value.rootItemId)
        .put("relations", JSONArray(value.relations.map { link -> JSONObject().put("id", link.id)
            .put("from", link.fromId).put("to", link.toId).put("type", link.type).put("at", link.createdAt) }))
        .put("toolResults", value.toolResults.takeIf { it.isNotEmpty() }?.let { rows ->
            JSONArray(rows.map(PrivateLibraryPayloadCodec::encode))
        })
        .put("items", JSONArray(value.items.map(::encodeItem))).toString().toByteArray(Charsets.UTF_8)

    fun decode(value: ByteArray): PrivateLibrarySnapshot {
        val root = JSONObject(value.toString(Charsets.UTF_8))
        require(root.getInt("version") == 1)
        val collection = root.optJSONObject("collection")?.let(::decodeCollection)
        val rootItemId = root.nullable("rootItem")
        val rows = root.getJSONArray("items")
        require(rows.length() <= maxItems && (collection != null || rootItemId != null || rows.length() == 1))
        val items = rows.objects().map(::decodeItem)
        require(items.distinctBy { it.header.id }.size == items.size)
        require(rootItemId == null || items.any { it.header.id == rootItemId })
        val relations = root.optJSONArray("relations")?.objects().orEmpty().map { link ->
            WorkspaceRelationEntity(link.getString("id"), link.getString("from"), link.getString("type"),
                link.getString("to"), link.getLong("at"))
        }
        val toolResults = PrivateLibraryPayloadCodec.decode(root.optJSONArray("toolResults"))
        val owners = items.mapTo(hashSetOf()) { it.header.id }
        require(toolResults.all { row -> row.references.all { it.ownerId in owners } })
        return PrivateLibrarySnapshot(collection, items, rootItemId, relations, toolResults)
    }

    private fun encodeItem(item: PrivateLibraryItem): JSONObject = with(item.header) {
        JSONObject().put("id", id).put("type", type).put("title", title).put("created", createdAt)
            .put("updated", updatedAt).put("source", source).put("lifecycle", lifecycle).put("inbox", inInbox)
            .put("indexed", indexingState).put("ref", contentRef)
            .put("note", item.note?.let { note -> JSONObject().put("body", note.body).put("rev", note.revision)
                .put("schema", note.schemaVersion).put("page", note.pageSettings) })
            .put("blocks", JSONArray(item.blocks.map(::encodeBlock)))
            .put("links", JSONArray(item.memberships.map { link -> JSONObject().put("id", link.collectionId)
                .put("at", link.addedAtEpochMs).put("suggestion", link.suggestionToken) }))
            .put("favorite", item.favorite?.addedAtEpochMs)
            .put("alias", item.alias?.let { alias -> JSONObject().put("old", alias.oldTitle)
                .put("new", alias.newTitle).put("expiry", alias.expiresAt) })
            .put("file", item.file)
            .put("canvas", item.canvas?.encode())
            .put("origin", item.provenance?.let { JSONObject().put("source", it.sourceId).put("revision", it.revision)
                .put("metadata", it.metadata) })
            .put("studyCard", item.studyCard)
            .put("organizer", item.organizer)
            .put("studio", item.studio?.encode())
            .put("noteAssets", item.noteAssets)
            .put("person", item.person)
            .put("working", item.working?.let { JSONObject().put("at", it.addedAt).put("source", it.source) })
            .put("draft", item.draft?.let { JSONObject().put("body", it.body).put("at", it.updatedAt)
                .put("base", it.baseRevision).put("sequence", it.sequence).put("structure", it.structureJson) })
            .put("studySession", item.studySession)
            .put("memory", item.memory)
            .put("history", item.history)
            .put("automation", item.automation)
            .put("composerDraft", item.composerDraft)
            .put("composerDraftCaptured", item.composerDraftCaptured)
    }

    private fun decodeItem(row: JSONObject): PrivateLibraryItem = with(row) {
        val id = getString("id")
        val header = WorkspaceObjectEntity(id, getString("type"), getString("title"), getLong("created"), getLong("updated"),
            getString("source"), getString("lifecycle"), getBoolean("inbox"), getString("indexed"), getString("ref"))
        val note = optJSONObject("note")?.let { NoteEntity(id, it.getString("body"), it.getInt("rev"), it.getInt("schema"), it.nullable("page")) }
        PrivateLibraryItem(header, note, getJSONArray("blocks").objects().map { decodeBlock(id, it) },
            getJSONArray("links").objects().map { NotebookCollectionMemberEntity(it.getString("id"), id, it.getLong("at"), it.nullable("suggestion")) },
            if (isNull("favorite")) null else NotebookFavoriteEntity(id, getLong("favorite")),
            optJSONObject("alias")?.let { WorkspaceLibraryTitleAlias(id, it.getString("old"), it.getString("new"), it.getLong("expiry")) },
            optJSONObject("file"),
            optJSONObject("canvas")?.let { PrivateLibraryCanvas.decode(id, it) },
            optJSONObject("origin")?.let { ObjectProvenance(id, it.getString("source"), it.getString("revision"), it.getString("metadata")) },
            optJSONObject("studyCard"),
            optJSONObject("organizer"),
            optJSONObject("studio")?.let { PrivateLibraryStudio.decode(id, it) },
            optJSONObject("noteAssets"),
            optJSONObject("person"),
            optJSONObject("working")?.let { WorkingSetEntry(id, it.getLong("at"), it.getString("source")) },
            optJSONObject("draft")?.let { NoteDraftEntity(id, it.getString("body"), it.getLong("at"), it.getLong("base"),
                it.getLong("sequence"), it.nullable("structure")) },
            optJSONObject("studySession"),
            optJSONObject("memory"),
            optJSONObject("history"),
            optJSONObject("automation"),
            nullable("composerDraft"),
            optBoolean("composerDraftCaptured", has("composerDraft")),
        )
    }

    private fun encodeCollection(col: NotebookCollectionEntity) = JSONObject().put("id", col.id).put("name", col.name)
        .put("order", col.sortOrder).put("created", col.createdAtEpochMs).put("updated", col.updatedAtEpochMs)

    private fun decodeCollection(row: JSONObject) = NotebookCollectionEntity(row.getString("id"), row.getString("name"),
        row.getInt("order"), row.getLong("created"), row.getLong("updated"))

    private fun encodeBlock(block: NoteBlockEntity) = JSONObject().put("id", block.id).put("position", block.position)
        .put("type", block.type).put("text", block.text).put("checked", block.checked).put("indent", block.indent)
        .put("ref", block.refObjectId).put("language", block.language).put("created", block.createdAtEpochMs)
        .put("updated", block.updatedAtEpochMs).put("presentation", block.presentation)

    private fun decodeBlock(note: String, value: JSONObject) = with(value) {
        NoteBlockEntity(getString("id"), note, getInt("position"), getString("type"), getString("text"), getBoolean("checked"),
            getInt("indent"), nullable("ref"), nullable("language"), getLong("created"), getLong("updated"), nullable("presentation"))
    }

    private fun JSONArray.objects(): List<JSONObject> = List(length(), ::getJSONObject)
    private fun JSONObject.nullable(key: String): String? = if (isNull(key)) null else getString(key)
}
