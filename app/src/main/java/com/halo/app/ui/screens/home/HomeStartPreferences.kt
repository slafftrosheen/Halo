package com.halo.app.ui.screens.home

import android.content.Context
import androidx.annotation.StringRes
import com.halo.app.R
import com.halo.app.ui.icons.HaloGlyph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class HomeStart(@StringRes val label: Int, @StringRes val detail: Int, val glyph: HaloGlyph, val iconId: String) {
    TODAY(R.string.home_start_today, R.string.home_start_today_detail, HaloGlyph.CALENDAR, "screen_calendar"),
    NOTE(R.string.home_start_note, R.string.home_start_note_detail, HaloGlyph.NOTE, "office_new_note"),
    STUDY(R.string.home_start_study, R.string.home_start_study_detail, HaloGlyph.QUESTION, "tool_study"),
    CAMERA(R.string.home_start_camera, R.string.home_start_camera_detail, HaloGlyph.SEE, "capture_camera"),
    TIMER(R.string.home_start_timer, R.string.home_start_timer_detail, HaloGlyph.AUTOMATION, "screen_clock"),
    WEATHER(R.string.home_start_weather, R.string.home_start_weather_detail, HaloGlyph.INTEGRATIONS, "screen_weather"),
    BRIEF(R.string.home_start_brief, R.string.home_start_brief_detail, HaloGlyph.READ_MODE, "action_read_aloud"),
    DRAW(R.string.home_start_draw, R.string.home_start_draw_detail, HaloGlyph.TOOL, "tool_studio"),
}

internal enum class HomeGreeting(@StringRes val label: Int) {
    WEATHER(R.string.home_start_greeting_weather),
    DAY(R.string.home_start_greeting_day),
    OFF(R.string.home_start_greeting_off),
}

internal data class HomeStartLayout(
    val starts: List<HomeStart> = listOf(HomeStart.TODAY, HomeStart.NOTE, HomeStart.STUDY, HomeStart.CAMERA),
    val greeting: HomeGreeting = HomeGreeting.WEATHER,
) {
    init { require(starts.size <= MAX_STARTS && starts.distinct() == starts) }

    fun move(start: HomeStart, by: Int): HomeStartLayout {
        val from = starts.indexOf(start)
        val to = from + by
        if (from !in starts.indices || to !in starts.indices || from == to) return this
        val ordered = starts.toMutableList()
        ordered.removeAt(from)
        ordered.add(to, start)
        return copy(starts = ordered)
    }

    companion object { const val MAX_STARTS = 4 }
}

internal data class HomeStartSnapshot(
    val current: HomeStartLayout = HomeStartLayout(),
    val previous: HomeStartLayout? = null,
    val savedAtMillis: Long = 0,
) {
    fun restorable(): HomeStartLayout? = previous?.takeIf {
        System.currentTimeMillis() - savedAtMillis in 0..7L * 24 * 60 * 60 * 1000
    }
}

/** Presentation only. The layout stores action identifiers, never contacts, prompts or results. */
internal class HomeStartPreferences(context: Context) {
    private val context = context.applicationContext
    private val disk get() = context.getSharedPreferences("halo_home_starts", Context.MODE_PRIVATE)

    suspend fun load() = withContext(Dispatchers.IO) { synchronized(writes) { read() } }

    suspend fun apply(expected: HomeStartSnapshot, layout: HomeStartLayout): HomeStartSnapshot =
        withContext(Dispatchers.IO) {
            synchronized(writes) {
                com.halo.app.ui.theme.HaloLayoutLock.get(context).requireUnlocked()
                val current = read()
                check(current == expected) { "Home layout changed" }
                if (current.current == layout) return@synchronized current
                val updated = HomeStartSnapshot(layout, current.current, System.currentTimeMillis())
                check(disk.edit()
                    .putString("layout", layout.encode())
                    .putString("previous", current.current.encode())
                    .putLong("saved_at", updated.savedAtMillis)
                    .commit()) { "Unable to save Home layout" }
                updated
            }
        }

    private fun read() = HomeStartSnapshot(
        decode(disk.getString("layout", null)) ?: HomeStartLayout(),
        decode(disk.getString("previous", null)),
        disk.getLong("saved_at", 0),
    )

    private fun HomeStartLayout.encode() = greeting.name + "|" + starts.joinToString(",") { it.name }

    private fun decode(value: String?): HomeStartLayout? {
        val fields = value?.split('|', limit = 2) ?: return null
        if (fields.size != 2) return null
        val greeting = HomeGreeting.entries.find { it.name == fields[0] } ?: return null
        val starts = fields[1].split(',').mapNotNull { name -> HomeStart.entries.find { it.name == name } }
        return HomeStartLayout(starts.distinct().take(HomeStartLayout.MAX_STARTS), greeting)
    }

    companion object { private val writes = Any() }
}
