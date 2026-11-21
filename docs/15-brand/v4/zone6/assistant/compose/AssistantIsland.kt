package com.halo.app.assistant.ui

/*
 * halo Zone 6 · Android assistant + mini halo — REFERENCE presentation (repo @98aa2fb).
 * Replaces the look of assistant/ui/AssistantSurface.kt and ui/mini/MiniHaloScreen.kt; keeps every owner:
 *   - state: InteractionTurnState + InteractionSurface (inputRequestId) + haloSpeechOutputCoordinator.playbackActive
 *   - stop: onStopClicked (cancelAssistantInteraction "user_stop"), open: onOpenHaloClicked
 *   - native VoiceInteractor confirm/pick (HaloVoiceInteractionSession.nativeChoice) → NeedsYou / Pick islands
 *     instead of a plain AlertDialog. Responses still go through the same `respond()` lambdas.
 *   - fx: haloAssistantEdge (edge light), HaloPackageMoment.ASSIST_INVOKE (sweep)
 */

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

/** Single source for island copy. Labels are the existing strings, lowercase "halo". */
sealed interface AssistantIsland {
    data class Listening(val liveWords: String?) : AssistantIsland                      // "Listening" · "Go ahead, I'm listening."
    data object Understanding : AssistantIsland                                         // Transcribing
    data class Thinking(val steps: List<String>, val current: Int) : AssistantIsland    // Preparing / Generating — steps only if runtime reports them
    data class Answer(val title: String, val body: String, val appActions: List<String>) : AssistantIsland // Speaking / Completed
    data class NeedsYou(val app: String?, val question: String, val preview: String?, val actionLabel: String) : AssistantIsland
    data class Pick(val app: String?, val question: String, val options: List<Pair<String, String?>>) : AssistantIsland
    data class Problem(val title: String, val detail: String, val fixes: List<String>) : AssistantIsland
    data object Stopped : AssistantIsland
}

private val Apricot = Color(0xFFFF9F6B)
private val Danger = Color(0xFFFF8FA3)

@Composable
fun AssistantIslandHost(
    island: AssistantIsland,
    currentApp: String?,
    screenIncluded: Boolean,
    busy: Boolean,
    onStop: () -> Unit,
    onOpenHalo: () -> Unit,
    onPrimary: (Int) -> Unit,       // NeedsYou: 0 = action ; Pick: index ; Problem: fix index
    onSecondary: () -> Unit,        // NeedsYou "Not now"
    ring: @Composable (caption: String) -> Unit, // existing HaloSignalFace at 60dp + caption
) {
    val edge = when (island) { is AssistantIsland.NeedsYou -> Apricot; is AssistantIsland.Problem -> Danger; else -> MaterialTheme.colorScheme.primary }
    val enter = remember { MutableTransitionState(false) }.apply { targetState = true }
    AnimatedVisibility(enter, enter = slideInVertically(spring(0.62f, 380f)) { it / 3 } + fadeIn(tween(200)) + scaleIn(initialScale = .96f)) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .96f),
            border = BorderStroke(1.dp, edge.copy(alpha = .5f)),
            modifier = Modifier.padding(horizontal = 10.dp).padding(bottom = 12.dp).widthIn(max = 600.dp).fillMaxWidth()
                .heightIn(max = 420.dp).navigationBarsPadding()
        ) {
            Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 12.dp)) {
                // context chips: "In Messages" · "Screen included"
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    currentApp?.let { AssistChip({}, { Text("In $it") }, enabled = false) }
                    if (screenIncluded) AssistChip({}, { Text("Screen included") }, enabled = false)
                }
                Column(Modifier.weight(1f, fill = false).semantics { liveRegion = LiveRegionMode.Polite }) { IslandBody(island, onPrimary, onSecondary) }
                Spacer(Modifier.height(12.dp))
                // Same order as the dock: Stop/Close · ring · Open halo
                Row(verticalAlignment = Alignment.Bottom) {
                    OutlinedButton(onStop, Modifier.weight(1f).heightIn(min = 48.dp)) { Text(if (busy) "Stop" else "Close") }
                    Box(Modifier.width(92.dp), contentAlignment = Alignment.Center) { ring(captionFor(island)) }
                    FilledTonalButton(onOpenHalo, Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Open halo") }
                }
            }
        }
    }
}

