package com.halo.app.ui.screens.kids

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import kotlinx.coroutines.delay

@Composable
internal fun KidsFocusTimer(paused: Boolean) {
    var open by rememberSaveable { mutableStateOf(false) }
    var duration by rememberSaveable { mutableIntStateOf(5) }
    var deadline by rememberSaveable { mutableLongStateOf(0) }
    var seconds by remember { mutableLongStateOf(0) }
    LaunchedEffect(paused, deadline) {
        if (paused) {
            deadline = 0
            open = false
            return@LaunchedEffect
        }
        while (deadline != 0L) {
            val left = deadline - SystemClock.elapsedRealtime()
            if (left !in 1L..(duration * 60_000L)) {
                seconds = 0
                deadline = 0
                break
            }
            seconds = (left + 999) / 1000
            delay(250)
        }
    }
    val running = deadline > 0L && !paused
    val time = stringResource(R.string.halo11_focus_kids_clock, seconds / 60, seconds % 60)
    OutlinedButton({ open = true }, enabled = !paused) {
        Icon(painterResource(R.drawable.halo_ic_timer), null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (running) time else stringResource(R.string.halo11_focus_kids_timer))
    }
    if (open && !paused) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(stringResource(R.string.halo11_focus_kids_timer)) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(136.dp), contentAlignment = Alignment.Center) {
                    val fraction = if (running) (seconds / (duration * 60f)).coerceIn(0f, 1f) else 1f
                    Icon(painterResource(R.drawable.halo_ic_timer), null,
                        Modifier.size((36 + 84 * fraction).dp), tint = MaterialTheme.colorScheme.tertiary)
                }
                if (running) Text(time, style = MaterialTheme.typography.headlineLarge)
                else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (choice in listOf(5, 10, 25)) {
                        FilterChip(duration == choice, { duration = choice },
                            label = { Text(stringResource(R.string.halo11_focus_kids_minutes, choice)) })
                    }
                }
            }
        },
        confirmButton = { Button({
            if (running) { deadline = 0; seconds = 0 }
            else { seconds = duration * 60L; deadline = SystemClock.elapsedRealtime() + seconds * 1000 }
        }) { Text(stringResource(if (running) R.string.halo11_focus_kids_stop else R.string.halo11_focus_kids_start)) } },
        dismissButton = { TextButton({ open = false }) { Text(stringResource(android.R.string.ok)) } },
    )
}
