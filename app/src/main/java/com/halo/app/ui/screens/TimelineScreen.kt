package com.halo.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.app.ui.components.*
import com.halo.app.ui.theme.*

data class CognitiveEvent(
    val timestamp: String,
    val stage: String,
    val description: String,
    val icon: @Composable () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen() {
    // Mock data for visual verification
    val stages = listOf("Speech", "Intent", "Goal", "Plan", "Execution")
    val mockEvents = listOf(
        CognitiveEvent("16:43:10", "Speech", "\"Play Spotify\"") { Icon(HaloIcons.Mic, contentDescription = null, tint = HaloPrimary) },
        CognitiveEvent("16:43:11", "Interpretation", "PlayMusicIntent") { Icon(HaloIcons.Cpu, contentDescription = null, tint = HaloInformation) },
        CognitiveEvent("16:43:11", "Goal", "MediaPlayback") { Icon(HaloIcons.Eye, contentDescription = null, tint = HaloWarning) },
        CognitiveEvent("16:43:12", "Plan", "Launch Spotify") { Icon(HaloIcons.Folder, contentDescription = null, tint = HaloTextSecondary) },
        CognitiveEvent("16:43:13", "Execution", "Success") { Icon(HaloIcons.Terminal, contentDescription = null, tint = HaloPrimary) },
        CognitiveEvent("16:43:14", "Response", "Playing Spotify.") { Icon(HaloIcons.Bell, contentDescription = null, tint = HaloTextSecondary) }
    ).reversed() // Newest at top

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cognitive Timeline", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = HaloBackground,
                    titleContentColor = HaloTextPrimary
                )
            )
        },
        containerColor = HaloBackground
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)) {
            // Pipeline representation at the top
            HaloSection("LIVE PIPELINE") {
                CognitiveFlow(stages = stages, activeStageIndex = 4) // Show almost complete
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            HaloDivider()
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(mockEvents) { event ->
                    HaloTimelineItem(
                        time = event.timestamp,
                        title = event.stage,
                        description = event.description,
                        icon = event.icon
                    )
                }
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Halo is waiting for input...", color = HaloTextSecondary)
                    }
                }
            }
        }
    }
}


