package com.halo.app.data.notebook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotebookSectionToolsTest {
    private fun block(id: String, type: NoteBlockType, position: Int) =
        NoteBlock(id = id, type = type, text = id, position = position)

    @Test fun nestedSectionsStopAtSameOrHigherHeading() {
        val blocks = listOf(
            block("Intro", NoteBlockType.HEADING, 0),
            block("text", NoteBlockType.PARAGRAPH, 1),
            block("Detail", NoteBlockType.HEADING_2, 2),
            block("body", NoteBlockType.PARAGRAPH, 3),
            block("Next", NoteBlockType.HEADING, 4),
        )
        val sections = NotebookSectionTools.sections(blocks)
        assertEquals(3, sections.size)
        assertEquals(4, sections[0].endExclusive)
        assertEquals(4, sections[1].endExclusive)
        assertEquals(5, sections[2].endExclusive)
        assertEquals(listOf("Intro", "Detail"), NotebookSectionTools.breadcrumb(blocks, "body").map { it.title })
        assertEquals(listOf("Detail", "body"), NotebookSectionTools.extract(blocks, "Detail").map { it.id })
        assertEquals(listOf(0, 1), NotebookSectionTools.extract(blocks, "Detail").map { it.position })
    }

    @Test fun unknownHeadingDoesNotExtractContent() {
        val blocks = listOf(block("body", NoteBlockType.PARAGRAPH, 0))
        assertTrue(NotebookSectionTools.extract(blocks, "missing").isEmpty())
        assertTrue(NotebookSectionTools.breadcrumb(blocks, "body").isEmpty())
    }
}
