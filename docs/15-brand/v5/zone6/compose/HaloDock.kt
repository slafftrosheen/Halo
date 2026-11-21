package com.halo.app.ui.zone6

/*
 * halo Zone 6 · "Calm Dock, alive" — REFERENCE implementation (presentation only).
 *
 * Replaces the visual layer of ui/shell/HaloZone.kt. It keeps every Zone 5 owner:
 * actions still come from the current-entry action lease (HaloOrbContextAction), the ring still
 * calls the canonical voice/runtime owner, Back stays Android Back, states stay truthful.
 * Compose BOM 2026.09.00 + lottie-compose 6.7.1 (already in libs.versions.toml).
 *
 * Wiring map (Zone 5 -> Zone 6):
 *   utility() / statusLabel            -> DockPlace (layer 1)
 *   WorkspaceToolMenus / ContextToolOrbit -> DockActions (layer 2, max 4, one primary, rest in "More")
 *   HaloZone5Navigation (hidden sheet) -> DockNav (layer 3, visible) + PlacesSheet (hold ring / Places tab)
 *   Zone5GuideInline                   -> DockPlace.next chip (one, only if it fits)
 *   orb(64.dp)                         -> DockRing (56dp + caption) ; compact 48dp above IME
 */

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/* ---------------- model ---------------- */

/** What the dock is showing. Derived from REAL owner state — never from an animation finishing. */
sealed interface DockState {
    data object Idle : DockState
    data class Listening(val liveWords: String) : DockState
    data class Working(val title: String, val step: Int, val steps: List<String>) : DockState
    /** Apricot. [actionLabel] names the action ("Send to Anna"), never "Allow". */
    data class NeedsYou(val question: String, val detail: String, val actionLabel: String) : DockState
    /** Only after the owner's receipt (e.g. CalendarWriteReceipt.CONFIRMED). */
    data class Done(val title: String, val detail: String, val undo: Boolean) : DockState
    /** Names the failed operation; draft is safe; one matching fix. */
    data class Failed(val title: String, val detail: String, val fixLabel: String, val altLabel: String?) : DockState
}

data class DockPlace(
    val iconId: String,          // Zone5 semantic icon id
    val title: String,           // "Poster draft"
    val status: String,          // "HaloStudio · Saved" — Saved only with a write receipt
    val statusOk: Boolean = false,
    val next: DockAction? = null // one guided next step, hidden when it does not fit
)

data class DockAction(val id: String, val iconId: String, val label: String, val primary: Boolean = false,
                      val enabled: Boolean = true, val disabledReason: String? = null)

enum class DockPlaceId { HOME, WORKSPACE, ACTIVITY, DEVICES, SETTINGS, WIDGETS }

/* ---------------- tokens ---------------- */

object Z6 {
    val DockHeightFraction = 0.25f
    val TopRadius = 30.dp
    val IslandRadius = 26.dp
    val IslandInset = 10.dp
    val ActionHeight = 52.dp
    val NavHeight = 76.dp
    val RingDp = 56.dp
    val RingCompactDp = 48.dp
    val KidsRingDp = 60.dp
    val MinTarget = 48.dp
    val KidsTarget = 56.dp
    /** ≈ cubic-bezier(.34,1.56,.64,1) 600ms */
    fun <T> spring() = spring<T>(dampingRatio = 0.62f, stiffness = 380f)
    val Emphasized = CubicBezierEasing(.2f, 0f, 0f, 1f)
}

/* ---------------- dock ---------------- */