fun captionFor(i: AssistantIsland) = when (i) {
    is AssistantIsland.Listening, is AssistantIsland.Pick -> "Listening"
    AssistantIsland.Understanding, is AssistantIsland.Thinking -> "Working"
    is AssistantIsland.NeedsYou -> "Waiting for you"
    is AssistantIsland.Answer -> "Tap to stop"   // while speaking; "Talk" when playback ends
    else -> "Talk"
}

@Composable
private fun IslandBody(i: AssistantIsland, onPrimary: (Int) -> Unit, onSecondary: () -> Unit) {
    val t = MaterialTheme.typography
    when (i) {
        is AssistantIsland.Listening -> { Text("Listening", style = t.titleLarge); Text(i.liveWords ?: "Go ahead, I’m listening.", style = t.bodyLarge) }
        AssistantIsland.Understanding -> { Text("Understanding", style = t.titleLarge); Text("Turning your voice into words.", style = t.bodyMedium) }
        is AssistantIsland.Thinking -> { Text("Thinking", style = t.titleLarge)
            if (i.steps.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { i.steps.forEachIndexed { n, s -> FilterChip(n == i.current, {}, { Text(s) }) } } }
        is AssistantIsland.Answer -> { Text(i.title, style = t.titleLarge); Text(i.body, style = t.bodyLarge, maxLines = 6)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { i.appActions.forEachIndexed { n, a -> AssistChip({ onPrimary(n) }, { Text(a) }) } } }
        is AssistantIsland.NeedsYou -> { Text(i.question, style = t.titleLarge); i.preview?.let { Text("“$it”", style = t.bodyLarge) }
            Text("${i.app ?: "An app"} is asking · nothing sent yet", style = t.labelMedium, color = Apricot)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onSecondary, Modifier.weight(1f).heightIn(min = 50.dp)) { Text("Not now") }
                Button({ onPrimary(0) }, Modifier.weight(1f).heightIn(min = 50.dp), colors = ButtonDefaults.buttonColors(containerColor = Apricot, contentColor = Color(0xFF060330))) { Text(i.actionLabel) }
            } }
        is AssistantIsland.Pick -> { Text(i.question, style = t.titleLarge); Text("Say the number or tap.", style = t.bodyMedium)
            i.options.forEachIndexed { n, (label, detail) -> OutlinedCard({ onPrimary(n) }, Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) { Text("${n + 1}  $label"); Spacer(Modifier.weight(1f)); detail?.let { Text(it, style = t.labelMedium) } } } } }
        is AssistantIsland.Problem -> { Text(i.title, style = t.titleLarge); Text(i.detail, style = t.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { i.fixes.forEachIndexed { n, f -> AssistChip({ onPrimary(n) }, { Text(f) }) } } }
        AssistantIsland.Stopped -> { Text("Stopped", style = t.titleLarge); Text("Ready whenever you need me.", style = t.bodyMedium) }
    }
}

/* ---------------- floating bubble (FloatingHaloService) ---------------- */

/** The bubble IS the ring. Painted into the existing ImageView bitmap (paintOrb), 60dp, from nearbyScheme(). */
enum class BubbleState { IDLE, LISTENING, WORKING, NEEDS_YOU }
/*
 * paintOrb() additions:
 *   IDLE      : ring (accent) on Night Ink disc, soft glow; breathe 4.8 s via ValueAnimator only when motion != STILL
 *   LISTENING : + accent outer glow ring (alpha .6)
 *   WORKING   : + thin progress arc (one quarter, rotates — the ONLY allowed rotation, as THINKING)
 *   NEEDS_YOU : + 16dp Apricot dot top-right with 2.5dp halo-coloured outline; contentDescription "halo needs you"
 * Source of state: AssistantRuntimeStateStore + attention summary (same as the dock's Activity badge).
 */
