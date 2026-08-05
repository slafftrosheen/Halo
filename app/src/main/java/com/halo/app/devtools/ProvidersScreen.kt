package com.halo.app.devtools

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.ui.components.*

@Composable
fun ProvidersScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HaloSection("LOCAL INFERENCE") {
                HaloProviderCard(name = "Whisper", status = "Healthy", isOnline = true)
                HaloCard(modifier = Modifier.fillMaxWidth()) {
                    MetricRow("Latency", "12ms")
                    MetricRow("Loaded Model", "tiny.en.tflite")
                    MetricRow("Memory Usage", "45 MB")
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
            HaloSection("EXTERNAL INFERENCE") {
                HaloProviderCard(name = "Ollama", status = "Offline", isOnline = false)
                HaloCard(modifier = Modifier.fillMaxWidth()) {
                    MetricRow("Context Window", "0 / 4096")
                }
            }
        }
    }
}
