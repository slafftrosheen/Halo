package com.halo.app.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PermissionsScreen(onPermissionsGranted: () -> Unit) {
    var hasRequested by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Welcome to HALO", style = MaterialTheme.typography.displayMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Halo requires the following permissions to operate:", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("• Microphone (for voice input)")
            Text("• Bluetooth scan and connection (for wearable discovery)")
            Text("• Location on older Android versions (required by BLE scanning)")

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    hasRequested = true
                    onPermissionsGranted()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Grant Permissions")
            }

            if (hasRequested) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Bluetooth and microphone permissions are required before device discovery can start.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
