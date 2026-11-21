package com.halo.app.ui.zone6

/*
 * halo Zone 6 · Final surfaces — REFERENCE implementation (presentation only). Pack 7.0.
 * Boards 22–26. Keeps every existing owner: tool actions stay in the current-entry action lease,
 * source state comes from SourcesViewModel / CollectorAttemptState, sign-in from ChatGptBrowserLogin,
 * Icons use the repo's HaloGlyph/HaloIcon (ui/icons). Line-style glyphs for link/close/more from look/ can replace the stand-ins.
 * online voices from OpenRouterSpeechProvider.state, article text from SavedArticleBody's ArticleFact.
 *
 *   HaloButtonSheet      Format / Insert / Layers / More — rises from its dock button, ends 8dp above the dock
 *   CanvasConnectHint    connect mode banner (+ LinkLabelSheet)
 *   CheckupList          Help & check-up — one row per REAL test; Apricot row only for "needs you"
 *   HaloRingEmptyState   (new body for design/HaloFeedback.kt HaloEmptyState) ring + line icon + what will appear + up to 3 starts
 *   OfflineLine / StaleChip   blue, never red; every internet copy carries its time
 *   DelayedSkeleton      nothing for 400 ms, skeleton after, sentence after 3 s
 *   RealProgressCard     bytes / steps from the real download, pausable
 *   AccountCard          signed out / connected / connected-without-access
 */

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import kotlinx.coroutines.delay

private val Mint = Color(0xFF0CF2BA)
private val Apricot = Color(0xFFFF9F6B)
private val Sky = Color(0xFFB6DFE2)
private val Danger = Color(0xFFFF8A8A)
private val DockHeight = 217.dp          // bottom 25 % halo zone incl. gesture bar (tokens: layout.zoneHeight)
private val SheetGap = 8.dp

/* ---------- 22 · sheets from buttons ---------- */
data class SheetAnchor(val buttonCenterX: Float)          // px, from the dock button's onGloballyPositioned

@Composable
fun HaloButtonSheet(
    visible: Boolean,
    title: String,
    subtitle: String?,
    anchor: SheetAnchor?,
    motion: HaloMotion,                                   // Full / Gentle / Still from HaloLook.kt
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(tween(150)), exit = fadeOut(tween(150))) {
            Box(Modifier.fillMaxSize().padding(bottom = DockHeight).background(Color(0x8C030122))
                .clickable(onClickLabel = "Close", onClick = onDismiss))
        }
        AnimatedVisibility(
            visible, modifier = Modifier.align(Alignment.BottomCenter).padding(start = 10.dp, end = 10.dp, bottom = DockHeight + SheetGap),
            enter = when (motion) {
                HaloMotion.STILL -> fadeIn(tween(150))
                HaloMotion.GENTLE -> fadeIn(tween(200)) + slideInVertically(tween(220)) { it / 8 }
                HaloMotion.FULL -> fadeIn(tween(160)) + scaleIn(spring(0.62f, 380f), 0.92f) + slideInVertically(spring(0.62f, 380f)) { it / 5 }
            },
            exit = fadeOut(tween(120)) + slideOutVertically(tween(160)) { it / 8 },
        ) {
            Surface(shape = RoundedCornerShape(30.dp), tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).semantics { paneTitle = title }) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp)) {
                    Box(Modifier.align(Alignment.CenterHorizontally).size(40.dp, 5.dp).clip(CircleShape).background(Color(0x40FAFAF4)))
                    Row(Modifier.padding(top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.titleMedium)
                            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                        IconButton(onDismiss, Modifier.size(48.dp)) { HaloIcon(HaloGlyph.STOP, "Close") }
                    }
                    content()
                }
            }
        }
    }
}

/** More list row. Destructive rows: last, red text, ellipsis, never filled, always confirm. */
@Composable
fun SheetRow(icon: HaloGlyph, label: String, meta: String? = null, primary: Boolean = false, destructive: Boolean = false, onClick: () -> Unit) {
    val tint = when { destructive -> Danger; primary -> Mint; else -> MaterialTheme.colorScheme.onSurfaceVariant }
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        HaloIcon(icon, null, tint = tint); Spacer(Modifier.width(14.dp))
        Text(if (destructive && !label.endsWith("…")) "$label…" else label, color = if (destructive) Danger else Color.Unspecified, modifier = Modifier.weight(1f))
        meta?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
    }
}

/* ---------- 23 · Canvas connect + Help & check-up ---------- */
@Composable
fun CanvasConnectHint(fromTitle: String) = Row(
    Modifier.fillMaxWidth().padding(14.dp).clip(RoundedCornerShape(18.dp)).background(Mint.copy(alpha = .12f))
        .border(1.dp, Mint.copy(alpha = .4f), RoundedCornerShape(18.dp)).padding(12.dp)
        .semantics { liveRegion = LiveRegionMode.Polite },
    verticalAlignment = Alignment.CenterVertically,
) { HaloIcon(HaloGlyph.INTEGRATIONS, null, tint = Mint); Spacer(Modifier.width(10.dp)); Text("Tap a card to connect it to “$fromTitle”") }

val LinkLabels = listOf("No label", "part of", "leads to", "inspired by", "needs")    // + "Your own"; "No label" first

enum class CheckState { Passed, Running, Waiting, NeedsYou }
/** One row per real test. Never show Passed without a test result behind it. */
data class CheckRow(val title: String, val detail: String, val state: CheckState, val fix: String? = null)

