package com.halo.app.devtools

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import com.halo.app.ui.theme.HaloTextPrimary
import com.halo.app.ui.components.*

@Composable
fun BlackboardScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HaloSection("ACTIVE GRAPH") {
                BlackboardGraph(
                    nodes = listOf("MediaPlayback", "Launch Spotify", "User wants music", "Audio Context")
                )
            }
        }
        item {
            HaloSection("GOALS") {
                GoalChip(goal = "MediaPlayback", priority = "High")
            }
        }
        item {
            HaloSection("RECENT MEMORIES") {
                MemoryNode(
                    title = "User prefers Spotify",
                    type = "Preference",
                    confidence = 0.95f,
                    timeAgo = "2d ago"
                )
                MemoryNode(
                    title = "Audio processing intent",
                    type = "Short-term",
                    confidence = 0.88f,
                    timeAgo = "12s ago"
                )
            }
        }
    }
}
