package com.halo.app.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.language.uiText
import com.halo.app.ui.design.HaloShapes
import com.halo.app.ui.icons.HaloIcon
import com.halo.app.ui.zone5.LocalZone5GuideSession
import com.halo.app.ui.zone6.Zone6GuideReplay
import com.halo.app.ui.zone6.Zone6Tour

@Composable
internal fun HelpTourChooser(onNavigate: (String) -> Unit) {
    val session = LocalZone5GuideSession.current
    Surface(shape = HaloShapes.card, color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.help_tour_chooser), Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium)
            Zone6Tour.entries.forEachIndexed { index, tour ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val seen = session?.suppressed(tour.seenKey) == true
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    HaloIcon(tour.glyph, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(tour.title), style = MaterialTheme.typography.titleSmall)
                        if (seen) Text(stringResource(R.string.help_tour_seen),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = {
                        Zone6GuideReplay.replay(tour)
                        onNavigate(tour.route)
                    }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(if (seen) stringResource(R.string.help_tour_replay) else uiText("Start"))
                    }
                }
            }
        }
    }
}
