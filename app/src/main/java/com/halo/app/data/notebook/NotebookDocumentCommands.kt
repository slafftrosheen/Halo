package com.halo.app.data.notebook

/** Result of reducing one command against a block list. */
data class DocumentReduction(
    val blocks: List<NoteBlock>,
    val focusBlockId: String? = null,
    val focusOffset: Int? = null,
    val changed: Boolean = true,
) {
    companion object {
        fun unchanged(blocks: List<NoteBlock>) = DocumentReduction(blocks, changed = false)
    }
}

/**
 * Every user-visible mutation of a document, expressed as data.
 *
 * Commands are pure inputs: the reducer produces the next block list and the repository commits it
 * with a revision check. Undo/redo, AI acceptance, import and the markdown source view all flow
 * through the same command path, so there is exactly one writer for document content.
 */
sealed interface DocumentCommand {
    data class InsertBlock(
        val afterBlockId: String?,
        val type: NoteBlockType = NoteBlockType.PARAGRAPH,
        val text: String = "",
        val indent: Int = 0,
    ) : DocumentCommand

    data class SetText(val blockId: String, val text: String) : DocumentCommand

    data class SetType(val blockId: String, val type: NoteBlockType) : DocumentCommand

    data class SetChecked(val blockId: String, val checked: Boolean) : DocumentCommand

    data class SetLanguage(val blockId: String, val language: String?) : DocumentCommand

    data class MoveBlock(val blockId: String, val delta: Int) : DocumentCommand

    data class IndentBlock(val blockId: String, val delta: Int) : DocumentCommand

    data class DeleteBlock(val blockId: String) : DocumentCommand

    /** Join a block into the one above it (backspace at offset 0). */
    data class MergeIntoPrevious(val blockId: String) : DocumentCommand

    /** Split a block at [offset] (Enter in the middle of a block). */
    data class SplitBlock(val blockId: String, val offset: Int) : DocumentCommand

    data class DuplicateBlock(val blockId: String) : DocumentCommand

    data class InsertReference(
        val afterBlockId: String?,
        val objectId: String,
        val label: String?,
    ) : DocumentCommand

    /**
     * Point an existing reference block at a real object. Used to repair legacy `[[object-id]]`
     * placeholders and to relink a reference after its target was deleted.
     */
    data class SetReference(
        val blockId: String,
        val objectId: String,
        val label: String?,
    ) : DocumentCommand

    /** Whole-document replacement: markdown source mode, import and migration. */
    data class ReplaceAll(val blocks: List<NoteBlock>) : DocumentCommand
}

/**
 * Pure document reducer. Deterministic, Android-free and independent of storage, so the same
 * semantics drive the editor, undo/redo and (later) server-side or test fixtures.
 */
object DocumentReducer {
    const val MAX_INDENT = 3
    const val MAX_BLOCKS = 2_000

    fun apply(
        blocks: List<NoteBlock>,
        command: DocumentCommand,
        newId: () -> String,
    ): DocumentReduction = when (command) {
        is DocumentCommand.InsertBlock -> insert(blocks, command, newId)
        is DocumentCommand.SetText -> update(blocks, command.blockId) { it.copy(text = command.text) }
        is DocumentCommand.SetType -> update(blocks, command.blockId) { block ->
            block.copy(
                type = command.type,
                checked = if (command.type == NoteBlockType.CHECKLIST) block.checked else false,
                indent = if (command.type.isList) block.indent else 0,
                language = if (command.type == NoteBlockType.CODE) block.language else null,
            )
        }
        is DocumentCommand.SetChecked -> update(blocks, command.blockId) {
            it.copy(checked = command.checked, type = if (it.type == NoteBlockType.CHECKLIST) it.type else NoteBlockType.CHECKLIST)
        }
        is DocumentCommand.SetLanguage -> update(blocks, command.blockId) { it.copy(language = command.language) }
        is DocumentCommand.MoveBlock -> move(blocks, command.blockId, command.delta)
        is DocumentCommand.IndentBlock -> update(blocks, command.blockId) { block ->
            if (!block.type.isList) block
            else block.copy(indent = (block.indent + command.delta).coerceIn(0, MAX_INDENT))
        }
        is DocumentCommand.DeleteBlock -> delete(blocks, command.blockId)
        is DocumentCommand.MergeIntoPrevious -> merge(blocks, command.blockId)
        is DocumentCommand.SplitBlock -> split(blocks, command.blockId, command.offset, newId)
        is DocumentCommand.DuplicateBlock -> duplicate(blocks, command.blockId, newId)
        is DocumentCommand.InsertReference -> insert(
            blocks,
            DocumentCommand.InsertBlock(
                afterBlockId = command.afterBlockId,
                type = NoteBlockType.REFERENCE,
                text = command.label.orEmpty(),
            ),
            newId,
            refObjectId = command.objectId,
        )
        is DocumentCommand.SetReference -> update(blocks, command.blockId) { block ->
            block.copy(
                type = NoteBlockType.REFERENCE,
                refObjectId = command.objectId,
                text = command.label?.take(200) ?: block.text,
            )
        }
        is DocumentCommand.ReplaceAll -> DocumentReduction(normalize(command.blocks))
    }

