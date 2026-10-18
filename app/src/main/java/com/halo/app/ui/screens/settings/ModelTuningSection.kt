package com.halo.app.ui.screens.settings

import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText
import com.halo.providers.litert.models.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ModelTuningSection(modelId: String, value: LiteRtUserTuning, enabled: Boolean,
    supportsThinking: Boolean, onChange: (LiteRtUserTuning?) -> Boolean) {
    var advanced by rememberSaveable(modelId) { mutableStateOf(false) }
    var current by remember(modelId, value) { mutableStateOf(value) }
    var invalid by remember(modelId) { mutableStateOf(false) }
    fun apply(next: LiteRtUserTuning?) {
        invalid = !onChange(next)
        if (!invalid) current = next ?: LiteRtUserTuning()
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(uiText("Model tuning"), style = MaterialTheme.typography.titleSmall)
        Text(uiText("Changes apply to this model only."), style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LiteRtTuningPreset.entries.forEach { preset ->
                val settings = LiteRtUserTuning.preset(preset)
                FilterChip(selected = current == settings, enabled = enabled,
                    onClick = { apply(settings) }, label = { Text(uiText(preset.name.lowercase().replaceFirstChar { it.uppercase() })) })
            }
        }
        Text(uiText("Response length"), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Short" to 256, "Standard" to 512, "Long" to 1024).forEach { (label, tokens) ->
                FilterChip(selected = current.maxOutputTokens == tokens, enabled = enabled,
                    onClick = { apply(current.copy(maxOutputTokens = tokens)) }, label = { Text(uiText(label)) })
            }
        }
        if (supportsThinking) {
            Text(uiText("Thinking mode"), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LiteRtThinkingMode.entries.forEach { mode -> FilterChip(selected = current.thinkingMode == mode,
                    enabled = enabled, onClick = { apply(current.copy(thinkingMode = mode)) },
                    label = { Text(uiText(mode.name.lowercase().replaceFirstChar { it.uppercase() })) }) }
            }
            if (current.thinkingMode == LiteRtThinkingMode.ON) {
                val requested = current.thinkingTokenBudget ?: com.halo.sdk.provider.LOCAL_INTERACTIVE_REASONING_TOKEN_BUDGET
                Text(uiText("Thinking budget: requested %1\$d · effective %2\$d", requested,
                    minOf(requested, com.halo.sdk.provider.LOCAL_INTERACTIVE_REASONING_TOKEN_BUDGET)), style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(uiText("Larger context and longer answers use more memory. Actual use depends on this artifact and your phone."), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { advanced = !advanced }) { Text(uiText(if (advanced) "Simple" else "Advanced")) }
        if (!enabled) Text(uiText("Unload this model before changing its settings."), style = MaterialTheme.typography.bodySmall)
        if (advanced) {
            val defaults = listOf("Temperature" to current.temperature?.toString().orEmpty(),
                "Top-p" to current.topP?.toString().orEmpty(), "Top-k" to current.topK?.toString().orEmpty(),
                "Maximum output tokens" to current.maxOutputTokens?.toString().orEmpty(),
                "CPU threads" to current.cpuThreads?.toString().orEmpty()) +
                if (supportsThinking) listOf("Thinking tokens" to current.thinkingTokenBudget?.toString().orEmpty()) else emptyList()
            var draft by remember(modelId, current) { mutableStateOf(defaults.toMap()) }
            defaults.forEach { (label, _) ->
                HaloTextField(value = draft[label].orEmpty(), onValueChange = { draft = draft + (label to it.take(12)) },
                    enabled = enabled, label = { Text(uiText(label)) }, singleLine = true,
                    placeholder = { Text(uiText("Model default")) }, modifier = Modifier.fillMaxWidth(), kind = VoiceFieldKind.NUMBER, maxChars = 12)
            }
            Text(uiText("CPU threads apply only on CPU. Thinking tokens remain bounded by Halo's runtime policy."), style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(uiText("Stream replies"))
                Switch(checked = current.streaming ?: true, enabled = enabled,
                    onCheckedChange = { apply(current.copy(streaming = it)) })
            }
            Button(enabled = enabled, onClick = {
                fun decimal(label: String): Double? = draft[label].orEmpty().trim().takeIf { it.isNotEmpty() }?.let { requireNotNull(it.toDoubleOrNull()) }
                fun integer(label: String): Int? = draft[label].orEmpty().trim().takeIf { it.isNotEmpty() }?.let { requireNotNull(it.toIntOrNull()) }
                val next = runCatching { current.copy(temperature = decimal("Temperature"), topP = decimal("Top-p"),
                    topK = integer("Top-k"), maxOutputTokens = integer("Maximum output tokens"), cpuThreads = integer("CPU threads"),
                    thinkingTokenBudget = if (supportsThinking) integer("Thinking tokens") else null) }
                if (next.isSuccess) apply(next.getOrThrow()) else invalid = true
            }) { Text(uiText("Apply settings")) }
        }
        TextButton(enabled = enabled, onClick = { apply(null) }) { Text(uiText("Reset model tuning")) }
        if (invalid) Text(uiText("Check the tuning values and unload the model before applying changes."), color = MaterialTheme.colorScheme.error)
    }
}
