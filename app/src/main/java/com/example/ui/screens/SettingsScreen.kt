package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.components.HaloDividerLine
import com.example.ui.components.MetricRow
import com.example.ui.components.SectionHeader
import com.example.ui.theme.HaloAccent
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment

@Composable
fun SettingsScreen(
    onNavigateToPrivacy: () -> Unit,
    onNavigateToAssistant: () -> Unit
) {
    var developerMode by remember { mutableStateOf(false) }
    
    Scaffold { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            contentPadding = PaddingValues(vertical = 24.dp)
        ) {
            item {
                SectionHeader(title = "Settings")
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                SettingRow(title = "AI Providers", onClick = onNavigateToAssistant)
                HaloDividerLine()
                SettingRow(title = "Devices", onClick = { })
                HaloDividerLine()
                SettingRow(title = "Voice", onClick = { })
                HaloDividerLine()
                SettingRow(title = "Privacy", onClick = onNavigateToPrivacy)
                HaloDividerLine()
                SettingRow(title = "Plugins", onClick = { })
                HaloDividerLine()
                SettingRow(title = "Automation", onClick = { })
                HaloDividerLine()
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { developerMode = !developerMode }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MetricRow(label = "Developer Mode", value = if (developerMode) "ON" else "OFF")
                }
            }
            
            if (developerMode) {
                item {
                    HaloDividerLine()
                    SectionHeader(title = "Runtime Status", subtitle = "Healthy")
                    MetricRow(label = "Latency", value = "192 ms")
                    MetricRow(label = "BLE", value = "-61 dBm")
                    SettingRow(title = "Logs", onClick = { })
                    SettingRow(title = "Profiling", onClick = { })
                }
            }
        }
    }
}

@Composable
fun SettingRow(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        MetricRow(label = title, value = ">")
    }
}
