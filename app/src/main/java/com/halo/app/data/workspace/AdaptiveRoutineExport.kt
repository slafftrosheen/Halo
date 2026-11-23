package com.halo.app.data.workspace

import com.halo.app.automation.RoutineSnapshotSection
import com.halo.app.automation.RoutineSnapshotTool
import com.halo.runtime.cognition.routine.Routine
import com.halo.runtime.cognition.routine.RoutineAction
import com.halo.runtime.cognition.routine.RoutineId
import com.halo.runtime.cognition.routine.RoutineTrigger
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import org.json.JSONObject

/** Copies ordered read-only actions for review. Page edits cannot change an approved schedule. */
internal data class AdaptiveRoutineExport(val sections: List<RoutineSnapshotSection>, val unsupported: List<AdaptiveSlot>) {
    fun actions(): List<RoutineAction> = sections.map { section ->
        RoutineAction(RoutineSnapshotTool.ID, JSONObject().put("section", section.name).put("allow_unavailable", true).toString())
    }

    fun draft(title: String, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): Routine {
        require(sections.isNotEmpty() && title.length in 1..120)
        return Routine(RoutineId(UUID.randomUUID().toString()), title, enabled = false,
            trigger = RoutineTrigger.AtTime(LocalTime.of(8, 0), zoneId = zone), actions = actions(),
            createdAt = now, updatedAt = now)
    }

    companion object {
        fun from(layout: AdaptiveLayout): AdaptiveRoutineExport {
            val sections = mutableListOf<RoutineSnapshotSection>()
            val unsupported = mutableListOf<AdaptiveSlot>()
            for (slot in layout.slots) {
                val section = when (slot) {
                    AdaptiveSlot.PLANNER -> RoutineSnapshotSection.AGENDA
                    AdaptiveSlot.WEATHER -> RoutineSnapshotSection.WEATHER
                    AdaptiveSlot.READS -> RoutineSnapshotSection.HEADLINE
                    AdaptiveSlot.TASKS -> RoutineSnapshotSection.TASKS
                    AdaptiveSlot.REMINDERS -> RoutineSnapshotSection.REMINDERS
                    AdaptiveSlot.ALARMS_TIMERS -> RoutineSnapshotSection.ALARMS_TIMERS
                    AdaptiveSlot.CLOCK -> RoutineSnapshotSection.CLOCK
                    AdaptiveSlot.BRIEF -> RoutineSnapshotSection.BRIEF
                    AdaptiveSlot.DEVICE -> RoutineSnapshotSection.DEVICE
                    AdaptiveSlot.STUDY, AdaptiveSlot.LIBRARY, AdaptiveSlot.FOCUS, AdaptiveSlot.WIND_DOWN -> null
                }
                if (section == null) unsupported.add(slot) else sections.add(section)
            }
            return AdaptiveRoutineExport(sections, unsupported)
        }
    }
}
