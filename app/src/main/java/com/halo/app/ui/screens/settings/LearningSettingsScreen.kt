package com.halo.app.ui.screens.settings

import com.halo.app.ui.presence.BindShellChildPage
import com.halo.app.language.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.ui.workspace.*
import com.halo.runtime.cognition.memory.*

@Composable fun LearningSettingsScreen(onBack: () -> Unit) {
    val graph = ManualDependencyGraph
    val model: LearningSettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            LearningSettingsViewModel(graph.storage, graph.learningPreferences(), graph.workspaceContext.useMemories, graph.workspaceContext::setUseMemories,
                graph.learningConfiguration) as T
    })
    val preferences by model.preferences.collectAsStateWithLifecycle()
    val memoryUse by model.memoryUse.collectAsStateWithLifecycle()
    val historyUse by graph.workspaceContext.referenceChatHistory.collectAsStateWithLifecycle()
    val shellHosted = BindShellChildPage("Memory & learning", onBack, sourceIdentity = "${preferences.mode}:$memoryUse:$historyUse")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(com.halo.app.ui.design.HaloSpacing.screen), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!shellHosted) HaloWorkspaceHeader(uiText("Memory & learning"), onBack = onBack)
        Text(uiText("Using memory"), style = MaterialTheme.typography.titleLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.memory_reference_saved))
                Text(stringResource(R.string.memory_reference_saved_detail), style = MaterialTheme.typography.bodySmall)
            }
            Switch(memoryUse, model::useMemory)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.memory_reference_history))
                Text(stringResource(R.string.memory_reference_history_detail), style = MaterialTheme.typography.bodySmall)
            }
            Switch(memoryUse && historyUse, graph.workspaceContext::setReferenceChatHistory, enabled = memoryUse)
        }
        Text(stringResource(R.string.memory_reference_active_profile), style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Text(uiText("Learning"), style = MaterialTheme.typography.titleLarge)
        LearningPreference.entries.forEach { mode ->
            HaloWorkspaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(preferences.mode == mode, { model.mode(mode) })
                    Text(uiText(when (mode) { LearningPreference.OFF -> "Off"; LearningPreference.EXPLICIT_ONLY -> "Explicit only"; LearningPreference.SUGGEST -> "Suggest" }))
                }
                Text(stringResource(when (mode) {
                    LearningPreference.OFF -> R.string.memory_learning_off_detail
                    LearningPreference.EXPLICIT_ONLY -> R.string.memory_learning_explicit_detail
                    LearningPreference.SUGGEST -> R.string.memory_learning_suggest_detail
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
    }
}
