package com.halo.app.ui.instruments

import android.content.res.Resources
import android.view.KeyEvent
import com.halo.app.R
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

internal enum class InstrumentLevel(val code: String, val label: String) {
    EASY("E", "Easy"), STANDARD("S", "Standard"), PRO("P", "Pro")
}

internal data class InstrumentSpec(
    val id: String,
    val name: String,
    val russianName: String,
    val icon: Int,
    val shortcut: String?,
    val group: String,
    val description: String,
    val russianDescription: String,
    val parameters: String,
    val levels: Set<String>,
    val states: List<String>,
    val hardStop: Boolean,
) {
    fun isVisible(level: InstrumentLevel): Boolean = level.code in levels || "E" in levels

    fun matches(query: String): Boolean {
        val words = query.lowercase(Locale.ROOT).trim().split(Regex("\\s+")).filter(String::isNotBlank)
        val searchable = listOf(id.replace('_', ' '), name, russianName, group, description, russianDescription, parameters, shortcut.orEmpty())
            .joinToString(" ").lowercase(Locale.ROOT)
        return words.all { it in searchable }
    }
}

internal object HaloInstrumentCatalogue {
    @Volatile private var tools: Map<String, List<InstrumentSpec>>? = null

    fun entries(resources: Resources, tool: String): List<InstrumentSpec> =
        (tools ?: synchronized(this) { tools ?: load(resources).also { tools = it } })[tool].orEmpty()

    private fun load(resources: Resources): Map<String, List<InstrumentSpec>> {
        fun objectFrom(id: Int) = resources.openRawResource(id).bufferedReader().use { JSONObject(it.readText()) }
        val rawTools = objectFrom(R.raw.halo_instruments).getJSONObject("tools")
        val translations = objectFrom(R.raw.halo_instrument_ru)
        val legacy = rawTools.keys().asSequence().associateWith { tool ->
            val items = rawTools.getJSONObject(tool).getJSONArray("instruments")
            List(items.length()) { index ->
                val item = items.getJSONObject(index)
                val id = item.getString("id")
                val translation = translations.getJSONObject(id)
                InstrumentSpec(
                    id, item.getString("name"), translation.getString("name"),
                    HaloInstrumentIcons.getValue(item.getString("icon")),
                    item.optString("shortcut").takeUnless { item.isNull("shortcut") || it.isBlank() },
                    item.getString("group"), item.getString("does"), translation.getString("description"), item.getString("params"),
                    item.getJSONArray("levels").strings().toSet(), item.getJSONArray("states").strings(),
                    item.getBoolean("hardStop"),
                )
            }
        }
        val currentTools = objectFrom(R.raw.halo_instruments_11).getJSONObject("tools")
        val current = currentTools.keys().asSequence().associateWith { tool ->
            val items = currentTools.getJSONObject(tool).getJSONArray("instruments")
            List(items.length()) { index ->
                val item = items.getJSONObject(index)
                InstrumentSpec(
                    id = item.getString("id"),
                    name = item.getString("name"),
                    russianName = item.getString("ru"),
                    icon = HaloInstrumentIcons.getValue(item.getString("icon")),
                    shortcut = item.optString("shortcut").takeUnless { item.isNull("shortcut") || it.isBlank() },
                    group = item.getString("group"),
                    description = item.getString("does"),
                    russianDescription = item.getString("descriptionRu"),
                    parameters = item.optString("params", "—"),
                    levels = item.getJSONArray("levels").strings().toSet(),
                    states = item.optJSONArray("states")?.strings().orEmpty(),
                    hardStop = item.optBoolean("hardStop"),
                )
            }
        }
        return legacy + current
    }

    private fun JSONArray.strings() = List(length(), ::getString)
}

internal fun InstrumentSpec.matchesShortcut(event: KeyEvent): Boolean {
    val raw = shortcut ?: return false
    val normalized = when (raw) {
        "Space flip" -> "Space"
        "< >" -> "< · >"
        "Space · J K L" -> "Space · J · K · L"
        "Ctrl PgUp/PgDn" -> "Ctrl PgUp / PgDn"
        "Ctrl Alt 1–4" -> "Ctrl Alt 1 / 2 / 3 / 4"
        "Ctrl ⇧ 1–5" -> "Ctrl ⇧ 1 / 2 / 3 / 4 / 5"
        else -> raw
    }
    val expanded = normalized.split(" · ").flatMap { alternative ->
        val first = alternative.substringBefore(" / ")
        val modifiers = first.substringBeforeLast(' ', "")
        listOf(first) + alternative.split(" / ").drop(1).map { "$modifiers $it".trim() }
    }
    return expanded.any { shortcut ->
        val tokens = shortcut.replace("⇧", "Shift").trim().split(Regex("\\s+"))
        val key = tokens.last().uppercase(Locale.ROOT)
        val flags = tokens.dropLast(1).toSet()
        val keyCode = when (key) {
            "SPACE" -> KeyEvent.KEYCODE_SPACE
            "PGUP" -> KeyEvent.KEYCODE_PAGE_UP
            "PGDN" -> KeyEvent.KEYCODE_PAGE_DOWN
            "=" -> KeyEvent.KEYCODE_EQUALS
            "+" -> KeyEvent.KEYCODE_EQUALS
            "−", "-" -> KeyEvent.KEYCODE_MINUS
            "←" -> KeyEvent.KEYCODE_DPAD_LEFT
            "→" -> KeyEvent.KEYCODE_DPAD_RIGHT
            "<" -> KeyEvent.KEYCODE_COMMA
            ">" -> KeyEvent.KEYCODE_PERIOD
            else -> KeyEvent.keyCodeFromString("KEYCODE_$key")
        }
        keyCode != KeyEvent.KEYCODE_UNKNOWN && keyCode == event.keyCode &&
            event.isCtrlPressed == ("Ctrl" in flags) && event.isAltPressed == ("Alt" in flags) &&
            event.isShiftPressed == ("Shift" in flags || key in setOf("<", ">", "+"))
    }
}
