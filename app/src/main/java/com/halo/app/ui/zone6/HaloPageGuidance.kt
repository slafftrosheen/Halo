package com.halo.app.ui.zone6

import android.content.Context
import android.os.SystemClock
import android.view.accessibility.AccessibilityManager
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.ui.presence.HaloOrbContextAction
import com.halo.app.ui.theme.HaloAccent
import com.halo.app.ui.theme.HaloLookMotion
import com.halo.app.ui.theme.brandAccent
import com.halo.app.ui.theme.rememberReducedMotion
import com.halo.app.ui.theme.rememberVisualPreferences
import com.halo.sdk.provider.RemoteDataCategory
import com.halo.sdk.runtime.Storage

internal enum class HaloGesture(@StringRes val title: Int, @StringRes val instructions: Int) {
    RING_HOLD(R.string.coach_ring_hold, R.string.coach_ring_hold_body),
    DOCK_SWIPE(R.string.coach_dock_swipe, R.string.coach_dock_swipe_body),
    STUDIO_MOVE(R.string.coach_studio_move, R.string.coach_studio_move_body),
    NOTES_DICTATE(R.string.coach_note_dictate, R.string.coach_note_dictate_body),
    CANVAS_LINK(R.string.coach_canvas_link, R.string.coach_canvas_link_body);

    companion object {
        fun forRoute(route: String?): List<HaloGesture> = when (route?.substringBefore('?')?.substringBefore('/')) {
            "home" -> listOf(RING_HOLD, DOCK_SWIPE)
            "studio" -> listOf(STUDIO_MOVE)
            "note" -> listOf(NOTES_DICTATE)
            "canvas" -> listOf(CANVAS_LINK)
            else -> when {
                route?.startsWith("notebook/note/") == true -> listOf(NOTES_DICTATE)
                route?.startsWith("notebook/canvas/") == true -> listOf(CANVAS_LINK)
                else -> emptyList()
            }
        }
    }
}

internal data class CoachControl(val key: String, val label: String, val explanation: String? = null)

/** A guide can highlight or explain a control. Only a registered, current screen can offer an alternative action. */
@Stable
internal class HaloPageGuidance(
    private val storage: Storage,
    private val elapsedMillis: () -> Long = SystemClock::elapsedRealtime,
) {
    data class Pending(val gesture: HaloGesture, val startedAt: Long)
    var showingControls by mutableStateOf(false)
        private set
    var choosingGesture by mutableStateOf(false)
        private set
    var demonstration by mutableStateOf<HaloGesture?>(null)
        private set
    var pending by mutableStateOf<Pending?>(null)
        private set
    var controls by mutableStateOf(emptyList<CoachControl>())
        private set
    var selectedControl by mutableStateOf<String?>(null)
        private set
    var replay by mutableIntStateOf(0)
        private set
    private val pageControls = mutableStateMapOf<String, CoachControl>()
    val visiblePageControls get() = pageControls.values.sortedBy { it.label }
    fun addControl(control: CoachControl) { pageControls[control.key] = control }
    fun removeControl(control: CoachControl) {
        if (pageControls[control.key] == control) pageControls.remove(control.key)
    }
    private val alternatives = mutableStateMapOf<HaloGesture, Pair<Any, () -> Unit>>()
    val open: Boolean get() = showingControls || choosingGesture || demonstration != null

    fun showControls() { dismiss(); showingControls = true }
    fun showGestures() { dismiss(); choosingGesture = true }
    fun demonstrate(gesture: HaloGesture) {
        dismiss()
        demonstration = gesture
        again()
    }

    fun updateControls(next: List<CoachControl>) { controls = next }
    fun explainControl(id: String): Boolean {
        if (!showingControls || controls.none { it.key == id }) return false
        selectedControl = id
        return true
    }

    fun gestureUsed(gesture: HaloGesture) {
        if (!open && pending == null && seen(gesture) < 3)
            pending = Pending(gesture, elapsedMillis())
    }

    fun presentPending(request: Pending) {
        if (pending != request) return
        pending = null
        if (elapsedMillis() - request.startedAt > 30_000L || open || seen(request.gesture) >= 3) return
        runCatching { storage.putString(key(request.gesture), (seen(request.gesture) + 1).toString()) }
        demonstrate(request.gesture)
    }

    fun discardPending() { pending = null }

    fun dismiss() {
        pending = null
        showingControls = false
        selectedControl = null
        choosingGesture = false
        demonstration = null
    }

    fun again() { replay++ }
    fun hasAlternative(gesture: HaloGesture): Boolean = alternatives[gesture] != null
    fun useAlternative(gesture: HaloGesture) {
        val alternative = alternatives[gesture]?.second ?: return
        dismiss()
        alternative()
    }
    fun bind(gesture: HaloGesture, owner: Any, action: () -> Unit) { alternatives[gesture] = owner to action }
    fun unbind(gesture: HaloGesture, owner: Any) {
        if (alternatives[gesture]?.first === owner) alternatives.remove(gesture)
    }

    private fun key(gesture: HaloGesture) = "halo.gesture.count.v1." + gesture.name
    private fun seen(gesture: HaloGesture) = runCatching { storage.getString(key(gesture))?.toIntOrNull() ?: 0 }
        .getOrDefault(3).coerceIn(0, 3)
}

