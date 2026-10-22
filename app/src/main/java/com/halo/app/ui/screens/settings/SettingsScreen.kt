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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
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
fun SettingsScreen(viewModel: SettingsViewModel, onSetup: (com.halo.app.ui.onboarding.SetupStage) -> Unit = {}, initialAdvanced: Boolean = false, onBack: (() -> Unit)? = null) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val languages = com.halo.app.di.ManualDependencyGraph.appLanguagePreferences
    val language by languages.language.collectAsStateWithLifecycle()
    val privacyMode by com.halo.app.di.ManualDependencyGraph.privacyModes.mode.collectAsStateWithLifecycle()
    SettingsContent(state.copy(privacyMode = privacyMode), language, { languages.select(it); com.halo.app.nativeactions.HaloNativeWidget.refreshAsync(com.halo.app.di.ManualDependencyGraph.applicationContext) }, viewModel::setAppearanceMode,
        viewModel::openDestination, onSetup,
        com.halo.app.di.ManualDependencyGraph.palettePreferences, com.halo.app.di.ManualDependencyGraph.kidsPreferences,
        nearby = { com.halo.app.ui.mini.HaloNearbyControls() }, initialAdvanced = initialAdvanced,
        developerStorage = com.halo.app.di.ManualDependencyGraph.storage, onBack = onBack,
        modalPrivacyHeader = { com.halo.app.ui.components.PrivacyModeControl(Modifier.statusBarsPadding()) })
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
    onBack: (() -> Unit)? = null,
    assistantPreferences: com.halo.app.ui.theme.AssistantPreferences? = null,
    modalPrivacyHeader: (@Composable () -> Unit)? = null,
) {
    var advanced by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initialAdvanced) }
    // Labs are opt-in: everyday/advanced setup stays user-oriented until explicitly enabled.
    var developerTools by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(developerStorage?.getBoolean("halo_developer_tools_enabled", false) == true)
    }
    var confirmDeveloperTools by remember { mutableStateOf(false) }
    var searchOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var searchQuery by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var page by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    androidx.activity.compose.BackHandler(enabled = advanced && !initialAdvanced && page == null) { advanced = false }
    val everydayGroups = listOf(
        SettingsMenuGroup("companion", "halo", listOf(
            SettingsMenuRow(HaloGlyph.MODEL, "How halo answers", "Choose how halo answers", { onOpen(SettingsDestination.INTELLIGENCE) }),
            SettingsMenuRow(HaloGlyph.LISTEN, "Voice & reading", "Listening and speaking", { onOpen(SettingsDestination.VOICE) }),
            SettingsMenuRow(HaloGlyph.APPEARANCE, "Appearance", state.appearanceMode.name.lowercase().replaceFirstChar { it.uppercase() }, { page = "Appearance" }, listOf("theme", "color", "colour", "dark", "light", "mint")),
            SettingsMenuRow(HaloGlyph.LANGUAGE, "Language", language.nativeName, { page = "Language" }, listOf("interface", "default replies")),
        )),
        SettingsMenuGroup("privacy", "Privacy", listOf(
            SettingsMenuRow(HaloGlyph.SECURITY, "What halo can use", "Choose what halo can use", { onOpen(SettingsDestination.PRIVACY) }, listOf("context", "sharing")),
            SettingsMenuRow(HaloGlyph.CLOUD, "What left this phone", "Review what was shared", { onOpen(SettingsDestination.SHARED_REQUESTS) }),
            SettingsMenuRow(HaloGlyph.SECURITY, "Permissions", "Device and data access", { onOpen(SettingsDestination.PERMISSIONS) }),
            SettingsMenuRow(HaloGlyph.MEMORY, "Memory", "Review what halo remembers", { onOpen(SettingsDestination.MEMORY) }),
            SettingsMenuRow(HaloGlyph.STORAGE, "Storage & backup", "Manage your saved work", { onOpen(SettingsDestination.STORAGE) }),
        )),
        SettingsMenuGroup("daily", "Everyday", listOfNotNull(
            if (nearby != null) SettingsMenuRow(HaloGlyph.HALO, "halo nearby", "Mini halo, floating orb and home-screen widget", { page = "halo nearby" }, listOf("Floating", "Mini Halo", "Widget")) else null,
            SettingsMenuRow(HaloGlyph.ACTIVITY, "Notifications", "Choose notifications and summaries", { onOpen(SettingsDestination.NOTIFICATIONS) }),
            SettingsMenuRow(HaloGlyph.PHONE, "Android assistant", "Use halo from your phone", { onOpen(SettingsDestination.ASSISTANT) }),
            if (kidsPreferences != null) SettingsMenuRow(HaloGlyph.READ_MODE, "Kids mode", "Learning and tools on one page.", { page = "Kids mode" }) else null,
        )),
        SettingsMenuGroup("actions", "Connections", listOf(
            SettingsMenuRow(HaloGlyph.INTEGRATIONS, "Apps & services", "Actions available in your apps", { onOpen(SettingsDestination.INTEGRATIONS) }),
            SettingsMenuRow(HaloGlyph.SKILLS, "Skills", "Actions halo can use", { onOpen(SettingsDestination.SKILLS) }),
            SettingsMenuRow(HaloGlyph.AUTOMATION, "Automation", "Routines and notification digests", { onOpen(SettingsDestination.AUTOMATION) }),
        )),
        SettingsMenuGroup("help", "Help", listOf(
            SettingsMenuRow(HaloGlyph.QUESTION, "Guided setup", "Resume or revisit setup without resetting your choices", { onSetup(com.halo.app.ui.onboarding.SetupStage.WELCOME) }),
            SettingsMenuRow(HaloGlyph.SUCCESS, "Check-up", "Check what works", { onOpen(SettingsDestination.SYSTEM_CHECK) }),
            SettingsMenuRow(HaloGlyph.HALO, "About halo", "A little about your companion", { page = "About halo" }),
        )),
    )
    val advancedGroups = listOf(
        SettingsMenuGroup("runtime", "Runtime & diagnostics", listOf(
            SettingsMenuRow(HaloGlyph.RUNTIME_STATUS, "Runtime status", "Tasks, capabilities and technical details", { onOpen(SettingsDestination.RUNTIME) }, listOf("developer", "task details")),
            SettingsMenuRow(HaloGlyph.PHONE, "Assistant diagnostics", "Android integration, audio routes and operator tools", { onOpen(SettingsDestination.ASSISTANT_DIAGNOSTICS) }),
            SettingsMenuRow(HaloGlyph.DIAGNOSTICS, "System check", "Inspect checks and measured results", { onOpen(SettingsDestination.DEVELOPER_CHECK) }),
            SettingsMenuRow(HaloGlyph.PHONE, "Phone sandbox", "Try phone actions", { onOpen(SettingsDestination.PHONE_SANDBOX) }),
            SettingsMenuRow(HaloGlyph.AUTOMATION, "Automation sandbox", "Preview routines", { onOpen(SettingsDestination.AUTOMATION_SANDBOX) }),
        )),
        SettingsMenuGroup("labs", "Labs & diagnostics", listOf(
            SettingsMenuRow(HaloGlyph.DIAGNOSTICS, "Local model lab", "Inspect model files and measured results", { onOpen(SettingsDestination.LOCAL_MODEL_LAB) }),
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
    val advancedEssentials = SettingsMenuGroup("advanced_essentials", "Advanced", listOf(
        SettingsMenuRow(HaloGlyph.MODEL, "Model roles", "Choose models for different kinds of work", { onOpen(SettingsDestination.MODEL_ROLES) }),
        SettingsMenuRow(HaloGlyph.LISTEN, "Voice packs", "Downloads and performance options", { onOpen(SettingsDestination.VOICE_PACKS) }),
        SettingsMenuRow(HaloGlyph.SKILLS, "Skill packages", "Import and manage your own skills", { onOpen(SettingsDestination.SKILL_PACKAGES) }),
        SettingsMenuRow(HaloGlyph.INTEGRATIONS, "Custom servers (MCP)", "Manage your own connections", { onOpen(SettingsDestination.CUSTOM_SERVERS) }),
        SettingsMenuRow(HaloGlyph.DIAGNOSTICS, "Technical report", "Safe to share: no keys, no messages", { onOpen(SettingsDestination.TECHNICAL_REPORT) }),
    ))
    val safeAdvancedGroups = listOf(advancedEssentials) + if (developerTools)
        advancedGroups.filter { it.key != "setup" }.map { group ->
            group.copy(rows = group.rows.filter { it.title != "Model roles" })
        } else emptyList()
    val allGroups = everydayGroups + safeAdvancedGroups
    val localizedLabels = allGroups.flatMap { group -> listOf(group.title) + group.rows.flatMap { it.searchLabels } }
        .distinct().associateWith { uiText(it) }
    fun matches(labels: List<String>): Boolean = settingsMatches(searchQuery, *labels.toTypedArray()) ||
        settingsMatches(searchQuery, *labels.map { localizedLabels[it] ?: it }.toTypedArray())
    fun filtered(groups: List<SettingsMenuGroup>) = groups.mapNotNull { group ->
        val rows = if (matches(listOf(group.title))) group.rows else group.rows.filter { matches(it.searchLabels) }
        group.copy(rows = rows).takeIf { rows.isNotEmpty() }
    }
    val visibleGroups = if (advanced) safeAdvancedGroups else filtered(everydayGroups)
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
        Column(Modifier.fillMaxSize()) {
        modalPrivacyHeader?.invoke()
        Box(Modifier.weight(1f).then(if (modalPrivacyHeader != null) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier)) {
        if (page == "Language") LanguagePickerPage(language, onLanguage, { page = null }, { page = null; onOpen(SettingsDestination.VOICE) })
        else Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()
                .verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                HaloScreenHeader(uiText(page!!), navigation = { page = null })
                when (page) {
                    "About halo" -> {
                        Text("halo", style = MaterialTheme.typography.headlineLarge)
                        Text(state.version, style = MaterialTheme.typography.bodyMedium)
                    }
                    "Appearance" -> {
                        if (palettePreferences != null) AppearanceControls(state.appearanceMode, onAppearance, palettePreferences,
                            assistantPreferences ?: com.halo.app.di.ManualDependencyGraph.assistantPreferences)
                    }
                    "halo nearby" -> nearby?.invoke()
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
    }
    }
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            HaloScreenHeader(uiText(if (advanced) "Advanced" else "Settings"),
                navigation = if (advanced) onBack ?: ({ advanced = false; searchQuery = "" }) else null, trailing = {
                if (!advanced) IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) searchQuery = "" }, modifier = Modifier.size(48.dp)) {
                    HaloIcon(HaloGlyph.SEARCH, androidx.compose.ui.res.stringResource(R.string.brand_settings_search), Modifier.size(24.dp))
                }
            })
        }
        if (searchOpen && !advanced) item(key = "search") {
            HaloTextField(kind = VoiceFieldKind.SEARCH, value = searchQuery, onValueChange = { searchQuery = it.take(100) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                shape = RoundedCornerShape(12.dp),
                label = { Text(androidx.compose.ui.res.stringResource(R.string.brand_settings_search)) },
                leadingIcon = { HaloIcon(HaloGlyph.SEARCH, null, Modifier.size(22.dp)) })
        }
        if (advanced) item(key = "advanced_intro") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(uiText("Technical options, kept in one place."),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            }
        }
        if (!advanced && searchQuery.isBlank()) item(key = "identity") { SettingsIdentity(state, onOpen) }
        visibleGroups.forEach { group ->
            item(key = group.key) {
                SettingsGroup(group.title, compactRows = true) {
                    group.rows.forEachIndexed { index, row -> SettingsLink(row.glyph, row.title, row.subtitle, row.onClick, divider = index < group.rows.lastIndex, laboratory = advanced && group.key in setOf("runtime", "labs")) }
                }
            }
            if (advanced && group.key == "advanced_essentials") item(key = "developer_tools") {
                val labInk = androidx.compose.ui.graphics.Color(if (MaterialTheme.colorScheme.background.luminance() < .5f) 0xFFC9B8FF else 0xFF6446D6)
                Surface(shape = com.halo.app.ui.design.HaloShapes.card,
                    border = BorderStroke(1.dp, labInk.copy(alpha = .35f)),
                    color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val developerToolsLabel = uiText("Developer tools")
                    Box(Modifier.size(38.dp).background(labInk.copy(alpha = .12f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        HaloIcon(HaloGlyph.DIAGNOSTICS, null, Modifier.size(22.dp), tint = labInk)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(developerToolsLabel, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), fontWeight = FontWeight.Bold)
                        Text(uiText("Labs, diagnostics and test sandboxes"), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = developerTools, modifier = Modifier.semantics { contentDescription = developerToolsLabel }, onCheckedChange = { enabled ->
                        if (enabled) confirmDeveloperTools = true
                        else {
                            developerStorage?.putBoolean("halo_developer_tools_enabled", false)
                            developerTools = false
                        }
                    })
                }
                if (developerTools) Text(uiText("Model labs and diagnostics are enabled. No test starts automatically."),
                    style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (!advanced && searchQuery.isNotBlank() && visibleGroups.isEmpty()) item(key = "no_results") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(androidx.compose.ui.res.stringResource(R.string.brand_settings_no_results),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { onOpen(SettingsDestination.ADVANCED) }) { Text(uiText("Open Advanced")) }
                Text(uiText("Technical options live in Advanced."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!advanced && searchQuery.isBlank()) item(key = "advanced") {
            Surface(shape = com.halo.app.ui.design.HaloShapes.card, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                SettingsLink(HaloGlyph.DIAGNOSTICS, "Advanced", "Model roles, custom servers and technical report",
                    { onOpen(SettingsDestination.ADVANCED) }, technical = true)
            }
        }
    }
    if (confirmDeveloperTools) AlertDialog(
        onDismissRequest = { confirmDeveloperTools = false },
        title = { Text(uiText("Turn on developer tools?")) },
        text = { Text(uiText("Show labs and diagnostics in Advanced. Nothing runs until you choose it.")) },
        confirmButton = { TextButton(onClick = {
            developerStorage?.putBoolean("halo_developer_tools_enabled", true)
            developerTools = true
            confirmDeveloperTools = false
        }) { Text(uiText("Turn on")) } },
        dismissButton = { TextButton(onClick = { confirmDeveloperTools = false }) { Text(uiText("Cancel")) } },
    )

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
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(uiText(title).uppercase(java.util.Locale.getDefault()), Modifier.padding(start = 2.dp, top = 4.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, letterSpacing = 1.54.sp),
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(shape = com.halo.app.ui.design.HaloShapes.card, color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = if (MaterialTheme.colorScheme.background.luminance() > .5f) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null) {
                Column(Modifier.fillMaxWidth(), content = content)
            }
        }
    } else {
        HaloSettingsSection(uiText(title)) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
internal fun SettingsLink(glyph: HaloGlyph, title: String, subtitle: String, onClick: () -> Unit, divider: Boolean = false, laboratory: Boolean = false, technical: Boolean = false) {
    val labInk = androidx.compose.ui.graphics.Color(if (MaterialTheme.colorScheme.background.luminance() < .5f) 0xFFC9B8FF else 0xFF6446D6)
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = androidx.compose.ui.graphics.Color.Transparent,
    ) {
        Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(38.dp).background(
                if (laboratory || technical) labInk.copy(alpha = .12f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center) {
                HaloIcon(glyph, contentDescription = null, modifier = Modifier.size(22.dp),
                    tint = if (laboratory || technical) labInk else MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(uiText(title), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
                    if (laboratory) Surface(shape = com.halo.app.ui.design.HaloShapes.pill,
                        color = androidx.compose.ui.graphics.Color(0xFFC9B8FF).copy(alpha = .18f)) {
                        Text(uiText("Lab"), Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall, color = labInk)
                    }
                }
                Text(uiText(subtitle), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsIdentity(state: SettingsUiState, onOpen: (SettingsDestination) -> Unit) {
    Surface(onClick = { onOpen(if (state.providerLocation == null) SettingsDestination.INTELLIGENCE else SettingsDestination.SYSTEM_CHECK) },
        modifier = Modifier.fillMaxWidth(),
        shape = com.halo.app.ui.design.HaloShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HaloSignalFace(presentation = HaloSignalPresentation(label = "halo", reducedMotion = true), size = 36.dp,
                contentDescription = "halo")
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(uiText(if (state.providerLocation == null) "Choose how halo answers" else "Your halo"), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp), fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (state.privacyMode != null) {
                    Text(uiText(if (state.privacyMode == com.halo.app.ai.PrivacyMode.PRIVATE) "Private mode" else "Cloud mode"),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("·", style = MaterialTheme.typography.bodySmall)
                } else state.providerLocation?.let { location ->
                    val label = when (location) {
                        com.halo.sdk.provider.AiExecutionLocation.ON_DEVICE -> R.string.brand_intelligence_phone
                        com.halo.sdk.provider.AiExecutionLocation.LOCAL_NETWORK -> R.string.brand_intelligence_network
                        com.halo.sdk.provider.AiExecutionLocation.REMOTE -> R.string.brand_intelligence_cloud
                    }
                    Text(androidx.compose.ui.res.stringResource(label),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("·", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${uiText("Voice")}: ${uiText(if (state.voiceStatus == "Ready") "Available" else state.voiceStatus)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
    "halo", "Privacy", "Everyday", "Connections", "Help", "How halo answers",
    "Voice & reading", "Listening and speaking", "Appearance", "Language", "theme",
    "color", "colour", "dark", "light", "mint", "interface", "default replies",
    "Kids mode", "halo nearby", "Floating", "Mini halo", "Widget", "Android assistant",
    "What halo can use", "Permissions", "Memory", "Storage & backup", "Skills",
    "Actions halo can use", "Automation", "Routines", "Check-up", "Guided setup", "About halo",
)

internal fun settingsHasMatches(query: String): Boolean =
    settingsMatches(query, *settingsSearchIndex.toTypedArray())

/** English catalogue label and native name are both searchable. */
internal fun languagePickerEnglishName(language: com.halo.app.language.AppLanguage): String = when (language) {
    com.halo.app.language.AppLanguage.ENGLISH -> "English"
    com.halo.app.language.AppLanguage.LATVIAN -> "Latvian"
    com.halo.app.language.AppLanguage.RUSSIAN -> "Russian"
    com.halo.app.language.AppLanguage.POLISH -> "Polish"
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
    onVoice: () -> Unit = {},
) {
    var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var previousLanguage by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
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
            previousLanguage?.let { previous ->
                Surface(shape = com.halo.app.ui.design.HaloShapes.card, color = scheme.surfaceContainerHigh) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(uiText("Language changed"), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = {
                            com.halo.app.language.AppLanguage.entries.firstOrNull { it.name == previous }?.let(onSelect)
                            previousLanguage = null
                        }) { Text(uiText("Undo")) }
                    }
                }
            }
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
                                            onClick = { if (option != selected) { previousLanguage = selected.name; onSelect(option) } })
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
                if (query.isBlank() || "polski polish".contains(query.trim(), ignoreCase = true)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Polski", style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant)
                            Text(uiText("Polish"), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        }
                            Text(uiText("Partly translated"), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                      }
                      Text(uiText("Polish interface is partly translated. Some screens may still appear in English."),
                          style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                }
                TextButton(onClick = onVoice, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Voice & reading")) }
                Text(uiText("Listening and answer languages are separate. Change them in Voice & reading."),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                Text(uiText("Dates and times follow this language too."),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant)
            }
        }
    }
}
