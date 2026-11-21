package com.halo.app.ui.guide

/*
 * halo guidance layer (pack 7.2): Show me tags, Watch-then-try coach, one-time tips, “Looking for something?”.
 * Lilac #C9B8FF = help. Reference Compose, not compiled against 98aa2fb.
 * Tags come from each control’s existing contentDescription / label — no second source of truth.
 */

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.app.ui.theme.HaloMotion
import kotlinx.coroutines.delay

val HaloLilac = Color(0xFFC9B8FF)
val HaloLilacInk = Color(0xFF1B1446)

/** Controls register themselves so Show me can name them in reading order. */
class GuideRegistry { val items = mutableStateMapOf<String, Pair<String, Rect>>() }
val LocalGuide = staticCompositionLocalOf { GuideRegistry() }

/** Add to any control: Modifier.guideTag("talk", stringResource(R.string.guide_hold_to_talk)). */
fun Modifier.guideTag(reg: GuideRegistry, id: String, label: String) = onGloballyPositioned { reg.items[id] = label to it.boundsInRoot() }

/** Show me: numbered lilac tags, popping in 450 ms apart (FULL), together (GENTLE/STILL). */
@Composable
fun ShowMeOverlay(reg: GuideRegistry, motion: HaloMotion, onRead: (List<String>) -> Unit) {
    val ordered = reg.items.values.sortedWith(compareBy({ it.second.top }, { it.second.left }))
    ordered.forEachIndexed { i, (label, r) ->
        var shown by remember { mutableStateOf(motion != HaloMotion.FULL) }
        LaunchedEffect(Unit) { if (!shown) { delay(450L * i); shown = true } }
        val a by animateFloatAsState(if (shown) 1f else 0f, if (motion == HaloMotion.STILL) snap() else tween(300), label = "tag")
        Row(Modifier.offset { IntOffset(r.left.toInt(), (r.top - 40).toInt()) }.graphicsLayer { alpha = a; translationY = (1 - a) * 10 }
            .background(HaloLilac, RoundedCornerShape(50)).padding(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 6.dp)) {
            Box(Modifier.size(22.dp).background(HaloLilacInk, CircleShape), contentAlignment = androidx.compose.ui.Alignment.Center) { Text("${i + 1}", color = HaloLilac, fontSize = 12.sp) }
            Spacer(Modifier.width(6.dp)); Text(label, color = HaloLilacInk, fontSize = 13.sp)
        }
    }
    LaunchedEffect(ordered.size) { /* "Read all" in the dock calls onRead(ordered.map { it.first }) */ }
}

/** Gestures that get a hand demo. Each shown at most 3 times; TalkBack → spoken instruction instead. */
enum class CoachGesture(val maxShows: Int = 3) { HOLD_RING, SWIPE_UP_DOCK, DRAG_CARD }
sealed interface CoachAction { data object Again : CoachAction; data object DoItForMe : CoachAction; data object GotIt : CoachAction }

/** One-time tips with fatigue: after 2 “Got it” in a row tips get shorter; “No more tips” = I_KNOW_MY_WAY. */
class TipPolicy(private val seen: MutableSet<String>, private var gotItStreak: Int = 0) {
    fun shouldShow(id: String, inKids: Boolean, duringTask: Boolean) = !inKids && !duringTask && id !in seen
    fun shown(id: String) { seen += id }
    fun gotIt() { gotItStreak++ }
    val short get() = gotItStreak >= 2
}

/**
 * “Looking for something?” — 3 back-and-forths within 60 s without a change, in Settings / Places / Make it yours.
 * Never in Kids, never during a task, max once per 10 min. Offers: Tell halo · Show me around · close.
 */
class StuckDetector(private val now: () -> Long = System::currentTimeMillis) {
    private val hops = ArrayDeque<Long>(); private var lastOffer = 0L
    fun onNavigate(changedSomething: Boolean, inKids: Boolean, duringTask: Boolean): Boolean {
        val t = now(); if (changedSomething || inKids || duringTask) { hops.clear(); return false }
        hops.addLast(t); while (hops.isNotEmpty() && t - hops.first() > 60_000) hops.removeFirst()
        if (hops.size >= 6 /* 3 round trips */ && t - lastOffer > 600_000) { lastOffer = t; hops.clear(); return true }
        return false
    }
}
