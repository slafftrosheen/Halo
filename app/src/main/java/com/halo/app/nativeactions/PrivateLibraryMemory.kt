package com.halo.app.nativeactions

import com.halo.app.backup.MemorySnapshot
import com.halo.app.data.AppDatabase
import com.halo.app.data.Memory
import com.halo.app.data.memory.MemoryPageLinkEntity
import com.halo.app.data.memory.MemoryPageTagEntity
import com.halo.app.data.memory.MemoryRecordEntity
import com.halo.app.data.memory.MemoryRevisionEntity
import com.halo.app.data.memory.PersonalSuggestionEntity
import com.halo.app.data.notebook.WorkspaceObjectEntity
import com.squareup.moshi.Moshi
import org.json.JSONArray
import org.json.JSONObject

/** Dependent memories can carry exact copies of a source, including earlier revisions. */
internal class PrivateLibraryMemory(private val database: AppDatabase) {
    private val dao get() = database.privateLibraryDao()
    private val codec = Moshi.Builder().build().adapter(MemorySnapshot::class.java)

    suspend fun capture(id: String, existing: WorkspaceObjectEntity?): PrivateLibraryItem {
        val row = database.memoryRecordDao().getRecord(id)
        val legacy = id.removePrefix("mem_").takeIf { it != id }?.toIntOrNull()?.let { dao.legacyMemory(it) }
        val suggestion = database.personalMemoryDao().suggestionById(id)
        check(row != null || legacy != null || suggestion != null && existing != null)
        val revisions = dao.memoryRevisions(id).map { JSONObject().put("id", it.id).put("before", it.previousContent)
            .put("after", it.newContent).put("at", it.changedAt).put("reason", it.reason) }
        val pages = database.memoryPagesDao()
        val links = pages.linksFor(id).sortedWith(compareBy(MemoryPageLinkEntity::sourceId, MemoryPageLinkEntity::targetId))
            .map { JSONObject().put("from", it.sourceId).put("to", it.targetId) }
        val tags = pages.tagsFor(id).map { JSONObject().put("key", it.key).put("label", it.label) }
        val payload = JSONObject().put("hasHeader", existing != null)
            .put("record", row?.let { codec.toJson(it.toSnapshot()) })
            .put("suggestion", suggestion?.let {
                JSONObject().put("fingerprint", it.fingerprint).put("kind", it.kind).put("json", it.json)
                    .put("state", it.state).put("created", it.createdAt).put("updated", it.updatedAt)
                    .put("count", it.evidenceCount).put("dismissed", it.dismissedAt).put("never", it.neverAgain)
            })
            .put("legacy", legacy?.let { JSONObject().put("id", it.id).put("content", it.content).put("at", it.timestamp)
                .put("source", it.sourceObjectId).put("revision", it.sourceRevision) })
            .put("revisions", JSONArray(revisions)).put("links", JSONArray(links)).put("tags", JSONArray(tags))
        val header = existing ?: WorkspaceObjectEntity(
            id, "MEMORY", (row?.content ?: requireNotNull(legacy).content).take(80),
            row?.createdAt ?: requireNotNull(legacy).timestamp, row?.updatedAt ?: requireNotNull(legacy).timestamp,
            "IMPORT", "ACTIVE", false, "READY", "memory:$id",
        )
        return PrivateLibraryItem(header, null, emptyList(), dao.memberships(id), dao.favorite(id), dao.alias(id), null,
            provenance = database.workspaceContextDao().provenanceFor(id), memory = payload,
            working = database.workspaceContextDao().selectedEntry(id))
    }

    suspend fun withdraw(item: PrivateLibraryItem) {
        val payload = requireNotNull(item.memory)
        val id = item.header.id
        val expected = if (payload.has("record")) 1 else 0
        check(dao.withdrawMemory(id) == expected)
        dao.withdrawMemoryRevisions(id)
        if (payload.has("suggestion")) check(database.personalMemoryDao().withdrawPrivateSuggestion(id) == 1)
        payload.optJSONObject("legacy")?.let { database.memoryDao().deleteMemoryById(it.getInt("id")) }
    }

