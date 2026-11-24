package com.halo.app.data.notebook

import org.json.JSONArray
import org.json.JSONObject

enum class NoteAlignment(val label: String) {
    LEFT("Left"), CENTER("Center"), RIGHT("Right"), JUSTIFY("Justify")
}

enum class NotePageSize(val label: String, val width: Int, val height: Int) {
    A4("A4", 595, 842), LETTER("Letter", 612, 792), A5("A5", 420, 595)
}

enum class NotePageNumbers(val label: String) {
    NONE("None"), HEADER_LEFT("Header left"), HEADER_CENTER("Header center"), HEADER_RIGHT("Header right"),
    FOOTER_LEFT("Footer left"), FOOTER_CENTER("Footer center"), FOOTER_RIGHT("Footer right")
}

data class NotePageSettings(
    val size: NotePageSize = NotePageSize.A4,
    val landscape: Boolean = false,
    val marginMm: Int = 20,
    val columns: Int = 1,
    val header: String = "",
    val footer: String = "",
    val pageNumbers: NotePageNumbers = NotePageNumbers.NONE,
    val firstPage: Int = 1,
    val trackChanges: Boolean = false,
) {
    val width: Int get() = if (landscape) size.height else size.width
    val height: Int get() = if (landscape) size.width else size.height

    fun encode(): String? = if (this == NotePageSettings()) null else JSONObject()
        .put("size", size.name).put("landscape", landscape).put("marginMm", marginMm).put("columns", columns)
        .put("header", header).put("footer", footer).put("pageNumbers", pageNumbers.name)
        .put("firstPage", firstPage).put("trackChanges", trackChanges).toString()

    companion object {
        fun decode(raw: String?): NotePageSettings {
            if (raw.isNullOrBlank()) return NotePageSettings()
            return runCatching {
                val settings = JSONObject(raw)
                NotePageSettings(
                    size = enumValue(settings, "size", NotePageSize.A4),
                    landscape = settings.optBoolean("landscape"),
                    marginMm = settings.optInt("marginMm", 20).coerceIn(5, 50),
                    columns = settings.optInt("columns", 1).coerceIn(1, 3),
                    header = settings.optString("header").take(160),
                    footer = settings.optString("footer").take(160),
                    pageNumbers = enumValue(settings, "pageNumbers", NotePageNumbers.NONE),
                    firstPage = settings.optInt("firstPage", 1).coerceIn(1, 9999),
                    trackChanges = settings.optBoolean("trackChanges"),
                )
            }.getOrDefault(NotePageSettings())
        }
    }
}

data class NoteFontSpan(val start: Int, val end: Int, val font: String, val size: Int) {
    fun inRange(length: Int) = start >= 0 && end > start && end <= length && size in 8..72 &&
        (font in NOTE_FONT_NAMES || noteCustomFont.matches(font))
    fun encode() = JSONObject().put("start", start).put("end", end).put("font", font).put("size", size)
}

internal val NOTE_FONT_NAMES = listOf("Default", "Serif", "Sans serif", "Monospace", "Sora", "Onest", "Atkinson Hyperlegible")
internal val noteCustomFont = Regex("[a-f0-9]{64}")

data class NoteCommentReply(val author: String, val text: String) {
    fun encode() = JSONObject().put("author", author).put("text", text)
}

data class NoteComment(
    val id: String,
    val start: Int,
    val end: Int,
    val quote: String,
    val author: String,
    val text: String,
    val replies: List<NoteCommentReply> = emptyList(),
    val resolved: Boolean = false,
) {
    fun encode() = JSONObject().put("id", id).put("start", start).put("end", end).put("quote", quote)
        .put("author", author).put("text", text).put("resolved", resolved)
        .put("replies", JSONArray(replies.map(NoteCommentReply::encode)))
}

enum class NoteChangeKind { TEXT, INSERTION, DELETION }

