package com.halo.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.app.ui.glass.components.*
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloTypography

@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HaloGlassColors.Background)
            .padding(24.dp)
    ) {
        // Subtle Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "NOW", 
                style = HaloTypography.titleMedium,
                color = HaloGlassColors.TextSecondary,
                letterSpacing = 2.sp
            )
            
            val connectionText = if (uiState.isDeviceConnected) "Glasses Connected" else "Phone Audio"
            Text(
                text = connectionText,
                style = HaloTypography.labelMedium,
                color = HaloGlassColors.TextMuted
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Primary Action: State Orb
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            HaloFloatingMic(
                state = uiState.runtimeState,
                onClick = { 
                    if (uiState.runtimeState == com.halo.app.ui.glass.material.HaloRuntimeState.Idle) {
                        viewModel.startListening()
                    } else {
                        viewModel.stopListening()
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
        
        // Subtle system status
        Text(
            text = uiState.systemStatus,
            style = HaloTypography.bodyLarge,
            color = HaloGlassColors.TextPrimary,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.weight(1.5f))
    }
}
