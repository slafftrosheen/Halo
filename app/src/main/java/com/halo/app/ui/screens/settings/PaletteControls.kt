package com.halo.app.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.language.uiText
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import com.halo.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun AppearanceControls(mode: AppearanceMode, onApply: suspend (AppearanceDraft) -> Unit,
    preferences: PalettePreferences, assistant: AssistantPreferences = ManualDependencyGraph.assistantPreferences,
    ownerUncertain: Boolean = false) {
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
    val options = rememberVisualPreferences()
    val context = LocalContext.current
    val visual = remember(context) { VisualPreferences.get(context) }
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
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
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
                    .selectable(selected, enabled = !saving, role = Role.RadioButton, onClick = { draft = draft.editPalette(dark, palette.copy(accent = accent)) })) {
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
        Text(stringResource(R.string.interface4_appearance_preview), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val requested = draft
                scope.launch { save(appearanceApply = true) {
                    onApply(requested)
                    draft = AppearanceDraft(requested.mode, requested.palettes)
                } }
            }, enabled = !saving && (draft.changed || rollbackUncertain), modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Apply")) }
            TextButton(onClick = { draft = AppearanceDraft(mode, palettes); if (!rollbackUncertain) failure = false }, enabled = !saving && draft.changed,
                modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Cancel")) }
            TextButton(onClick = { draft = draft.editPalette(dark, HaloPalette()) }, enabled = !saving,
                modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.interface4_appearance_defaults)) }
        }
        Text(uiText("The halo ring stays Mint in every theme. Apricot means halo needs you. Red means a problem."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                com.halo.app.ui.workspace.HaloWorkspaceToggleRow(uiText("Less motion"),
                    options.lessMotion, { value -> scope.launch { save { visual.update { it.copy(lessMotion = value) } } } })
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                com.halo.app.ui.workspace.HaloWorkspaceToggleRow(uiText("Guide light"),
                    options.guideLight, { value -> scope.launch { save { visual.update { it.copy(guideLight = value) } } } })
            }
        }
        if (failure) Text(if (rollbackUncertain) stringResource(R.string.interface4_appearance_rollback_unknown)
            else uiText("Could not save your change. Try again."), color = MaterialTheme.colorScheme.error)
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
                        contentDescription = null, modifier = Modifier.width(48.dp).height(26.dp))
                    Spacer(Modifier.weight(1f))
                    HaloIcon(HaloGlyph.HALO, null, Modifier.size(24.dp), tint = Color(0xFF0CF2BA))
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
                            PreviewBadge("Needs your OK", Color(0xFFFF9F6B).copy(alpha = .16f), Color(if (dark) 0xFFFF9F6B else 0xFFA84B12))
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