data class NoteChange(val kind: NoteChangeKind, val before: String = "") {
    fun encode() = JSONObject().put("kind", kind.name).put("before", before)
}

data class NoteBlockPresentation(
    val fonts: List<NoteFontSpan> = emptyList(),
    val alignment: NoteAlignment? = null,
    /** 10–30 represents 1.0–3.0 line spacing. */
    val spacing: Int? = null,
    val comments: List<NoteComment> = emptyList(),
    val change: NoteChange? = null,
    val drawing: String? = null,
) {
    fun encode(): String? = if (this == NoteBlockPresentation()) null else JSONObject()
        .put("fonts", JSONArray(fonts.map(NoteFontSpan::encode))).put("alignment", alignment?.name)
        .put("spacing", spacing).put("comments", JSONArray(comments.map(NoteComment::encode)))
        .put("change", change?.encode()).put("drawing", drawing).toString()

    companion object {
        fun decode(raw: String?, length: Int): NoteBlockPresentation {
            if (raw.isNullOrBlank()) return NoteBlockPresentation()
            return runCatching {
                val data = JSONObject(raw)
                NoteBlockPresentation(
                    fonts = data.optJSONArray("fonts").objects(120).map {
                        NoteFontSpan(it.optInt("start"), it.optInt("end"), it.optString("font"), it.optInt("size"))
                    }.filter { it.inRange(length) },
                    alignment = NoteAlignment.entries.firstOrNull { it.name == data.optString("alignment") },
                    spacing = data.optInt("spacing").takeIf { it in 10..30 },
                    comments = data.optJSONArray("comments").objects(40).map(::decodeNoteComment),
                    change = data.optJSONObject("change")?.let { stored ->
                        NoteChange(enumValue(stored, "kind", NoteChangeKind.TEXT), stored.optString("before").take(1_000_000))
                    },
                    drawing = data.optString("drawing").takeIf { com.halo.app.ui.notes.v13.NoteDrawing.parse(it) != null },
                )
            }.getOrDefault(NoteBlockPresentation())
        }
    }
}

private fun decodeNoteComment(data: JSONObject): NoteComment = NoteComment(
    data.optString("id").take(64), data.optInt("start").coerceAtLeast(0), data.optInt("end").coerceAtLeast(0),
    data.optString("quote").take(240), data.optString("author").take(24), data.optString("text").take(1200),
    data.optJSONArray("replies").objects(25).map { NoteCommentReply(it.optString("author").take(24), it.optString("text").take(1200)) },
    data.optBoolean("resolved"),
)

private fun JSONArray?.objects(limit: Int): List<JSONObject> =
    if (this == null) emptyList() else (0 until length().coerceAtMost(limit)).mapNotNull(::optJSONObject)

private inline fun <reified T : Enum<T>> enumValue(data: JSONObject, key: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == data.optString(key) } ?: default

internal fun NoteBlock.fontOver(selection: DocumentSelection, font: String, size: Int): NoteBlock {
    val span = selection.normalized(text.length).let { NoteFontSpan(it.start, it.end, font, size) }
    if (!span.inRange(text.length)) return this
    val outside = presentation.fonts.flatMap { existing ->
        if (existing.end <= span.start || existing.start >= span.end) listOf(existing)
        else listOfNotNull(existing.copy(end = span.start).takeIf { it.start < it.end },
            existing.copy(start = span.end).takeIf { it.start < it.end })
    }
    val fonts = (outside + span).sortedBy(NoteFontSpan::start)
    return if (fonts.size <= 120) copy(presentation = presentation.copy(fonts = fonts)) else this
}

internal data class NoteTextHunk(val before: Int, val after: Int, val removed: String, val inserted: String)