    fun normalize(blocks: List<NoteBlock>): List<NoteBlock> = blocks
        .take(MAX_BLOCKS)
        .mapIndexed { index, block ->
            block.copy(
                position = index,
                indent = block.indent.coerceIn(0, MAX_INDENT),
                checked = block.checked && block.type == NoteBlockType.CHECKLIST,
                language = block.language?.takeIf { block.type == NoteBlockType.CODE && it.isNotBlank() },
                refObjectId = block.refObjectId?.takeIf { block.type == NoteBlockType.REFERENCE },
            )
        }

    private fun insert(
        blocks: List<NoteBlock>,
        command: DocumentCommand.InsertBlock,
        newId: () -> String,
        refObjectId: String? = null,
    ): DocumentReduction {
        if (blocks.size >= MAX_BLOCKS) return DocumentReduction.unchanged(blocks)
        val anchorIndex = command.afterBlockId?.let { id -> blocks.indexOfFirst { it.id == id } } ?: -1
        val insertAt = if (anchorIndex < 0) blocks.size else anchorIndex + 1
        val block = NoteBlock(
            id = newId(),
            position = insertAt,
            type = command.type,
            text = command.text,
            checked = false,
            indent = if (command.type.isList) command.indent.coerceIn(0, MAX_INDENT) else 0,
            refObjectId = refObjectId,
        )
        val next = blocks.toMutableList().apply { add(insertAt, block) }
        return DocumentReduction(normalize(next), focusBlockId = block.id, focusOffset = block.text.length)
    }

    private fun update(
        blocks: List<NoteBlock>,
        blockId: String,
        transform: (NoteBlock) -> NoteBlock,
    ): DocumentReduction {
        var changed = false
        val next = blocks.map { block ->
            if (block.id != blockId) {
                block
            } else {
                val mutated = transform(block)
                if (mutated == block) block else mutated.also { changed = true }
            }
        }
        return if (changed) DocumentReduction(next) else DocumentReduction.unchanged(blocks)
    }

    private fun move(blocks: List<NoteBlock>, blockId: String, delta: Int): DocumentReduction {
        val ids = blocks.map(NoteBlock::id).toMutableList()
        val from = ids.indexOf(blockId)
        val to = from + delta
        if (from < 0 || to !in ids.indices) return DocumentReduction.unchanged(blocks)
        val moved = ids.removeAt(from)
        ids.add(to, moved)
        val byId = blocks.associateBy(NoteBlock::id)
        val next = ids.mapNotNull { byId[it] }.mapIndexed { index, block -> block.withPosition(index) }
        return DocumentReduction(next, focusBlockId = blockId, focusOffset = null)
    }

    private fun delete(blocks: List<NoteBlock>, blockId: String): DocumentReduction {
        if (blocks.size <= 1) return DocumentReduction.unchanged(blocks)
        val index = blocks.indexOfFirst { it.id == blockId }
        if (index < 0) return DocumentReduction.unchanged(blocks)
        val next = blocks.filterNot { it.id == blockId }
        val focus = next.getOrNull((index - 1).coerceAtLeast(0))
        return DocumentReduction(normalize(next), focusBlockId = focus?.id, focusOffset = focus?.text?.length)
    }

    private fun merge(blocks: List<NoteBlock>, blockId: String): DocumentReduction {
        val index = blocks.indexOfFirst { it.id == blockId }
        if (index <= 0) return DocumentReduction.unchanged(blocks)
        val previous = blocks[index - 1]
        val current = blocks[index]
        // A merge into a non-text block (divider, code fence) is not meaningful; keep it explicit.
        if (previous.type == NoteBlockType.DIVIDER || previous.type == NoteBlockType.REFERENCE) {
            return DocumentReduction.unchanged(blocks)
        }
        val merged = previous.copy(
            text = when {
                previous.text.isEmpty() -> current.text
                current.text.isEmpty() -> previous.text
                else -> previous.text + "\n" + current.text
            },
        )
        val next = blocks.toMutableList().apply {
            this[index - 1] = merged
            removeAt(index)
        }
        return DocumentReduction(normalize(next), focusBlockId = merged.id, focusOffset = previous.text.length)
    }

    private fun split(
        blocks: List<NoteBlock>,
        blockId: String,
        offset: Int,
        newId: () -> String,
    ): DocumentReduction {
        if (blocks.size >= MAX_BLOCKS) return DocumentReduction.unchanged(blocks)
        val index = blocks.indexOfFirst { it.id == blockId }
        if (index < 0) return DocumentReduction.unchanged(blocks)
        val block = blocks[index]
        if (block.type == NoteBlockType.DIVIDER) {
            return insert(blocks, DocumentCommand.InsertBlock(blockId), newId)
        }
        val at = offset.coerceIn(0, block.text.length)
        val head = block.text.substring(0, at)
        val tail = block.text.substring(at)
        val continuationType = when {
            block.type == NoteBlockType.HEADING ||
                block.type == NoteBlockType.HEADING_2 ||
                block.type == NoteBlockType.HEADING_3 -> NoteBlockType.PARAGRAPH
            else -> block.type
        }
        val splitBlock = NoteBlock(
            id = newId(),
            position = index + 1,
            type = continuationType,
            text = tail,
            checked = false,
            indent = block.indent,
            language = block.language,
        )
        val next = blocks.toMutableList().apply {
            this[index] = block.copy(text = head)
            add(index + 1, splitBlock)
        }
        return DocumentReduction(normalize(next), focusBlockId = splitBlock.id, focusOffset = 0)
    }

