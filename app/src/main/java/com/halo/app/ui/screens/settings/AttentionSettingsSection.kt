package com.halo.app.ui.screens.settings

import com.halo.app.language.uiText
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.halo.app.di.ManualDependencyGraph
import com.halo.runtime.cognition.attention.*

@Composable fun AttentionSettingsSection() {
    val storage = ManualDependencyGraph.storage
    var level by remember { mutableStateOf(AttentionInitiativeLevel.fromStored(storage.getString("attention_initiative"))) }
    var dnd by remember { mutableStateOf(storage.getBoolean("attention_respect_dnd", true)) }
    LaunchedEffect(Unit) { storage.putString("attention_initiative", level.name) }
    Text(uiText("Halo initiative"), style = MaterialTheme.typography.titleMedium)
    AttentionInitiativeLevel.entries.forEach { option -> TextButton(onClick = {
        level = option; storage.putString("attention_initiative", option.name); ManualDependencyGraph.attentionContextProvider.refresh()
    }) { Text((if(level == option) "✓ " else "") + option.name.lowercase().replaceFirstChar(Char::uppercase)) } }
    Text(when(level) { AttentionInitiativeLevel.QUIET -> "Updates stay silent. Explicitly selected automation speech remains available."
        AttentionInitiativeLevel.BALANCED -> "Important updates may notify. Speech requires your selection."
        AttentionInitiativeLevel.ACTIVE -> "Important updates may notify more readily. Speech still requires your selection." })
    Row { Checkbox(dnd, { dnd = it; storage.putBoolean("attention_respect_dnd", it); ManualDependencyGraph.attentionContextProvider.refresh() }); Text(uiText("Respect Do Not Disturb")) }
    Text(uiText("Automation results: silent by default. Spoken updates: off unless configured per automation."))
}
