package com.halo.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import com.halo.app.language.uiText
import com.halo.app.skills.SkillStarter
import com.halo.sdk.runtime.skills.SkillDescriptor

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun SkillStarterEditor(onDismiss: () -> Unit, onReview: (String, ByteArray) -> Unit, sourceFor: (SkillStarter) -> SkillDescriptor?) {
    var starterName by rememberSaveable { mutableStateOf(SkillStarter.WORKSPACE.name) }
    val starter = SkillStarter.valueOf(starterName)
    val suggestedName = uiText(starter.title)
    var name by rememberSaveable(starterName) { mutableStateOf(suggestedName) }
    var instructions by rememberSaveable(starterName) { mutableStateOf(starter.instructions) }
    var error by remember { mutableStateOf<String?>(null) }
    BackHandler(onBack = onDismiss)
    Surface(Modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().imePadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(uiText("Create a skill"), style = MaterialTheme.typography.headlineSmall)
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(uiText("Start with existing Halo actions, then customize the instructions. Review before installing."))
                SkillStarter.entries.forEach { option ->
                    FilterChip(starter == option, onClick = { starterName = option.name; error = null }, label = { Text(uiText(option.title)) })
                }
                OutlinedTextField(name, { name = it.take(80); error = null }, modifier = Modifier.fillMaxWidth(), label = { Text(uiText("Skill name")) }, singleLine = true)
                OutlinedTextField(instructions, { instructions = it.take(16_000); error = null },
                modifier = Modifier.fillMaxWidth().height(180.dp), label = { Text(uiText("Instructions")) })
                Text(uiText("The skill installs disabled. You can review its actions before enabling it."))
                error?.let { Text(uiText(it), color = MaterialTheme.colorScheme.error) }
            }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onDismiss) { Text(uiText("Cancel")) }
                TextButton(enabled = name.isNotBlank() && instructions.isNotBlank(), onClick = {
                    runCatching { starter.packageBytes(name, instructions, sourceFor(starter) ?: error("Skill starter is unavailable")) }.onSuccess { onReview("SKILL.md", it) }
                    .onFailure { error = it.message ?: "Invalid skill package" }
                }) { Text(uiText("Review skill")) }
            }
        }
    }

}
