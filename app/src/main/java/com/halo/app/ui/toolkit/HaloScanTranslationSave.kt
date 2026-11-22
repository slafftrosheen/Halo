package com.halo.app.ui.toolkit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.annotation.MainThread
import com.halo.app.di.ManualDependencyGraph as Graph
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.math.min

internal enum class ScanTranslationPhase { READY, KEEPING, KEPT }

internal data class ScanTranslationReceipt(
    val sessionToken: String = UUID.randomUUID().toString(),
    val sourceVersion: Long = 1L,
    val phase: ScanTranslationPhase = ScanTranslationPhase.READY,
    val objectId: String? = null,
)

/** Exact foreground translation with one Workspace write owner for native controls and tool calls. */
internal class HaloScanTranslationDraft(
    private val context: Context,
    private val bitmap: Bitmap,
    private val translated: List<HaloScanLine>,
    private val from: String,
    private val to: String,
    private val title: String,
    private val adultRevision: Long,
) {
    private val mutable = MutableStateFlow(ScanTranslationReceipt())
    val state = mutable.asStateFlow()

    @MainThread
    suspend fun keep(observed: ScanTranslationReceipt): ScanTranslationReceipt? {
        val current = mutable.value
        if (current != observed || current.phase != ScanTranslationPhase.READY || !HaloScanTranslationSessions.isCurrent(this)) return null
        Graph.kidsPreferences.requireAdultAction(adultRevision)
        val keeping = current.copy(sourceVersion = current.sourceVersion + 1, phase = ScanTranslationPhase.KEEPING)
        mutable.value = keeping
        try {
            // Once admitted by the exact live view, finish the local commit and record its receipt even if
            // the page closes. The existing Workspace import owns rollback of partial writes.
            return withContext(NonCancellable) {
                val id = importTranslation()
                keeping.copy(sourceVersion = keeping.sourceVersion + 1, phase = ScanTranslationPhase.KEPT,
                    objectId = id).also { mutable.value = it }
            }
        } catch (failure: Exception) {
            if (mutable.value == keeping)
                mutable.value = keeping.copy(sourceVersion = keeping.sourceVersion + 1, phase = ScanTranslationPhase.READY)
            throw failure
        }
    }

    private suspend fun importTranslation(): String {
        val text = translated.groupBy(HaloScanLine::paragraph).values.joinToString("\n\n") { paragraph ->
            paragraph.joinToString(" ") { it.text }
        }
        require(text.isNotBlank() && text.length <= 50_000)
        val file = withContext(Dispatchers.IO) {
            val staging = File(context.filesDir, "workspace/owned/scan-staging")
            check(staging.isDirectory || staging.mkdirs())
            File.createTempFile("translation-", ".pdf", staging).also { destination ->
                try { writeTranslationPdf(destination, bitmap, text, "${from.uppercase()} → ${to.uppercase()}") }
                catch (failure: Exception) { destination.delete(); throw failure }
            }
        }
        try {
            Graph.kidsPreferences.requireAdultAction(adultRevision)
            return Graph.workspaceUseCases.importForCanvas(captureFileUri(context, file), "$title.pdf", "application/pdf").objectId.value
        } finally { withContext(Dispatchers.IO) { file.delete() } }
    }
}

internal object HaloScanTranslationSessions {
    private var active: HaloScanTranslationDraft? = null
    @MainThread fun enter(draft: HaloScanTranslationDraft) { active = draft }
    @MainThread fun leave(draft: HaloScanTranslationDraft) { if (active === draft) active = null }
    @MainThread fun isCurrent(draft: HaloScanTranslationDraft) = active === draft
    @MainThread fun current() = active
}

private fun writeTranslationPdf(destination: File, bitmap: Bitmap, text: String, language: String) {
    val width = 612
    val height = 792
    val margin = 42
    val pdf = PdfDocument()
    try {
        val first = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, 1).create())
        val scale = min((width - margin * 2).toFloat() / bitmap.width, (height - margin * 2).toFloat() / bitmap.height)
        val left = (width - bitmap.width * scale) / 2f
        val top = (height - bitmap.height * scale) / 2f
        first.canvas.drawColor(Color.WHITE)
        first.canvas.drawBitmap(bitmap, null, RectF(left, top, width - left, height - top),
            Paint(Paint.FILTER_BITMAP_FLAG))
        pdf.finishPage(first)
        appendSelectablePdfText(pdf, text, language, 2)
        destination.outputStream().use(pdf::writeTo)
    } finally { pdf.close() }
}

internal fun appendSelectablePdfText(pdf: PdfDocument, text: String, title: String, firstPage: Int): Int {
    if (text.isBlank()) return firstPage
    val width = 612
    val height = 792
    val margin = 42
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 18f }
    val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width - margin * 2)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setLineSpacing(3f, 1f).build()
    val header = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 18f; typeface = Typeface.DEFAULT_BOLD }
    val textTop = margin + 32
    val bodyHeight = height - textTop - margin
    var line = 0
    var page = firstPage
    while (line < layout.lineCount) {
        val firstTop = layout.getLineTop(line)
        var endExclusive = line + 1
        while (endExclusive < layout.lineCount && layout.getLineTop(endExclusive + 1) - firstTop <= bodyHeight)
            endExclusive++
        val output = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, page++).create())
        val canvas = output.canvas
        canvas.drawColor(Color.WHITE)
        canvas.drawText(title, margin.toFloat(), margin + 10f, header)
        canvas.save()
        canvas.clipRect(margin.toFloat(), textTop.toFloat(), width - margin.toFloat(),
            (textTop + layout.getLineTop(endExclusive) - firstTop).toFloat())
        canvas.translate(margin.toFloat(), (textTop - firstTop).toFloat())
        layout.draw(canvas)
        canvas.restore()
        pdf.finishPage(output)
        line = endExclusive
    }
    return page
}
