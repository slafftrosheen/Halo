package com.halo.app.ui.onboarding.v13

import java.time.LocalDate

/**
 * halo 13.0 · "finish setting up" without a checklist.
 * Small cards on Today, at most ONE per day, each one a real benefit ("Let halo hear you"), never a task.
 * Dismissed twice → gone for good (still reachable in Settings → What halo can do).
 */
data class SetupCard(val id: String, val title: Int, val body: Int, val action: Int, val priority: Int, val forWho: Set<WhoFor>)
data class CardMemory(val shownOn: Map<String, LocalDate> = emptyMap(), val dismissals: Map<String, Int> = emptyMap(), val done: Set<String> = emptySet())

object SetupCards {
    const val PER_DAY = 1
    const val DISMISS_FOREVER_AFTER = 2
    const val QUIET_DAYS_AFTER_ONBOARDING = 1   // day one is for trying, not configuring

    fun today(all: List<SetupCard>, mem: CardMemory, who: Set<WhoFor>, today: LocalDate, onboardedOn: LocalDate): SetupCard? {
        if (today.isBefore(onboardedOn.plusDays(QUIET_DAYS_AFTER_ONBOARDING.toLong()))) return null
        if (mem.shownOn.values.any { it == today }) return mem.shownOn.entries.firstOrNull { it.value == today }?.let { e -> all.firstOrNull { it.id == e.key && it.id !in mem.done } }
        return all.filter { it.id !in mem.done && (mem.dismissals[it.id] ?: 0) < DISMISS_FOREVER_AFTER && (it.forWho.isEmpty() || it.forWho.any { w -> w in who }) }
            .minByOrNull { it.priority }
    }
}
