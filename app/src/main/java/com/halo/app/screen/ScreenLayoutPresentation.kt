package com.halo.app.screen

/** Presentation only. Twelve canonical columns map exactly to two, three or four tracks. */
data class ScreenPlacement(val column: Int, val row: Int, val width: Int, val height: Int) {
    fun bounded(): ScreenPlacement {
        val w = width.coerceIn(3, 12)
        return copy(column = column.coerceIn(0, 12 - w), row = row.coerceIn(0, 143),
            width = w, height = height.coerceIn(1, 6))
    }
}

enum class ScreenFont { APP, SYSTEM, SORA, ATKINSON }
enum class ScreenBackground { PLAIN, MINT, WARM, COOL }
data class ScreenAppearance(val font: ScreenFont = ScreenFont.APP,
    val background: ScreenBackground = ScreenBackground.PLAIN, val translucent: Boolean = false)
data class ScreenCardTypography(val titlePercent: Int = 100, val valuePercent: Int = 100,
    val clockPercent: Int = 100) {
    fun bounded() = copy(titlePercent = titlePercent.coerceIn(100, 150),
        valuePercent = valuePercent.coerceIn(100, 150), clockPercent = clockPercent.coerceIn(100, 200))
}

/** Desk emphasis is monotonic and never reduces the OS preference or compounds 200% to 280%. */
internal fun screenAmbientFontScale(base: Float, fullScreen: Boolean): Float =
    if (fullScreen) maxOf(base, minOf(base * 1.4f, 2f)) else base

/** Migrate legacy duplicate sources once, then retain identity through edits and persistence. */
internal fun screenCardIdentities(cards: List<ScreenCard>): List<ScreenCard> {
    val used = mutableSetOf<String>()
    return cards.mapIndexed { index, card ->
        val original = card.presentationId.takeIf { it.isNotBlank() && it.length <= 512 }
            ?: "${card.widgetType}:${card.itemId.orEmpty()}"
        var identity = original
        var suffix = index
        while (!used.add(identity)) identity = "$original#legacy-${suffix++}"
        if (identity == card.presentationId) card else card.copy(presentationId = identity)
    }
}

internal data class ScreenGridCell(val index: Int, val column: Int, val row: Int, val width: Int, val height: Int) {
    fun overlaps(other: ScreenGridCell) = column < other.column + other.width && column + width > other.column &&
        row < other.row + other.height && row + height > other.row
    fun persisted(columns: Int) = ScreenPlacement(column * 12 / columns, row,
        width * 12 / columns, height).bounded()
}

/** Bounded packing preserves every card; collisions displace cards rather than overwriting them. */
internal object ScreenGrid {
    fun columns(widthDp: Float, fontScale: Float): Int = when {
        fontScale >= 1.5f -> 1
        widthDp >= 840 -> 4
        widthDp >= 600 -> 3
        else -> 2
    }

    fun resolve(cards: List<ScreenCard>, columns: Int, preferred: Int? = null): List<ScreenGridCell> {
        require(columns in 1..4 && cards.size <= 24)
        val placed = mutableListOf<ScreenGridCell>()
        val order = cards.indices.toMutableList().apply {
            if (preferred != null && preferred in cards.indices) { remove(preferred); add(0, preferred) }
        }
        for (index in order) {
            val card = cards[index]
            val p = card.placement?.bounded()
            val width = if (columns == 1) 1 else p?.let { ((it.width * columns + 11) / 12).coerceIn(1, columns) }
                ?: when (card.size) { CardSize.COMPACT -> 1; CardSize.STANDARD -> minOf(2, columns); CardSize.EXPANDED -> columns }
            val height = p?.height ?: if (card.size == CardSize.EXPANDED) 2 else 1
            val requested = ScreenGridCell(index, if (columns == 1) 0 else p?.let { it.column * columns / 12 }?.coerceAtMost(columns - width) ?: 0,
                if (columns == 1) 0 else p?.row ?: 0, width, height)
            fun free(cell: ScreenGridCell) = placed.none(cell::overlaps)
            val cell = if (p != null && columns > 1 && free(requested)) requested else {
                var found: ScreenGridCell? = null
                for (row in 0..149) {
                    for (column in 0..columns - width) {
                        val candidate = requested.copy(column = column, row = row)
                        if (free(candidate)) { found = candidate; break }
                    }
                    if (found != null) break
                }
                checkNotNull(found) // 24 bounded cards occupy at most 144 rows.
            }
            placed += cell
        }
        return placed.sortedBy { it.index }
    }

