package com.halo.app.ui.screens.settings

import com.halo.app.language.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.ui.workspace.*
import com.halo.runtime.cognition.memory.*

@Composable fun LearningSettingsScreen(onBack: () -> Unit) {
    val graph = ManualDependencyGraph
    val model: LearningSettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            LearningSettingsViewModel(graph.storage, graph.learningPreferences(), graph.workspaceContext.useMemories, graph.workspaceContext::setUseMemories) as T
    })
    val preferences by model.preferences.collectAsStateWithLifecycle()
    val memoryUse by model.memoryUse.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(com.halo.app.ui.design.HaloSpacing.screen), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HaloWorkspaceHeader(uiText("Memory & learning"), onBack = onBack)
        Text(uiText("Learning"), style = MaterialTheme.typography.titleLarge)
        LearningPreference.entries.forEach { mode ->
            HaloWorkspaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(preferences.mode == mode, { model.mode(mode) })
                    Text(uiText(when (mode) { LearningPreference.OFF -> "Off"; LearningPreference.EXPLICIT_ONLY -> "Explicit only"; LearningPreference.SUGGEST -> "Suggest" }))
                }
                Text(uiText(when (mode) {
                    LearningPreference.OFF -> "Halo will not learn new personal memory from conversations. Existing memories remain available for manual use and editing."
                    LearningPreference.EXPLICIT_ONLY -> "Halo remembers something only when you directly ask."
                    LearningPreference.SUGGEST -> "Halo may quietly suggest useful memories and reusable workflows. Nothing is saved automatically."
                }))
            }
        }
        if (preferences.mode == LearningPreference.SUGGEST) {
            listOf(Triple("learning_memories", "Suggest memories", preferences.suggestMemories),
                Triple("learning_procedures", "Suggest repeated procedures", preferences.suggestProcedures),
                Triple("learning_automations", "Suggest automations", preferences.suggestAutomations),
                Triple("learning_model_reflection", "Local reflection", preferences.modelReflection)).forEach { (key, label, checked) ->
                Row(verticalAlignment = Alignment.CenterVertically) { Text(uiText(label), Modifier.weight(1f)); Switch(checked, { model.option(key, it) }) }
            }
            Text(uiText("Local reflection allows an eligible local model to propose memory after meaningful tasks. Off by default, runs only when system conditions allow, and suggestions require your approval."), style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider()
        Text(uiText("Using memory"), style = MaterialTheme.typography.titleLarge)
        Text(uiText("Memory sharing follows the Private / Power switch at the top of Halo."))
        Text(uiText("Halo may retrieve relevant saved memories within the current scope and context budget."))
        Text(uiText("Memory holds saved information. Learning controls suggestions. Notebook holds documents and files. Learning history describes past tasks, not current facts."))
        Text(uiText("Memory retrieval depends on available search capabilities and your privacy settings."), style = MaterialTheme.typography.bodySmall)
    }
}
