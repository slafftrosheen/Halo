package com.halo.app.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText
import com.halo.app.ui.design.HaloScreenHeader
import com.halo.app.ui.design.HaloShapes
import com.halo.app.ui.theme.AppearanceMode
import com.halo.app.ui.theme.PalettePreferences

@Composable
internal fun AppearanceSettingsScreen(
    mode: AppearanceMode,
    onMode: (AppearanceMode) -> Unit,
    preferences: PalettePreferences?,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { HaloScreenHeader(uiText("Appearance"), uiText("Make halo feel like yours."), navigation = onBack) }
        item {
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppearanceMode.entries.forEach { option ->
                    val selected = option == mode
                    Surface(Modifier.weight(1f).heightIn(min = 48.dp).selectable(selected = selected,
                        role = Role.RadioButton, onClick = { onMode(option) }),
                        shape = HaloShapes.pill,
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant)) {
                        Box(Modifier.padding(horizontal = 8.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                            Text(uiText(option.name.lowercase().replaceFirstChar { it.uppercase() }),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
        if (preferences != null) item {
            Surface(shape = HaloShapes.card, color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                PaletteControls(preferences)
            }
        }
    }
}
