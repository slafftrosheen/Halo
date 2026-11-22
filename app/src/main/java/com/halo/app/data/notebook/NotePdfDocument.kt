package com.halo.app.data.notebook

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.MetricAffectingSpan
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.roundToInt

internal class NotePdfDocument(context: Context) {
    private val fonts = NoteFontLibrary(context)

    private data class TextRun(val layout: StaticLayout, val left: Int, val top: Int, val firstLine: Int, val lastLine: Int)
    private data class PageGeometry(val width: Int, val height: Int, val margin: Int, val bodyTop: Int,
        val bodyBottom: Int, val columnWidth: Int, val gap: Int)

    suspend fun render(note: NotebookDocument, includeComments: Boolean, first: Int?, last: Int?): ByteArray {
        require(first == null || first >= 1)
        require(last == null || last >= (first ?: 1))
        val settings = note.pageSettings
        val geometry = geometry(settings)
        val pages = paginate(note, settings, geometry, includeComments)
        val from = (first ?: 1).coerceAtMost(pages.size + 1)
        val through = (last ?: pages.size).coerceAtMost(pages.size)
        require(from <= through) { "The selected page range is outside this note." }
        val document = PdfDocument()
        try {
            for (ordinal in from..through) {
                currentCoroutineContext().ensureActive()
                val page = document.startPage(PdfDocument.PageInfo.Builder(geometry.width, geometry.height, ordinal).create())
                try {
                    drawFurniture(page.canvas, settings, geometry, settings.firstPage + ordinal - 1)
                    pages[ordinal - 1].forEach { run -> drawRun(page.canvas, run) }
                } finally {
                    document.finishPage(page)
                }
            }
            return ByteArrayOutputStream().use { output -> document.writeTo(output); output.toByteArray() }
        } finally {
            document.close()
        }
    }

    private fun geometry(settings: NotePageSettings): PageGeometry {
        val margin = (settings.marginMm * 72f / 25.4f).roundToInt()
        val spacing = if (settings.columns == 1) 0 else 14
        val topRoom = if (settings.header.isNotBlank() || settings.pageNumbers.name.startsWith("HEADER")) 16 else 0
        val bottomRoom = if (settings.footer.isNotBlank() || settings.pageNumbers.name.startsWith("FOOTER")) 16 else 0
        val column = (settings.width - 2 * margin - (settings.columns - 1) * spacing) / settings.columns
        return PageGeometry(settings.width, settings.height, margin, margin + topRoom,
            settings.height - margin - bottomRoom, column, spacing)
    }