    suspend fun restore(item: PrivateLibraryItem) {
        val payload = requireNotNull(item.memory)
        val id = item.header.id
        payload.optional("record")?.let {
            val row = requireNotNull(codec.fromJson(it))
            check(row.id == id && database.memoryRecordDao().getRecord(id) == null)
            database.memoryRecordDao().insertRecord(row.toRecord())
        }
        payload.optJSONObject("legacy")?.let {
            val legacyId = it.getInt("id")
            check(id == "mem_$legacyId")
            dao.restoreLegacyMemory(Memory(legacyId, it.getString("content"), it.getLong("at"), it.optional("source"),
                if (it.isNull("revision")) null else it.getInt("revision")))
        }
        payload.optJSONObject("suggestion")?.let {
            database.personalMemoryDao().restorePrivateSuggestion(PersonalSuggestionEntity(
                id, it.getString("fingerprint"), it.getString("kind"), it.getString("json"), it.getString("state"),
                it.getLong("created"), it.getLong("updated"), it.getInt("count"),
                if (it.isNull("dismissed")) null else it.getLong("dismissed"), it.getBoolean("never"),
            ))
        }
        check(dao.memoryRevisions(id).isEmpty())
        for (revision in payload.rows("revisions")) {
            database.memoryRecordDao().insertRevision(MemoryRevisionEntity(revision.getString("id"), id,
                revision.getString("before"), revision.getString("after"), revision.getLong("at"), revision.getString("reason")))
        }
        for (tag in payload.rows("tags")) {
            database.memoryPagesDao().putTag(MemoryPageTagEntity(id, tag.getString("key"), tag.getString("label")))
        }
    }

    suspend fun restoreLinks(items: List<PrivateLibraryItem>) {
        val links = items.flatMap { links(it) }.distinct()
        for (link in links) {
            val source = database.memoryRecordDao().getRecord(link.sourceId)
            val target = database.memoryRecordDao().getRecord(link.targetId)
            if (source != null && target != null) database.memoryPagesDao().insertLink(link)
        }
    }

    fun linkedIds(item: PrivateLibraryItem): Set<String> = buildSet {
        links(item).forEach { add(it.sourceId); add(it.targetId) }
        val payload = requireNotNull(item.memory)
        payload.optional("record")?.let(codec::fromJson)?.let { row ->
            row.sourceObjectId?.let(::add)
            row.scopeId?.let(::add)
        }
        payload.optJSONObject("legacy")?.optional("source")?.let(::add)
    }

    private fun links(item: PrivateLibraryItem): List<MemoryPageLinkEntity> = requireNotNull(item.memory).rows("links").map {
        MemoryPageLinkEntity(it.getString("from"), it.getString("to"))
    }

    private fun JSONObject.rows(key: String) = getJSONArray(key).let { array -> List(array.length(), array::getJSONObject) }
    private fun JSONObject.optional(key: String) = if (isNull(key)) null else getString(key)

    private fun MemoryRecordEntity.toSnapshot() = MemorySnapshot(id, content, kind, scope, scopeId, createdAt, updatedAt,
        sourceObjectId, sourceRevision, confidence, status, origin, evidenceJson, sourceEpisodeId,
        evidenceCount, lastConfirmedAt, usageCount, lastUsedAt, validUntil, fingerprint, userConfirmed)

    private fun MemorySnapshot.toRecord() = MemoryRecordEntity(id, content, kind, scope, scopeId, createdAt, updatedAt,
        sourceObjectId, sourceRevision, confidence, status, origin, evidenceJson, sourceEpisodeId,
        evidenceCount, lastConfirmedAt, usageCount, lastUsedAt, validUntil, fingerprint, userConfirmed)
}
