package com.halo.app.ui.adaptive

import android.content.Context
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.ui.adaptive.v13.*
import java.time.LocalTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

internal object AdaptiveInputState {
    val kind = MutableStateFlow(InputKind.FINGER)

    fun touch(event: MotionEvent) {
        if (event.actionMasked == MotionEvent.ACTION_UP &&
            event.getToolType(event.actionIndex) in listOf(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_ERASER)
        ) kind.value = InputKind.PEN
    }

    fun key(event: KeyEvent) {
        val device = event.device ?: return
        if (event.action == KeyEvent.ACTION_DOWN && !device.isVirtual &&
            device.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC
        ) kind.value = InputKind.KEYBOARD
    }
}

private data class Directive(val id: String, val trigger: String, val operation: String, val target: String)

private data class SurfaceDefinition(val anchors: Set<Anchor>, val order: List<String>, val rules: List<Directive>)

private object AdaptiveSurfaceCatalogue {
    @Volatile private var definitions: Map<String, SurfaceDefinition>? = null

    fun find(context: Context, id: String): SurfaceDefinition? =
        (definitions ?: synchronized(this) { definitions ?: load(context).also { definitions = it } })[id]

    private fun load(context: Context): Map<String, SurfaceDefinition> {
        val surfaces = context.assets.open("adaptive/v13/surfaces-13.json").bufferedReader().use {
            JSONObject(it.readText()).getJSONObject("surfaces")
        }
        return surfaces.keys().asSequence().associateWith { surface ->
            val value = surfaces.getJSONObject(surface)
            val anchors = value.getJSONArray("anchors")
            val order = listOf("simple", "standard", "pro").flatMap { level ->
                val entries = value.getJSONArray(level)
                List(entries.length()) { entries.getString(it).removePrefix("+") }
            }
            val rules = value.getJSONArray("rules")
            SurfaceDefinition(List(anchors.length()) { Anchor.valueOf(anchors.getString(it)) }.toSet(), order,
                List(rules.length()) { index ->
                    val item = rules.getJSONArray(index)
                    val trigger = item.getString(0)
                    val target = item.getString(2)
                    Directive("$trigger:$target", trigger, item.getString(1), target)
                })
        }
    }
}

internal data class AdaptiveSurfaceLayout(
    val result: ResolvedSurface,
    val controls: Boolean,
    val fixed: Boolean,
    val keep: () -> Unit,
    val restore: () -> Unit,
    val stop: () -> Unit,
)

