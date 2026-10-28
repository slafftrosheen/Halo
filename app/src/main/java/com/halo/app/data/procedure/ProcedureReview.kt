package com.halo.app.data.procedure

import com.halo.runtime.cognition.procedures.ProcedureInput
import com.halo.runtime.cognition.procedures.ProcedureStep
import com.halo.runtime.cognition.tools.ToolDescriptor
import org.json.JSONArray
import org.json.JSONObject

/** Complete, human-readable data review. Never truncate a consequential template for approval. */
object ProcedureReview {
    const val MAX_CHARS = 2000
    fun summary(name: String, description: String, triggers: List<String>, inputs: List<ProcedureInput>,
        steps: List<ProcedureStep>, language: String = "en", translate: (String) -> String = { it },
        descriptor: (String) -> ToolDescriptor?): String = buildString {
        val words = when (language.substringBefore('-')) {
            "ru" -> listOf("Фразы", "Поле", "из поля", "текст", "целое число", "да/нет")
            "lv" -> listOf("Frāzes", "Lauks", "no lauka", "teksts", "vesels skaitlis", "jā/nē")
            "pl" -> listOf("Frazy", "Pole", "z pola", "tekst", "liczba całkowita", "tak/nie")
            else -> listOf("Triggers", "Input", "from input", "text", "whole number", "true/false")
        }
        append(name).append('\n')
        if (description.isNotBlank()) append(description).append('\n')
        if (triggers.isNotEmpty()) append(words[0]).append(": ").append(triggers.joinToString(", ") { JSONObject.quote(it) }).append('\n')
        inputs.forEach { append(words[1]).append(' ').append(it.name).append(" (").append(words[when (it.valueType) { "integer" -> 4; "boolean" -> 5; else -> 3 }]).append("): ").append(it.description).append('\n') }
        steps.forEachIndexed { index, step ->
            val current = descriptor(step.capabilityId)
            append(index + 1).append(". ").append(current?.presentation?.title?.let(translate) ?: step.capabilityId)
            if (step.intent.isNotBlank()) append(" — ").append(step.intent)
            append('\n')
            step.argumentsJson?.let { json ->
                val arguments = JSONObject(json)
                arguments.keys().asSequence().toList().sorted().forEach { key ->
                    append("  ").append(label(key)).append(": ").append(value(arguments.get(key))).append('\n')
                }
            }
            step.inputBindings.toSortedMap().forEach { (key, input) ->
                append("  ").append(label(key)).append(": ").append(words[2]).append(' ').append(input).append('\n')
            }
        }
    }
    private fun label(key: String): String = key.replace(Regex("([a-z])([A-Z])"), "$1 $2").replace('_', ' ')
    private fun value(raw: Any?): String = when (raw) {
        is JSONObject -> raw.keys().asSequence().toList().sorted().joinToString("; ") { label(it) + ": " + value(raw.get(it)) }
        is JSONArray -> (0 until raw.length()).joinToString(", ") { value(raw.get(it)) }
        is String -> JSONObject.quote(raw)
        else -> raw.toString()
    }
}
