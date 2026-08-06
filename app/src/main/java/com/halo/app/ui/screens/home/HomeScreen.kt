package com.halo.app.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.app.ui.components.*
import com.halo.app.ui.theme.*

@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Header
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
                Text(
                    text = if (uiState.isRuntimeReady) "Runtime Ready" else "Initializing",
                    color = HaloTextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                HaloFloatingMic(
                    state = uiState.runtimeState,
                    onClick = { viewModel.triggerMicrophone() },
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Current Runtime Status
        HaloCard {
            HaloSection(title = "Current Runtime Status") {
                HaloStatus("Speech", if (uiState.isSpeechReady) "Ready" else "Offline", uiState.isSpeechReady)
                HaloStatus("Reasoning", if (uiState.isReasoningReady) "Ready" else "Offline", uiState.isReasoningReady)
                HaloStatus("Planning", if (uiState.isPlanningReady) "Ready" else "Offline", uiState.isPlanningReady)
                HaloStatus("Execution", if (uiState.isExecutionReady) "Ready" else "Offline", uiState.isExecutionReady)
                
                Spacer(modifier = Modifier.height(8.dp))
                
                HaloStatus("Provider", uiState.activeProviderId ?: "None Selected", uiState.activeProviderId != null)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Primary Action
        HaloFloatingMic(
            state = uiState.runtimeState,
            onClick = { viewModel.triggerMicrophone() },
            modifier = Modifier.size(120.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Tap to speak",
            color = HaloTextSecondary,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.weight(1f))

        // Recent Activity
        HaloCard {
            HaloSection(title = "Recent Activity") {
                HaloMetric("Last command", uiState.lastCommand)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            HaloMetric("Battery", uiState.batteryLevel)
            HaloMetric("Network", uiState.networkState)
            HaloMetric("Model", uiState.activeProviderId?.take(8) ?: "Offline")
        }
    }
}
