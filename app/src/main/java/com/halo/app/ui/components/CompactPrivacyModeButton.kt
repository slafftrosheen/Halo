package com.halo.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.halo.app.ai.PrivacyMode
import com.halo.app.language.uiText

/** The compact two-position projection of the existing durable Local/Power mode owner. */
@Composable
internal fun CompactPrivacyModeButton(mode: PrivacyMode, description: String, enabled: Boolean,
    onClick: () -> Unit, modifier: Modifier = Modifier) {
    val power = mode == PrivacyMode.CLOUD
    Column(modifier.heightIn(min = 48.dp).widthIn(min = 48.dp)
        .toggleable(value = power, enabled = enabled, role = Role.Switch, onValueChange = { onClick() })
        .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally) {
        Switch(checked = power, onCheckedChange = null, enabled = enabled)
        Text(androidx.compose.ui.res.stringResource(if (power) com.halo.app.R.string.interface4_mode_power_short else com.halo.app.R.string.interface4_mode_local_short), style = MaterialTheme.typography.labelSmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
