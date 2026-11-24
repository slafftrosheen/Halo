package com.halo.app.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.halo.app.R
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.language.uiText
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import com.halo.app.ui.theme.*
import com.halo.app.ui.theme.v13.Halo13Animation
import com.halo.app.ui.theme.v13.Halo13Motion
import com.halo.app.wallpaper.wallpaperLookSeed
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun AppearanceControls(mode: AppearanceMode, onApply: suspend (AppearanceDraft) -> Unit,
    preferences: PalettePreferences, assistant: AssistantPreferences = ManualDependencyGraph.assistantPreferences,
    ownerUncertain: Boolean = false) {
    val lockContext = LocalContext.current
    val layoutLock = remember(lockContext.applicationContext) { HaloLayoutLock.get(lockContext) }
    val layoutLocked by layoutLock.state.collectAsStateWithLifecycle()
    if (layoutLocked) {
        Text(stringResource(R.string.layout_kept), style = MaterialTheme.typography.bodyMedium)
        HaloLayoutLock.Controls(layoutLock, locked = true, enabled = true)
        return
    }
    val palettes by preferences.state.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf(AppearanceDraft(mode, palettes)) }
    LaunchedEffect(mode, palettes) { draft = draft.refresh(mode, palettes) }
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val dark = when (draft.mode) {
        AppearanceMode.LIGHT -> false
        AppearanceMode.DARK -> true
        AppearanceMode.SYSTEM -> systemDark
    }
    val palette = draft.palette(dark)
    val assistantState by assistant.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val visual = remember(context) { VisualPreferences.get(context) }
    val savedVisual by visual.state.collectAsStateWithLifecycle()
    var lookDraft by remember { mutableStateOf<HaloVisualOptions?>(null) }
    var guidedStart by remember { mutableStateOf<Pair<AppearanceDraft, HaloVisualOptions?>?>(null) }
    val options = lookDraft ?: savedVisual
    var careReading by remember { mutableStateOf<Boolean?>(null) }
    var presetFontScale by remember { mutableStateOf<Float?>(null) }
    var tintPreview by remember { mutableIntStateOf(0) }
    var tintUnavailable by remember { mutableStateOf(false) }
    val changed = draft.changed || lookDraft?.let { it != savedVisual } == true || careReading != null ||
        presetFontScale?.let { it != assistantState.fontScale } == true
    val previewOwner = remember { Any() }
    SideEffect {
        if (changed) HaloLookPreview.show(HaloLookPreview.Draft(previewOwner, dark, palette, options))
        else HaloLookPreview.clear(previewOwner)
    }
    DisposableEffect(previewOwner) { onDispose { HaloLookPreview.clear(previewOwner) } }
    val scope = rememberCoroutineScope()
    var failure by remember { mutableStateOf(false) }
    var rollbackUncertain by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(ownerUncertain) {
        rollbackUncertain = ownerUncertain
        failure = ownerUncertain
    }
    suspend fun save(appearanceApply: Boolean = false, block: suspend () -> Unit) {
        saving = true
        try {
            block()
            if (appearanceApply || !rollbackUncertain) { failure = false; rollbackUncertain = false }
        }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (error: Exception) {
            failure = true
            rollbackUncertain = rollbackUncertain || (error as? AppearanceApplyException)?.rollbackUncertain == true
        }
        finally { saving = false }
    }
    suspend fun applyLook() {
        val requested = draft
        val requestedVisual = options
        save(appearanceApply = true) {
            layoutLock.requireUnlocked()
            if (requested.changed || rollbackUncertain) onApply(requested)
            if (requestedVisual != savedVisual) visual.update { latest ->
                latest.copy(iconStyle = requestedVisual.iconStyle, appIcon = requestedVisual.appIcon,
                    motionLevel = requestedVisual.motionLevel, lessMotion = requestedVisual.lessMotion,
                    haptics = requestedVisual.haptics, edgeLight = requestedVisual.edgeLight,
                    buttonLabels = requestedVisual.buttonLabels, viewMode = requestedVisual.viewMode,
                    guideLight = requestedVisual.guideLight, guidanceAmount = requestedVisual.guidanceAmount,
                    higherContrast = requestedVisual.higherContrast)
            }
            presetFontScale?.let { assistant.update(it, null) }
            HaloAppIconSwitcher.apply(context, requestedVisual.appIcon, dark, requested.palette(dark).accent)
            careReading?.let { read ->
                val reader = ManualDependencyGraph.readAloud
                if (reader.autoReadAnswers.value != read) {
                    reader.setAutoReadAnswers(read)
                    check(reader.autoReadAnswers.value == read) { "Could not save automatic reading" }
                }
            }
            careReading = null
            draft = AppearanceDraft(requested.mode, requested.palettes)
            lookDraft = null
            presetFontScale = null
            tintPreview = 0
        }
    }
    fun resetDraft() {
        draft = AppearanceDraft(mode, palettes)
        lookDraft = null
        careReading = null
        presetFontScale = null
        tintPreview = 0
        tintUnavailable = false
        if (!rollbackUncertain) failure = false
    }
    guidedStart?.let { (originalDraft, originalVisual) ->
        AppearanceGuidedSetup(
            dark = dark,
            palette = palette,
            onLook = { preset -> draft = draft.editPalette(preset.dark, preset.palette)
                .copy(mode = if (preset.dark) AppearanceMode.DARK else AppearanceMode.LIGHT) },
            onView = { lookDraft = (lookDraft ?: savedVisual).withViewMode(it) },
            onMotion = { lookDraft = (lookDraft ?: savedVisual).copy(motionLevel = it, lessMotion = it == HaloLookMotion.STILL) },
            onCancel = {
                draft = originalDraft
                lookDraft = originalVisual
                guidedStart = null
            },
            onDone = { guidedStart = null },
        )
    }
    val applyLabel = stringResource(R.string.look_apply)
    val lookOwner = com.halo.app.ui.presence.LocalShellPageActions.current
    // The Settings page owns the preferences. The dock uses the same apply and undo callbacks.
    if (lookOwner != null) com.halo.app.ui.presence.BindShellPageActions(
        com.halo.app.ui.presence.ShellPageOwner("settings", activeToolId = "settings.appearance", statusLabel = if (changed) uiText("Previewing changes") else null),
        listOf(
            com.halo.app.ui.presence.ShellPageAction("page.look.apply", applyLabel, HaloGlyph.SUCCESS,
                !saving && (changed || rollbackUncertain)) { scope.launch { applyLook() } },
            com.halo.app.ui.presence.ShellPageAction("page.look.undo", uiText("Undo"), HaloGlyph.PREVIOUS,
                !saving && changed) { resetDraft() },
        ))
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(stringResource(R.string.look_subtitle), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { guidedStart = draft to lookDraft }, enabled = !saving,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            HaloIcon(HaloGlyph.QUESTION, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.alive_guide_appearance))
        }
        OutlinedButton(onClick = {
            lookDraft = options.withViewMode(HaloViewMode.EASY)
            careReading = true
        }, enabled = !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.layout_setup_other))
        }
        careReading?.let { checked ->
            com.halo.app.ui.workspace.HaloWorkspaceToggleRow(
                title = stringResource(R.string.layout_read_replies), checked = checked,
                onCheckedChange = { value -> if (!saving) careReading = value })
        }
        HaloLayoutLock.Controls(layoutLock, locked = false, enabled = !saving && !changed)
        AppearanceLabel(stringResource(R.string.look_presets_label))
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HaloLookPreset.releaseLooks.forEach { preset ->
                val scheme = haloColorScheme(preset.dark, preset.palette)
                val name = stringResource(preset.label)
                val selected = dark == preset.dark && palette == preset.palette
                Surface(onClick = {
                    draft = draft.editPalette(preset.dark, preset.palette).copy(
                        mode = if (preset.dark) AppearanceMode.DARK else AppearanceMode.LIGHT)
                    lookDraft = options.copy(motionLevel = preset.motion, lessMotion = preset.motion == HaloLookMotion.STILL,
                        higherContrast = preset.clear || savedVisual.higherContrast)
                    presetFontScale = if (preset.clear) maxOf(1.3f, assistantState.fontScale) else null
                    tintPreview = 0
                    tintUnavailable = false
                }, enabled = !saving, shape = MaterialTheme.shapes.medium, color = scheme.surface,
                    contentColor = scheme.onSurface,
                    border = BorderStroke(1.dp, if (selected) scheme.primary else scheme.outlineVariant),
                    modifier = Modifier.widthIn(min = 100.dp).heightIn(min = 64.dp).semantics { this.selected = selected }) {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        HaloIcon(HaloGlyph.HALO, null, Modifier.size(24.dp), tint = scheme.primary)
                        Text(name, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        OutlinedButton(onClick = {
            scope.launch {
                save {
                    val seed = wallpaperLookSeed(context)
                    tintUnavailable = seed == null
                    if (seed != null) {
                        val match = HaloPalette(wallpaperSeed = seed)
                        draft = draft.editPalette(false, match).editPalette(true, match)
                        lookDraft = options.copy(motionLevel = HaloLookMotion.GENTLE, lessMotion = false)
                        tintPreview++
                    }
                }
            }
        }, enabled = !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.f13_look_match))
        }
        if (tintUnavailable) Text(stringResource(R.string.f13_look_no_wallpaper), style = MaterialTheme.typography.bodySmall)
        if (tintPreview > 0 && palette.wallpaperSeed != null) key(tintPreview) {
            Halo13Animation(Halo13Motion.TINT_FROM_WALLPAPER, Modifier.fillMaxWidth().height(80.dp))
        }
        AppearanceLabel("Mode")
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val fontScale = LocalDensity.current.fontScale
            if (fontScale >= 1.3f || maxWidth < 260.dp) {
                // Wide independent options avoid clipping translated labels inside a shared capsule.
                Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(AppearanceMode.LIGHT to "Light", AppearanceMode.DARK to "Dark", AppearanceMode.SYSTEM to "Same as phone").forEach { (value, title) ->
                        val selected = value == draft.mode
                        Surface(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected,
                            role = Role.RadioButton, enabled = !saving, onClick = { draft = draft.copy(mode = value) }),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                            border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                            color = if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Text(uiText(title), Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                }
            } else {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = com.halo.app.ui.design.HaloShapes.pill) {
                    Row(Modifier.fillMaxWidth().padding(3.dp).selectableGroup()) {
                        listOf(AppearanceMode.LIGHT to "Light", AppearanceMode.DARK to "Dark", AppearanceMode.SYSTEM to "Same as phone").forEach { (value, title) ->
                            val selected = value == draft.mode
                            Surface(modifier = Modifier.weight(1f).heightIn(min = 48.dp).selectable(selected,
                                role = Role.RadioButton, enabled = !saving, onClick = { draft = draft.copy(mode = value) }),
                                color = if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
                                shape = com.halo.app.ui.design.HaloShapes.pill) {
                                Box(Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                                    Text(uiText(title), style = MaterialTheme.typography.labelLarge,
                                        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }
        AppearanceLabel("Accent")
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HaloAccent.entries.forEach { accent ->
                val selected = accent == palette.accent
                val fill = brandAccent(dark, accent)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 48.dp)
                    .selectable(selected, enabled = !saving, role = Role.RadioButton,
                        onClick = { draft = draft.editPalette(dark, palette.copy(accent = accent, wallpaperSeed = null)) })) {
                    Surface(Modifier.size(52.dp), shape = CircleShape, color = Color.Transparent,
                        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null) {
                        Box(Modifier.padding(4.dp).background(fill.fill, CircleShape), contentAlignment = Alignment.Center) {
                            if (selected) HaloIcon(HaloGlyph.SUCCESS, null, Modifier.size(24.dp), tint = fill.onFill)
                        }
                    }
                    Text(appearanceAccentLabel(accent),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        AppearanceLabel("Tone")
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HaloTone.entries.forEach { tone ->
                FilterChip(palette.tone == tone, { draft = draft.editPalette(dark, palette.copy(tone = tone)) }, enabled = !saving,
                    label = { Text(appearanceToneLabel(tone)) },
                    modifier = Modifier.heightIn(min = 48.dp), shape = com.halo.app.ui.design.HaloShapes.pill)
            }
        }
        Text(stringResource(R.string.interface4_background), style = MaterialTheme.typography.labelMedium)
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HaloBackgroundStyle.entries.forEach { background ->
                FilterChip(palette.background == background, { draft = draft.editPalette(dark, palette.copy(background = background)) }, enabled = !saving,
                    label = { Text(stringResource(when (background) {
                        HaloBackgroundStyle.STILL -> R.string.interface4_background_still
                        HaloBackgroundStyle.HALO -> R.string.interface4_background_halo
                        HaloBackgroundStyle.MIST -> R.string.interface4_background_mist
                    })) }, modifier = Modifier.heightIn(min = 48.dp))
            }
        }
        AppearanceLabel("Preview")
        AppearancePreview(dark, palette)
        com.halo.app.wallpaper.WallpaperControls()
        Text(stringResource(R.string.alive_view_title), style = MaterialTheme.typography.labelLarge)
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(HaloViewMode.EASY to R.string.alive_view_easy, HaloViewMode.STANDARD to R.string.alive_view_standard,
                HaloViewMode.COMPACT to R.string.alive_view_compact).forEach { (viewMode, title) ->
                FilterChip(options.viewMode == viewMode, { lookDraft = options.withViewMode(viewMode) },
                    enabled = !saving, label = { Text(stringResource(title)) }, modifier = Modifier.heightIn(min = 48.dp))
            }
        }
        Text(stringResource(R.string.interface4_appearance_preview), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { scope.launch { applyLook() } }, enabled = !saving && (changed || rollbackUncertain),
                modifier = Modifier.heightIn(min = 48.dp)) { Text(applyLabel) }
            TextButton(onClick = ::resetDraft, enabled = !saving && changed,
                modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Undo")) }
            TextButton(onClick = { draft = draft.editPalette(dark, HaloPalette()) }, enabled = !saving,
                modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.interface4_appearance_defaults)) }
        }
        Text(stringResource(R.string.zone5_theme_colors),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.look_icons), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.look_app_icon), style = MaterialTheme.typography.labelLarge)
        LookChoices(options.appIcon, listOf(
            HaloAppIconMode.MATCH_LOOK to R.string.look_app_icon_match, HaloAppIconMode.CLASSIC to R.string.look_app_icon_classic,
            HaloAppIconMode.LIGHT to R.string.look_app_icon_light, HaloAppIconMode.SYSTEM to R.string.look_app_icon_system),
            !saving) { lookDraft = options.copy(appIcon = it) }
        Text(stringResource(R.string.look_app_icon_note), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.look_icon_style), style = MaterialTheme.typography.labelLarge)
        IconStyleChoices(options, dark, palette, enabled = !saving) {
            lookDraft = options.copy(iconStyle = it)
        }
        Text(stringResource(R.string.look_motion), style = MaterialTheme.typography.titleMedium)
        AppearanceMotionChoices(options, dark, palette, enabled = !saving) {
            lookDraft = options.copy(motionLevel = it, lessMotion = it == HaloLookMotion.STILL)
        }
        com.halo.app.ui.workspace.HaloWorkspaceToggleRow(stringResource(R.string.look_haptics),
            options.haptics, { lookDraft = options.copy(haptics = it) })
        com.halo.app.ui.workspace.HaloWorkspaceToggleRow(stringResource(R.string.look_edge_light),
            options.edgeLight, { lookDraft = options.copy(edgeLight = it) })
        Text(stringResource(R.string.look_labels), style = MaterialTheme.typography.labelLarge)
        LookChoices(options.buttonLabels, listOf(HaloButtonLabels.ALWAYS to R.string.look_labels_always,
            HaloButtonLabels.SELECTED_ONLY to R.string.look_labels_selected), !saving) { lookDraft = options.copy(buttonLabels = it) }
        AppearanceLabel("Text size")
        Text(uiText("Follows Android. You can make it bigger here."), style = MaterialTheme.typography.bodyMedium)
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0f to "Same as phone", 1.25f to "Large", 1.5f to "Larger", 2f to "Largest").forEach { (scale, title) ->
                FilterChip(assistantState.fontScale == scale, { scope.launch { save { assistant.update(scale, null) } } },
                    label = { Text(uiText(title)) }, enabled = !saving, modifier = Modifier.heightIn(min = 48.dp))
            }
        }
        AppearanceLabel("Easier to see")
        Surface(shape = com.halo.app.ui.design.HaloShapes.card, color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column {
                com.halo.app.ui.workspace.HaloWorkspaceToggleRow(uiText("Higher contrast"),
                    options.higherContrast, { value -> scope.launch { save { visual.update { it.copy(higherContrast = value) } } } })
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                com.halo.app.ui.workspace.HaloWorkspaceToggleRow(uiText("Guide light"),
                    options.guideLight, { value -> scope.launch { save { visual.update { it.copy(guideLight = value) } } } })
            }
        }
        Text(stringResource(R.string.zone5_help_amount), style = MaterialTheme.typography.titleSmall)
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            com.halo.app.ui.theme.HaloGuidanceAmount.entries.forEach { amount ->
                FilterChip(options.guidanceAmount == amount,
                    { scope.launch { save { visual.update { it.copy(guidanceAmount = amount) } } } },
                    label = { Text(stringResource(when (amount) {
                        com.halo.app.ui.theme.HaloGuidanceAmount.OFF -> R.string.zone5_help_off
                        com.halo.app.ui.theme.HaloGuidanceAmount.CUES -> R.string.zone5_help_cues
                        com.halo.app.ui.theme.HaloGuidanceAmount.EXPLANATIONS -> R.string.zone5_help_explanations
                    })) }, enabled = !saving, modifier = Modifier.heightIn(min = 48.dp))
            }
        }
        TextButton(onClick = { com.halo.app.ui.zone6.Zone6GuideReplay.replay() },
            modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Replay the dock guide")) }
        if (failure) Text(if (rollbackUncertain) stringResource(R.string.interface4_appearance_rollback_unknown)
            else uiText("Could not save your change. Try again."), color = MaterialTheme.colorScheme.error)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconStyleChoices(options: HaloVisualOptions, dark: Boolean, palette: HaloPalette,
                             enabled: Boolean, onSelect: (HaloIconStyle) -> Unit) {
    HaloTheme(darkTheme = dark, palette = palette) {
        val colors = MaterialTheme.colorScheme
        FlowRow(Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(HaloIconStyle.LINE to R.string.look_icon_line,
                HaloIconStyle.SPOT to R.string.look_icon_spot, HaloIconStyle.TILE to R.string.look_icon_tile)
                .forEach { (style, label) ->
                    val selected = options.iconStyle == style
                    Surface(onClick = { onSelect(style) }, enabled = enabled, shape = MaterialTheme.shapes.medium,
                        color = colors.surface, border = BorderStroke(1.dp, if (selected) colors.primary else colors.outlineVariant),
                        modifier = Modifier.widthIn(min = 92.dp).heightIn(min = 64.dp)
                            .semantics { this.selected = selected; role = Role.RadioButton }) {
                        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            CompositionLocalProvider(LocalHaloVisualOverride provides options.copy(iconStyle = style)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    com.halo.app.ui.zone5.Zone5Icon("nav_workspace", HaloGlyph.NOTE, Modifier.size(18.dp), tint = colors.primary)
                                    com.halo.app.ui.zone5.Zone5Icon("action_notifications", HaloGlyph.NOTIFICATIONS, Modifier.size(18.dp), tint = colors.primary)
                                    com.halo.app.ui.zone5.Zone5Icon("tool_study", HaloGlyph.QUESTION, Modifier.size(18.dp), tint = colors.primary)
                                }
                            }
                            Text(stringResource(label), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
        }
    }
}

@Composable
private fun AppearanceMotionChoices(options: HaloVisualOptions, dark: Boolean, palette: HaloPalette,
                                    enabled: Boolean, onSelect: (HaloLookMotion) -> Unit) {
    val platformReduced by rememberAndroidReducedMotion()
    val constrained = com.halo.app.ui.fx.rememberHaloMotionConstrained()
    val canPreview = enabled && !platformReduced && !constrained && !options.higherContrast
    var sequence by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    fun replay() { playing = true; sequence++ }
    LaunchedEffect(sequence) {
        if (playing) {
            kotlinx.coroutines.delay(2_700)
            playing = false
        }
    }
    LookChoices(options.motionLevel, listOf(HaloLookMotion.FULL to R.string.look_motion_full,
        HaloLookMotion.GENTLE to R.string.look_motion_gentle, HaloLookMotion.STILL to R.string.look_motion_still), enabled) {
        onSelect(it)
        if (canPreview && it != HaloLookMotion.STILL) replay() else playing = false
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CompositionLocalProvider(LocalHaloVisualOverride provides options) {
            HaloTheme(darkTheme = dark, palette = palette) {
                key(sequence) {
                    val active = playing && canPreview && options.motionLevel != HaloLookMotion.STILL
                    com.halo.app.ui.presence.HaloSignalFace(com.halo.app.ui.presence.HaloSignalPresentation(
                        state = if (active) com.halo.app.ui.presence.HaloSignalState.THINKING
                            else com.halo.app.ui.presence.HaloSignalState.IDLE,
                        label = if (active) "Working" else "Talk", reducedMotion = !active),
                        Modifier.semantics { }, size = 56.dp, zone5 = true, dock = true)
                }
            }
        }
        TextButton(onClick = ::replay, enabled = canPreview && options.motionLevel != HaloLookMotion.STILL,
            modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.look_motion_play))
        }
    }
    val motionNotice = when {
        platformReduced -> R.string.look_motion_android_off
        constrained -> R.string.look_motion_device_limited
        options.higherContrast -> R.string.look_motion_contrast
        else -> null
    }
    motionNotice?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> LookChoices(selected: T, choices: List<Pair<T, Int>>, enabled: Boolean, onSelect: (T) -> Unit) {
    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { (value, title) ->
            FilterChip(selected == value, { onSelect(value) }, enabled = enabled,
                label = { Text(stringResource(title)) }, modifier = Modifier.heightIn(min = 48.dp))
        }
    }
}

@Composable private fun AppearanceLabel(text: String) {
    Text(uiText(text).uppercase(), style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = androidx.compose.ui.unit.TextUnit(1.3f, androidx.compose.ui.unit.TextUnitType.Sp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun AppearancePreview(dark: Boolean, palette: HaloPalette) {
    HaloTheme(darkTheme = dark, palette = palette) {
        val scheme = MaterialTheme.colorScheme
        Surface(shape = com.halo.app.ui.design.HaloShapes.card, color = scheme.background,
            border = BorderStroke(1.dp, scheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
            Box {
            HaloAmbientBackdrop(Modifier.matchParentSize(), palette, animate = false)
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(
                        if (dark) com.halo.app.R.drawable.halo_wordmark_dark else com.halo.app.R.drawable.halo_wordmark_light),
                        contentDescription = null, modifier = Modifier.width(48.dp).height(26.dp),
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(scheme.onSurface))
                    Spacer(Modifier.weight(1f))
                    HaloIcon(HaloGlyph.HALO, null, Modifier.size(24.dp), tint = scheme.primary)
                }
                Surface(Modifier.align(Alignment.End), shape = com.halo.app.ui.design.HaloShapes.card,
                    color = scheme.primaryContainer.copy(alpha = .2f)) {
                    Text(uiText("Plan dinner for four"), Modifier.padding(10.dp), style = MaterialTheme.typography.bodyMedium)
                }
                Surface(shape = com.halo.app.ui.design.HaloShapes.card, color = scheme.surfaceContainerLow) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(uiText("Lemon chicken traybake, 50 min."), style = MaterialTheme.typography.bodyMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            PreviewBadge("Apply", scheme.primaryContainer, scheme.onPrimaryContainer)
                            PreviewBadge("Needs your OK", scheme.tertiaryContainer, scheme.onTertiaryContainer)
                            PreviewBadge("Error", scheme.error.copy(alpha = .12f), scheme.error)
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable private fun PreviewBadge(label: String, fill: Color, ink: Color) {
    Surface(shape = com.halo.app.ui.design.HaloShapes.pill, color = fill, contentColor = ink) {
        Text(uiText(label), Modifier.padding(horizontal = 10.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium)
    }
}

/** Shared grown-up Appearance entry used by Kids mode as well as Settings. */
@Composable internal fun PaletteControls(preferences: PalettePreferences = ManualDependencyGraph.palettePreferences) {
    val mode by ManualDependencyGraph.appearancePreferences.mode.collectAsStateWithLifecycle()
    val uncertain by ManualDependencyGraph.appearancePreferences.applyUncertain.collectAsStateWithLifecycle()
    AppearanceControls(mode, { draft ->
        ManualDependencyGraph.appearancePreferences.applyAppearancePersisted(draft.savedMode, draft.mode,
            preferences, draft.savedPalettes, draft.palettes)
    }, preferences, ownerUncertain = uncertain)
}

@Composable private fun appearanceAccentLabel(accent: HaloAccent): String = when (accent) {
    HaloAccent.SAGE -> stringResource(R.string.interface4_accent_sage)
    HaloAccent.ROSE -> stringResource(R.string.interface4_accent_rose)
    else -> uiText(accent.name.lowercase().replaceFirstChar { it.uppercase() })
}

@Composable private fun appearanceToneLabel(tone: HaloTone): String = when (tone) {
    HaloTone.PAPER -> stringResource(R.string.interface4_tone_paper)
    HaloTone.SLATE -> stringResource(R.string.interface4_tone_slate)
    else -> uiText(tone.name.lowercase().replaceFirstChar { it.uppercase() })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceGuidedSetup(
    dark: Boolean,
    palette: HaloPalette,
    onLook: (HaloLookPreset) -> Unit,
    onView: (HaloViewMode) -> Unit,
    onMotion: (HaloLookMotion) -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
) {
    var step by remember { mutableIntStateOf(0) }
    val answers = remember { mutableStateListOf<Int?>(null, null, null) }
    val questions = listOf(R.string.alive_guide_look, R.string.alive_guide_size, R.string.alive_guide_motion)
    val options = when (step) {
        0 -> listOf(R.string.alive_guide_night to HaloGlyph.HALO, R.string.alive_guide_paper to HaloGlyph.NOTE)
        1 -> listOf(R.string.alive_guide_comfortable to HaloGlyph.NOTE, R.string.alive_guide_bigger to HaloGlyph.NOTE)
        else -> listOf(R.string.alive_guide_lively to HaloGlyph.SPEAK, R.string.alive_guide_calm to HaloGlyph.HALO,
            R.string.alive_guide_still to HaloGlyph.STOP)
    }
    val question = stringResource(questions[step])
    val answerLabels = options.map { stringResource(it.first) }
    ModalBottomSheet(onDismissRequest = onCancel) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(question, style = MaterialTheme.typography.titleLarge)
            com.halo.app.ui.components.HaloReadAloud(
                (listOf(question) + answerLabels).joinToString(". "),
                category = com.halo.sdk.provider.RemoteDataCategory.TEXT, compact = true)
            AppearancePreview(dark, palette)
            options.forEachIndexed { index, (_, icon) ->
                FilterChip(selected = answers[step] == index, onClick = {
                    answers[step] = index
                    when (step) {
                        0 -> onLook(if (index == 0) HaloLookPreset.NIGHT_MINT else HaloLookPreset.WARM_ROSE)
                        1 -> onView(if (index == 0) HaloViewMode.STANDARD else HaloViewMode.EASY)
                        else -> onMotion(HaloLookMotion.entries[index])
                    }
                }, label = { Text(answerLabels[index]) },
                    leadingIcon = { HaloIcon(icon, null, Modifier.size(20.dp)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { if (step == 0) onCancel() else step-- }) {
                    Text(uiText(if (step == 0) "Cancel" else "Back"))
                }
                Button(onClick = { if (step == questions.lastIndex) onDone() else step++ },
                    enabled = answers[step] != null) {
                    Text(stringResource(R.string.alive_guide_this_one))
                }
            }
        }
    }
}
