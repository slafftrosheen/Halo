package com.halo.app.ui.design

import com.halo.app.language.uiText
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon

data class HaloHeaderAction(val label: String, val onClick: () -> Unit)

@Composable
fun HaloScreenHeader(
    title: String,
    subtitle: String? = null,
    navigation: (() -> Unit)? = null,
    primaryAction: HaloHeaderAction? = null,
    glyph: HaloGlyph? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    FlowRow(Modifier.fillMaxWidth().padding(vertical = HaloSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(HaloSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(HaloSpacing.sm)) {
        if (navigation != null) {
            FilledTonalIconButton(onClick = navigation, modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Icon(painterResource(com.halo.app.R.drawable.halo_ic_back),
                    contentDescription = uiText("Back"), modifier = Modifier.size(24.dp))
            }
        }
        if (glyph != null) {
            Surface(shape = HaloShapes.control, color = MaterialTheme.colorScheme.primaryContainer) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    HaloIcon(glyph, contentDescription = null, modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        Column(Modifier.weight(1f).widthIn(min = 160.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold,
                color = HaloContentColors.primary)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                color = HaloContentColors.secondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        if (primaryAction != null) {
            OutlinedButton(onClick = primaryAction.onClick, modifier = Modifier.heightIn(min = 48.dp), shape = HaloShapes.control) { Text(primaryAction.label) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}
