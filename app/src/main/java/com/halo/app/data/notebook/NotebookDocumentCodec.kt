package com.halo.app.data.notebook

object NotebookDocumentCodec {
    fun serialize(blocks: List<NoteBlock>): String {
        return blocks.joinToString("\n\n") { block ->
            when (block.type) {
                NoteBlockType.HEADING -> "# ${block.text}"
                NoteBlockType.CHECKLIST -> if (block.checked) "- [x] ${block.text}" else "- [ ] ${block.text}"
                NoteBlockType.CODE -> "```\n${block.text}\n```"
                NoteBlockType.QUOTE -> "> ${block.text}"
                else -> block.text
            }
        }.trim()
    }
}