/** Separate disjoint changes so each can be reviewed independently. */
internal fun noteTextHunks(before: String, after: String): List<NoteTextHunk> {
    if (before == after) return emptyList()
    val prefix = before.commonPrefixWith(after).length
    val suffix = before.substring(prefix).commonSuffixWith(after.substring(prefix)).length
    val removed = before.substring(prefix, before.length - suffix)
    val inserted = after.substring(prefix, after.length - suffix)
    if (removed.isEmpty() || inserted.isEmpty() || removed.length + inserted.length > 8000)
        return listOf(NoteTextHunk(prefix, prefix, removed, inserted))
    val words = Regex("\\p{L}+[\\p{M}\\p{L}]*|\\p{N}+|\\s+|.")
    val a = words.findAll(removed).map { it.value }.toList()
    val b = words.findAll(inserted).map { it.value }.toList()
    if (a.size * b.size > 40_000) return listOf(NoteTextHunk(prefix, prefix, removed, inserted))
    val lcs = Array(a.size + 1) { IntArray(b.size + 1) }
    for (i in a.indices.reversed()) for (j in b.indices.reversed()) {
        lcs[i][j] = if (a[i] == b[j]) 1 + lcs[i + 1][j + 1] else maxOf(lcs[i + 1][j], lcs[i][j + 1])
    }
    val hunks = mutableListOf<NoteTextHunk>()
    var i = 0
    var j = 0
    var from = prefix
    var to = prefix
    while (i < a.size || j < b.size) {
        if (i < a.size && j < b.size && a[i] == b[j]) {
            from += a[i++].length
            to += b[j++].length
        } else {
            val startBefore = from
            val startAfter = to
            while (i < a.size || j < b.size) {
                if (i < a.size && j < b.size && a[i] == b[j]) break
                if (i < a.size && (j >= b.size || lcs[i + 1][j] >= lcs[i][j + 1])) from += a[i++].length
                else to += b[j++].length
            }
            hunks += NoteTextHunk(startBefore, startAfter, before.substring(startBefore, from), after.substring(startAfter, to))
        }
    }
    return hunks
}

internal fun NoteBlock.withEditedText(value: String): NoteBlock {
    if (text == value) return this
    val start = text.commonPrefixWith(value).length
    val suffix = text.substring(start).commonSuffixWith(value.substring(start)).length
    val oldEnd = text.length - suffix
    val newEnd = value.length - suffix
    val delta = newEnd - oldEnd
    fun offset(position: Int, end: Boolean): Int = when {
        position <= start -> position
        position >= oldEnd -> position + delta
        end -> newEnd
        else -> start
    }
    val fonts = presentation.fonts.map {
        it.copy(start = offset(it.start, false), end = offset(it.end, true))
    }.filter { it.inRange(value.length) }
    val comments = presentation.comments.map {
        it.copy(start = offset(it.start, false).coerceIn(0, value.length), end = offset(it.end, true).coerceIn(0, value.length))
    }
    return copy(text = value, presentation = presentation.copy(fonts = fonts, comments = comments))
}

internal fun trackNoteChanges(before: List<NoteBlock>, reduced: List<NoteBlock>): List<NoteBlock> {
    val previous = before.associateBy(NoteBlock::id)
    val changed = reduced.map { next ->
        val old = previous[next.id]
        val previousChange = old?.presentation?.change
        val change = when {
            old == null -> NoteChange(NoteChangeKind.INSERTION)
            previousChange?.kind == NoteChangeKind.TEXT && previousChange.before == next.text -> null
            previousChange != null -> previousChange
            old.text != next.text -> NoteChange(NoteChangeKind.TEXT, old.text)
            else -> null
        }
        next.copy(presentation = next.presentation.copy(change = change))
    }.toMutableList()
    val remaining = reduced.mapTo(hashSetOf(), NoteBlock::id)
    before.forEachIndexed { index, block ->
        if (block.id !in remaining && block.presentation.change?.kind != NoteChangeKind.INSERTION) {
            val original = block.presentation.change?.before ?: block.text
            val originalBlock = block.withEditedText(original)
            val deletion = originalBlock.copy(presentation = originalBlock.presentation.copy(change = NoteChange(NoteChangeKind.DELETION, original)))
            changed.add(index.coerceAtMost(changed.size), deletion)
        }
    }
    return changed
}

