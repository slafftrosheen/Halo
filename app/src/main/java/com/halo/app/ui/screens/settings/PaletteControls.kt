package com.halo.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import com.halo.app.ui.fx.HaloAccentSweepRing
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.language.uiText
import com.halo.app.ui.theme.*

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable internal fun PaletteControls(preferences: PalettePreferences = ManualDependencyGraph.palettePreferences) {
    val palettes by preferences.state.collectAsStateWithLifecycle()
    val currentDark = MaterialTheme.colorScheme.background.luminance() < .5f
    var dark by rememberSaveable { mutableStateOf(currentDark) }
    val palette = if (dark) palettes.dark else palettes.light
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(false to "Light colours", true to "Dark colours").forEach { (value, label) ->
                FilterChip(modifier = Modifier.heightIn(min = 48.dp), selected = dark == value, onClick = { dark = value }, label = { Text(uiText(label)) })
            }
        }
        Text(uiText("Accent colour"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HaloAccent.entries.forEach { accent ->
                FilterChip(modifier = Modifier.heightIn(min = 48.dp), selected = palette.accent == accent, onClick = { preferences.select(dark, palette.copy(accent = accent)) },
                    leadingIcon = { Box(Modifier.size(16.dp).background(haloColorScheme(dark, HaloPalette(accent)).primary, CircleShape)) },
                    label = { Text(uiText(accent.name.lowercase().replaceFirstChar { it.uppercase() })) })
            }
        }
        Text(uiText("Background tone"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HaloTone.entries.forEach { tone ->
                FilterChip(modifier = Modifier.heightIn(min = 48.dp), selected = palette.tone == tone, onClick = { preferences.select(dark, palette.copy(tone = tone)) },
                    label = { Text(uiText(tone.name.lowercase().replaceFirstChar { it.uppercase() })) })
            }
        }
        HaloTheme(darkTheme = dark, palette = palette) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(uiText("Your halo"), style = MaterialTheme.typography.titleMedium)
                    Text(uiText("A little colour. Still easy to read."), style = MaterialTheme.typography.bodyMedium)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        HaloAccentSweepRing(
                            ringStart = MaterialTheme.colorScheme.primary,
                            ringEnd = MaterialTheme.colorScheme.tertiary,
                            ringSize = 96.dp,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(uiText("Accent colour"), style = MaterialTheme.typography.labelMedium)
                            Surface(color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                shape = MaterialTheme.shapes.small) {
                                Text("Aa", Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
