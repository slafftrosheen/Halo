package com.halo.app.ui.screens.notebook

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import com.halo.app.ui.fx.rememberHaloReducedMotion
import kotlinx.coroutines.delay

/** Only a successful board arrangement supplies an origin; ordinary edits snap to their target. */
@Composable
internal fun canvasArrangementPosition(id: String, target: Offset, motion: CanvasArrangementMotion?): Offset {
    val reducedMotion = rememberHaloReducedMotion()
    val origin = motion?.origins?.get(id)?.let { Offset(it.first, it.second) }
    val fresh = motion != null && SystemClock.elapsedRealtime() - motion.startedAt < 2400L
    val position = remember(id) { Animatable(if (fresh && !reducedMotion) origin ?: target else target, Offset.VectorConverter) }
    LaunchedEffect(motion?.startedAt, target, reducedMotion) {
        if (reducedMotion || !fresh || origin == null || origin == target) {
            position.snapTo(target)
        } else {
            position.snapTo(origin)
            val index = motion!!.origins.keys.indexOf(id).coerceIn(0, 8)
            delay(index * 100L)
            position.animateTo(target, tween(900, easing = CubicBezierEasing(.2f, 0f, 0f, 1f)))
        }
    }
    return if (reducedMotion || !fresh || origin == null) target else position.value
}

/** New group frames settle once after their cards arrive; no pulse on existing groups. */
@Composable
internal fun canvasArrangementGroupScale(id: String, motion: CanvasArrangementMotion?): Float {
    val reduced = rememberHaloReducedMotion()
    val scale = remember(id) { Animatable(1f) }
    LaunchedEffect(motion?.startedAt, reduced) {
        scale.snapTo(1f)
        if (!reduced && motion != null && id in motion.newGroupIds && SystemClock.elapsedRealtime() - motion.startedAt < 2400L) {
            val arrival = 900L + (motion.origins.size - 1).coerceIn(0, 8) * 100L
            delay((arrival - (SystemClock.elapsedRealtime() - motion.startedAt)).coerceAtLeast(0L))
            scale.snapTo(.96f)
            scale.animateTo(1.02f, tween(220, easing = CubicBezierEasing(.2f, 0f, 0f, 1f)))
            scale.animateTo(1f, tween(250))
        }
    }
    return if (reduced || motion == null) 1f else scale.value
}
