package com.halo.app.data.notebook

/**
 * The single markdown codec used by the editor, the source view, import and the derived
 * `notes.body` projection.
 *
 * Guarantees:
 * - [serialize] is deterministic for a given block list.
 * - [parse] never drops text it does not understand. Unrepresentable constructs become literal
 *   paragraph blocks and are reported through [DocumentParseResult.lossy], so the UI can refuse a
 *   destructive mode conversion instead of quietly flattening a document.
 */
object NotebookDocumentCodec {
    private val heading = Regex("^(#{1,6})\\s+(.*)$")
    private val checklist = Regex("^(\\s*)[-*+]\\s+\\[([ xX])]\\s?(.*)$")
    private val bullet = Regex("^(\\s*)[-*+]\\s+(.*)$")
    private val numbered = Regex("^(\\s*)(\\d+)[.)]\\s+(.*)$")
    private val quote = Regex("^(\\s*)>\\s?(.*)$")
    private val divider = Regex("^\\s*(-{3,}|\\*{3,}|_{3,})\\s*$")
    private val reference = Regex("^\\[\\[([^\\]|]+)(?:\\|([^\\]]*))?]]$")
    private val rawHtml = Regex("^\\s*<[/!a-zA-Z][^>]*>?.*$")
    private val tableRow = Regex("^\\s*\\|.*\\|\\s*$")
    private val mathFence = Regex("^\\s*(\\$\\$|\\\\\\[|\\\\\\()")
    private val imageOrMedia = Regex("^\\s*!\\[[^\\]]*]\\([^)]*\\)\\s*$")
    private val fencedCode = Regex("^\\s*```(.*)$")

    fun serialize(blocks: List<NoteBlock>): String = serializeAll(blocks).trim()

    private fun serializeAll(blocks: List<NoteBlock>): String = buildString {
        var numberedRun = mutableMapOf<Int, Int>()
        blocks.forEachIndexed { index, block ->
            if (index > 0) append("\n\n")
            if (block.type != NoteBlockType.NUMBERED_LIST) numberedRun = mutableMapOf()
            append(serializeBlock(block, numberedRun))
        }
    }

    private fun serializeBlock(block: NoteBlock, numberedRun: MutableMap<Int, Int>): String {
        val indent = if (block.type.isList) "  ".repeat(block.indent.coerceIn(0, DocumentReducer.MAX_INDENT)) else ""
        return when (block.type) {
            NoteBlockType.PARAGRAPH -> block.text
            NoteBlockType.HEADING -> "# ${block.text}"
            NoteBlockType.HEADING_2 -> "## ${block.text}"
            NoteBlockType.HEADING_3 -> "### ${block.text}"
            NoteBlockType.BULLET_LIST -> "$indent- ${block.text}"
            NoteBlockType.NUMBERED_LIST -> {
                val ordinal = (numberedRun[block.indent] ?: 0) + 1
                numberedRun[block.indent] = ordinal
                "$indent$ordinal. ${block.text}"
            }
            NoteBlockType.CHECKLIST -> "$indent- [${if (block.checked) "x" else " "}] ${block.text}"
            NoteBlockType.QUOTE -> "$indent> ${block.text}"
            NoteBlockType.CODE -> "```${block.language.orEmpty()}\n${block.text}\n```"
            NoteBlockType.DIVIDER -> "---"
            NoteBlockType.REFERENCE -> {
                val target = block.refObjectId?.takeIf { it.isNotBlank() }
                if (target == null) "[[${block.text}]]"
                else if (block.text.isBlank()) "[[$target]]" else "[[$target|${block.text}]]"
            }
        }
    }

