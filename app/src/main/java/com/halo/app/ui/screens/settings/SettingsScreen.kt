package com.halo.app.ui.screens.settings

import com.halo.app.language.uiText

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import com.halo.app.R
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.rotate
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
    var appearancePicker by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val paletteState = palettePreferences?.state?.collectAsStateWithLifecycle()
    val darkPalette = state.appearanceMode == AppearanceMode.DARK ||
        state.appearanceMode == AppearanceMode.SYSTEM && androidx.compose.foundation.isSystemInDarkTheme()
    val palette = if (darkPalette) paletteState?.value?.dark else paletteState?.value?.light
    val accentName = palette?.accent?.name?.lowercase()?.replaceFirstChar { it.uppercase() }
    val appearanceValue = listOfNotNull(uiText(state.appearanceMode.name.lowercase().replaceFirstChar { it.uppercase() }),
        accentName?.let { uiText(it) }).joinToString(" · ")
    val locationValue = uiText(when (state.providerLocation) {
        com.halo.sdk.provider.AiExecutionLocation.ON_DEVICE -> "On this phone"
        com.halo.sdk.provider.AiExecutionLocation.LOCAL_NETWORK -> "On your network"
        com.halo.sdk.provider.AiExecutionLocation.REMOTE -> "In the cloud"
        null -> "Not configured"
    })
    if (appearancePicker) {
        AppearanceSettingsScreen(state.appearanceMode, onAppearance, palettePreferences) { appearancePicker = false }
        return
    }
    if (languagePicker) {
        LanguageSettingsScreen(language, onLanguage, onBack = { languagePicker = false })
        return
    }
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        state = listState,
        contentPadding = PaddingValues(horizontal = com.halo.app.ui.design.HaloSpacing.screen, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
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
        if (searchQuery.isBlank()) item(key = "identity") { SettingsIdentity(state, accentName) }
        if (matches("Your companion", "Intelligence", "Choose how halo answers", "Voice", "Listening and speaking", "Language", "interface", "default replies")) item(key = "companion") {
            SettingsGroup("Your companion") {
                if (matches("Your companion", "Intelligence", "Choose how halo answers")) SettingsLink(HaloGlyph.MODEL, "Intelligence", "Choose how halo answers", { onOpen(SettingsDestination.INTELLIGENCE) }, value = locationValue)
                if (matches("Your companion", "Voice", "Listening and speaking")) SettingsLink(HaloGlyph.LISTEN, "Voice", "Listening and speaking", { onOpen(SettingsDestination.VOICE) })
                if (matches("Your companion", "Language", "interface", "default replies")) {
                    SettingsLink(HaloGlyph.CONVERSATION, "Language", "Interface and default replies",
                        { languagePicker = true }, value = language.nativeName, iconResource = R.drawable.halo_ic_language)
                }
            }
        }
        if (matches("Look and feel", "Appearance", "theme", "color", "colour", "dark", "light", "mint",
                "Kids mode", "learning", "tools", "halo nearby", "Floating", "Mini halo", "Widget")) item(key = "appearance") {
            SettingsGroup("Look and feel") {
                if (matches("Look and feel", "Appearance", "theme", "color", "colour", "dark", "light", "mint"))
                    SettingsLink(HaloGlyph.SETTINGS, "Appearance", "Make halo feel like yours.",
                        { appearancePicker = true }, value = appearanceValue)
                if (kidsPreferences != null && matches("Look and feel", "Kids mode", "learning", "tools"))
                    KidsModeSettingsRow(kidsPreferences)
                if (nearby != null && matches("Look and feel", "halo nearby", "Floating", "Mini halo", "Widget")) {
                    Text(uiText("halo nearby"), Modifier.padding(start = 16.dp, top = 12.dp),
                        style = MaterialTheme.typography.titleSmall)
                    nearby()
                }
            }
        }
        if (matches("Actions & connections", "Integrations", "Connect apps and services", "Skills", "Actions halo can use", "Automation", "Routines and notification digests")) item(key = "actions") {
            SettingsGroup("Actions & connections") {
                if (matches("Actions & connections", "Integrations", "Connect apps and services")) SettingsLink(HaloGlyph.INTEGRATIONS, "Integrations", "Connect apps and services", { onOpen(SettingsDestination.INTEGRATIONS) })
                if (matches("Actions & connections", "Skills", "Actions halo can use")) SettingsLink(HaloGlyph.SKILLS, "Skills", "Actions halo can use", { onOpen(SettingsDestination.SKILLS) })
                if (matches("Actions & connections", "Automation", "Routines and notification digests")) SettingsLink(HaloGlyph.AUTOMATION, "Automation", "Routines and notification digests", { onOpen(SettingsDestination.AUTOMATION) })
            }
        }
        if (matches("Privacy and data", "Privacy", "Context", "Choose what halo can use", "Permissions", "Device and data access", "Memory & learning", "Review what halo remembers", "Storage & backup", "Manage your saved work")) item(key = "access") {
            SettingsGroup("Privacy and data") {
                if (matches("Privacy and data", "Privacy", "Context", "Choose what halo can use")) SettingsLink(HaloGlyph.SECURITY, "Context & privacy", "Choose what halo can use", { onOpen(SettingsDestination.PRIVACY) })
                if (matches("Privacy and data", "Permissions", "Device and data access")) SettingsLink(HaloGlyph.SECURITY, "Permissions", "Device and data access", { onOpen(SettingsDestination.PERMISSIONS) })
                if (matches("Privacy and data", "Memory & learning", "Review what halo remembers")) SettingsLink(HaloGlyph.MEMORY, "Memory & learning", "Review what halo remembers", { onOpen(SettingsDestination.MEMORY) })
                if (matches("Privacy and data", "Storage & backup", "Manage your saved work")) SettingsLink(HaloGlyph.STORAGE, "Storage & backup", "Manage your saved work", { onOpen(SettingsDestination.STORAGE) })
            }
        }
        if (matches("Setup and support", "Guided setup", "Resume or revisit setup without resetting your choices", "Check setup", "Setup readiness", "System check", "Check what works and share a safe report", "Android assistant", "Use halo from your phone")) item(key = "setup") {
            SettingsGroup("Setup and support") {
                if (matches("Setup and support", "Guided setup", "Resume or revisit setup without resetting your choices")) SettingsLink(HaloGlyph.QUESTION, "Guided setup", "Resume or revisit setup without resetting your choices", { onSetup(com.halo.app.ui.onboarding.SetupStage.WELCOME) })
                if (matches("Setup and support", "Check setup", "Setup readiness", "Check what works and share a safe report")) SettingsLink(HaloGlyph.DIAGNOSTICS, "Setup readiness", "Check what works and share a safe report", { onSetup(com.halo.app.ui.onboarding.SetupStage.READINESS) })
                if (matches("Setup and support", "System check", "Check what works and share a safe report"))
                    SettingsLink(HaloGlyph.DIAGNOSTICS, "System check", "Check what works and share a safe report",
                        { onOpen(SettingsDestination.SYSTEM_CHECK) })
                if (matches("Setup and support", "Android assistant", "Use halo from your phone")) SettingsLink(HaloGlyph.PHONE, "Android assistant", "Use halo from your phone", { onOpen(SettingsDestination.ASSISTANT) })
            }
        }
        if (matches("Advanced", "Runtime status", "developer", "task details", "System check")) item(key = "advanced") {
            SettingsGroup("Advanced") {
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

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(uiText(title).uppercase(androidx.compose.ui.platform.LocalConfiguration.current.locales[0]),
            Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = .8.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(Modifier.fillMaxWidth(), shape = com.halo.app.ui.design.HaloShapes.card,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
private fun SettingsLink(glyph: HaloGlyph, title: String, subtitle: String, onClick: () -> Unit,
    value: String? = null, iconResource: Int? = null) {
    val inlineValue = androidx.compose.ui.platform.LocalDensity.current.fontScale <= 1.3f
    Surface(onClick = onClick, color = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = com.halo.app.ui.design.HaloShapes.control,
                color = MaterialTheme.colorScheme.secondaryContainer) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    if (iconResource != null) Icon(painterResource(iconResource), null,
                        Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                    else HaloIcon(glyph, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(uiText(title), style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                if (value == null || !inlineValue) Text(value ?: uiText(subtitle),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (value != null && inlineValue) Text(value, Modifier.widthIn(max = 112.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End)
            Icon(painterResource(R.drawable.halo_ic_chevron_down), null, Modifier.size(18.dp).rotate(-90f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Brand presence is decorative; these are configuration summaries, not health claims. */
@Composable
private fun SettingsIdentity(state: SettingsUiState, accentName: String? = null) {
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
            HaloSignalFace(presentation = HaloSignalPresentation(label = "halo", reducedMotion = true), size = 64.dp,
                contentDescription = "halo")
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                androidx.compose.foundation.Image(
                    painterResource(if (MaterialTheme.colorScheme.background.luminance() < .5f)
                        R.drawable.halo_wordmark_dark else R.drawable.halo_wordmark_light),
                    contentDescription = "halo", modifier = Modifier.width(64.dp).height(24.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                val locationLabel = state.providerLocation?.let { location ->
                    val label = when (location) {
                        com.halo.sdk.provider.AiExecutionLocation.ON_DEVICE -> R.string.brand_intelligence_phone
                        com.halo.sdk.provider.AiExecutionLocation.LOCAL_NETWORK -> R.string.brand_intelligence_network
                        com.halo.sdk.provider.AiExecutionLocation.REMOTE -> R.string.brand_intelligence_cloud
                    }
                    androidx.compose.ui.res.stringResource(label)
                }
                val speechLabel = when (state.voiceStatus) {
                    "Ready" -> "Speech model installed"
                    "Checking…" -> "Checking speech model…"
                    else -> "Speech model not installed"
                }
                Text(listOfNotNull(locationLabel, uiText(speechLabel), accentName?.let { uiText(it) }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    "Look and feel",
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
    "halo nearby",
    "Floating",
    "Mini halo",
    "Widget",
    "Actions & connections",
    "Integrations",
    "Connect apps and services",
    "Skills",
    "Actions halo can use",
    "Automation",
    "Routines and notification digests",
    "Privacy and data",
    "Privacy",
    "Context",
    "Choose what halo can use",
    "Permissions",
    "Device and data access",
    "Memory & learning",
    "Review what halo remembers",
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
    "Use halo from your phone",
    "Advanced",
    "Runtime status",
    "developer",
    "task details",
    "System check",
)

internal fun settingsHasMatches(query: String): Boolean =
    settingsMatches(query, *settingsSearchIndex.toTypedArray())


@Composable
private fun KidsModeSettingsRow(preferences: com.halo.app.ui.screens.kids.KidsPreferences) {
    val enabled by preferences.enabled.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var failure by remember { mutableStateOf(false) }
    val save: (Boolean) -> Unit = { value -> scope.launch {
        try { preferences.setEnabled(value); failure = false }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { failure = true }
    } }
    val leaveKids = com.halo.app.ui.screens.kids.rememberGrownUpAccess { save(false) }
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = com.halo.app.ui.design.HaloShapes.control, color = MaterialTheme.colorScheme.secondaryContainer) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                HaloIcon(HaloGlyph.SECURITY, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(uiText("Kids mode"), style = MaterialTheme.typography.titleSmall)
            Text(uiText("Simpler screens and kinder words for children."), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val label = uiText("Kids mode")
        Switch(checked = enabled, onCheckedChange = { value -> if (enabled && !value) leaveKids() else save(value) },
            modifier = Modifier.semantics { contentDescription = label })
    }
    if (failure) Text(uiText("Could not save your change. Try again."),
        color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
}
