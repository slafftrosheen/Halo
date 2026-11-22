package com.halo.app.data.workspace

import org.json.JSONObject
import java.time.LocalDate

internal enum class HaloSheetEntryKind(val label: String) {
    LIST("List"), NUMBER("Number range"), DATE("Date range"),
}

/** A column rule uses displayed values, so supported formulas can satisfy numeric rules. */
internal data class HaloSheetEntryRule(
    val column: Int,
    val kind: HaloSheetEntryKind,
    val lower: String,
    val upper: String = "",
    val header: Boolean = true,
) {
    val choices: List<String> get() = lower.lineSequence().map(String::trim).filter(String::isNotBlank).distinct().toList()

    fun valid(): Boolean = column in 0 until WorkspaceCsv.MAX_COLUMNS && lower.length <= 1200 && upper.length <= 120 && when (kind) {
        HaloSheetEntryKind.LIST -> choices.size in 1..30
        HaloSheetEntryKind.NUMBER -> {
            val min = sheetNumber(lower)
            val max = sheetNumber(upper)
            min != null && max != null && min <= max
        }
        HaloSheetEntryKind.DATE -> {
            val min = date(lower)
            val max = date(upper)
            min != null && max != null && min <= max
        }
    }

    fun accepts(value: String): Boolean = value.isBlank() || when (kind) {
        HaloSheetEntryKind.LIST -> value.trim() in choices
        HaloSheetEntryKind.NUMBER -> sheetNumber(value)?.let { it in requireNotNull(sheetNumber(lower))..requireNotNull(sheetNumber(upper)) } == true
        HaloSheetEntryKind.DATE -> date(value)?.let { it >= requireNotNull(date(lower)) && it <= requireNotNull(date(upper)) } == true
    }

    fun encode(): String = JSONObject().put("column", column).put("kind", kind.name).put("lower", lower)
        .put("upper", upper).put("header", header).toString()

    companion object {
        fun decode(raw: String?): HaloSheetEntryRule? = raw?.let {
            runCatching {
                val json = JSONObject(it)
                HaloSheetEntryRule(json.getInt("column"), HaloSheetEntryKind.valueOf(json.getString("kind")),
                    json.getString("lower"), json.getString("upper"), json.getBoolean("header")).takeIf(HaloSheetEntryRule::valid)
            }.getOrNull()
        }

        private fun date(value: String): LocalDate? = runCatching { LocalDate.parse(value.trim()) }.getOrNull()
    }
}