    fun move(cards: List<ScreenCard>, index: Int, columns: Int, dx: Int, dy: Int): List<ScreenCard> {
        if (index !in cards.indices || dx == 0 && dy == 0) return cards
        if (columns == 1) {
            val target = (index + dy).coerceIn(cards.indices)
            if (target == index) return cards
            val reordered = cards.toMutableList().apply { add(target, removeAt(index)) }
            var row = 0
            return reordered.map { card ->
                val original = card.placement?.bounded() ?: ScreenPlacement(0, 0,
                    if (card.size == CardSize.COMPACT) 6 else 12, if (card.size == CardSize.EXPANDED) 2 else 1)
                card.copy(placement = original.copy(row = row).bounded()).also { row += original.height }
            }
        }
        val cell = resolve(cards, columns).first { it.index == index }
        val target = cell.copy(column = (cell.column + dx).coerceIn(0, columns - cell.width), row = (cell.row + dy).coerceIn(0, 143))
        return if (target == cell) cards else place(cards, index, columns, target)
    }

    fun resize(cards: List<ScreenCard>, index: Int, columns: Int, dw: Int, dh: Int): List<ScreenCard> {
        if (index !in cards.indices || dw == 0 && dh == 0) return cards
        val cell = resolve(cards, columns).first { it.index == index }
        val width = (cell.width + dw).coerceIn(1, columns)
        val target = cell.copy(column = cell.column.coerceAtMost(columns - width), width = width, height = (cell.height + dh).coerceIn(1, 6))
        return if (target == cell) cards else place(cards, index, columns, target, resizeWidth = target.width != cell.width)
    }

    fun reorder(cards: List<ScreenCard>, from: Int, to: Int): List<ScreenCard> {
        if (from !in cards.indices || to !in cards.indices || from == to) return cards
        val cells = resolve(cards, 4).associateBy { it.index }
        val own = cells.getValue(from)
        val destination = cells.getValue(to)
        val reordered = cards.toMutableList().apply { add(to, removeAt(from)) }
        return place(reordered, to, 4, own.copy(index = to,
            column = destination.column.coerceAtMost(4 - own.width), row = destination.row))
    }

    private fun place(cards: List<ScreenCard>, index: Int, columns: Int, target: ScreenGridCell, resizeWidth: Boolean = false): List<ScreenCard> {
        // Canonical placement is retained in the one-column accessibility fallback.
        if (columns == 1) {
            val original = cards[index].placement ?: ScreenPlacement(0, target.row, 12, target.height)
            return cards.mapIndexed { i, card -> if (i == index) card.copy(placement = original.copy(height = target.height).bounded()) else card }
        }
        val originalWidth = cards[index].placement?.bounded()?.width
        val selected = target.persisted(columns).let { placement ->
            if (!resizeWidth && originalWidth != null) placement.copy(width = originalWidth).bounded() else placement
        }
        val moved = cards.mapIndexed { i, card -> if (i == index) card.copy(placement = selected) else card }
        val resolved = resolve(moved, columns, preferred = index).associateBy { it.index }
        return moved.mapIndexed { i, card ->
            val projected = resolved.getValue(i).persisted(columns)
            card.copy(placement = projected.copy(width = card.placement?.bounded()?.width ?: projected.width).bounded())
        }
    }
}