    fun parse(markdown: String, newId: () -> String): DocumentParseResult {
        val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n")
        val blocks = mutableListOf<NoteBlock>()
        val preserved = mutableListOf<String>()
        var lossy = false
        var index = 0

        fun add(
            type: NoteBlockType,
            text: String,
            indent: Int = 0,
            checked: Boolean = false,
            refObjectId: String? = null,
            language: String? = null,
        ) {
            blocks += NoteBlock(
                id = newId(),
                position = blocks.size,
                type = type,
                text = text,
                checked = checked,
                indent = indent,
                refObjectId = refObjectId,
                language = language,
            )
        }

        while (index < lines.size) {
            val line = lines[index]
            val fence = fencedCode.find(line)
            if (fence != null) {
                val language = fence.groupValues[1].trim().takeIf { it.isNotEmpty() }
                val body = mutableListOf<String>()
                index++
                var closed = false
                while (index < lines.size) {
                    if (fencedCode.matches(lines[index])) {
                        closed = true
                        index++
                        break
                    }
                    body += lines[index]
                    index++
                }
                if (!closed) {
                    lossy = true
                    val literal = (listOf(line) + body).joinToString("\n")
                    preserved += listOf(line) + body
                    // A malformed fence is not a code block. Preserve every source character in a
                    // literal paragraph so forced conversion cannot silently delete the opener.
                    add(NoteBlockType.PARAGRAPH, literal)
                    continue
                }
                add(NoteBlockType.CODE, body.joinToString("\n"), language = language)
                continue
            }

            when {
                line.isBlank() -> index++

                divider.matches(line) -> {
                    add(NoteBlockType.DIVIDER, "")
                    index++
                }

                reference.matches(line) -> {
                    val match = reference.find(line)!!
                    val target = match.groupValues[1].trim()
                    val label = match.groupValues[2].trim()
                    add(NoteBlockType.REFERENCE, label, refObjectId = target)
                    index++
                }

                checklist.matches(line) -> {
                    val match = checklist.find(line)!!
                    add(
                        type = NoteBlockType.CHECKLIST,
                        text = match.groupValues[3],
                        indent = indentLevel(match.groupValues[1]),
                        checked = match.groupValues[2].equals("x", ignoreCase = true),
                    )
                    index++
                }

                heading.matches(line) -> {
                    val match = heading.find(line)!!
                    val level = match.groupValues[1].length
                    val text = match.groupValues[2]
                    if (level > 3) {
                        lossy = true
                        preserved += line
                    }
                    add(NoteBlockType.PARAGRAPH.withHeadingLevel(level), text)
                    index++
                }

                quote.matches(line) -> {
                    val match = quote.find(line)!!
                    add(NoteBlockType.QUOTE, match.groupValues[2], indent = indentLevel(match.groupValues[1]))
                    index++
                }

                bullet.matches(line) -> {
                    val match = bullet.find(line)!!
                    add(NoteBlockType.BULLET_LIST, match.groupValues[2], indent = indentLevel(match.groupValues[1]))
                    index++
                }

                numbered.matches(line) -> {
                    val match = numbered.find(line)!!
                    add(NoteBlockType.NUMBERED_LIST, match.groupValues[3], indent = indentLevel(match.groupValues[1]))
                    index++
                }

                tableRow.matches(line) || mathFence.containsMatchIn(line) ||
                    imageOrMedia.matches(line) || rawHtml.matches(line) -> {
                    // Unsupported structure is preserved verbatim as literal text. It round-trips
                    // unchanged, and `lossy` tells the UI this mode conversion is not faithful.
                    lossy = true
                    preserved += line
                    add(NoteBlockType.PARAGRAPH, line.trim())
                    index++
                }

                else -> {
                    val paragraph = mutableListOf(line)
                    index++
                    while (index < lines.size && lines[index].isNotBlank() && !isSpecial(lines[index])) {
                        paragraph += lines[index]
                        index++
                    }
                    add(NoteBlockType.PARAGRAPH, paragraph.joinToString("\n").trimEnd())
                }
            }
        }

        if (blocks.isEmpty()) add(NoteBlockType.PARAGRAPH, "")
        return DocumentParseResult(
            blocks = DocumentReducer.normalize(blocks),
            lossy = lossy,
            preservedRawLines = preserved,
        )
    }

    private fun isSpecial(line: String): Boolean =
        fencedCode.containsMatchIn(line) ||
            heading.matches(line) ||
            checklist.matches(line) ||
            bullet.matches(line) ||
            numbered.matches(line) ||
            quote.matches(line) ||
            divider.matches(line) ||
            reference.matches(line) ||
            tableRow.matches(line) ||
            mathFence.containsMatchIn(line) ||
            imageOrMedia.matches(line) ||
            rawHtml.matches(line)

    private fun indentLevel(prefix: String): Int {
        val spaces = prefix.count { it == ' ' } + prefix.count { it == '\t' } * 2
        return (spaces / 2).coerceIn(0, DocumentReducer.MAX_INDENT)
    }

    /** Plain text projection for narration, snippets and search: markup removed, structure kept. */
    fun plainText(blocks: List<NoteBlock>): String = blocks
        .joinToString("\n") { block ->
            when (block.type) {
                NoteBlockType.DIVIDER -> "—"
                NoteBlockType.REFERENCE -> block.text.ifBlank { block.refObjectId.orEmpty() }
                else -> InlineFormatting.stripInlineMarkers(block.text)
            }
        }
        .trim()

    /** Derived title policy: first heading, else first non-blank paragraph line. */
    fun deriveTitle(blocks: List<NoteBlock>): String {
        val heading = blocks.firstOrNull { it.type.isHeading && it.text.isNotBlank() }
        if (heading != null) return cleanTitle(heading.text)
        val paragraph = blocks.firstOrNull { it.type == NoteBlockType.PARAGRAPH && it.text.isNotBlank() }
            ?: blocks.firstOrNull { it.text.isNotBlank() }
        return cleanTitle(paragraph?.text.orEmpty())
    }

    private fun cleanTitle(raw: String): String {
        val firstLine = raw.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        return InlineFormatting.stripInlineMarkers(firstLine)
            .trim()
            .removePrefix("#")
            .trim()
            .take(80)
            .ifEmpty { "Untitled note" }
    }
}

data class DocumentParseResult(
    val blocks: List<NoteBlock>,
    val lossy: Boolean,
    val preservedRawLines: List<String>,
)
