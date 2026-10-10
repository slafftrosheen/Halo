package com.halo.app.ui.screens.settings

import com.halo.app.language.uiText

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.ui.design.HaloScreenHeader
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import com.halo.app.ui.theme.AppearanceMode

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onSetup: (com.halo.app.ui.onboarding.SetupStage) -> Unit = {}) {
    var advanced by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var developer by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val languages = com.halo.app.di.ManualDependencyGraph.appLanguagePreferences
    val language by languages.language.collectAsStateWithLifecycle()
    val languageScope = rememberCoroutineScope()
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(key = "header") { HaloScreenHeader(uiText("Settings"), uiText("Make Halo feel like yours.")) }
        item(key = "guided_setup") {
            SettingsGroup("Basic setup") {
                SettingsLink(HaloGlyph.QUESTION, "Guided setup", "Resume or revisit setup without resetting your choices", { onSetup(com.halo.app.ui.onboarding.SetupStage.WELCOME) })
                SettingsLink(HaloGlyph.MODEL, "Answer setup", "Check how Halo answers", { onSetup(com.halo.app.ui.onboarding.SetupStage.AI) })
                SettingsLink(HaloGlyph.LISTEN, "Voice setup", "Test listening and speaking", { onSetup(com.halo.app.ui.onboarding.SetupStage.SPEECH) })
                SettingsLink(HaloGlyph.DEVICE, "Devices", "Your devices", { onSetup(com.halo.app.ui.onboarding.SetupStage.DEVICES) })
                SettingsLink(HaloGlyph.SECURITY, "Privacy", "Your data, your choice", { onSetup(com.halo.app.ui.onboarding.SetupStage.PRIVACY) })
                SettingsLink(HaloGlyph.SECURITY, "Setup readiness", "Check what works and share a safe report", { onSetup(com.halo.app.ui.onboarding.SetupStage.READINESS) })
            }
        }
        item(key = "language") {
            SettingsGroup("Language") {
                Column(Modifier.fillMaxWidth().selectableGroup().padding(12.dp)) {
                    com.halo.app.language.AppLanguage.entries.forEach { option ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                            selected = option == language, role = Role.RadioButton,
                            onClick = {
                                languages.select(option)
                                languageScope.launch { com.halo.app.di.ManualDependencyGraph.speechLanguageSettingsRepository.select(option.tag) }
                            }), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = option == language, onClick = null)
                            Text(option.nativeName)
                        }
                    }
                    Text(uiText("Use this language for Halo's interface and default replies. You can request another language at any time."),
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item(key = "advanced_toggle") {
            OutlinedButton(onClick = { advanced = !advanced }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(uiText(if(advanced) "Hide advanced settings" else "Advanced settings")) }
        }
        if(advanced) item(key = "companion") {
            SettingsGroup("Your companion") {
                SettingsLink(HaloGlyph.MODEL, "Intelligence", "Choose how Halo answers", viewModel::openIntelligence)
                SettingsLink(HaloGlyph.LISTEN, "Voice", "Listening and speaking", viewModel::openVoice)
                SettingsLink(HaloGlyph.TOOL, "Integrations", "Connect apps and services", viewModel::openIntegrations)
                SettingsLink(HaloGlyph.TOOL, "Skills", "Actions Halo can use", viewModel::openSkills)
                SettingsLink(HaloGlyph.STORAGE, "Memory & learning", "Review what Halo remembers", viewModel::openLearning)
            }
        }
        item(key = "access") {
            SettingsGroup("Access & data") {
                SettingsLink(HaloGlyph.SECURITY, "Permissions", "Device and data access", viewModel::openPermissions)
                SettingsLink(HaloGlyph.STORAGE, "Storage & backup", "Manage your saved work", viewModel::openStorageBackup)
            }
        }
        item(key = "appearance") {
            SettingsGroup("Appearance") {
                Row(Modifier.fillMaxWidth().padding(12.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppearanceMode.entries.forEach { mode ->
                        val selected = state.appearanceMode == mode
                        Surface(
                            Modifier.weight(1f).heightIn(min = 48.dp).selectable(
                                selected = selected, role = Role.RadioButton, onClick = { viewModel.setAppearanceMode(mode) },
                            ),
                            shape = MaterialTheme.shapes.small,
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(uiText(mode.name.lowercase().replaceFirstChar { it.uppercase() }), textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        if(advanced) item(key = "system") {
            SettingsGroup("System") {
                SettingsLink(HaloGlyph.PHONE, "Android assistant", "Use Halo from your phone", viewModel::openAndroidAssistant)
                SettingsLink(HaloGlyph.AUTOMATION, "Automation", "Routines and notification digests", viewModel::openAutomation)
                TextButton(onClick = { developer = !developer }) { Text(uiText("Developer tools")) }
                if(developer) SettingsLink(HaloGlyph.ACTIVITY, "Runtime status", "Advanced task details", viewModel::openRuntimeStatus)
            }
        }
        item(key = "version") {
            Text("Halo  \u00b7  ${state.version}", Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(uiText(title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
private fun SettingsLink(glyph: HaloGlyph, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            HaloIcon(glyph, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(uiText(title), style = MaterialTheme.typography.titleSmall)
                Text(uiText(subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