internal val LocalHaloPageGuidance = staticCompositionLocalOf<HaloPageGuidance?> { null }

@Composable
internal fun BindHaloGestureAlternative(gesture: HaloGesture, action: () -> Unit) {
    val guidance = LocalHaloPageGuidance.current ?: return
    val latest by rememberUpdatedState(action)
    DisposableEffect(guidance, gesture) {
        val owner = Any()
        guidance.bind(gesture, owner) { latest() }
        onDispose { guidance.unbind(gesture, owner) }
    }
}

@Composable
internal fun rememberHaloTouchExploration(): Boolean {
    val context = LocalContext.current
    val access = remember(context) { context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager }
    var exploring by remember(access) { mutableStateOf(access?.isTouchExplorationEnabled == true) }
    DisposableEffect(access) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { exploring = it }
        access?.addTouchExplorationStateChangeListener(listener)
        onDispose { access?.removeTouchExplorationStateChangeListener(listener) }
    }
    return exploring
}

@Composable
internal fun Modifier.haloGuideControl(id: String, label: String? = null, explanation: String? = null): Modifier {
    val guidance = LocalHaloPageGuidance.current ?: return this
    if (label != null) {
        DisposableEffect(guidance, id, label, explanation) {
            val control = CoachControl(id, label, explanation)
            guidance.addControl(control)
            onDispose { guidance.removeControl(control) }
        }
    }
    if (!guidance.showingControls || rememberHaloTouchExploration()) return this
    val index = guidance.controls.indexOfFirst { it.key == id }
    if (index < 0) return this
    val dark = MaterialTheme.colorScheme.surface.luminance() < .5f
    val badge = brandAccent(dark, HaloAccent.LILAC)
    val reduced by rememberReducedMotion()
    val level = rememberVisualPreferences().motionLevel
    var visible by remember(id) { mutableStateOf(reduced || level == HaloLookMotion.STILL) }
    LaunchedEffect(id, level, reduced) {
        if (!reduced && level == HaloLookMotion.FULL) kotlinx.coroutines.delay((index * 65L).coerceAtMost(780L))
        visible = true
    }
    val alpha by animateFloatAsState(if (visible) 1f else 0f,
        tween(if (reduced || level == HaloLookMotion.STILL) 0 else 180), label = "control guide")
    val marker = remember(badge.onFill) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = badge.onFill.toArgb()
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
    }
    return pointerInput(guidance, id) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val markerSize = 26.dp.toPx()
            if (down.position.x >= size.width - markerSize && down.position.y in 0f..markerSize) {
                down.consume()
                val up = waitForUpOrCancellation(PointerEventPass.Initial)
                if (up != null) {
                    up.consume()
                    guidance.explainControl(id)
                }
            }
        }
    }.drawWithContent {
        drawContent()
        if (alpha > 0f) {
            val radius = 11.dp.toPx()
            val position = Offset((size.width - radius - 2.dp.toPx()).coerceAtLeast(radius), radius)
            drawCircle(badge.fill.copy(alpha = alpha), radius, position)
            marker.textSize = 12.dp.toPx()
            drawContext.canvas.nativeCanvas.drawText(
                (index + 1).toString(), position.x, position.y - (marker.ascent() + marker.descent()) / 2, marker)
        }
    }
}

