package com.halo.app.backup

import com.halo.app.data.notebook.*
import com.halo.app.data.workspace.*
import org.json.JSONObject

/** Exact canonical blocks preserve literal files and annotation/receipt markers across backups. */
internal object NoteBlockArchive {
    fun validate(snapshot: BrainSnapshot) {
        val all = snapshot.notes.flatMap { it.blocks.orEmpty() }
        require(all.map { it.id }.distinct().size == all.size)
        snapshot.notes.forEach { note ->
            val blocks = note.blocks ?: return@forEach // Legacy archives still load through the legacy note migration.
            require(blocks.size <= DocumentReducer.MAX_BLOCKS && note.revision > 0)
            require(blocks.map { it.position } == blocks.indices.toList())
            blocks.forEach { block ->
                require(block.id.matches(Regex("[A-Za-z0-9_-]{1,100}")))
                require(block.indent in 0..DocumentReducer.MAX_INDENT && block.createdAt >= 0 && block.updatedAt >= 0)
                // Canonical editor content is opaque here, including unfinished CSV and unknown future blocks.
                // Format and operation limits belong to the consuming operation, never to backup restoration.
            }
        }
    }
    private fun domain(block: NoteBlockSnapshot) = NoteBlock(block.id, block.position, NoteBlockType.fromStored(block.type),
        block.text, block.checked, block.indent, block.refObjectId, block.language)
    fun remap(note: NoteSnapshot, ids: Map<String, String>, blockIds: Map<String, String>): NoteSnapshot {
        val blocks = note.blocks?.map { block ->
            val content = if (block.language == WorkspaceFileReceipts.LANGUAGE) runCatching {
                val receipt = WorkspaceFileReceipts.decode(note.objectId, note.revision.toLong(), "Receipt", block.text)
                WorkspaceFileReceipts.encode(receipt.copy(items = receipt.items.map { item -> item.copy(
                    source = item.source.copy(objectId = ids[item.source.objectId] ?: item.source.objectId),
                    output = item.output?.let { it.copy(objectId = ids[it.objectId] ?: it.objectId) }) }))
            }.getOrDefault(block.text) else block.text
            block.copy(id = blockIds[block.id] ?: block.id, text = content,
                refObjectId = block.refObjectId?.let { ids[it] ?: it })
        }
        return note.copy(objectId = ids[note.objectId] ?: note.objectId, blocks = blocks,
            body = if (blocks != null && blocks.zip(note.blocks.orEmpty()).any { (after, before) ->
                after.text != before.text || after.refObjectId != before.refObjectId
            }) NotebookDocumentCodec.serialize(blocks.map(::domain)) else note.body)
    }
    fun metadata(encoded: String, ids: Map<String, String>, blockIds: Map<String, String>): String {
        val root = runCatching { JSONObject(encoded) }.getOrNull() ?: return encoded
        if (!root.optString("operation").startsWith("file-")) return encoded
        for (key in listOf("pdfSourceId", "batchReceiptId")) {
            if (root.has(key) && !root.isNull(key)) { val id = root.opt(key) as? String ?: continue; root.put(key, ids[id] ?: id) }
        }
        (root.opt("blockId") as? String)?.let { id -> root.put("blockId", blockIds[id] ?: id) }
        root.optJSONArray("sources")?.let { sources -> for (index in 0 until sources.length()) {
            val source = sources.optJSONObject(index) ?: continue; val id = source.opt("id") as? String ?: continue; source.put("id", ids[id] ?: id)
        } }
        return root.toString()
    }
}
