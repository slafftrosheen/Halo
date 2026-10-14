package com.halo.app.ui.screens.settings

import com.halo.app.language.uiText

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.res.painterResource
import com.halo.app.R
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onSetup: (com.halo.app.ui.onboarding.SetupStage) -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val languages = com.halo.app.di.ManualDependencyGraph.appLanguagePreferences
    val language by languages.language.collectAsStateWithLifecycle()
    SettingsContent(state, language, { languages.select(it); com.halo.app.nativeactions.HaloNativeWidget.refreshAsync(com.halo.app.di.ManualDependencyGraph.applicationContext) }, viewModel::setAppearanceMode,
        viewModel::openDestination, onSetup,
        com.halo.app.di.ManualDependencyGraph.palettePreferences, com.halo.app.di.ManualDependencyGraph.kidsPreferences,
        nearby = { com.halo.app.ui.mini.HaloNearbyControls() })
}

@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    language: com.halo.app.language.AppLanguage,
    onLanguage: (com.halo.app.language.AppLanguage) -> Unit,
    onAppearance: (AppearanceMode) -> Unit,
    onOpen: (SettingsDestination) -> Unit,
    onSetup: (com.halo.app.ui.onboarding.SetupStage) -> Unit = {},
    palettePreferences: com.halo.app.ui.theme.PalettePreferences? = null,
    kidsPreferences: com.halo.app.ui.screens.kids.KidsPreferences? = null,
    nearby: (@Composable () -> Unit)? = null,
) {
    var advanced by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var searchOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var searchQuery by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val searchableLabels = settingsSearchIndex.associateWith { uiText(it) }
    fun matches(vararg terms: String): Boolean =
        settingsMatches(searchQuery, *terms) ||
            settingsMatches(searchQuery, *terms.map { searchableLabels[it] ?: it }.toTypedArray())
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
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(key = "header") {
            HaloScreenHeader(uiText("Settings"), uiText("Make Halo feel like yours."),
                glyph = HaloGlyph.SETTINGS,
                trailing = {
                    IconButton(onClick = {
                        searchOpen = !searchOpen
                        if (!searchOpen) searchQuery = ""
                    }, modifier = Modifier.size(48.dp)) {
                        Icon(painterResource(R.drawable.halo_ic_search),
                            contentDescription = androidx.compose.ui.res.stringResource(R.string.brand_settings_search))
                    }
                })
        }
        if (searchOpen) item(key = "settings_search") {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it.take(100) },
                label = { Text(androidx.compose.ui.res.stringResource(R.string.brand_settings_search)) },
                leadingIcon = { Icon(painterResource(R.drawable.halo_ic_search), null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) TextButton(onClick = { searchQuery = "" }) {
                        Text(androidx.compose.ui.res.stringResource(R.string.brand_settings_clear))
                    }
                },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
        }
        if (searchQuery.isBlank()) item(key = "identity") { SettingsIdentity(state) }
        if (matches("Your companion", "Intelligence", "Choose how Halo answers", "Voice", "Listening and speaking")) item(key = "companion") {
            SettingsGroup("Your companion") {
                if (matches("Your companion", "Intelligence", "Choose how Halo answers")) SettingsLink(HaloGlyph.MODEL, "Intelligence", "Choose how Halo answers", { onOpen(SettingsDestination.INTELLIGENCE) })
                if (matches("Your companion", "Voice", "Listening and speaking")) SettingsLink(HaloGlyph.LISTEN, "Voice", "Listening and speaking", { onOpen(SettingsDestination.VOICE) })
            }
        }
        if (matches("Language", "interface", "default replies")) item(key = "language") {
            SettingsGroup("Language") {
                HaloWorkspaceRow(
                    title = language.nativeName,
                    subtitle = uiText("Use this language for Halo's interface and default replies. You can request another language at any time."),
                    trailingText = uiText("Change"),
                    onClick = { languagePicker = true },
                )
            }
        }
        if (matches("Appearance", "theme", "color", "colour", "dark", "light", "mint")) item(key = "appearance") {
            SettingsGroup("Appearance") {
                Row(Modifier.fillMaxWidth().padding(12.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppearanceMode.entries.forEach { mode ->
                        val selected = state.appearanceMode == mode
                        Surface(
                            Modifier.weight(1f).heightIn(min = 48.dp).selectable(
                                selected = selected, role = Role.RadioButton, onClick = { onAppearance(mode) },
                            ),
                            shape = com.halo.app.ui.design.HaloShapes.control,
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
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
                if (palettePreferences != null) PaletteControls(palettePreferences)
            }
        }
        if (kidsPreferences != null && matches("Kids mode", "learning", "tools")) item(key = "kids_mode") {
            val preferences = kidsPreferences
            val enabled by preferences.enabled.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()
            var failure by remember { mutableStateOf(false) }
            SettingsGroup("Kids mode") {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(uiText("Learning and tools on one page."), style = MaterialTheme.typography.bodyLarge)
                        Text(uiText("Tool approvals stay the same."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val kidsLabel = uiText("Kids mode")
                    Switch(modifier = Modifier.semantics { contentDescription = kidsLabel }, checked = enabled, onCheckedChange = { value -> scope.launch {
                        try { preferences.setEnabled(value); failure = false }
                        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                        catch (_: Exception) { failure = true }
                    } })
                }
                if (failure) Text(uiText("Could not save your change. Try again."), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }
        }
        if (nearby != null && matches("Halo nearby", "Floating", "Mini Halo", "Widget")) item(key = "nearby") { SettingsGroup("Halo nearby") { nearby() } }
        if (matches("Actions & connections", "Integrations", "Skills", "Automation", "Routines")) item(key = "actions") {
            SettingsGroup("Actions & connections") {
                if (matches("Actions & connections", "Integrations", "Connect apps and services")) SettingsLink(HaloGlyph.INTEGRATIONS, "Integrations", "Connect apps and services", { onOpen(SettingsDestination.INTEGRATIONS) })
                if (matches("Actions & connections", "Skills", "Actions Halo can use")) SettingsLink(HaloGlyph.SKILLS, "Skills", "Actions Halo can use", { onOpen(SettingsDestination.SKILLS) })
                if (matches("Actions & connections", "Automation", "Routines and notification digests")) SettingsLink(HaloGlyph.AUTOMATION, "Automation", "Routines and notification digests", { onOpen(SettingsDestination.AUTOMATION) })
            }
        }
        if (matches("Privacy and data", "Privacy", "Permissions", "Memory", "Storage", "backup")) item(key = "access") {
            SettingsGroup("Privacy and data") {
                if (matches("Privacy and data", "Privacy", "Context", "Choose what Halo can use")) SettingsLink(HaloGlyph.SECURITY, "Context & privacy", "Choose what Halo can use", { onOpen(SettingsDestination.PRIVACY) })
                if (matches("Privacy and data", "Permissions", "Device and data access")) SettingsLink(HaloGlyph.SECURITY, "Permissions", "Device and data access", { onOpen(SettingsDestination.PERMISSIONS) })
                if (matches("Privacy and data", "Memory & learning", "Review what Halo remembers")) SettingsLink(HaloGlyph.MEMORY, "Memory & learning", "Review what Halo remembers", { onOpen(SettingsDestination.MEMORY) })
                if (matches("Privacy and data", "Storage & backup", "Manage your saved work")) SettingsLink(HaloGlyph.STORAGE, "Storage & backup", "Manage your saved work", { onOpen(SettingsDestination.STORAGE) })
            }
        }
        if (matches("Setup and support", "Guided setup", "Check setup", "Android assistant")) item(key = "setup") {
            SettingsGroup("Setup and support") {
                if (matches("Setup and support", "Guided setup", "Resume or revisit setup without resetting your choices")) SettingsLink(HaloGlyph.QUESTION, "Guided setup", "Resume or revisit setup without resetting your choices", { onSetup(com.halo.app.ui.onboarding.SetupStage.WELCOME) })
                if (matches("Setup and support", "Check setup", "Setup readiness", "Check what works and share a safe report")) SettingsLink(HaloGlyph.DIAGNOSTICS, "Setup readiness", "Check what works and share a safe report", { onSetup(com.halo.app.ui.onboarding.SetupStage.READINESS) })
                if (matches("Setup and support", "Android assistant", "Use Halo from your phone")) SettingsLink(HaloGlyph.PHONE, "Android assistant", "Use Halo from your phone", { onOpen(SettingsDestination.ASSISTANT) })
            }
        }
        if (matches("Advanced", "Runtime status", "developer", "task details")) item(key = "advanced_toggle") {
            OutlinedButton(onClick = { advanced = !advanced }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(uiText(if(advanced) "Hide advanced settings" else "Advanced settings")) }
        }
        if(advanced || (searchQuery.isNotBlank() && matches("Runtime status", "task details"))) item(key = "system") {
            SettingsGroup("System") {
                SettingsLink(HaloGlyph.RUNTIME_STATUS, "Runtime status", "Advanced task details", { onOpen(SettingsDestination.RUNTIME) })
            }
        }
        if (searchQuery.isNotBlank() &&
            !settingsHasMatches(searchQuery) &&
            searchableLabels.values.none { it.contains(searchQuery, ignoreCase = true) }) item(key = "no_results") {
            Text(androidx.compose.ui.res.stringResource(R.string.brand_settings_no_results),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (searchQuery.isBlank()) item(key = "version") {
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
        Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small),
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
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
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

/**
 * Pure search projection. Every indexed destination uses an existing Settings route;
 * searching never requests permissions, tests providers, or changes selected models.
 */
internal fun settingsMatches(query: String, vararg labels: String): Boolean {
    val q = query.trim()
    return q.isEmpty() || labels.any { it.contains(q, ignoreCase = true) }
}

private val settingsSearchIndex = listOf(
    "Your companion", "Actions & connections", "Privacy and data", "Setup and support",
    "Intelligence", "Choose how Halo answers", "Voice", "Listening and speaking",
    "Language", "default replies", "Appearance", "theme", "color", "Kids mode",
    "Halo nearby", "Floating", "Mini Halo", "Integrations", "Connect apps and services",
    "Skills", "Actions Halo can use", "Automation", "Routines",
    "Privacy", "Context", "Permissions", "Device and data access",
    "Memory", "Storage", "backup", "Guided setup", "Check setup",
    "Android assistant", "Advanced", "Runtime status", "task details"
)

internal fun settingsHasMatches(query: String): Boolean =
    settingsMatches(query, *settingsSearchIndex.toTypedArray())
