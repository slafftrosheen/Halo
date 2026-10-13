package com.halo.app.ui.screens.settings

import com.halo.app.language.uiText

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.halo.app.ui.presence.HaloSignalFace
import com.halo.app.ui.presence.HaloSignalPresentation
import com.halo.app.ui.workspace.HaloWorkspaceRow
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
    var languagePicker by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    if (languagePicker) {
        AlertDialog(
            onDismissRequest = { languagePicker = false },
            title = { Text(uiText("Language")) },
            text = {
                Column(Modifier.selectableGroup()) {
                    com.halo.app.language.AppLanguage.entries.forEach { option ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 52.dp).selectable(
                                selected = option == language,
                                role = Role.RadioButton,
                                onClick = { onLanguage(option); languagePicker = false },
                            ).padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = option == language, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(option.nativeName, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { languagePicker = false }) { Text(uiText("Close")) }
            },
        )
    }
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = com.halo.app.ui.design.HaloSpacing.screen, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(key = "header") { HaloScreenHeader(uiText("Settings"), uiText("Make Halo feel like yours."), glyph = HaloGlyph.SETTINGS) }
        item(key = "identity") { SettingsIdentity(state) }
        item(key = "companion") {
            SettingsGroup("Your companion") {
                SettingsLink(HaloGlyph.MODEL, "Intelligence", "Choose how Halo answers", { onOpen(SettingsDestination.INTELLIGENCE) })
                SettingsLink(HaloGlyph.LISTEN, "Voice", "Listening and speaking", { onOpen(SettingsDestination.VOICE) })
            }
        }
        item(key = "language") {
            SettingsGroup("Language") {
                HaloWorkspaceRow(
                    title = language.nativeName,
                    subtitle = uiText("Use this language for Halo's interface and default replies. You can request another language at any time."),
                    trailingText = uiText("Change"),
                    onClick = { languagePicker = true },
                )
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
                SettingsLink(HaloGlyph.MEMORY, "Memory & learning", "Review what Halo remembers", { onOpen(SettingsDestination.MEMORY) })
                SettingsLink(HaloGlyph.STORAGE, "Storage & backup", "Manage your saved work", { onOpen(SettingsDestination.STORAGE) })
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
    com.halo.app.ui.screens.settings.HaloSettingsSection(uiText(title)) {
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun SettingsLink(glyph: HaloGlyph, title: String, subtitle: String, onClick: () -> Unit) {
    com.halo.app.ui.workspace.HaloWorkspaceDestination(uiText(title), uiText(subtitle), onClick, leading = {
        Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center) {
            HaloIcon(glyph, contentDescription = null, modifier = Modifier.size(23.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    })
}

/** Brand presence is decorative; these are configuration summaries, not health claims. */
@Composable
private fun SettingsIdentity(state: SettingsUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HaloSignalFace(presentation = HaloSignalPresentation(label = "Halo", reducedMotion = true), size = 76.dp,
                contentDescription = uiText("Halo"))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(uiText("Halo"), style = MaterialTheme.typography.titleLarge)
                Text("${uiText("Intelligence")}: ${uiText(state.provider)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${uiText("Voice")}: ${uiText(state.voiceStatus)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
