package com.halo.app.ui.screens.settings

import com.halo.app.language.uiText

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val languages = com.halo.app.di.ManualDependencyGraph.appLanguagePreferences
    val language by languages.language.collectAsStateWithLifecycle()
    SettingsContent(state, language, languages::select, viewModel::setAppearanceMode,
        viewModel::openDestination, onSetup)
}

@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    language: com.halo.app.language.AppLanguage,
    onLanguage: (com.halo.app.language.AppLanguage) -> Unit,
    onAppearance: (AppearanceMode) -> Unit,
    onOpen: (SettingsDestination) -> Unit,
    onSetup: (com.halo.app.ui.onboarding.SetupStage) -> Unit = {},
) {
    var advanced by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(key = "header") { HaloScreenHeader(uiText("Settings"), uiText("Make Halo feel like yours.")) }
        item(key = "companion") {
            SettingsGroup("Your companion") {
                SettingsLink(HaloGlyph.MODEL, "Intelligence", "Choose how Halo answers", { onOpen(SettingsDestination.INTELLIGENCE) })
                SettingsLink(HaloGlyph.LISTEN, "Voice", "Listening and speaking", { onOpen(SettingsDestination.VOICE) })
                SettingsLink(HaloGlyph.DEVICE, "Devices", "Your devices", { onOpen(SettingsDestination.DEVICES) })
                SettingsLink(HaloGlyph.STORAGE, "Memory & learning", "Review what Halo remembers", { onOpen(SettingsDestination.MEMORY) })
            }
        }
        item(key = "language") {
            SettingsGroup("Language") {
                Column(Modifier.fillMaxWidth().selectableGroup().padding(12.dp)) {
                    com.halo.app.language.AppLanguage.entries.forEach { option ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                            selected = option == language, role = Role.RadioButton,
                            onClick = {
                                onLanguage(option)
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
        item(key = "actions") {
            SettingsGroup("Actions & connections") {
                SettingsLink(HaloGlyph.TOOL, "Integrations", "Connect apps and services", { onOpen(SettingsDestination.INTEGRATIONS) })
                SettingsLink(HaloGlyph.TOOL, "Skills", "Actions Halo can use", { onOpen(SettingsDestination.SKILLS) })
                SettingsLink(HaloGlyph.AUTOMATION, "Automation", "Routines and notification digests", { onOpen(SettingsDestination.AUTOMATION) })
            }
        }
        item(key = "access") {
            SettingsGroup("Access & data") {
                SettingsLink(HaloGlyph.SECURITY, "Permissions", "Device and data access", { onOpen(SettingsDestination.PERMISSIONS) })
                SettingsLink(HaloGlyph.STORAGE, "Storage & backup", "Manage your saved work", { onOpen(SettingsDestination.STORAGE) })
            }
        }
        item(key = "appearance") {
            SettingsGroup("Appearance") {
                Row(Modifier.fillMaxWidth().padding(12.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppearanceMode.entries.forEach { mode ->
                        val selected = state.appearanceMode == mode
                        Surface(
                            Modifier.weight(1f).heightIn(min = 48.dp).selectable(
                                selected = selected, role = Role.RadioButton, onClick = { onAppearance(mode) },
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
        item(key = "setup") {
            SettingsGroup("Setup & support") {
                SettingsLink(HaloGlyph.QUESTION, "Guided setup", "Resume or revisit setup without resetting your choices", { onSetup(com.halo.app.ui.onboarding.SetupStage.WELCOME) })
                SettingsLink(HaloGlyph.SECURITY, "Setup readiness", "Check what works and share a safe report", { onSetup(com.halo.app.ui.onboarding.SetupStage.READINESS) })
                SettingsLink(HaloGlyph.PHONE, "Android assistant", "Use Halo from your phone", { onOpen(SettingsDestination.ASSISTANT) })
            }
        }
        item(key = "advanced_toggle") {
            OutlinedButton(onClick = { advanced = !advanced }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(uiText(if(advanced) "Hide advanced settings" else "Advanced settings")) }
        }
        if(advanced) item(key = "system") {
            SettingsGroup("System") {
                SettingsLink(HaloGlyph.ACTIVITY, "Runtime status", "Advanced task details", { onOpen(SettingsDestination.RUNTIME) })
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
    com.halo.app.ui.workspace.HaloWorkspaceSection(uiText(title)) {
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun SettingsLink(glyph: HaloGlyph, title: String, subtitle: String, onClick: () -> Unit) {
    com.halo.app.ui.workspace.HaloWorkspaceDestination(title, subtitle, onClick, leading = {
            HaloIcon(glyph, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
    })
}
