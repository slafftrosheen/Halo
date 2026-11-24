package com.halo.app.ui.everyday

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.language.uiText
import com.halo.app.ui.planner.v13.Slot
import com.halo.app.ui.theme.v13.Halo13Animation
import com.halo.app.ui.theme.v13.Halo13Motion
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlannerFindTimeSheet(
    state: OrganizerPageState,
    locale: Locale,
    onFind: (Long, Int) -> Unit,
    onSelect: (String, Slot) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var minutes by rememberSaveable { mutableLongStateOf(60) }
    var days by rememberSaveable { mutableIntStateOf(7) }
    var requested by rememberSaveable { mutableStateOf<Pair<Long, Int>?>(null) }
    val dates = remember(locale) { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale) }
    val times = remember(locale) { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale) }
    EverydaySheet({ if (!state.busy) onDismiss() }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.sp13_find_time), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.sp13_find_time_q))
            Text(stringResource(R.string.sp13_free_time_scope), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(title, { title = it.take(200) }, modifier = Modifier.fillMaxWidth(),
                label = { Text(uiText("Title")) }, singleLine = true, enabled = !state.busy)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15L, 30L, 60L, 90L, 120L, 240L).forEach {
                    FilterChip(minutes == it, { minutes = it }, enabled = !state.busy,
                        label = { Text(stringResource(R.string.sp13_slot_minutes, it)) })
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1 to R.string.sp13_planner_today, 3 to R.string.sp13_three_days, 7 to R.string.sp13_next_week).forEach { (count, label) ->
                    FilterChip(days == count, { days = count }, enabled = !state.busy, label = { Text(stringResource(label)) })
                }
            }
            Button({ requested = minutes to days; onFind(minutes, days) }, enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.sp13_find_time))
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { EverydayMessage(it, true) }
            val slots = state.freeSlots.takeIf { requested == (minutes to days) && !state.busy && state.error == null }
            if (slots != null) {
                if (slots.isEmpty()) {
                    Text(stringResource(R.string.sp13_no_slots))
                } else {
                    Halo13Animation(Halo13Motion.FIND_TIME, Modifier.fillMaxWidth().height(72.dp))
                    Text(stringResource(R.string.sp13_review_slot))
                    slots.forEach { slot ->
                        OutlinedButton({ onSelect(title, slot) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text("${dates.format(slot.start)}–${times.format(slot.end)}")
                        }
                    }
                }
            }
            TextButton(onDismiss, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(uiText("Cancel"))
            }
        }
    }
}