@Composable
fun HaloDock(
    usableHeight: Dp,
    place: DockPlace,
    actions: List<DockAction>,
    state: DockState,
    current: DockPlaceId,
    activityNeedsYou: Int,
    ringCaption: String,
    onAction: (DockAction) -> Unit,
    onPlace: (DockPlaceId) -> Unit,
    onRingTap: () -> Unit,            // canonical voice owner: talk / finish / stop / interrupt
    onOpenPlaces: () -> Unit,         // hold ring OR Places tab — never starts speech
    onIslandPrimary: () -> Unit,
    onIslandSecondary: () -> Unit,
    composer: (@Composable RowScope.() -> Unit)? = null,
    kids: Boolean = false,
    reducedMotion: Boolean = false,
) {
    val largeText = LocalDensity.current.fontScale >= 1.4f
    val island = state !is DockState.Idle
    val anim: FiniteAnimationSpec<Dp> = if (reducedMotion) tween(150) else Z6.spring()
    val inset by animateDpAsState(if (island) Z6.IslandInset else 0.dp, anim, label = "islandInset")
    val radius by animateDpAsState(if (island) Z6.IslandRadius else Z6.TopRadius, anim, label = "islandRadius")
    val edge = when (state) {
        is DockState.NeedsYou -> HaloColors.needsYou
        is DockState.Failed -> HaloColors.danger
        is DockState.Idle -> Color.Transparent
        else -> MaterialTheme.colorScheme.primary
    }
    val edgeColor by animateColorAsState(edge.copy(alpha = if (island) .55f else 0f), tween(400), label = "edge")

    Box(
        Modifier.fillMaxWidth()
            .then(if (largeText) Modifier.wrapContentHeight() else Modifier.height(usableHeight * Z6.DockHeightFraction))
            .semantics { isTraversalGroup = true; paneTitle = "halo" }
    ) {
        // Surface that lifts into the island (C). Nav + ring stay put underneath.
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .92f),
            shape = if (island) RoundedCornerShape(radius) else RoundedCornerShape(topStart = radius, topEnd = radius),
            border = BorderStroke(1.dp, edgeColor),
            modifier = Modifier.padding(horizontal = inset, vertical = if (island) 4.dp else 0.dp)
                .fillMaxWidth().height(if (island) 107.dp else usableHeight * Z6.DockHeightFraction)
        ) {}
        Column(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = state,
                contentKey = { it::class },
                transitionSpec = {
                    (fadeIn(tween(280, 120)) + scaleIn(initialScale = .98f, animationSpec = if (reducedMotion) tween(150) else Z6.spring()))
                        .togetherWith(fadeOut(tween(160)))
                },
                label = "dockPanel",
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) { s ->
                when (s) {
                    DockState.Idle -> IdlePanel(place, actions, onAction, composer, kids, largeText, reducedMotion)
                    is DockState.Listening -> IslandRow(title = "Listening", detail = s.liveWords, primary = "Stop", onPrimary = onIslandPrimary, tone = IslandTone.ACCENT)
                    is DockState.Working -> IslandRow(title = s.title, detail = "Step ${s.step} of ${s.steps.size} · ${s.steps.getOrNull(s.step - 1).orEmpty()}", primary = "Stop", onPrimary = onIslandPrimary, tone = IslandTone.ACCENT)
                    is DockState.NeedsYou -> IslandRow(s.question, s.detail, primary = s.actionLabel, secondary = "Not now", onPrimary = onIslandPrimary, onSecondary = onIslandSecondary, tone = IslandTone.NEEDS_YOU)
                    is DockState.Done -> IslandRow(s.title, s.detail, primary = if (s.undo) "Undo" else "OK", onPrimary = onIslandPrimary, tone = IslandTone.ACCENT)
                    is DockState.Failed -> IslandRow(s.title, s.detail, primary = s.fixLabel, secondary = s.altLabel, onPrimary = onIslandPrimary, onSecondary = onIslandSecondary, tone = IslandTone.DANGER)
                }
            }
            DockNav(current, activityNeedsYou, ringCaption, onPlace, onRingTap, onOpenPlaces, kids, largeText)
        }
    }
}

@Composable
private fun IdlePanel(place: DockPlace, actions: List<DockAction>, onAction: (DockAction) -> Unit,
                      composer: (@Composable RowScope.() -> Unit)?, kids: Boolean, largeText: Boolean, reducedMotion: Boolean) {
    Column(Modifier.padding(horizontal = 16.dp).padding(top = 8.dp)) {
        PlaceRow(place, onAction)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().height(if (kids) Z6.KidsTarget else Z6.ActionHeight), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (composer != null) { composer() ; return@Row }
            // ≤4 visible; overflow and large text collapse into "More"/"All actions" (same action lease)
            val visible = if (largeText) actions.take(1) else actions.take(if (actions.size > 4) 3 else 4)
            visible.forEachIndexed { i, a -> SpringButton(a, i, Modifier.weight(if (a.primary) 1.25f else 1f), reducedMotion) { onAction(a) } }
            if (largeText || actions.size > 4) SpringButton(DockAction("all-actions", "more", if (largeText) "All actions" else "More"), visible.size, Modifier.weight(1f), reducedMotion) { onAction(DockAction("all-actions", "more", "All actions")) }
        }
    }
}

