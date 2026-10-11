package com.halo.app.ui.design

import com.halo.app.language.uiText
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

data class HaloHeaderAction(val label: String, val onClick: () -> Unit)

@Composable
fun HaloScreenHeader(
    title: String,
    subtitle: String? = null,
    navigation: (() -> Unit)? = null,
    primaryAction: HaloHeaderAction? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(Modifier.fillMaxWidth().padding(vertical = HaloSpacing.md),
        verticalAlignment = Alignment.CenterVertically) {
        if (navigation != null) {
            IconButton(onClick = navigation) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = uiText("Back"))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = HaloContentColors.primary)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                color = HaloContentColors.secondary)
        }
        if (primaryAction != null) {
            TextButton(onClick = primaryAction.onClick) { Text(primaryAction.label) }
        }
        trailing()
    }
}
