package com.halo.app.ui.screens.settings
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.halo.runtime.cognition.tools.ToolDescriptor
import org.json.JSONObject

/** Only simple provider schema fields. Unsupported nesting stays unavailable in the editor. */
@Composable
internal fun SchemaArguments(d: ToolDescriptor, raw: String, onChange: (String) -> Unit) {
    val properties = d.definition.parameters["properties"] as? Map<*, *> ?: emptyMap<Any, Any>()
    val complex = properties.values.any { v -> (v as? Map<*, *>)?.get("type") !in setOf("string", "integer", "number", "boolean") }
    if(complex) { Text("Not configurable as an automation yet"); return }
    properties.forEach { (key, schemaValue) ->
        val field = key.toString(); val schema = schemaValue as? Map<*, *> ?: return@forEach
        val json = remember(raw) { JSONObject(raw) }
        val type = schema["type"]?.toString()
        val choices = schema["enum"] as? List<*>
        var input by remember(raw, field) { mutableStateOf(if(json.has(field)) json.get(field).toString() else "") }
        val label = field.replace('_', ' ').replaceFirstChar(Char::uppercase)
        when {
            choices != null -> choices.forEach { choice -> TextButton(onClick = { onChange(JSONObject(raw).put(field, choice).toString()) }) { Text(choice.toString()) } }
            type == "boolean" -> Row { Checkbox(json.optBoolean(field, false), { onChange(JSONObject(raw).put(field, it).toString()) }); Text(label) }
            else -> OutlinedTextField(input, { value ->
                input = value.take(2048)
                val converted: Any? = when(type) { "integer" -> value.toLongOrNull(); "number" -> value.toDoubleOrNull()?.takeIf { it.isFinite() }; else -> value.take((schema["maxLength"] as? Number)?.toInt()?.coerceIn(1,2048) ?: 2048) }
                val updated = JSONObject(raw)
                if(value.isBlank()) updated.remove(field) else updated.put(field, converted ?: value.take(2048))
                onChange(updated.toString())
            }, label = { Text(label) })
        }
    }
}

internal fun routineSchemaEditable(d: ToolDescriptor): Boolean {
    val properties = d.definition.parameters["properties"] as? Map<*, *> ?: return true
    return properties.values.all { (it as? Map<*, *>)?.get("type") in setOf("string", "integer", "number", "boolean") }
}