internal fun resolveNoteChange(block: NoteBlock, accept: Boolean, index: Int?): NoteBlock? {
    val change = block.presentation.change ?: return block
    fun clear(value: NoteBlock) = value.copy(presentation = value.presentation.copy(change = null))
    return when (change.kind) {
        NoteChangeKind.INSERTION -> if (accept) clear(block) else null
        NoteChangeKind.DELETION -> if (accept) null else clear(block)
        NoteChangeKind.TEXT -> {
            if (index == null) return clear(if (accept) block else block.withEditedText(change.before))
            val hunk = noteTextHunks(change.before, block.text).getOrNull(index) ?: return block
            val baseline = if (accept) change.before.replaceRange(hunk.before, hunk.before + hunk.removed.length, hunk.inserted) else change.before
            val current = if (accept) block else block.withEditedText(block.text.replaceRange(hunk.after, hunk.after + hunk.inserted.length, hunk.removed))
            if (baseline == current.text) clear(current)
            else current.copy(presentation = current.presentation.copy(change = change.copy(before = baseline)))
        }
    }
}

internal fun NoteBlock.isPendingDeletion() = presentation.change?.kind == NoteChangeKind.DELETION

/** Ordinary edits outside a pending change remain ordinary after tracking is switched off. */
internal fun retainUntrackedEdits(before: List<NoteBlock>, reduced: List<NoteBlock>): List<NoteBlock> {
    val previous = before.filter { it.presentation.change?.kind == NoteChangeKind.TEXT }.associateBy(NoteBlock::id)
    if (previous.isEmpty()) return reduced
    return reduced.map { block ->
        val old = previous[block.id]
        val change = old?.presentation?.change
        if (old == null || change == null || old.text == block.text) block
        else {
            val pending = noteTextHunks(change.before, old.text)
            var baseline = change.before
            for (edit in noteTextHunks(old.text, block.text).asReversed()) {
                val end = edit.before + edit.removed.length
                val overlaps = pending.any { mark ->
                    val markEnd = mark.after + mark.inserted.length
                    edit.before < markEnd && end > mark.after ||
                        mark.inserted.isEmpty() && mark.after in edit.before..end
                }
                if (!overlaps) {
                    val adjustment = pending.filter { it.after + it.inserted.length <= edit.before }
                        .sumOf { it.removed.length - it.inserted.length }
                    val start = edit.before + adjustment
                    if (start >= 0 && start + edit.removed.length <= baseline.length) {
                        baseline = baseline.replaceRange(start, start + edit.removed.length, edit.inserted)
                    }
                }
            }
            block.copy(presentation = block.presentation.copy(change = change.copy(before = baseline).takeUnless { baseline == block.text }))
        }
    }
}

/** Keep exact-matching blocks first, then reuse the position of edited paragraphs of the same kind. */
internal fun retainNoteAnchors(before: List<NoteBlock>, parsed: List<NoteBlock>): List<NoteBlock> {
    val visible = before.filterNot(NoteBlock::isPendingDeletion)
    val matches = visible.groupBy { Triple(it.type, it.text, it.refObjectId) }
        .mapValues { (_, group) -> ArrayDeque(group) }
    val matched = parsed.map { next -> matches[Triple(next.type, next.text, next.refObjectId)]?.removeFirstOrNull() }
    val used = matched.filterNotNull().mapTo(hashSetOf(), NoteBlock::id)
    val result = parsed.mapIndexed { index, next ->
        val old = matched[index] ?: visible.getOrNull(index)?.takeIf { it.type == next.type && used.add(it.id) }
        if (old == null) next else next.copy(id = old.id, presentation = old.withEditedText(next.text).presentation)
    }.toMutableList()
    before.forEachIndexed { index, block ->
        if (block.isPendingDeletion()) result.add(index.coerceAtMost(result.size), block)
    }
    return result
}
