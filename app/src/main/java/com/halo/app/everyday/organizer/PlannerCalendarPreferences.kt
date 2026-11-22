package com.halo.app.everyday.organizer

import com.halo.app.everyday.EverydayWriteAuthority
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

enum class PlannerCalendarColor(val label: String, val argb: Long) {
    BLUE("Blue", 0xFF5079DC),
    GREEN("Green", 0xFF3A9A72),
    AMBER("Amber", 0xFFD39231),
    VIOLET("Violet", 0xFF9A63CB),
    ROSE("Rose", 0xFFC95C84),
    TEAL("Teal", 0xFF359C9E),
}

data class PlannerCalendarAppearance(val visible: Boolean, val color: PlannerCalendarColor)

data class PlannerCalendarSettings(
    val version: Long = 0,
    val appearances: Map<OrganizerCalendar, PlannerCalendarAppearance> = emptyMap(),
) {
    fun appearance(calendar: OrganizerCalendar): PlannerCalendarAppearance = appearances[calendar]
        ?: PlannerCalendarAppearance(true, when (calendar) {
            OrganizerCalendar.PERSONAL -> PlannerCalendarColor.BLUE
            OrganizerCalendar.FAMILY -> PlannerCalendarColor.GREEN
            OrganizerCalendar.OTHERS -> PlannerCalendarColor.AMBER
        })
}

/** On-device view choices only. Record membership and Kids sharing have independent owners. */
internal object PlannerCalendarPreferences {
    private const val KEY = "planner_calendar_appearance_v1"
    private val lock = Any()
    private val changes = MutableStateFlow(0)
    val observeChanges = changes.asStateFlow()

    fun read(storage: Storage): PlannerCalendarSettings = synchronized(lock) { readLocked(storage) }

    fun update(storage: Storage, version: Long, calendar: OrganizerCalendar, visible: Boolean?,
        color: PlannerCalendarColor?): PlannerCalendarSettings = synchronized(lock) {
        val before = readLocked(storage)
        if (before.version != version) throw OrganizerConflict("Calendar settings changed")
        val previous = before.appearance(calendar)
        val appearance = PlannerCalendarAppearance(visible ?: previous.visible, color ?: previous.color)
        if (appearance == previous) return@synchronized before
        val saved = before.copy(version = Math.incrementExact(version), appearances = before.appearances + (calendar to appearance))
        val data = JSONObject().put("version", saved.version)
        saved.appearances.forEach { (group, value) ->
            data.put(group.name, JSONObject().put("visible", value.visible).put("color", value.color.name))
        }
        EverydayWriteAuthority.requireCurrent()
        storage.putString(KEY, data.toString())
        check(readLocked(storage) == saved)
        changes.update { it + 1 }
        saved
    }

    private fun readLocked(storage: Storage): PlannerCalendarSettings {
        val raw = storage.getString(KEY) ?: return PlannerCalendarSettings()
        val data = JSONObject(raw)
        val version = data.getLong("version").also { require(it >= 0) }
        val appearances = OrganizerCalendar.entries.mapNotNull { calendar ->
            val item = data.optJSONObject(calendar.name) ?: return@mapNotNull null
            calendar to PlannerCalendarAppearance(item.getBoolean("visible"), PlannerCalendarColor.valueOf(item.getString("color")))
        }.toMap()
        return PlannerCalendarSettings(version, appearances)
    }
}
