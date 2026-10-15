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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.sp
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
    Column(Modifier.fillMaxWidth().padding(vertical = HaloSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(HaloSpacing.sm)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HaloSpacing.sm),
            verticalAlignment = Alignment.Top) {
            if (navigation != null) {
                Surface(shape = HaloShapes.pill, color = MaterialTheme.colorScheme.secondaryContainer) {
                    IconButton(onClick = navigation, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = uiText("Back"),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            } else if (glyph != null) {
                Surface(shape = HaloShapes.control, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        HaloIcon(glyph, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp, lineHeight = 28.sp),
                    fontWeight = FontWeight.Bold, color = HaloContentColors.primary)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = HaloContentColors.secondary)
            }
            Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
        }
        if (primaryAction != null) {
            OutlinedButton(onClick = primaryAction.onClick,
                modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp), shape = HaloShapes.pill) {
                Text(primaryAction.label)
            }
        }
    }
}
