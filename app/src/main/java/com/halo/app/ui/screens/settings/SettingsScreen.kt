package com.halo.app.ui.screens.settings

import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
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
import androidx.compose.ui.graphics.luminance
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

private data class SettingsMenuRow(
    val glyph: HaloGlyph,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit,
    val aliases: List<String> = emptyList(),
) {
    val searchLabels get() = listOf(title, subtitle) + aliases
}

private data class SettingsMenuGroup(val key: String, val title: String, val rows: List<SettingsMenuRow>)

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onSetup: (com.halo.app.ui.onboarding.SetupStage) -> Unit = {}, initialAdvanced: Boolean = false) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val languages = com.halo.app.di.ManualDependencyGraph.appLanguagePreferences
    val language by languages.language.collectAsStateWithLifecycle()
    SettingsContent(state, language, { languages.select(it); com.halo.app.nativeactions.HaloNativeWidget.refreshAsync(com.halo.app.di.ManualDependencyGraph.applicationContext) }, viewModel::setAppearanceMode,
        viewModel::openDestination, onSetup,
        com.halo.app.di.ManualDependencyGraph.palettePreferences, com.halo.app.di.ManualDependencyGraph.kidsPreferences,
        nearby = { com.halo.app.ui.mini.HaloNearbyControls() }, initialAdvanced = initialAdvanced,
        developerStorage = com.halo.app.di.ManualDependencyGraph.storage)
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
    initialAdvanced: Boolean = false,
    developerStorage: com.halo.sdk.runtime.Storage? = null,
) {
    var advanced by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initialAdvanced) }
    // Labs are opt-in: everyday/advanced setup stays user-oriented until explicitly enabled.
    var developerTools by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(developerStorage?.getBoolean("halo_developer_tools_enabled", false) == true)
    }
    var searchOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var searchQuery by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var page by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val everydayGroups = listOf(
        SettingsMenuGroup("companion", "Your companion", listOf(
            SettingsMenuRow(HaloGlyph.MODEL, "Intelligence", "Choose how halo answers", { onOpen(SettingsDestination.INTELLIGENCE) }),
            SettingsMenuRow(HaloGlyph.LISTEN, "Voice", "Listening and speaking", { onOpen(SettingsDestination.VOICE) }),
            SettingsMenuRow(HaloGlyph.APPEARANCE, "Appearance", state.appearanceMode.name.lowercase().replaceFirstChar { it.uppercase() }, { page = "Appearance" }, listOf("theme", "color", "colour", "dark", "light", "mint")),
            SettingsMenuRow(HaloGlyph.LANGUAGE, "Language", language.nativeName, { page = "Language" }, listOf("interface", "default replies")),
        )),
        SettingsMenuGroup("privacy", "Privacy and data", listOf(
            SettingsMenuRow(HaloGlyph.SECURITY, "Context & privacy", "Choose what Halo can use", { onOpen(SettingsDestination.PRIVACY) }),
            SettingsMenuRow(HaloGlyph.SECURITY, "Permissions", "Device and data access", { onOpen(SettingsDestination.PERMISSIONS) }),
            SettingsMenuRow(HaloGlyph.MEMORY, "Memory & learning", "Review what Halo remembers", { onOpen(SettingsDestination.MEMORY) }),
            SettingsMenuRow(HaloGlyph.STORAGE, "Storage & backup", "Manage your saved work", { onOpen(SettingsDestination.STORAGE) }),
        )),
        SettingsMenuGroup("daily", "Daily life", listOfNotNull(
            if (nearby != null) SettingsMenuRow(HaloGlyph.HALO, "Halo nearby", "Mini halo, floating orb and home-screen widget", { page = "Halo nearby" }, listOf("Floating", "Mini Halo", "Widget")) else null,
            SettingsMenuRow(HaloGlyph.PHONE, "Android assistant", "Use Halo from your phone", { onOpen(SettingsDestination.ASSISTANT) }),
            if (kidsPreferences != null) SettingsMenuRow(HaloGlyph.READ_MODE, "Kids mode", "Learning and tools on one page.", { page = "Kids mode" }) else null,
        )),
        SettingsMenuGroup("actions", "Actions & connections", listOf(
            SettingsMenuRow(HaloGlyph.INTEGRATIONS, "Integrations", "Connect apps and services", { onOpen(SettingsDestination.INTEGRATIONS) }),
            SettingsMenuRow(HaloGlyph.SKILLS, "Skills", "Actions Halo can use", { onOpen(SettingsDestination.SKILLS) }),
            SettingsMenuRow(HaloGlyph.AUTOMATION, "Automation", "Routines and notification digests", { onOpen(SettingsDestination.AUTOMATION) }),
        )),
    )
    val advancedGroups = listOf(
        SettingsMenuGroup("runtime", "Runtime & diagnostics", listOf(
            SettingsMenuRow(HaloGlyph.RUNTIME_STATUS, "Runtime status", "Tasks, capabilities and technical details", { onOpen(SettingsDestination.RUNTIME) }, listOf("developer", "task details")),
            SettingsMenuRow(HaloGlyph.PHONE, "Assistant diagnostics", "Android integration, audio routes and operator tools", { onOpen(SettingsDestination.ASSISTANT_DIAGNOSTICS) }),
            SettingsMenuRow(HaloGlyph.DIAGNOSTICS, "System check", "Check what works and share a safe report", { onOpen(SettingsDestination.SYSTEM_CHECK) }),
        )),
        SettingsMenuGroup("labs", "Model labs", listOf(
            SettingsMenuRow(HaloGlyph.MODEL, "Model roles", "Route different kinds of work", { onOpen(SettingsDestination.MODEL_ROLES) }),
            SettingsMenuRow(HaloGlyph.ACTIVITY, "Workload diagnostics", "Inspect model routing and execution", { onOpen(SettingsDestination.WORKLOAD) }),
            SettingsMenuRow(HaloGlyph.SEE, "Vision Lab", "Verify and compare image models", { onOpen(SettingsDestination.VISION_LAB) }),
            SettingsMenuRow(HaloGlyph.SPEAK, "Local audio test", "Try an audio file with an installed model", { onOpen(SettingsDestination.AUDIO_LAB) }),
            SettingsMenuRow(HaloGlyph.LISTEN, "Speech Lab", "Compare speech recognition on one sample", { onOpen(SettingsDestination.SPEECH_LAB) }),
        )),
        SettingsMenuGroup("setup", "Setup tools", listOf(
            SettingsMenuRow(HaloGlyph.QUESTION, "Guided setup", "Resume or revisit setup without resetting your choices", { onSetup(com.halo.app.ui.onboarding.SetupStage.WELCOME) }),
            SettingsMenuRow(HaloGlyph.SUCCESS, "Setup readiness", "Check what works and share a safe report", { onSetup(com.halo.app.ui.onboarding.SetupStage.READINESS) }),
        )),
    )
    val safeAdvancedGroups = if (developerTools) advancedGroups else advancedGroups.mapNotNull { group ->
        when (group.key) {
            "setup" -> group
            "runtime" -> group.copy(rows = group.rows.filter { it.title == "System check" })
            else -> null
        }
    }
    val allGroups = everydayGroups + safeAdvancedGroups
    val localizedLabels = allGroups.flatMap { group -> listOf(group.title) + group.rows.flatMap { it.searchLabels } }
        .distinct().associateWith { uiText(it) }
    fun matches(labels: List<String>): Boolean = settingsMatches(searchQuery, *labels.toTypedArray()) ||
        settingsMatches(searchQuery, *labels.map { localizedLabels[it] ?: it }.toTypedArray())
    fun filtered(groups: List<SettingsMenuGroup>) = groups.mapNotNull { group ->
        val rows = if (matches(listOf(group.title))) group.rows else group.rows.filter { matches(it.searchLabels) }
        group.copy(rows = rows).takeIf { rows.isNotEmpty() }
    }
    val visibleGroups = filtered(if (advanced) safeAdvancedGroups else everydayGroups)
    val otherTabMatches = filtered(if (advanced) everydayGroups else safeAdvancedGroups).isNotEmpty()
    val pageDensity = androidx.compose.ui.platform.LocalDensity.current
    if (page != null) androidx.compose.ui.window.Dialog(
        onDismissRequest = { page = null },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // A native Dialog creates a fresh density. Preserve halo's accessible
        // app text scale, including explicit enlargement above the system scale.
        CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides pageDensity) {
        val dialogView = androidx.compose.ui.platform.LocalView.current
        val dialogWindow = (dialogView.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        val lightBars = MaterialTheme.colorScheme.background.luminance() > .5f
        SideEffect {
            dialogWindow?.let { window ->
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
                androidx.core.view.WindowCompat.getInsetsController(window, dialogView).apply {
                    isAppearanceLightStatusBars = lightBars
                    isAppearanceLightNavigationBars = lightBars
                }
            }
        }
        if (page == "Language") LanguagePickerPage(language, { onLanguage(it); page = null }, { page = null })
        else Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()
                .verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                HaloScreenHeader(uiText(page!!), navigation = { page = null })
                when (page) {
                    "Appearance" -> {
                        Text(uiText("Make halo feel like yours."), style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SettingsGroup("Theme", compactRows = true) {
                            Row(Modifier.fillMaxWidth().padding(12.dp).selectableGroup(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppearanceMode.entries.forEach { mode ->
                                    val selected = state.appearanceMode == mode
                                    Surface(Modifier.weight(1f).heightIn(min = 48.dp).selectable(
                                        selected = selected, role = Role.RadioButton, onClick = { onAppearance(mode) }),
                                        shape = com.halo.app.ui.design.HaloShapes.pill,
                                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) {
                                        Box(Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                                            Text(uiText(mode.name.lowercase().replaceFirstChar { it.uppercase() }),
                                                textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
                                        }
                                    }
                                }
                            }
                        }
                        if (palettePreferences != null) SettingsGroup("Colours", compactRows = true) { PaletteControls(palettePreferences) }
                    }
                    "Halo nearby" -> nearby?.invoke()
                    "Kids mode" -> if (kidsPreferences != null) {
                        val enabled by kidsPreferences.enabled.collectAsStateWithLifecycle()
                        val scope = rememberCoroutineScope()
                        var failure by remember { mutableStateOf(false) }
                        SettingsGroup("Kids mode") {
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(uiText("Learning and tools on one page."), style = MaterialTheme.typography.bodyLarge)
                                    Text(uiText("Tool approvals stay the same."), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                val label = uiText("Kids mode")
                                Switch(modifier = Modifier.semantics { contentDescription = label }, checked = enabled,
                                    onCheckedChange = { value -> scope.launch {
                                        try { kidsPreferences.setEnabled(value); failure = false }
                                        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                                        catch (_: Exception) { failure = true }
                                    } })
                            }
                            if (failure) Text(uiText("Could not save your change. Try again."), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
    }
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "header") {
            HaloScreenHeader(uiText("Settings"), trailing = {
                IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) searchQuery = "" }, modifier = Modifier.size(48.dp)) {
                    HaloIcon(HaloGlyph.SEARCH, androidx.compose.ui.res.stringResource(R.string.brand_settings_search), Modifier.size(24.dp))
                }
            })
        }
        item(key = "tabs") {
            Surface(shape = com.halo.app.ui.design.HaloShapes.pill, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.fillMaxWidth().padding(4.dp).selectableGroup()) {
                    listOf(false to "Everyday", true to "Advanced").forEach { (value, label) ->
                        val selected = advanced == value
                        Surface(Modifier.weight(1f).heightIn(min = 48.dp).selectable(
                            selected = selected, role = Role.Tab, onClick = { advanced = value; searchQuery = "" }),
                            shape = com.halo.app.ui.design.HaloShapes.pill,
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                            contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) {
                            Box(Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                                Text(uiText(label), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
        if (searchOpen) item(key = "search") {
            HaloTextField(kind = VoiceFieldKind.SEARCH, value = searchQuery, onValueChange = { searchQuery = it.take(100) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                shape = RoundedCornerShape(12.dp),
                label = { Text(androidx.compose.ui.res.stringResource(R.string.brand_settings_search)) },
                leadingIcon = { HaloIcon(HaloGlyph.SEARCH, null, Modifier.size(22.dp)) })
        }
        if (advanced) item(key = "advanced_intro") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(uiText("Additional settings, model setup and system check."),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(uiText("Developer tools"), Modifier.weight(1f))
                    Switch(checked = developerTools, onCheckedChange = { enabled ->
                        developerStorage?.putBoolean("halo_developer_tools_enabled", enabled)
                        developerTools = enabled
                    })
                }
                if (developerTools) Text(uiText("Model labs and diagnostics are enabled. No test starts automatically."),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        if (!advanced && searchQuery.isBlank()) item(key = "identity") { SettingsIdentity(state) }
        visibleGroups.forEach { group ->
            item(key = group.key) {
                SettingsGroup(group.title, compactRows = true) {
                    group.rows.forEachIndexed { index, row -> SettingsLink(row.glyph, row.title, row.subtitle, row.onClick, divider = index < group.rows.lastIndex) }
                }
            }
        }
        if (searchQuery.isNotBlank() && visibleGroups.isEmpty()) item(key = "no_results") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(androidx.compose.ui.res.stringResource(R.string.brand_settings_no_results),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (otherTabMatches) TextButton(onClick = { advanced = !advanced }) {
                    Text(uiText(if (advanced) "Search Everyday settings" else "Search Advanced settings"))
                }
            }
        }
        if (advanced && searchQuery.isBlank()) item(key = "version") {
            Text("halo · ${state.version}", Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
internal fun SettingsLink(glyph: HaloGlyph, title: String, subtitle: String, onClick: () -> Unit, divider: Boolean = false) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = androidx.compose.ui.graphics.Color.Transparent,
    ) {
        Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 68.dp)
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
            com.halo.app.ui.components.HaloReadAloud(uiText(title) + ". " + uiText(subtitle), compact = true)
            Icon(androidx.compose.material.icons.Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp))
        }
        if (divider) HorizontalDivider(Modifier.padding(start = 64.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
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
            HaloSignalFace(presentation = HaloSignalPresentation(label = "halo", reducedMotion = true), size = 64.dp,
                contentDescription = "halo")
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(uiText("Your halo"), style = MaterialTheme.typography.titleMedium)
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
    "Everyday", "Assistant diagnostics", "Runtime & diagnostics", "Tasks, capabilities and technical details", "Model labs", "Vision Lab", "Local audio test", "Speech Lab", "Model roles", "Workload diagnostics", "Setup tools", "Daily life",
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
