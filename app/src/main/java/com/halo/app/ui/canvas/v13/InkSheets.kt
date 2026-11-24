package com.halo.app.ui.canvas.v13

import com.halo.sdk.runtime.ink.InkDocument
import com.halo.sdk.runtime.ink.InkLimits
import com.halo.sdk.runtime.ink.InkStroke

/**
 * halo 13.0 · drawing without walls.
 *
 * The per-sketch limits in InkLimits (256 strokes · 2,048 points per stroke · 16,384 points) stay exactly as they are,
 * so saving, sync, OCR and remote ink keep their budgets. What goes is the wall in the UI: when a sheet is full the
 * next stroke lands on a new sheet stacked under the same drawing. Each sheet is one existing SKETCH node; the sheets of
 * one drawing sit in one GROUP node, joined by edges labelled [CONTINUES_LABEL]. No new storage.
 *
 * R.string.ink_capacity (shown today in the error colour) is never shown in 13.0. Red is kept for failed sources.
 */
data class DrawingSheets(val groupId: String, val sheetNodeIds: List<String>, val active: Int = sheetNodeIds.lastIndex) {
    init {
        require(sheetNodeIds.isNotEmpty() && sheetNodeIds.size <= InkSheets.MAX_SHEETS)
        require(active in sheetNodeIds.indices)
    }
    val canGrow: Boolean get() = sheetNodeIds.size < InkSheets.MAX_SHEETS
    val suggestNewDrawing: Boolean get() = sheetNodeIds.size >= InkSheets.CALM_NOTE_AT
}

object InkSheets {
    const val MAX_SHEETS = 32
    const val CALM_NOTE_AT = 24
    const val PREPARE_AT = 0.9
    const val CONTINUES_LABEL = "continues"

    /** 0.0–1.0: how full this sheet is, by whichever budget is closer to its limit. */
    fun fullness(doc: InkDocument): Double {
        val strokes = doc.strokes.size.toDouble() / InkLimits.MAX_STROKES
        val points = doc.strokes.sumOf { it.points.size }.toDouble() / InkLimits.MAX_TOTAL_POINTS
        return maxOf(strokes, points).coerceIn(0.0, 1.0)
    }

    /** Create the next sheet node quietly, before it is needed, so the switch costs nothing mid-stroke. */
    fun shouldPrepareNext(doc: InkDocument): Boolean = fullness(doc) >= PREPARE_AT

    /** Would this stroke still fit on the current sheet? Mirrors InkEditor.begin + InkLimits.requireValid. */
    fun fits(doc: InkDocument, stroke: InkStroke): Boolean =
        doc.strokes.size + 1 <= InkLimits.MAX_STROKES &&
            stroke.points.size <= InkLimits.MAX_POINTS_PER_STROKE &&
            doc.strokes.sumOf { it.points.size } + stroke.points.size <= InkLimits.MAX_TOTAL_POINTS

    sealed interface Placement {
        data class SameSheet(val doc: InkDocument) : Placement
        data class NextSheet(val finished: InkDocument, val next: InkDocument) : Placement
        /** Only at MAX_SHEETS. The UI shows the calm "Start a new drawing?" card, in apricot, never red. */
        object Full : Placement
    }

    /** Where a finished stroke goes. A stroke too long for any sheet is split at the point budget. */
    fun place(doc: InkDocument, stroke: InkStroke, sheets: DrawingSheets): Placement {
        if (fits(doc, stroke)) return Placement.SameSheet(doc.copy(strokes = doc.strokes + stroke))
        if (!sheets.canGrow) return Placement.Full
        val first = if (stroke.points.size > InkLimits.MAX_POINTS_PER_STROKE)
            stroke.copy(points = stroke.points.take(InkLimits.MAX_POINTS_PER_STROKE)) else stroke
        return Placement.NextSheet(doc, InkDocument(listOf(first), doc.aspectMilli))
    }

    /** Old sketch:v1 drawings (64-stroke cap) open as v4 documents. The original text is kept until the save succeeds. */
    fun upgradeV1(points: List<List<Pair<Int, Int>>>, aspectMilli: Int = 0): InkDocument = InkDocument(
        points.filter { it.isNotEmpty() }.take(InkLimits.MAX_STROKES).map { s ->
            InkStroke(com.halo.sdk.runtime.ink.InkTool.LEGACY,
                s.take(InkLimits.MAX_POINTS_PER_STROKE).map { (x, y) -> com.halo.sdk.runtime.ink.InkPoint(x, y) })
        }, aspectMilli)
}