@Composable
fun CheckupList(rows: List<CheckRow>, onFix: (CheckRow) -> Unit) = Column {
    rows.forEach { r ->
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
            val c = if (r.state == CheckState.NeedsYou) Apricot else Mint
            Box(Modifier.size(28.dp).clip(CircleShape).background(c.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                when (r.state) {
                    CheckState.Running -> CircularProgressIndicator(Modifier.size(18.dp), color = Mint, strokeWidth = 2.5.dp)
                    CheckState.Waiting -> HaloIcon(HaloGlyph.NEXT, null, tint = Color(0xFF8E8FB5))
                    CheckState.NeedsYou -> HaloIcon(HaloGlyph.WARNING, null, tint = Apricot)
                    CheckState.Passed -> HaloIcon(HaloGlyph.SUCCESS, null, tint = Mint)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(r.title); Text(r.detail, style = MaterialTheme.typography.bodySmall) }
            if (r.state == CheckState.NeedsYou && r.fix != null) TextButton({ onFix(r) }, Modifier.heightIn(min = 48.dp)) { Text(r.fix) }
        }
    }
}

/* ---------- 24 · empty states ---------- */
data class EmptyStart(val icon: HaloGlyph, val label: String, val onClick: () -> Unit)

@Composable
fun HaloRingEmptyState(icon: HaloGlyph, title: String, whatAppearsHere: String, starts: List<EmptyStart> = emptyList()) {
    require(starts.size <= 3) { "Empty states offer at most 3 starts" }
    Column(Modifier.fillMaxWidth().padding(top = 46.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {   // brand ring, gap at 1–2 o'clock (start 315°, sweep 315°)
                drawArc(Mint.copy(alpha = .55f), startAngle = -45f + 22.5f, sweepAngle = 315f, useCenter = false,
                    style = Stroke(width = size.minDimension * 0.058f, cap = StrokeCap.Round))
            }
            HaloIcon(icon, null, tint = Mint, modifier = Modifier.size(44.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 18.dp))
        Text(whatAppearsHere, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.widthIn(max = 290.dp).padding(top = 6.dp))
        starts.forEach { s -> OutlinedButton(s.onClick, Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 52.dp)) { HaloIcon(s.icon, null); Spacer(Modifier.width(12.dp)); Text(s.label) } }
    }
}

/* ---------- 25 · offline & loading ---------- */
@Composable
fun OfflineLine(text: String = "No internet · halo answers on this phone") = Row(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Sky.copy(alpha = .08f)).border(1.dp, Sky.copy(alpha = .3f), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 10.dp)
        .semantics { liveRegion = LiveRegionMode.Polite },
    verticalAlignment = Alignment.CenterVertically,
) { HaloIcon(HaloGlyph.OFFLINE, null, tint = Sky); Spacer(Modifier.width(10.dp)); Text(text, color = Sky) }

/** Same words as SourcesScreen: "Cached · may be out of date" / "Retrieved %1$s". */
@Composable
fun StaleChip(retrievedAt: String) = AssistChip({}, label = { Text("From $retrievedAt · may be out of date") },
    leadingIcon = { HaloIcon(HaloGlyph.CLOUD, null, tint = Sky) })

@Composable
fun DelayedSkeleton(loading: Boolean, skeleton: @Composable () -> Unit, slowSentence: String = "Still loading — this is taking longer than usual.") {
    var phase by remember(loading) { mutableIntStateOf(0) }
    LaunchedEffect(loading) { if (loading) { delay(400); phase = 1; delay(2600); phase = 2 } }
    if (!loading || phase == 0) return
    Column { skeleton(); if (phase == 2) Text(slowSentence, style = MaterialTheme.typography.bodySmall) }
}

@Composable
fun RealProgressCard(title: String, doneBytes: Long, totalBytes: Long, onPause: () -> Unit) {
    val p = if (totalBytes > 0) doneBytes.toFloat() / totalBytes else 0f
    Card(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp).semantics { progressBarRangeInfo = ProgressBarRangeInfo(p, 0f..1f) }) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text("%.1f of %.1f GB · keep halo open".format(doneBytes / 1e9, totalBytes / 1e9), style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(progress = { p }, Modifier.fillMaxWidth().padding(top = 12.dp), color = Mint)
            TextButton(onPause, Modifier.heightIn(min = 48.dp)) { Text("Pause") }
        }
    }
}

/* ---------- 26 · refreshed features ---------- */
/** Maps ChatGptAccountState(connected, subscriptionAccess, displayName). */
@Composable
fun AccountCard(connected: Boolean, subscriptionAccess: Boolean, displayName: String?) {
    val (title, line, tone) = when {
        !connected -> Triple("No account connected", "Sign in in your browser, then come back to halo.", Color.Unspecified)
        subscriptionAccess -> Triple("Connected · ${displayName ?: "your account"}", "Subscription access granted", Mint)
        else -> Triple("Signed in, no access", "Subscription access wasn’t granted to this account. halo keeps using this phone.", Apricot)
    }
    Card(shape = RoundedCornerShape(22.dp), border = if (tone != Color.Unspecified) BorderStroke(1.dp, tone.copy(alpha = .45f)) else null) {
        Column(Modifier.padding(16.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(line, color = if (tone == Color.Unspecified) Color.Unspecified else tone) }
    }
}
