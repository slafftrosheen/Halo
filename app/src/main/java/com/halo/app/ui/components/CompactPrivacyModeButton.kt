package com.halo.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.ai.PrivacyMode
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon

/** The same mode action, with a bounded label that remains readable at 200% text. */
@Composable
internal fun CompactPrivacyModeButton(mode: PrivacyMode, description: String, enabled: Boolean,
    onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(if (mode == PrivacyMode.PRIVATE) R.string.interface4_private_short else R.string.interface4_power_short)
    val largeText = LocalDensity.current.fontScale >= 1.4f
    val icon: @Composable () -> Unit = {
        HaloIcon(if (mode == PrivacyMode.PRIVATE) HaloGlyph.SECURITY else HaloGlyph.CLOUD, null, Modifier.size(18.dp))
    }
    val text: @Composable () -> Unit = {
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    OutlinedButton(onClick = onClick, enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp).semantics { contentDescription = description },
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
        if (largeText) Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            icon(); text()
        } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            icon(); text()
        }
    }
}