    private suspend fun paginate(note: NotebookDocument, settings: NotePageSettings, geometry: PageGeometry,
        includeComments: Boolean): List<List<TextRun>> {
        val pages = mutableListOf(mutableListOf<TextRun>())
        var column = 0
        var y = geometry.bodyTop
        fun advance() {
            column++
            y = geometry.bodyTop
            if (column == settings.columns) {
                require(pages.size < 300) { "This note is too long to export as one PDF." }
                pages.add(mutableListOf())
                column = 0
            }
        }
        fun add(letters: CharSequence, block: NoteBlock?, heading: Boolean = false, comment: Boolean = false) {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (comment) Color.DKGRAY else Color.BLACK
                textSize = if (heading) 20f else if (comment) 9f else when (block?.type) {
                    NoteBlockType.HEADING -> 17f
                    NoteBlockType.HEADING_2 -> 15f
                    NoteBlockType.HEADING_3 -> 13f
                    else -> 11f
                }
                typeface = when {
                    heading || block?.type?.isHeading == true -> Typeface.DEFAULT_BOLD
                    block?.type == NoteBlockType.CODE -> Typeface.MONOSPACE
                    block?.type == NoteBlockType.QUOTE || comment -> Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                    else -> Typeface.DEFAULT
                }
            }
            val presentation = block?.presentation
            val indentation = (block?.indent ?: 0) * 7
            val layout = StaticLayout.Builder.obtain(letters, 0, letters.length, paint, geometry.columnWidth - indentation)
                .setIncludePad(false)
                .setAlignment(when (presentation?.alignment) {
                    NoteAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
                    NoteAlignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
                    else -> Layout.Alignment.ALIGN_NORMAL
                })
                .setJustificationMode(if (presentation?.alignment == NoteAlignment.JUSTIFY)
                    Layout.JUSTIFICATION_MODE_INTER_WORD else Layout.JUSTIFICATION_MODE_NONE)
                .setLineSpacing(0f, (presentation?.spacing ?: 14) / 10f)
                .build()
            var line = 0
            while (line < layout.lineCount) {
                val lineTop = layout.getLineTop(line)
                val available = geometry.bodyBottom - y
                var end = line
                while (end < layout.lineCount && layout.getLineTop(end + 1) - lineTop <= available) end++
                if (end == line && y != geometry.bodyTop) {
                    advance()
                    continue
                }
                if (end == line) end++
                val left = geometry.margin + column * (geometry.columnWidth + geometry.gap) + indentation
                pages.last().add(TextRun(layout, left, y, line, end))
                y += layout.getLineTop(end) - lineTop
                line = end
                if (line < layout.lineCount) advance()
            }
            y += if (heading) 14 else 6
        }
        if (note.title.isNotBlank() && !note.titleIsDerived) add(note.title, null, heading = true)
        for (block in note.blocks) {
            currentCoroutineContext().ensureActive()
            if (block.isPendingDeletion()) continue
            if (block.text.isEmpty() && block.type != NoteBlockType.DIVIDER) continue
            add(printable(block), block)
            if (includeComments) for (comment in block.presentation.comments) {
                add(comment.author + ": " + comment.text, null, comment = true)
                for (reply in comment.replies) add("  " + reply.author + ": " + reply.text, null, comment = true)
            }
        }
        return pages
    }

    private fun printable(block: NoteBlock): CharSequence {
        if (block.type == NoteBlockType.DIVIDER) return "────────────────────"
        val prefix = when (block.type) {
            NoteBlockType.BULLET_LIST -> "• "
            NoteBlockType.NUMBERED_LIST -> "${block.position + 1}. "
            NoteBlockType.CHECKLIST -> if (block.checked) "☑ " else "☐ "
            else -> ""
        }
        val text = SpannableStringBuilder(prefix)
        val raw = block.text
        var cursor = 0
        fun append(from: Int, to: Int) {
            val start = text.length
            text.append(raw, from, to)
            block.presentation.fonts.forEach { span ->
                val first = maxOf(from, span.start)
                val last = minOf(to, span.end)
                if (first < last) {
                    val startOffset = start + first - from
                    val endOffset = start + last - from
                    text.setSpan(AbsoluteSizeSpan(span.size), startOffset, endOffset, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    text.setSpan(PrintTypeface(fonts.typeface(span.font)), startOffset, endOffset, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
        if (block.type == NoteBlockType.CODE) append(0, raw.length)
        else {
            for (item in inlineMarks.findAll(raw)) {
                append(cursor, item.range.first)
                val visible = item.groups[2] ?: item.groups[3] ?: item.groups[4] ?: item.groups[5]
                if (visible != null) {
                    val start = text.length
                    append(visible.range.first, visible.range.last + 1)
                    if (start < text.length) {
                        val span = when (item.groups[1]?.value) {
                            "**" -> android.text.style.StyleSpan(Typeface.BOLD)
                            "*" -> android.text.style.StyleSpan(Typeface.ITALIC)
                            "~~" -> android.text.style.StrikethroughSpan()
                            "==" -> android.text.style.BackgroundColorSpan(0xfff4df9b.toInt())
                            "`" -> PrintTypeface(Typeface.MONOSPACE)
                            else -> android.text.style.UnderlineSpan()
                        }
                        text.setSpan(span, start, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
                cursor = item.range.last + 1
            }
            append(cursor, raw.length)
        }
        return text
    }

    private fun drawRun(canvas: Canvas, run: TextRun) {
        val inset = run.layout.getLineTop(run.firstLine)
        val height = run.layout.getLineTop(run.lastLine) - inset
        canvas.save()
        canvas.clipRect(run.left, run.top, run.left + run.layout.width, run.top + height)
        canvas.translate(run.left.toFloat(), (run.top - inset).toFloat())
        run.layout.draw(canvas)
        canvas.restore()
    }

    private fun drawFurniture(canvas: Canvas, settings: NotePageSettings, geometry: PageGeometry, currentPage: Int) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = Color.DKGRAY }
        fun row(raw: String, y: Float, numberAt: String) {
            val numberWidth = if (numberAt.isEmpty()) 0 else paint.measureText(currentPage.toString()).toInt() + 12
            val position = when {
                numberAt.endsWith("LEFT") -> geometry.margin.toFloat()
                numberAt.endsWith("CENTER") -> geometry.width / 2f
                else -> (geometry.width - geometry.margin).toFloat()
            }
            paint.textAlign = Paint.Align.LEFT
            val left = geometry.margin + if (numberAt.endsWith("LEFT")) numberWidth else 0
            val max = if (numberAt.endsWith("CENTER")) geometry.width / 2 - left - numberWidth
                else geometry.width - geometry.margin - left - numberWidth
            val label = android.text.TextUtils.ellipsize(raw, paint, max.coerceAtLeast(0).toFloat(), android.text.TextUtils.TruncateAt.END)
            canvas.drawText(label.toString(), left.toFloat(), y, paint)
            if (numberAt.isNotEmpty()) {
                paint.textAlign = when {
                    numberAt.endsWith("LEFT") -> Paint.Align.LEFT
                    numberAt.endsWith("CENTER") -> Paint.Align.CENTER
                    else -> Paint.Align.RIGHT
                }
                canvas.drawText(currentPage.toString(), position, y, paint)
            }
        }
        val position = settings.pageNumbers.name
        row(settings.header, (geometry.margin + 9).toFloat(), position.takeIf { it.startsWith("HEADER") }.orEmpty())
        row(settings.footer, (geometry.height - geometry.margin - 4).toFloat(), position.takeIf { it.startsWith("FOOTER") }.orEmpty())
    }

    private class PrintTypeface(private val font: Typeface) : MetricAffectingSpan() {
        override fun updateMeasureState(textPaint: TextPaint) { textPaint.typeface = font }
        override fun updateDrawState(textPaint: TextPaint) { textPaint.typeface = font }
    }

    companion object {
        val inlineMarks = Regex("""(\*\*|~~|==|`|\*)([^\n]+?)\1|<u>(.*?)</u>|\[([^]]+)]\([^)]*\)|!\[([^]]*)]\([^)]*\)""")
    }
}
