package com.halo.app.data.notebook

/**
 * Local, deterministic outline navigation and heading-section extraction for Notebook.
 * The resulting blocks can be passed through the existing revision-checked ReplaceAll
 * command when the user confirms a new document; source content is never changed here.
 */
object NotebookSectionTools {
    data class Section(
        val headingId: String,
        val title: String,
        val level: Int,
        val start: Int,
        val endExclusive: Int,
        val blockCount: Int,
    )

    fun sections(blocks: List<NoteBlock>): List<Section> {
        val result = mutableListOf<Section>()
        blocks.forEachIndexed { index, block ->
            if (!block.type.isHeading) return@forEachIndexed
            val level = block.type.headingLevel
            val end = ((index + 1) until blocks.size).firstOrNull {
                val next = blocks[it]
                next.type.isHeading && next.type.headingLevel <= level
            } ?: blocks.size
            result += Section(block.id, block.text.ifBlank { "Untitled section" }, level,
                index, end, end - index)
        }
        return result
    }

    fun extract(blocks: List<NoteBlock>, headingId: String): List<NoteBlock> {
        val section = sections(blocks).firstOrNull { it.headingId == headingId }
            ?: return emptyList()
        return DocumentReducer.normalize(blocks.subList(section.start, section.endExclusive))
    }

    /** Stable anchor ordering for a sidebar or keyboard-driven jump menu. */
    fun breadcrumb(blocks: List<NoteBlock>, blockId: String): List<Section> {
        val index = blocks.indexOfFirst { it.id == blockId }
        if (index < 0) return emptyList()
        val active = sections(blocks).filter { index in it.start until it.endExclusive }
        return active.sortedWith(compareBy<Section> { it.level }.thenBy { it.start })
    }
}
