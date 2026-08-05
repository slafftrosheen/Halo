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
fun AssistantScreen() {
    Scaffold { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            contentPadding = PaddingValues(vertical = 24.dp)
        ) {
            item {
                SectionHeader(title = "AI Engine")
                HaloDividerLine()
            }
            
            item {
                Text(text = "Current Provider", style = MaterialTheme.typography.labelMedium, color = HaloTextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "llama.cpp", style = MaterialTheme.typography.titleLarge, color = HaloTextPrimary)
                Text(text = "Local", style = MaterialTheme.typography.bodyLarge, color = HaloTextSecondary)
                HaloDividerLine()
            }
            
            item {
                Text(text = "Providers", style = MaterialTheme.typography.titleLarge, color = HaloTextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                MetricRow(label = "llama.cpp", value = "SELECTED")
                MetricRow(label = "Ollama", value = "")
                MetricRow(label = "Gemini", value = "")
                MetricRow(label = "OpenAI", value = "")
                MetricRow(label = "Claude", value = "")
                HaloDividerLine()
            }
            
            item {
                Text(text = "Model", style = MaterialTheme.typography.titleLarge, color = HaloTextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                MetricRow(label = "Gemma", value = "")
                MetricRow(label = "Llama", value = "SELECTED")
                MetricRow(label = "Qwen", value = "")
                MetricRow(label = "Phi", value = "")
                MetricRow(label = "Mistral", value = "")
            }
        }
    }
}
