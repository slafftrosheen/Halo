package com.halo.app.ui.design

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Round 2 segmented surface; translated labels can grow without shrinking touch targets. */
@Composable
fun HaloSegmentedTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(modifier.fillMaxWidth(), shape = HaloShapes.pill, color = scheme.secondaryContainer) {
        Row(Modifier.padding(4.dp).height(IntrinsicSize.Min).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            labels.forEachIndexed { index, label ->
                val active = index == selected
                Surface(Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp).selectable(selected = active,
                    role = Role.Tab, onClick = { onSelect(index) }),
                    shape = HaloShapes.pill, color = if (active) scheme.primaryContainer else Color.Transparent) {
                    Box(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (active) scheme.onPrimaryContainer else scheme.onSecondaryContainer)
                    }
                }
            }
        }
    }
}