@Composable
internal fun Modifier.haloGestureAnchor(gesture: HaloGesture): Modifier {
    val guidance = LocalHaloPageGuidance.current ?: return this
    if (guidance.demonstration != gesture || rememberHaloTouchExploration()) return this
    val tint = brandAccent(MaterialTheme.colorScheme.surface.luminance() < .5f, HaloAccent.LILAC).ink
    val reduced by rememberReducedMotion()
    val motion = rememberVisualPreferences().motionLevel
    var phase by remember(gesture) { mutableFloatStateOf(0f) }
    LaunchedEffect(guidance.replay, reduced, motion) {
        phase = 0f
        if (reduced || motion == HaloLookMotion.STILL) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) {
            phase = ((withFrameNanos { it } - start) % 2_400_000_000L) / 2_400_000_000f
        }
    }
    return drawWithContent {
        drawContent()
        val inset = 2.dp.toPx()
        if (size.width <= inset * 2 || size.height <= inset * 2) return@drawWithContent
        drawRoundRect(tint, Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2),
            CornerRadius(16.dp.toPx()), style = Stroke(3.dp.toPx()))
        val origin = when (gesture) {
            HaloGesture.DOCK_SWIPE -> Offset(size.width * .5f, size.height * .75f)
            HaloGesture.STUDIO_MOVE, HaloGesture.CANVAS_LINK -> Offset(size.width * .28f, size.height * .48f)
            else -> center
        }
        val end = when (gesture) {
            HaloGesture.DOCK_SWIPE -> Offset(size.width * .5f, size.height * .27f)
            HaloGesture.STUDIO_MOVE, HaloGesture.CANVAS_LINK -> Offset(size.width * .70f, size.height * .43f)
            else -> origin
        }
        val travel = if (reduced || motion == HaloLookMotion.STILL) .8f else (phase * 1.6f).coerceIn(0f, 1f)
        val tip = origin + (end - origin) * travel
        if (origin != end) drawLine(tint, origin, end, 2.dp.toPx(),
            cap = StrokeCap.Round, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(7f, 6f)))
        drawCircle(tint, 10.dp.toPx(), tip, style = Stroke(2.dp.toPx()))
        if (!reduced && motion != HaloLookMotion.STILL) {
            val finger = tip + Offset(0f, 15.dp.toPx())
            drawLine(tint, tip, finger, 4.dp.toPx(), cap = StrokeCap.Round)
            drawRoundRect(tint, finger - Offset(4.dp.toPx(), 2.dp.toPx()), Size(13.dp.toPx(), 12.dp.toPx()),
                CornerRadius(5.dp.toPx()), style = Stroke(2.dp.toPx()))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HaloPageCoach(
    route: String?,
    actions: List<HaloOrbContextAction>,
    available: Boolean,
    automatic: Boolean,
    composer: Boolean,
) {
    val guidance = LocalHaloPageGuidance.current ?: return
    val touchExploration = rememberHaloTouchExploration()
    val reduced by rememberReducedMotion()
    val gestures = HaloGesture.forRoute(route)
    val controlDetails = guidance.visiblePageControls
    val controls = buildList {
        add(CoachControl("ring", stringResource(R.string.coach_talk), stringResource(R.string.coach_ring_hold_body)))
        add(CoachControl("places", stringResource(R.string.coach_places), stringResource(R.string.help_tour_places)))
        if (composer) add(CoachControl("composer", stringResource(R.string.help_tour_input_title),
            stringResource(R.string.help_tour_input)))
        if (HaloGesture.NOTES_DICTATE in gestures) add(CoachControl("dictate", com.halo.app.language.uiText("Dictate"),
            stringResource(R.string.coach_note_dictate_body)))
        add(CoachControl("more", stringResource(R.string.coach_page_actions),
            stringResource(R.string.coach_control_hint, stringResource(R.string.coach_page_actions))))
        addAll(controlDetails.take(20))
        actions.filter { it.enabled && !it.id.endsWith("show-me") && it.id != "type-message" }
            .distinctBy { it.id }.take(16).forEach { add(CoachControl(it.id, com.halo.app.language.uiText(it.title))) }
    }
    SideEffect { guidance.updateControls(controls) }
    val pending = guidance.pending
    LaunchedEffect(pending, available, automatic) {
        if (pending != null) {
            if (!automatic) guidance.discardPending()
            else if (available) {
                kotlinx.coroutines.delay(500)
                guidance.presentPending(pending)
            }
        }
    }
    if (!available) return
    if (guidance.showingControls) {
        val title = stringResource(if (touchExploration) R.string.coach_explore else R.string.coach_show)
        val highlighted = controls.firstOrNull { it.key == guidance.selectedControl }
        Zone6ButtonSheet(title, guidance::dismiss, blockWorkingArea = false) {
            Column(Modifier.fillMaxWidth().heightIn(max = 310.dp).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                controls.forEachIndexed { index, control ->
                    TextButton(onClick = { guidance.explainControl(control.key) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(if (touchExploration) control.label else "${index + 1}. ${control.label}")
                    }
                }
                highlighted?.let {
                    Text(it.explanation ?: stringResource(R.string.coach_control_hint, it.label),
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                // Working-area labels may contain a saved item or a device name.
                CompositionLocalProvider(com.halo.app.ui.components.LocalPrivateSpeech provides controlDetails.isNotEmpty()) {
                    com.halo.app.ui.components.HaloReadAloud(controls.joinToString(". ") { it.label },
                        label = stringResource(R.string.coach_read_all), category = RemoteDataCategory.TEXT)
                }
                TextButton(onClick = guidance::dismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.coach_stop_showing))
                }
            }
        }
    } else if (guidance.choosingGesture) {
        Zone6ButtonSheet(stringResource(R.string.coach_watch), guidance::dismiss, blockWorkingArea = false) {
            gestures.forEach { gesture ->
                TextButton(onClick = { guidance.demonstrate(gesture) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(gesture.title))
                }
            }
        }
    } else guidance.demonstration?.let { gesture ->
        val instructions = stringResource(gesture.instructions)
        Zone6ButtonSheet(stringResource(gesture.title), guidance::dismiss, blockWorkingArea = false) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(instructions, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (touchExploration || reduced) com.halo.app.ui.components.HaloReadAloud(instructions,
                        category = RemoteDataCategory.TEXT)
                    else TextButton(onClick = guidance::again, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.coach_again))
                    }
                    if (guidance.hasAlternative(gesture)) TextButton(onClick = { guidance.useAlternative(gesture) },
                        modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.coach_alternative)) }
                    Button(onClick = guidance::dismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.coach_got_it))
                    }
                }
            }
        }
    }
}

