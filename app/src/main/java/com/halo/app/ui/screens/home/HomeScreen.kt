package com.halo.app.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import com.halo.app.ui.components.*
import com.halo.app.ui.theme.*

@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Top Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "HALO",
                    color = HaloTextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Small status chip/dot
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                color = if (uiState.isRuntimeReady) HaloPrimary else HaloTextSecondary,
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (uiState.isRuntimeReady) "Ready" else "Starting",
                        color = HaloTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Greeting Section
        item {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    text = uiState.greeting,
                    color = HaloTextPrimary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = uiState.systemStatus,
                    color = HaloTextSecondary,
                    fontSize = 16.sp
                )
            }
        }

        // Hero Microphone Element
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                HaloFloatingMic(
                    state = uiState.runtimeState,
                    onClick = { 
                        if (uiState.runtimeState == HaloRuntimeState.Idle) {
                            viewModel.startListening()
                        } else {
                            viewModel.stopListening()
                        }
                    },
                    modifier = Modifier.size(120.dp)
                )
            }
        }

        // Timeline Preview
        item {
            HaloCard {
                HaloSection(title = "Recent Timeline") {
                    if (uiState.recentEvents.isEmpty()) {
                        Text(
                            text = "No recent activity.",
                            color = HaloTextSecondary,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            uiState.recentEvents.forEach { event ->
                                TimelinePreviewItem(event)
                            }
                        }
                    }
                }
            }
        }

        // Runtime Summary
        item {
            HaloCard {
                HaloSection(title = "Runtime Summary") {
                    uiState.engines.forEach { engine ->
                        HaloStatus(
                            label = engine.id,
                            statusText = engine.state,
                            isHealthy = engine.isHealthy
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    HaloMetric("Provider", uiState.activeProvider)
                }
            }
        }
    }
}

@Composable
fun TimelinePreviewItem(event: TimelinePreviewEvent) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = event.command, color = HaloTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            color = if (event.status.contains("Success")) HaloPrimary else HaloDanger,
                            shape = CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = event.status, color = HaloTextSecondary, fontSize = 14.sp)
            }
        }
        Text(text = event.timestamp, color = HaloTextSecondary, fontSize = 12.sp)
    }
}
