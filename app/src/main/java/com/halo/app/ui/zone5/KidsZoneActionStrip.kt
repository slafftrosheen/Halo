package com.halo.app.ui.zone5

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText
import com.halo.app.ui.fx.haloTrace
import com.halo.app.ui.presence.HaloOrbContextAction

/** Horizontal Type/Help targets fit the measured child band; longer labels can still trigger Actions fallback. */
@Composable
internal fun KidsZoneActionStrip(actions: List<HaloOrbContextAction>, invoke: (HaloOrbContextAction) -> Unit, guided: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        actions.forEach { action ->
            TextButton(onClick = { if (action.enabled) invoke(action) }, enabled = action.enabled,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f).heightIn(min = 56.dp)
                    .haloTrace(action.id.removePrefix("page:") == guided, MaterialTheme.colorScheme.primary, 12.dp, lapOnAppear = true)) {
                Zone5Icon(zone5ActionIconId(action), action.glyph, Modifier.size(24.dp))
                Spacer(Modifier.width(6.dp))
                Text(uiText(action.title), Modifier.weight(1f))
            }
        }
    }
}
