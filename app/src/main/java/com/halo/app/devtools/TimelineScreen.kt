package com.halo.app.devtools
import com.halo.sdk.runtime.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.ui.theme.HaloTextPrimary
import com.halo.app.ui.theme.HaloTextSecondary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TimelineScreen() {
    val eventBus = ManualDependencyGraph.eventBus as? RecordingEventBus
    val history by eventBus?.history?.collectAsState(initial = emptyList()) ?: collectAsState(emptyList())

    val formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(history.reversed()) { event ->
            val timestampStr = formatter.format(Instant.ofEpochMilli(event.createdAt))
            Column {
                Text(
                    text = timestampStr,
                    color = HaloTextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
                Text(
                    text = event.event.javaClass.simpleName,
                    color = HaloTextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                )
                Text(
                    text = "────────────",
                    color = HaloTextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// Temporary empty collectAsState replacement for when RecordingEventBus is null
@Composable
fun <T> collectAsState(initial: T): androidx.compose.runtime.State<T> {
    return androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(initial) }
}
