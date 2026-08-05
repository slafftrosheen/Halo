package com.halo.app.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PermissionsScreen(onPermissionsGranted: () -> Unit) {
    var hasRequested by remember { mutableStateOf(false) }
    
    val permissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.BLUETOOTH_CONNECT
    )
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // In a real app we'd check if all required permissions are granted.
        // For Alpha, we just continue after asking.
        onPermissionsGranted()
    }
    
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
            Text("• Camera (for flashlight control)")
            Text("• Location (for navigation)")
            Text("• Bluetooth (for wearables)")
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = {
                    hasRequested = true
                    launcher.launch(permissions)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Grant Permissions")
            }
            
            if (hasRequested) {
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(
                    onClick = onPermissionsGranted,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Continue anyway (Some features will be degraded)")
                }
            }
        }
    }
}
