package com.halo.app.ui.screens.kids

import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.halo.app.ui.fx.haloTrace
import com.halo.app.R
import com.halo.app.language.uiText
import com.halo.app.ui.design.HaloShapes
import com.halo.app.ui.presence.HaloSignalFace
import com.halo.app.ui.presence.HaloSignalPresentation

internal enum class KidsDestination { HOME, ASK, LEARN, CREATE }

/** Stack translated labels on phone widths below 480dp, and whenever text is enlarged.
 * Avoid squeezing a word into a half-width button with an icon beside it.
 */
internal fun kidsHomeChoicesStacked(fontScale: Float, widthDp: Int): Boolean =
    fontScale >= 1.3f || widthDp < 480

/** K01: three stable choices; resume belongs to its activity, not a fourth card. */
@Composable
internal fun KidsHomeChoices(
    recentLessonTitle: String? = null,
    projectTitle: String? = null,
    onSelect: (KidsDestination) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KidsChoice("Ask a question", R.drawable.halo_ic_ask_mode, Modifier.fillMaxWidth()) {
            onSelect(KidsDestination.ASK)
        }
        KidsChoice("Learn something", R.drawable.halo_ic_study, Modifier.fillMaxWidth(),
            subtitle = recentLessonTitle?.let { uiText("Keep going: %1\$s", it) },
            highlighted = recentLessonTitle != null) {
            onSelect(KidsDestination.LEARN)
        }
        KidsChoice("Make something", R.drawable.halo_ic_sketch, Modifier.fillMaxWidth(),
            subtitle = projectTitle?.let { uiText("Keep going: %1\$s", it) },
            highlighted = recentLessonTitle == null && projectTitle != null) {
            onSelect(KidsDestination.CREATE)
        }
    }
}

@Composable
internal fun KidsChoice(title: String, icon: Int, modifier: Modifier = Modifier,
    subtitle: String? = null, highlighted: Boolean = false, enabled: Boolean = true,
    onClick: () -> Unit) {
    val choice = rememberKidsSpokenChoice(uiText(title), onClick)
    Surface(onClick = choice.onClick, enabled = enabled, modifier = modifier.heightIn(min = 76.dp)
        .haloTrace(highlighted, MaterialTheme.colorScheme.primary, corner = 26.dp, lapOnAppear = true),
        shape = HaloShapes.hero,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(if (choice.heard) 2.dp else 1.dp, if (choice.heard) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).background(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f),
                RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), null, Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(uiText(title), style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold))
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 14.sp, lineHeight = 20.sp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

/** A text-creation path through the existing assistant pipeline, not an image promise. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun KidsCreationPanel(
    idea: String, onIdeaChange: (String) -> Unit, enabled: Boolean, onCreate: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(uiText("Create a story with halo"), style = MaterialTheme.typography.titleLarge)
        Text(uiText("Make something together"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        HaloTextField(kind = VoiceFieldKind.TEXT, value = idea, onValueChange = onIdeaChange, enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(uiText("My idea")) }, minLines = 2, maxLines = 4)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Write a story", "Invent a character").forEach { starter ->
                val localized = uiText(starter)
                OutlinedButton(onClick = { onIdeaChange(localized) },
                    enabled = enabled, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(uiText(starter), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        KidsPrimaryButton(onClick = onCreate, enabled = enabled && idea.isNotBlank(),
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
    lessonTitle: String? = null,
    onBack: () -> Unit,
    onStop: () -> Unit,
) {
    val title = lessonTitle?.takeIf { it.isNotBlank() } ?: when (destination) {
        KidsDestination.ASK -> "Ask a question"
        KidsDestination.LEARN -> "Learn something"
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
        Text(lessonTitle?.takeIf { it.isNotBlank() } ?: uiText(title), modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 23.sp, lineHeight = 30.sp),
            maxLines = 2)
        IconButton(onClick = onStop, modifier = Modifier.size(56.dp)) {
            Icon(painterResource(R.drawable.halo_ic_stop), contentDescription = uiText("Stop"))
        }
    }
}
