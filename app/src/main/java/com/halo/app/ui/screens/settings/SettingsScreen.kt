package com.halo.app.ui.screens.settings

import com.halo.app.language.uiText

import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.verticalScroll
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
    var searchOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var searchQuery by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val searchableLabels = settingsSearchIndex.associateWith { uiText(it) }
    fun matches(vararg terms: String): Boolean =
        settingsMatches(searchQuery, *terms) ||
            settingsMatches(searchQuery, *terms.map { searchableLabels[it] ?: it }.toTypedArray())
    var languagePicker by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    if (languagePicker) {
        // Full-screen Round 2 language page. The book illustrates five languages;
        // only the three languages with complete app catalogues are actionable.
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { languagePicker = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            LanguagePickerPage(
                selected = language,
                onSelect = { onLanguage(it); languagePicker = false },
                onBack = { languagePicker = false },
            )
        }
    }
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = com.halo.app.ui.design.HaloSpacing.screen, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            HaloScreenHeader(uiText("Settings"),
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
        if (matches("Your companion", "Intelligence", "Choose how halo answers",
                "Voice", "Listening and speaking", "Language", "interface", "default replies")) item(key = "companion") {
            SettingsGroup("Your companion", compactRows = true) {
                if (matches("Your companion", "Intelligence", "Choose how halo answers"))
                    SettingsLink(HaloGlyph.MODEL, "Intelligence", "Choose how halo answers", { onOpen(SettingsDestination.INTELLIGENCE) })
                if (matches("Your companion", "Voice", "Listening and speaking"))
                    SettingsLink(HaloGlyph.LISTEN, "Voice", "Listening and speaking", { onOpen(SettingsDestination.VOICE) })
                if (matches("Your companion", "Language", "interface", "default replies"))
                    SettingsLink(HaloGlyph.LANGUAGE, "Language", language.nativeName, { languagePicker = true })
            }
        }
        if (matches("Privacy and data", "Privacy", "Context", "Choose what Halo can use", "Permissions", "Device and data access", "Memory & learning", "Review what Halo remembers", "Storage & backup", "Manage your saved work")) item(key = "access") {
            SettingsGroup("Privacy and data", compactRows = true) {
                if (matches("Privacy and data", "Privacy", "Context", "Choose what Halo can use")) SettingsLink(HaloGlyph.SECURITY, "Context & privacy", "Choose what Halo can use", { onOpen(SettingsDestination.PRIVACY) })
                if (matches("Privacy and data", "Permissions", "Device and data access")) SettingsLink(HaloGlyph.SECURITY, "Permissions", "Device and data access", { onOpen(SettingsDestination.PERMISSIONS) })
                if (matches("Privacy and data", "Memory & learning", "Review what Halo remembers")) SettingsLink(HaloGlyph.MEMORY, "Memory & learning", "Review what Halo remembers", { onOpen(SettingsDestination.MEMORY) })
                if (matches("Privacy and data", "Storage & backup", "Manage your saved work")) SettingsLink(HaloGlyph.STORAGE, "Storage & backup", "Manage your saved work", { onOpen(SettingsDestination.STORAGE) })
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
                            shape = com.halo.app.ui.design.HaloShapes.pill,
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
        if (matches("Actions & connections", "Integrations", "Connect apps and services", "Skills", "Actions Halo can use", "Automation", "Routines and notification digests")) item(key = "actions") {
            SettingsGroup("Actions & connections", compactRows = true) {
                if (matches("Actions & connections", "Integrations", "Connect apps and services")) SettingsLink(HaloGlyph.INTEGRATIONS, "Integrations", "Connect apps and services", { onOpen(SettingsDestination.INTEGRATIONS) })
                if (matches("Actions & connections", "Skills", "Actions Halo can use")) SettingsLink(HaloGlyph.SKILLS, "Skills", "Actions Halo can use", { onOpen(SettingsDestination.SKILLS) })
                if (matches("Actions & connections", "Automation", "Routines and notification digests")) SettingsLink(HaloGlyph.AUTOMATION, "Automation", "Routines and notification digests", { onOpen(SettingsDestination.AUTOMATION) })
            }
        }
        if (matches("Setup and support", "Guided setup", "Resume or revisit setup without resetting your choices", "Check setup", "Setup readiness", "System check", "Check what works and share a safe report", "Android assistant", "Use Halo from your phone")) item(key = "setup") {
            SettingsGroup("Setup and support", compactRows = true) {
                if (matches("Setup and support", "Guided setup", "Resume or revisit setup without resetting your choices")) SettingsLink(HaloGlyph.QUESTION, "Guided setup", "Resume or revisit setup without resetting your choices", { onSetup(com.halo.app.ui.onboarding.SetupStage.WELCOME) })
                if (matches("Setup and support", "Check setup", "Setup readiness", "Check what works and share a safe report")) SettingsLink(HaloGlyph.DIAGNOSTICS, "Setup readiness", "Check what works and share a safe report", { onSetup(com.halo.app.ui.onboarding.SetupStage.READINESS) })
                if (matches("Setup and support", "System check", "Check what works and share a safe report"))
                    SettingsLink(HaloGlyph.DIAGNOSTICS, "System check", "Check what works and share a safe report",
                        { onOpen(SettingsDestination.SYSTEM_CHECK) })
                if (matches("Setup and support", "Android assistant", "Use Halo from your phone")) SettingsLink(HaloGlyph.PHONE, "Android assistant", "Use Halo from your phone", { onOpen(SettingsDestination.ASSISTANT) })
            }
        }
        if (matches("Advanced", "Runtime status", "developer", "task details", "System check")) item(key = "advanced") {
            SettingsGroup("Advanced", compactRows = true) {
                SettingsLink(HaloGlyph.DIAGNOSTICS, "Advanced",
                    "For developers and testers", { onOpen(SettingsDestination.ADVANCED) })
            }
        }
        if (searchQuery.isNotBlank() &&
            !settingsHasMatches(searchQuery) &&
            searchableLabels.values.none { it.contains(searchQuery.trim(), ignoreCase = true) }) item(key = "no_results") {
            Text(androidx.compose.ui.res.stringResource(R.string.brand_settings_no_results),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (searchQuery.isBlank()) item(key = "version") {
            Text("halo  \u00b7  ${state.version}", Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Round 2 p.41: destination groups share ONE outer card. Extra nested cards,
 * their insets and double borders made the Settings root far taller than the
 * reference. Complex controls retain the original padded settings section.
 */
@Composable
private fun SettingsGroup(
    title: String,
    compactRows: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (compactRows) {
        com.halo.app.ui.workspace.HaloWorkspaceSection(uiText(title)) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    } else {
        HaloSettingsSection(uiText(title)) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
private fun SettingsLink(glyph: HaloGlyph, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = androidx.compose.ui.graphics.Color.Transparent,
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(40.dp).background(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f),
                RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center) {
                HaloIcon(glyph, contentDescription = null, modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(uiText(title), style = MaterialTheme.typography.titleSmall)
                Text(uiText(subtitle), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(androidx.compose.material.icons.Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp))
        }
    }
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
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HaloSignalFace(presentation = HaloSignalPresentation(label = "halo", reducedMotion = true), size = 56.dp,
                contentDescription = "halo")
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("halo", style = MaterialTheme.typography.titleMedium)
                state.providerLocation?.let { location ->
                    val label = when (location) {
                        com.halo.sdk.provider.AiExecutionLocation.ON_DEVICE -> R.string.brand_intelligence_phone
                        com.halo.sdk.provider.AiExecutionLocation.LOCAL_NETWORK -> R.string.brand_intelligence_network
                        com.halo.sdk.provider.AiExecutionLocation.REMOTE -> R.string.brand_intelligence_cloud
                    }
                    Text(androidx.compose.ui.res.stringResource(label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
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
    "Your companion",
    "Intelligence",
    "Choose how halo answers",
    "Voice",
    "Listening and speaking",
    "Language",
    "interface",
    "default replies",
    "Appearance",
    "theme",
    "color",
    "colour",
    "dark",
    "light",
    "mint",
    "Kids mode",
    "learning",
    "tools",
    "Halo nearby",
    "Floating",
    "Mini Halo",
    "Widget",
    "Actions & connections",
    "Integrations",
    "Connect apps and services",
    "Skills",
    "Actions Halo can use",
    "Automation",
    "Routines and notification digests",
    "Privacy and data",
    "Privacy",
    "Context",
    "Choose what Halo can use",
    "Permissions",
    "Device and data access",
    "Memory & learning",
    "Review what Halo remembers",
    "Storage & backup",
    "Manage your saved work",
    "Context & privacy",
    "Setup and support",
    "Guided setup",
    "Resume or revisit setup without resetting your choices",
    "Check setup",
    "Setup readiness",
    "Check what works and share a safe report",
    "Android assistant",
    "Use Halo from your phone",
    "Advanced",
    "Runtime status",
    "developer",
    "task details",
    "System check",
)

internal fun settingsHasMatches(query: String): Boolean =
    settingsMatches(query, *settingsSearchIndex.toTypedArray())

/** English catalogue label and native name are both searchable. */
internal fun languagePickerEnglishName(language: com.halo.app.language.AppLanguage): String = when (language) {
    com.halo.app.language.AppLanguage.ENGLISH -> "English"
    com.halo.app.language.AppLanguage.LATVIAN -> "Latvian"
    com.halo.app.language.AppLanguage.RUSSIAN -> "Russian"
}

internal fun languagePickerMatches(
    language: com.halo.app.language.AppLanguage, query: String,
    localizedName: String = languagePickerEnglishName(language),
): Boolean {
    val term = query.trim()
    return term.isBlank() ||
        language.nativeName.contains(term, ignoreCase = true) ||
        languagePickerEnglishName(language).contains(term, ignoreCase = true) ||
        localizedName.contains(term, ignoreCase = true) ||
        language.tag.contains(term, ignoreCase = true)
}

/** Round 2 p.41: search pill, one grouped list, native-language titles
 * followed by their readable translated name. No fictional languages or
 * unsupported automatic reply-language switch.
 */
@Composable
internal fun LanguagePickerPage(
    selected: com.halo.app.language.AppLanguage,
    onSelect: (com.halo.app.language.AppLanguage) -> Unit,
    onBack: () -> Unit,
) {
    var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val scheme = MaterialTheme.colorScheme
    val options = com.halo.app.language.AppLanguage.entries.filter {
        languagePickerMatches(it, query, uiText(languagePickerEnglishName(it)))
    }
    Surface(Modifier.fillMaxSize(), color = scheme.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HaloScreenHeader(
                title = uiText("Language"),
                subtitle = uiText("halo's words and replies"),
                navigation = onBack,
            )
            TextField(
                value = query,
                onValueChange = { query = it.take(80) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                singleLine = true,
                placeholder = { Text(uiText("Search languages")) },
                leadingIcon = { Icon(painterResource(R.drawable.halo_ic_search), contentDescription = null) },
                trailingIcon = {
                    if (query.isNotBlank()) IconButton(onClick = { query = "" }) {
                        Icon(androidx.compose.material.icons.Icons.Default.Close,
                            contentDescription = uiText("Clear"))
                    }
                },
                shape = com.halo.app.ui.design.HaloShapes.pill,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = scheme.surfaceContainer,
                    unfocusedContainerColor = scheme.surfaceContainer,
                    disabledContainerColor = scheme.surfaceContainer,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
            )
            Column(Modifier.weight(1f).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                if (options.isEmpty()) {
                    Text(uiText("No matching languages"), style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = com.halo.app.ui.design.HaloShapes.card,
                        color = scheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, scheme.outlineVariant),
                    ) {
                        Column(Modifier.fillMaxWidth().selectableGroup()) {
                            options.forEachIndexed { index, option ->
                                if (index > 0) HorizontalDivider(
                                    Modifier.padding(horizontal = 16.dp),
                                    color = scheme.outlineVariant)
                                val checked = selected == option
                                Row(
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp)
                                        .selectable(selected = checked, role = Role.RadioButton,
                                            onClick = { onSelect(option) })
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Column(Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(option.nativeName, style = MaterialTheme.typography.titleSmall,
                                            color = scheme.onSurface)
                                        Text(uiText(languagePickerEnglishName(option)),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = scheme.onSurfaceVariant)
                                    }
                                    if (checked) {
                                        Icon(androidx.compose.material.icons.Icons.Default.Check,
                                            contentDescription = null,
                                            tint = scheme.primary,
                                            modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                Text(uiText("Dates and times follow this language too."),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant)
            }
        }
    }
}
