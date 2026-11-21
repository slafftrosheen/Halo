package com.halo.app.ui.toolkit

import android.os.SystemClock
import com.halo.app.di.ManualDependencyGraph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalTime
import java.util.UUID

internal data class PlannerHandoffDraft(val title: String, val time: LocalTime? = null, val source: String? = null)
internal data class PlannerHandoff(
    val id: String, val draft: PlannerHandoffDraft, val created: Long, val adultRevision: Long,
)

/** One draft handoff. Nothing is saved or scheduled before Planner's own review and save. */
internal object HaloPlannerHandoff {
    private val state = MutableStateFlow<PlannerHandoff?>(null)
    val pending = state.asStateFlow()

    fun propose(title: String, time: LocalTime? = null, source: String? = null): Boolean {
        val clean = title.trim().take(160)
        if (clean.isBlank()) return false
        val revision = try { ManualDependencyGraph.kidsPreferences.adultActionRevision() } catch (_: IllegalStateException) { return false }
        state.value = PlannerHandoff(UUID.randomUUID().toString(), PlannerHandoffDraft(clean, time, source),
            SystemClock.elapsedRealtime(), revision)
        return true
    }

    fun take(expected: PlannerHandoff): PlannerHandoffDraft? {
        if (!state.compareAndSet(expected, null) || SystemClock.elapsedRealtime() - expected.created !in 0L..60_000L) return null
        return try { ManualDependencyGraph.kidsPreferences.requireAdultAction(expected.adultRevision); expected.draft }
        catch (_: IllegalStateException) { null }
    }
}

private val printedTime = Regex("(?<![0-9])([01]?[0-9]|2[0-3]):([0-5][0-9])(?![0-9])")
internal fun scanReminderTimes(lines: List<HaloScanLine>): List<Pair<String, LocalTime>> = lines.asSequence()
    .take(120).flatMap { line -> printedTime.findAll(line.text).map { match ->
        line.text.trim().take(160) to LocalTime.of(match.groupValues[1].toInt(), match.groupValues[2].toInt())
    } }.distinct().take(8).toList()
