package com.halo.app.data.workspace

import com.halo.app.everyday.presentation.EverydayCardConfig
import com.halo.app.everyday.presentation.EverydayCardKind
import com.halo.app.everyday.presentation.EverydayWidgetSize

/** Only the ordered choices cross to the launcher. Page IDs, titles and content stay in Halo. */
internal data class AdaptiveWidgetExport(val available: List<EverydayCardKind>, val unsupported: List<AdaptiveSlot>) {
    val suggested: List<EverydayCardKind> get() = available.take(MAX_SECTIONS)

    fun config(sections: List<EverydayCardKind> = suggested): EverydayCardConfig {
        require(sections.size in 1..MAX_SECTIONS && sections.distinct() == sections &&
            sections.all { it in available })
        return EverydayCardConfig(EverydayCardKind.DASHBOARD, sections = available.filter(sections::contains), limit = MAX_SECTIONS,
            widgetSize = EverydayWidgetSize.TALL, adaptiveDashboard = true)
    }

    companion object {
        const val MAX_SECTIONS = 3

        fun from(layout: AdaptiveLayout): AdaptiveWidgetExport {
            val sections = mutableListOf<EverydayCardKind>()
            val unsupported = mutableListOf<AdaptiveSlot>()
            for (slot in layout.slots) {
                val section = when (slot) {
                    AdaptiveSlot.PLANNER -> EverydayCardKind.UPCOMING
                    AdaptiveSlot.WEATHER -> EverydayCardKind.WEATHER
                    AdaptiveSlot.READS -> EverydayCardKind.HEADLINES
                    AdaptiveSlot.TASKS -> EverydayCardKind.TASKS
                    AdaptiveSlot.REMINDERS -> EverydayCardKind.REMINDERS
                    AdaptiveSlot.ALARMS_TIMERS -> EverydayCardKind.ALARMS_TIMERS
                    AdaptiveSlot.CLOCK -> EverydayCardKind.CLOCK
                    AdaptiveSlot.BRIEF -> EverydayCardKind.BRIEF
                    AdaptiveSlot.DEVICE -> EverydayCardKind.DEVICE
                    AdaptiveSlot.STUDY, AdaptiveSlot.LIBRARY, AdaptiveSlot.FOCUS, AdaptiveSlot.WIND_DOWN -> null
                }
                if (section == null) unsupported.add(slot) else sections.add(section)
            }
            return AdaptiveWidgetExport(sections, unsupported)
        }
    }
}

/** An in-app request carries an exact page version and the current adult authority. */
internal data class AdaptiveWidgetEntry(val page: AdaptivePageRef, val familyRevision: Long) {
    init { require(familyRevision >= 0) }

    fun route() = "$ROOT/${page.id}/${page.revision}/$familyRevision"

    companion object {
        const val ROOT = "home/pages/widget"
        const val PATTERN = "$ROOT/{pageId}/{pageRevision}/{familyRevision}"

        fun parse(route: String): AdaptiveWidgetEntry? {
            if (!route.startsWith("$ROOT/")) return null
            val parts = route.removePrefix("$ROOT/").split('/')
            if (parts.size != 3) return null
            val version = parts[1].toLongOrNull() ?: return null
            val familyRevision = parts[2].toLongOrNull() ?: return null
            return runCatching { AdaptiveWidgetEntry(AdaptivePageRef(parts[0], version), familyRevision) }
                .getOrNull()?.takeIf { it.route() == route }
        }
    }
}
