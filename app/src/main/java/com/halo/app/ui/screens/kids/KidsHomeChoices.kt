package com.halo.app.ui.screens.kids

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.language.uiText
import com.halo.app.ui.design.HaloShapes
import com.halo.app.ui.presence.HaloSignalFace
import com.halo.app.ui.presence.HaloSignalPresentation

internal enum class KidsDestination { HOME, ASK, LEARN, CREATE }

/** Kids 2.0: three child-sized choices, with the main voice action first. */
@Composable
internal fun KidsHomeChoices(onSelect: (KidsDestination) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(onClick = { onSelect(KidsDestination.ASK) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 78.dp),
            shape = HaloShapes.pill,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center) {
                Icon(painterResource(R.drawable.halo_ic_mic), contentDescription = null,
                    modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text(uiText("Ask me anything"), style = MaterialTheme.typography.titleMedium)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KidsChoice("Learn", R.drawable.halo_ic_study, Modifier.weight(1f)) {
                onSelect(KidsDestination.LEARN)
            }
            KidsChoice("Create", R.drawable.halo_ic_sketch, Modifier.weight(1f)) {
                onSelect(KidsDestination.CREATE)
            }
        }
    }
}

@Composable
private fun KidsChoice(title: String, icon: Int, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.heightIn(min = 68.dp),
        shape = HaloShapes.pill,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), null, Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(uiText(title), style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** A text-creation path through the existing assistant pipeline, not an image promise. */
@Composable
internal fun KidsCreationPanel(
    idea: String, onIdeaChange: (String) -> Unit, enabled: Boolean, onCreate: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(uiText("Create a story with halo"), style = MaterialTheme.typography.titleLarge)
        Text(uiText("Make something together"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(idea, onIdeaChange, enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(uiText("My idea")) }, minLines = 2, maxLines = 4)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Write a story", "Invent a character").forEach { starter ->
                val localized = uiText(starter)
                OutlinedButton(onClick = { onIdeaChange(localized) },
                    enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                    Text(uiText(starter), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Button(onClick = onCreate, enabled = enabled && idea.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(uiText("Start creating"))
        }
    }
}

/** Compact Kids header for task screens; the 160dp orb belongs only on Home. */
@Composable
internal fun KidsActivityHeader(
    destination: KidsDestination,
    signal: HaloSignalPresentation,
    onBack: () -> Unit,
    onStop: () -> Unit,
) {
    val title = when (destination) {
        KidsDestination.ASK -> "Ask Halo"
        KidsDestination.LEARN -> "My learning adventure"
        KidsDestination.CREATE -> "Create a story with halo"
        KidsDestination.HOME -> "Kids"
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.size(56.dp)) {
            Icon(painterResource(R.drawable.halo_ic_back),
                contentDescription = uiText("Back to choices"))
        }
        HaloSignalFace(signal, kids = true, size = 48.dp,
            contentDescription = uiText("Talk to Halo"))
        Text(uiText(title), modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall, maxLines = 2)
        IconButton(onClick = onStop, modifier = Modifier.size(56.dp)) {
            Icon(painterResource(R.drawable.halo_ic_stop), contentDescription = uiText("Stop"))
        }
    }
}