@Composable
internal fun rememberAdaptiveSurfaceLayout(
    surface: String,
    blocks: List<Block>,
    aliases: Map<String, String>,
    level: ExperienceLevel,
    locked: Boolean,
    hour: Int? = null,
    pins: Pins = Pins(),
    availableTargets: Set<String> = blocks.mapTo(mutableSetOf(), Block::id),
): AdaptiveSurfaceLayout {
    val context = LocalContext.current.applicationContext
    val definition = remember(context, surface) { AdaptiveSurfaceCatalogue.find(context, surface) }
    val prefs = remember(context) { context.getSharedPreferences("halo.surface.layout.v13", Context.MODE_PRIVATE) }
    var kept by remember(surface, prefs) { mutableStateOf(prefs.getStringSet("$surface.kept", null).orEmpty().toSet()) }
    var stopped by remember(surface, prefs) { mutableStateOf(prefs.getBoolean("$surface.stopped", false)) }
    var reset by remember(surface) { mutableStateOf(false) }
    val input by AdaptiveInputState.kind.collectAsStateWithLifecycle()
    val clockHour by produceState(LocalTime.now().hour, hour, definition) {
        if (hour == null && definition?.rules?.any { it.trigger == "EVENING" || it.trigger == "EARLY|MORNING" } == true) {
            while (true) {
                value = LocalTime.now().hour
                delay(30_000)
            }
        }
    }
    val form = if (LocalConfiguration.current.smallestScreenWidthDp >= 600) FormFactor.TABLET else FormFactor.PHONE
    val signals = Signals(when (hour ?: clockHour) {
        in 4..6 -> DayPart.EARLY
        in 7..11 -> DayPart.MORNING
        in 12..17 -> DayPart.AFTERNOON
        in 18..22 -> DayPart.EVENING
        else -> DayPart.NIGHT
    }, input, form, situation = null, level = level)
    val rules = definition?.rules.orEmpty().mapNotNull { directive ->
        val id = aliases[directive.target]?.takeIf { it in availableTargets } ?: return@mapNotNull null
        val (condition, reason) = when (directive.trigger) {
            "EARLY|MORNING" -> Condition.Parts(setOf(DayPart.EARLY, DayPart.MORNING)) to R.string.f13_why_morning
            "EVENING" -> Condition.Part(DayPart.EVENING) to when (surface) {
                "today" -> R.string.f13_layout_tomorrow_today
                "planner" -> R.string.f13_layout_tomorrow_planner
                else -> R.string.f13_layout_review
            }
            "PEN" -> Condition.Input(InputKind.PEN) to if (surface == "notes") R.string.f13_layout_note_pen else R.string.f13_why_pen
            "KEYBOARD" -> Condition.Input(InputKind.KEYBOARD) to R.string.f13_layout_keyboard
            "TABLET" -> Condition.Form(FormFactor.TABLET) to R.string.f13_layout_tablet_canvas
            else -> return@mapNotNull null
        }
        val change = when (directive.operation) {
            "promote" -> Change.Promote(id)
            "make_primary" -> Change.MakePrimary(id)
            "reveal" -> Change.Reveal(id)
            "tuck" -> Change.Tuck(id)
            else -> return@mapNotNull null
        }
        val keepKey = "${directive.id}|$id"
        if (kept.isNotEmpty() && keepKey !in kept) return@mapNotNull null
        SurfaceRule(directive.id, if (keepKey in kept) emptySet() else setOf(condition), change, reason)
    }
    val order = definition?.order.orEmpty().mapNotNull(aliases::get).distinct().withIndex().associate { (index, id) -> id to index }
    val ordered = if (locked) blocks else blocks.sortedBy { order[it.id] ?: Int.MAX_VALUE }
    val spec = SurfaceSpec(surface, definition?.anchors.orEmpty(), ordered, rules)
    val result = SurfaceResolver.resolve(spec, signals, pins.copy(frozen = locked || pins.frozen || stopped || reset))
    val fixed = kept.isNotEmpty() || stopped || reset
    return AdaptiveSurfaceLayout(result, !locked && (result.applied.isNotEmpty() || fixed), fixed,
        keep = {
            kept = result.applied.map { "${it.id}|${it.change.blockId}" }.toSet()
            stopped = false
            reset = false
            prefs.edit().putStringSet("$surface.kept", kept).remove("$surface.stopped").apply()
        }, restore = {
            val wasFixed = fixed
            kept = emptySet()
            stopped = false
            reset = !wasFixed
            prefs.edit().remove("$surface.kept").remove("$surface.stopped").apply()
        }, stop = {
            kept = emptySet()
            stopped = true
            reset = false
            prefs.edit().remove("$surface.kept").putBoolean("$surface.stopped", true).apply()
        })
}

@Composable
internal fun AdaptiveSurfaceControl(layout: AdaptiveSurfaceLayout) {
    if (!layout.controls) return
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.f13_why_title)) }
    if (open) AlertDialog(onDismissRequest = { open = false },
        title = { Text(stringResource(R.string.f13_why_title)) },
        text = {
            Column {
                if (layout.fixed) Text(stringResource(R.string.f13_layout_fixed))
                else layout.result.applied.forEach { Text(stringResource(it.reason)) }
                if (!layout.fixed) {
                    TextButton(onClick = { layout.keep(); open = false }) { Text(stringResource(R.string.f13_pin)) }
                    TextButton(onClick = { layout.restore(); open = false }) { Text(stringResource(R.string.f13_reset)) }
                }
            }
        },
        confirmButton = {
            if (layout.fixed) TextButton(onClick = { layout.restore(); open = false }) { Text(stringResource(R.string.f13_layout_resume)) }
            else TextButton(onClick = { layout.stop(); open = false }) { Text(stringResource(R.string.f13_stop_changing)) }
        }, dismissButton = {
            TextButton(onClick = { open = false }) { Text(stringResource(android.R.string.cancel)) }
        })
}
