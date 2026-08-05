package com.halo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.app.ui.theme.HaloBackground
import com.halo.app.ui.theme.HaloPrimary
import com.halo.app.ui.theme.HaloTextPrimary
import com.halo.app.ui.theme.HaloTextSecondary
import kotlinx.coroutines.delay

@Composable
fun StartupScreen(onBootComplete: () -> Unit) {
    val steps = listOf(
        "Runtime",
        "EventBus",
        "Blackboard",
        "Speech",
        "Memory",
        "Providers",
        "Reasoning"
    )
    
    var currentStep by remember { mutableStateOf(0) }
    var isReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        for (i in steps.indices) {
            delay(150) // Simulate fast progressive load for UI
            currentStep = i + 1
        }
        isReady = true
        delay(500) // Pause on "Ready" before transitioning
        onBootComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HaloBackground)
            .padding(32.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "HALO",
                color = HaloTextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = if (isReady) "Ready" else "Initializing...",
                color = HaloTextSecondary,
                fontSize = 18.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            steps.forEachIndexed { index, step ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (index < currentStep) "✓" else " ",
                        color = HaloPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = step,
                        color = if (index < currentStep) HaloPrimary else if (index == currentStep) HaloTextPrimary else HaloTextSecondary.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
