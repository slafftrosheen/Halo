package com.halo.app.ui.planner.v13

/**
 * halo 13.0 · "Too much today?" – halo notices, suggests, and asks. It never moves anything by itself.
 * Load = planned minutes / free minutes between first and last thing. Over 90 % → a gentle card in apricot.
 */
data class DayLoad(val plannedMin: Int, val freeMin: Int) { val ratio get() = if (freeMin == 0) 1.0 else plannedMin.toDouble() / freeMin }
data class Movable(val id: String, val minutes: Int, val mustToday: Boolean, val priority: Int)

object TooMuchToday {
    const val GENTLE_AT = 0.9
    fun suggest(load: DayLoad, tasks: List<Movable>): List<Movable> {
        if (load.ratio < GENTLE_AT) return emptyList()
        var over = load.plannedMin - (load.freeMin * 0.8).toInt()
        return tasks.filterNot { it.mustToday }.sortedBy { it.priority }.takeWhile { over > 0 }.onEach { over -= it.minutes }
    }
}