/** Child demos are chosen by the learner and only available under the grown-up preference. */
@Composable
internal fun KidsChoiceGestureDemo(onDismiss: () -> Unit) {
    var play by remember { mutableIntStateOf(0) }
    val reduce by rememberReducedMotion()
    val exploration = rememberHaloTouchExploration()
    val motion = !reduce && !exploration && rememberVisualPreferences().motionLevel != HaloLookMotion.STILL
    val tint = MaterialTheme.colorScheme.primary
    val pulse = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(play, motion) {
        if (motion) repeat(2) {
            pulse.snapTo(.65f)
            pulse.animateTo(1.15f, tween(420))
            pulse.animateTo(1f, tween(240))
            kotlinx.coroutines.delay(300)
        }
    }
    val message = stringResource(R.string.coach_kids_demo_body)
    Zone6ButtonSheet(stringResource(R.string.coach_watch), onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            if (!exploration) androidx.compose.foundation.Canvas(Modifier.size(64.dp)) {
                drawCircle(tint, 13.dp.toPx() * pulse.value, style = Stroke(3.dp.toPx()))
                drawCircle(tint, 5.dp.toPx())
            }
            Text(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (motion) TextButton(onClick = { play++ }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.coach_again))
                }
                com.halo.app.ui.components.HaloReadAloud(message, category = RemoteDataCategory.TEXT, compact = true)
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.coach_got_it))
                }
            }
        }
    }
}
