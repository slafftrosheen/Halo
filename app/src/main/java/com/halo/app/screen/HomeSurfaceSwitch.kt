package com.halo.app.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText
import com.halo.app.ui.design.HaloShapes

@Composable
fun HomeSurfaceSwitch(screen: Boolean, onSelect: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Surface(shape = HaloShapes.pill, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(Modifier.padding(4.dp).selectableGroup()) {
                listOf(false to "Chat", true to "Screen").forEach { (value, label) ->
                    Surface(Modifier.widthIn(min = 56.dp).heightIn(min = 48.dp)
                        .selectable(screen == value, role = Role.Tab, onClick = { onSelect(value) }),
                        shape = HaloShapes.pill,
                        color = if (screen == value) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .18f) else Color.Transparent) {
                        Box(Modifier.padding(horizontal = 10.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                            Text(uiText(label), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}
