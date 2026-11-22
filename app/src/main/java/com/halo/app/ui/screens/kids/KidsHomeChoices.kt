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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.halo.app.ui.fx.haloTrace
import com.halo.app.R
import com.halo.app.language.uiText
import com.halo.app.ui.design.HaloShapes
import com.halo.app.ui.presence.HaloSignalFace
import com.halo.app.ui.presence.HaloSignalPresentation
import com.halo.app.ui.zone5.Zone5Icon
import com.halo.app.ui.zone5.Zone5ActionButton
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon

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
    onIdentify: (() -> Unit)? = null,
    onPlay: (() -> Unit)? = null,
    playEnabled: Boolean = true,
    onSelect: (KidsDestination) -> Unit,
) {
    if (onIdentify != null && onPlay != null) {
        val colors = MaterialTheme.colorScheme
        val choices = listOf(
            KidsHomeTile(R.string.halo11_kids_learn, R.drawable.halo_ic_study, colors.secondaryContainer,
                colors.onSecondaryContainer) { onSelect(KidsDestination.LEARN) },
            KidsHomeTile(R.string.halo11_kids_make, R.drawable.halo_ic_sketch, colors.tertiaryContainer,
                colors.onTertiaryContainer) { onSelect(KidsDestination.CREATE) },
            KidsHomeTile(R.string.halo11_kids_identify, R.drawable.halo_ic_ask_mode, colors.primaryContainer,
                colors.onPrimaryContainer, onOpen = onIdentify),
            KidsHomeTile(R.string.halo11_kids_play, R.drawable.halo_ic_ask_mode, colors.surfaceContainerHighest,
                colors.onSurface, enabled = playEnabled, onOpen = onPlay),
        )
        val columns = if (LocalDensity.current.fontScale >= 1.4f || LocalConfiguration.current.screenWidthDp < 300) 1 else 2
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { item -> KidsHomeTileCard(item, Modifier.weight(1f)) }
                }
            }
        }
        return
    }
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

private data class KidsHomeTile(val title: Int, val icon: Int, val color: Color, val ink: Color,
    val enabled: Boolean = true, val onOpen: () -> Unit)

@Composable
private fun KidsHomeTileCard(item: KidsHomeTile, modifier: Modifier) {
    val title = stringResource(item.title)
    val spoken = rememberKidsSpokenChoice(title, onChoose = item.onOpen)
    val confirm = stringResource(R.string.kids_choice_tap_again)
    Surface(onClick = spoken.onClick, enabled = item.enabled, modifier = modifier.heightIn(min = 104.dp)
        .semantics { if (spoken.heard) stateDescription = confirm },
        shape = RoundedCornerShape(22.dp), color = item.color, contentColor = item.ink) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .7f),
                modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(item.icon), null, Modifier.size(23.dp), tint = item.ink)
                }
            }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun KidsChoice(title: String, icon: Int, modifier: Modifier = Modifier,
    subtitle: String? = null, highlighted: Boolean = false, enabled: Boolean = true,
    onClick: () -> Unit) {
    val choice = rememberKidsSpokenChoice(uiText(title), onChoose = onClick)
    val confirm = stringResource(R.string.kids_choice_tap_again)
    Surface(onClick = choice.onClick, enabled = enabled, modifier = modifier.heightIn(min = 76.dp)
        .semantics { if (choice.heard) stateDescription = confirm }
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
                androidx.compose.runtime.CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                    Zone5Icon(when (icon) {
                        R.drawable.halo_ic_ask_mode -> "action_help"
                        R.drawable.halo_ic_study -> "study_kids"
                        R.drawable.halo_ic_sketch -> "studio_draw"
                        else -> "action_help"
                    }, HaloGlyph.TOOL, Modifier.size(28.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(uiText(title), style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold))
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 14.sp, lineHeight = 20.sp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (choice.heard) HaloIcon(HaloGlyph.SPEAK, null, Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary)
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
@OptIn(ExperimentalLayoutApi::class)
internal fun KidsActivityHeader(
    destination: KidsDestination,
    signal: HaloSignalPresentation,
    lessonTitle: String? = null,
    onBack: () -> Unit,
    onStop: () -> Unit,
) {
    if (com.halo.app.ui.presence.LocalShellHaloOrb.current) return
    val title = lessonTitle?.takeIf { it.isNotBlank() } ?: when (destination) {
        KidsDestination.ASK -> "Ask a question"
        KidsDestination.LEARN -> "Learn something"
        KidsDestination.CREATE -> "Create a story with halo"
        KidsDestination.HOME -> "Kids"
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Zone5ActionButton(uiText("Back to choices"), "action_back", modifier = Modifier.heightIn(min = 56.dp), onClick = onBack)
            Zone5ActionButton(uiText("Stop"), "media_stop", modifier = Modifier.heightIn(min = 56.dp), onClick = onStop)
        }
        Text(lessonTitle?.takeIf { it.isNotBlank() } ?: uiText(title), modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 23.sp, lineHeight = 30.sp),
            maxLines = 2)
    }
}
