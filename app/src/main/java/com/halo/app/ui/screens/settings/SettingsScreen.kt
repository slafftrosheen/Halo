package com.halo.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
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
fun SettingsScreen(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "SETTINGS",
            color = HaloTextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                HaloCard {
                    HaloSection("Speech") {
                        SettingRow("Wake Word", "Halo")
                        SettingRow("Sensitivity", "Medium")
                    }
                }
            }
            item {
                HaloCard {
                    HaloSection("Providers") {
                        SettingRow("Whisper", "Local (Online)")
                        SettingRow("Ollama/LlamaCpp", "Local")
                    }
                }
            }
            item {
                HaloCard {
                    HaloSection("Developer") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Developer Mode", color = HaloTextPrimary, fontSize = 16.sp)
                            Switch(
                                checked = uiState.developerModeEnabled,
                                onCheckedChange = { viewModel.toggleDeveloperMode() }
                            )
                        }
                    }
                }
            }
            item {
                HaloCard {
                    HaloSection("About") {
                        SettingRow("Version", "Alpha v0.1")
                        SettingRow("License", "Apache 2.0")
                    }
                }
            }
        }
    }
}

@Composable
fun SettingRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = HaloTextPrimary, fontSize = 16.sp)
        Text(text = value, color = HaloTextSecondary, fontSize = 14.sp)
    }
}
