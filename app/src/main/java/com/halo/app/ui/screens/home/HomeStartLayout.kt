package com.halo.app.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.ui.design.HaloShapes
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

internal data class HomeWeather(val place: String, val temperatureC: Double)

@Composable
internal fun homeHeadingEdit(onEdit: () -> Unit): Modifier {
    val invoke by rememberUpdatedState(onEdit)
    val label = stringResource(R.string.home_start_edit)
    return Modifier.pointerInput(Unit) {
        detectTapGestures(onLongPress = { invoke() })
    }.semantics { onLongClick(label) { invoke(); true } }
}

@Composable
internal fun HomeGreetingLine(greeting: HomeGreeting, day: LocalDate, weather: HomeWeather?) {
    if (greeting == HomeGreeting.OFF) return
    val locale = LocalConfiguration.current.locales[0] ?: java.util.Locale.getDefault()
    val dayLabel = day.format(DateTimeFormatter.ofPattern(
        android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEEdMMM"), locale))
    val label = if (greeting == HomeGreeting.WEATHER && weather != null) {
        stringResource(R.string.home_start_day_with_weather, dayLabel, weather.place, weather.temperatureC.roundToInt())
    } else dayLabel
    Text(label, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
internal fun HomeStartButtons(
    layout: HomeStartLayout,
    enabled: (HomeStart) -> Boolean,
    onOpen: (HomeStart) -> Unit,
) {
    val easy = com.halo.app.ui.theme.rememberVisualPreferences().viewMode == com.halo.app.ui.theme.HaloViewMode.EASY
    layout.starts.forEach { start ->
        Surface(onClick = { onOpen(start) }, enabled = enabled(start),
            modifier = Modifier.fillMaxWidth().heightIn(min = if (easy) 64.dp else 56.dp),
            shape = HaloShapes.card, color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HaloIcon(start.glyph, null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(start.label), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                HaloIcon(HaloGlyph.NEXT, null, Modifier.size(18.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun HomeStartEditor(
    layout: HomeStartLayout,
    saved: HomeStartLayout,
    previous: HomeStartLayout?,
    day: LocalDate,
    weather: HomeWeather?,
    saving: Boolean,
    error: Boolean,
    onChange: (HomeStartLayout) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    val currentLayout by rememberUpdatedState(layout)
    val changeLayout by rememberUpdatedState(onChange)
    fun move(start: HomeStart, by: Int) = changeLayout(currentLayout.move(start, by))
    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.home_start_title), style = MaterialTheme.typography.headlineSmall)
            Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.home_start_instructions), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                layout.starts.forEachIndexed { index, start -> key(start) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        HaloIcon(start.glyph, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(start.label), Modifier.weight(1f).padding(start = 8.dp),
                            style = MaterialTheme.typography.bodyLarge)
                        IconButton(enabled = !saving && index > 0, onClick = { move(start, -1) }) {
                            HaloIcon(HaloGlyph.NEXT, stringResource(R.string.home_start_move_up), Modifier.rotate(-90f))
                        }
                        IconButton(enabled = !saving && index < layout.starts.lastIndex, onClick = { move(start, 1) }) {
                            HaloIcon(HaloGlyph.NEXT, stringResource(R.string.home_start_move_down), Modifier.rotate(90f))
                        }
                        IconButton(enabled = !saving, onClick = { onChange(layout.copy(starts = layout.starts - start)) }) {
                            HaloIcon(HaloGlyph.TRASH, stringResource(R.string.home_start_remove, stringResource(start.label)))
                        }
                        if (!saving && layout.starts.size > 1) HomeStartDragHandle(start, ::move)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                } }
                if (layout.starts.size < HomeStartLayout.MAX_STARTS) {
                    Text(stringResource(R.string.home_start_add), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HomeStart.entries.filterNot { it in layout.starts }.forEach { start ->
                            FilterChip(selected = false, enabled = !saving,
                                onClick = { onChange(layout.copy(starts = layout.starts + start)) },
                                label = { Text(stringResource(start.label)) },
                                modifier = Modifier.heightIn(min = 48.dp))
                        }
                    }
                }
                Text(stringResource(R.string.home_start_greeting), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HomeGreeting.entries.forEach { option ->
                        FilterChip(selected = layout.greeting == option, enabled = !saving,
                            onClick = { onChange(layout.copy(greeting = option)) },
                            label = { Text(stringResource(option.label)) },
                            modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
                HomeGreetingLine(layout.greeting, day, weather)
                if (previous != null && previous != layout) {
                    TextButton(onClick = { onChange(previous) }, enabled = !saving,
                        modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.home_start_restore)) }
                }
                if (error) Text(stringResource(R.string.home_start_save_error),
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error)
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onChange(saved) }, enabled = !saving && layout != saved,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.home_start_undo))
                }
                Button(onClick = onApply, enabled = !saving, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.home_start_done))
                }
            }
        }
    }
}

@Composable
private fun HomeStartDragHandle(start: HomeStart, onMove: (HomeStart, Int) -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val move by rememberUpdatedState(onMove)
    Canvas(Modifier.size(32.dp, 48.dp).pointerInput(start) {
        val rowHeight = 56.dp.toPx()
        var offset = 0f
        detectVerticalDragGestures(onDragStart = { offset = 0f }) { change, delta ->
            change.consume()
            offset += delta
            if (offset <= -rowHeight) {
                move(start, -1)
                offset += rowHeight
            } else if (offset >= rowHeight) {
                move(start, 1)
                offset -= rowHeight
            }
        }
    }) {
        val step = 5.dp.toPx()
        for (column in 0..1) for (row in -1..1) {
            drawCircle(ink, 1.4.dp.toPx(), Offset(center.x + (column - .5f) * step, center.y + row * step))
        }
    }
}