/** Buttons spring out of the ring (B): staggered 40ms, origin = ring position. */
@Composable
private fun SpringButton(a: DockAction, index: Int, modifier: Modifier, reducedMotion: Boolean, onClick: () -> Unit) {
    val shown = remember { MutableTransitionState(reducedMotion) }.apply { targetState = true }
    AnimatedVisibility(
        visibleState = shown, modifier = modifier,
        enter = if (reducedMotion) fadeIn(tween(150)) else
            fadeIn(tween(200, index * 40)) + scaleIn(initialScale = .4f, animationSpec = spring(0.62f, 380f)) +
                slideInVertically(spring(0.62f, 380f)) { it }
    ) {
        val colors = if (a.primary) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors()
        Button(onClick = onClick, enabled = a.enabled, colors = colors, shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
            modifier = Modifier.fillMaxSize().semantics { if (!a.enabled && a.disabledReason != null) stateDescription = a.disabledReason }) {
            Zone6Icon(a.iconId); Spacer(Modifier.width(6.dp)); Text(a.label, maxLines = 2, overflow = TextOverflow.Clip)
        }
    }
}

@Composable
private fun PlaceRow(place: DockPlace, onAction: (DockAction) -> Unit) {
    Row(Modifier.fillMaxWidth().height(44.dp).semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = .12f)), Alignment.Center) { Zone6Icon(place.iconId) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(place.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(place.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        place.next?.let { n -> AssistChip(onClick = { onAction(n) }, label = { Text(n.label) }, leadingIcon = { Zone6Icon(n.iconId) },
            modifier = Modifier.heightIn(min = Z6.MinTarget).haloTraceBreathe()) }
    }
}

enum class IslandTone { ACCENT, NEEDS_YOU, DANGER }

@Composable
private fun IslandRow(title: String, detail: String, primary: String, onPrimary: () -> Unit, tone: IslandTone,
                      secondary: String? = null, onSecondary: () -> Unit = {}) {
    Column(Modifier.padding(horizontal = 24.dp).padding(top = 14.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(detail, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            secondary?.let { OutlinedButton(onSecondary, Modifier.weight(1f).heightIn(min = 44.dp)) { Text(it) } }
            val c = when (tone) { IslandTone.NEEDS_YOU -> HaloColors.needsYou; IslandTone.DANGER -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.primary }
            Button(onPrimary, Modifier.weight(1f).heightIn(min = 44.dp), colors = ButtonDefaults.buttonColors(containerColor = c)) { Text(primary) }
        }
    }
}

@Composable
private fun DockNav(current: DockPlaceId, needsYou: Int, ringCaption: String, onPlace: (DockPlaceId) -> Unit,
                    onRingTap: () -> Unit, onOpenPlaces: () -> Unit, kids: Boolean, largeText: Boolean) {
    val haptic = LocalHapticFeedback.current
    Row(Modifier.fillMaxWidth().height(Z6.NavHeight).padding(horizontal = 8.dp), verticalAlignment = Alignment.Bottom) {
        @Composable fun Tab(id: DockPlaceId, icon: String, label: String, badge: Int = 0) = NavTab(icon, label, current == id, badge, Modifier.weight(1f)) {
            if (id == DockPlaceId.WIDGETS) onOpenPlaces() else onPlace(id) }
        if (kids) { Tab(DockPlaceId.HOME, "home", "Home") }
        else if (largeText) { Tab(DockPlaceId.HOME, "home", "Home") }
        else { Tab(DockPlaceId.HOME, "home", "Home"); Tab(DockPlaceId.WORKSPACE, "notebook", "Workspace") }
        // Ring: tap = canonical voice action; long-press = Places. Two separate semantics actions.
        Column(Modifier.width(96.dp).pointerInput(Unit) {
            detectTapGestures(onTap = { haptic.performHapticFeedback(HapticFeedbackType.ContextClick); onRingTap() },
                onLongPress = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onOpenPlaces() })
        }.semantics(mergeDescendants = true) {
            contentDescription = ringCaption; role = Role.Button
            customActions = listOf(CustomAccessibilityAction("Open Places") { onOpenPlaces(); true })
        }, horizontalAlignment = Alignment.CenterHorizontally) {
            HaloRing(if (kids) Z6.KidsRingDp else Z6.RingDp)   // existing classic thick ring renderer + state Lottie
            Text(ringCaption, style = MaterialTheme.typography.labelMedium)
        }
        if (kids) { NavTab("study", "Learn", false, 0, Modifier.weight(1f)) { onPlace(DockPlaceId.WORKSPACE) } }
        else if (largeText) { NavTab("grid_view", "Places", false, 0, Modifier.weight(1f), onOpenPlaces) }
        else { Tab(DockPlaceId.ACTIVITY, "notifications", "Activity", needsYou); NavTab("grid_view", "Places", current in setOf(DockPlaceId.DEVICES, DockPlaceId.SETTINGS, DockPlaceId.WIDGETS), 0, Modifier.weight(1f), onOpenPlaces) }
    }
}

@Composable
private fun NavTab(icon: String, label: String, selected: Boolean, badge: Int, modifier: Modifier, onClick: () -> Unit) {
    val w by animateDpAsState(if (selected) 64.dp else 56.dp, Z6.spring(), label = "ind")
    Column(modifier.heightIn(min = Z6.MinTarget).clickable(role = Role.Tab, onClickLabel = label, onClick = onClick)
        .semantics { this.selected = selected }, horizontalAlignment = Alignment.CenterHorizontally) {
        BadgedBox(badge = { if (badge > 0) Badge(containerColor = HaloColors.needsYou) { Text("$badge") } }) {
            Box(Modifier.size(w, 32.dp).clip(CircleShape).background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .16f) else Color.Transparent), Alignment.Center) { Zone6Icon(icon) }
        }
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/* ---------------- places sheet (B tiles springing from the ring) ---------------- */

data class PlaceTile(val id: DockPlaceId, val iconId: String, val label: String, val live: String, val needsYou: Boolean = false)

@Composable
fun PlacesSheet(tiles: List<PlaceTile>, current: DockPlaceId, onPlace: (DockPlaceId) -> Unit, onDismiss: () -> Unit) {
    // Use ModalBottomSheet so Back / scrim / TalkBack focus restore are platform-correct.
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("Places", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp).semantics { heading() })
        val rows = tiles.chunked(2)
        rows.forEachIndexed { r, row ->
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEachIndexed { c, t ->
                    val i = r * 2 + c
                    val vis = remember { MutableTransitionState(false) }.apply { targetState = true }
                    AnimatedVisibility(vis, Modifier.weight(1f),
                        enter = fadeIn(tween(200, i * 40)) + scaleIn(spring(0.62f, 380f), initialScale = .9f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, 1.4f))) {
                        OutlinedCard(onClick = { onPlace(t.id) }, modifier = Modifier.heightIn(min = 96.dp).semantics { selected = t.id == current }) {
                            Column(Modifier.padding(14.dp)) {
                                Zone6Icon(t.iconId); Text(t.label, style = MaterialTheme.typography.titleSmall)
                                Text(t.live, style = MaterialTheme.typography.bodySmall, color = if (t.needsYou) HaloColors.needsYou else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

/* ---------------- edge light (listening / working / needs you) ---------------- */

/** Screen-edge light. Breathes (2.4s); never rotates. Static at 60% under reduced motion. */
@Composable
fun Modifier.haloEdgeLight(state: DockState, reducedMotion: Boolean): Modifier {
    val on = state is DockState.Listening || state is DockState.Working || state is DockState.NeedsYou
    val color = if (state is DockState.NeedsYou) HaloColors.needsYou else MaterialTheme.colorScheme.primary
    val t = rememberInfiniteTransition(label = "edge")
    val a by t.animateFloat(.55f, 1f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a")
    val alpha = if (!on) 0f else if (reducedMotion || state is DockState.NeedsYou) .6f else a
    return this.border(BorderStroke(2.dp, color.copy(alpha = .55f * alpha)), RoundedCornerShape(28.dp))
}

/** Next-step chip trace: breathe, not spin (Motion v2 rule). */
@Composable
fun Modifier.haloTraceBreathe(): Modifier {
    val t = rememberInfiniteTransition(label = "trace")
    val a by t.animateFloat(.35f, 1f, infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "t")
    return this.border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = a), CircleShape)
}

/* ---------------- placeholders bound to existing code ---------------- */
object HaloColors { val needsYou = Color(0xFFFF9F6B); val danger = Color(0xFFFF8FA3) }
@Composable fun Zone6Icon(id: String) { /* = Zone5Icon(id, fallback) from ui/zone5/HaloZone5Presentation.kt */ }
@Composable fun HaloRing(size: Dp) { /* = HaloZone5OrbLauncher ring painter + halo_z6_* / orb Lottie by state */ }
