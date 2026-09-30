package com.halo.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.ui.workspace.*
import com.halo.runtime.cognition.memory.*

@Composable fun LearningSettingsScreen(onBack: ()->Unit) {
    val graph=ManualDependencyGraph;val storage=graph.storage
    var preferences by remember { mutableStateOf(graph.learningPreferences()) }
    var memoryUse by remember { mutableStateOf(storage.getBoolean("policy_memories",false)) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        HaloWorkspaceHeader("Memory & learning",onBack=onBack)
        LearningPreference.entries.forEach { mode -> TextButton(onClick={ preferences=preferences.copy(mode=mode);storage.putString("learning_mode",mode.name) }) {
            Text((if(mode==preferences.mode) "✓ " else "")+when(mode) { LearningPreference.OFF->"Off";LearningPreference.EXPLICIT_ONLY->"Explicit requests only";LearningPreference.SUGGEST->"Suggest useful learning" })
        } }
        Text("You approve durable memories and reusable behavior. Turning learning off keeps manual Notebook operations available.")
        Row { Checkbox(preferences.mode != LearningPreference.OFF,{ enabled -> preferences=preferences.copy(mode=if(enabled) LearningPreference.EXPLICIT_ONLY else LearningPreference.OFF);storage.putString("learning_mode",preferences.mode.name) });Text("Remember explicit requests") }
        listOf("learning_memories" to "Suggest useful memories","learning_procedures" to "Suggest repeated procedures","learning_automations" to "Suggest automations","learning_model_reflection" to "Allow bounded local model reflection").forEach { (key,label) ->
            var checked by remember(key) { mutableStateOf(storage.getBoolean(key,key != "learning_model_reflection")) }
            Row { Checkbox(checked,{checked=it;storage.putBoolean(key,it)});Text(label) }
        }
        Row { Checkbox(memoryUse,{memoryUse=it;graph.workspaceContext.setUseMemories(it)});Text("Use memory in conversations") }
        Text("Semantic memory: lexical retrieval available; encoder is not installed. No model downloads happen here.")
        Text("Ordinary learning suggestions stay silent in Notebook Inbox. Local model reflection is optional and yields to your work.")
    }
}