    private fun duplicate(blocks: List<NoteBlock>, blockId: String, newId: () -> String): DocumentReduction {
        if (blocks.size >= MAX_BLOCKS) return DocumentReduction.unchanged(blocks)
        val index = blocks.indexOfFirst { it.id == blockId }
        if (index < 0) return DocumentReduction.unchanged(blocks)
        val copy = blocks[index].copy(id = newId(), position = index + 1)
        val next = blocks.toMutableList().apply { add(index + 1, copy) }
        return DocumentReduction(normalize(next), focusBlockId = copy.id, focusOffset = 0)
    }
}

/** Literal, case-insensitive find/replace over the canonical blocks. */
object DocumentSearch {
    fun find(blocks: List<NoteBlock>, query: String): List<DocumentSelection> {
        if (query.isEmpty()) return emptyList()
        val needle = query.lowercase()
        return blocks.flatMap { block ->
            val haystack = block.text.lowercase()
            val matches = mutableListOf<DocumentSelection>()
            var from = 0
            while (true) {
                val index = haystack.indexOf(needle, from)
                if (index < 0) break
                matches += DocumentSelection(block.id, index, index + query.length)
                from = index + query.length.coerceAtLeast(1)
            }
            matches
        }
    }

    fun replaceAll(blocks: List<NoteBlock>, query: String, replacement: String): List<NoteBlock> {
        if (query.isEmpty()) return blocks
        return blocks.map { block ->
            val haystack = block.text.lowercase()
            if (!haystack.contains(query.lowercase())) return@map block
            val builder = StringBuilder()
            var from = 0
            while (true) {
                val index = haystack.indexOf(query.lowercase(), from)
                if (index < 0) break
                builder.append(block.text, from, index).append(replacement)
                from = index + query.length
            }
            builder.append(block.text, from, block.text.length)
            block.copy(text = builder.toString())
        }
    }
}

enum class InlineStyle(val marker: String) {
    BOLD("**"),
    ITALIC("*"),
    STRIKE("~~"),
    CODE("`"),
}

data class InlineEdit(val text: String, val selection: DocumentSelection)

/**
 * Inline emphasis is stored as markdown markers inside the block text rather than as a separate
 * span schema. That keeps one canonical serialization: the source view, export and reimport all see
 * exactly the text the user formatted.
 */
object InlineFormatting {
    fun toggle(
        block: NoteBlock,
        selection: DocumentSelection,
        style: InlineStyle,
    ): InlineEdit {
        val text = block.text
        val range = selection.normalized(text.length)
        val marker = style.marker
        val start = range.start
        val end = range.end

        if (start == end) {
            val inserted = text.substring(0, start) + marker + marker + text.substring(start)
            val caret = start + marker.length
            return InlineEdit(inserted, range.copy(start = caret, end = caret))
        }

        val inner = text.substring(start, end)
        if (inner.length >= marker.length * 2 && inner.startsWith(marker) && inner.endsWith(marker)) {
            val unwrapped = inner.removePrefix(marker).removeSuffix(marker)
            val next = text.substring(0, start) + unwrapped + text.substring(end)
            return InlineEdit(next, range.copy(end = start + unwrapped.length))
        }
        val before = text.substring(0, start)
        val after = text.substring(end)
        if (before.endsWith(marker) && after.startsWith(marker)) {
            val next = before.dropLast(marker.length) + inner + after.drop(marker.length)
            val newStart = start - marker.length
            return InlineEdit(next, range.copy(start = newStart, end = newStart + inner.length))
        }
        val next = before + marker + inner + marker + after
        return InlineEdit(next, range.copy(start = start + marker.length, end = end + marker.length))
    }

    /** Insert or replace a markdown link over the selection. */
    fun link(block: NoteBlock, selection: DocumentSelection, url: String): InlineEdit {
        val text = block.text
        val range = selection.normalized(text.length)
        val label = text.substring(range.start, range.end)
        val rendered = "[${label.ifBlank { url }}]($url)"
        val next = text.substring(0, range.start) + rendered + text.substring(range.end)
        return InlineEdit(next, range.copy(end = range.start + rendered.length))
    }

    /** Remove inline markers for plain-text projection (search snippets, narration). */
    fun stripInlineMarkers(text: String): String = text
        .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
        .replace(Regex("~~(.+?)~~"), "$1")
        .replace(Regex("(?<!\\*)\\*([^*]+)\\*(?!\\*)"), "$1")
        .replace(Regex("`([^`]+)`"), "$1")
        .replace(Regex("\\[([^\\]]+)]\\(([^)]*)\\)"), "$1")
}
