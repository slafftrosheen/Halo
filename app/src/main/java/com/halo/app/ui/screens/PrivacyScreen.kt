package com.halo.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.ui.components.HaloDividerLine
import com.halo.app.ui.components.MetricRow
import com.halo.app.ui.components.SectionHeader
import com.halo.app.ui.theme.HaloTextPrimary
import com.halo.app.ui.theme.HaloTextSecondary

@Composable
fun PrivacyScreen() {
    Scaffold { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            contentPadding = PaddingValues(vertical = 24.dp)
        ) {
            item {
                SectionHeader(title = "Privacy")
                HaloDividerLine()
            }
            
            item {
                MetricRow(label = "Microphone", value = "LOCAL")
                HaloDividerLine()
            }
            
            item {
                MetricRow(label = "Memory", value = "LOCAL")
                HaloDividerLine()
            }
            
            item {
                MetricRow(label = "Vision", value = "LOCAL")
                HaloDividerLine()
            }
            
            item {
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Network", style = MaterialTheme.typography.bodyLarge, color = HaloTextPrimary)
                        Text(text = "ALLOWED", style = MaterialTheme.typography.bodyLarge, color = HaloTextSecondary)
                    }
                    Text(text = "Only on request", style = MaterialTheme.typography.labelMedium, color = HaloTextSecondary)
                }
                HaloDividerLine()
            }
            
            item {
                MetricRow(label = "Telemetry", value = "OFF")
                HaloDividerLine()
            }
            
            item {
                MetricRow(label = "Analytics", value = "OFF")
                HaloDividerLine()
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader(title = "Network Log")
                Text(text = "Today", style = MaterialTheme.typography.bodyLarge, color = HaloTextPrimary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "0 requests", style = MaterialTheme.typography.bodyLarge, color = HaloTextSecondary)
            }
        }
    }
}
