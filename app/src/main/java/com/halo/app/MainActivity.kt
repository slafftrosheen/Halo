package com.halo.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.ui.screens.home.HomeScreen
import com.halo.app.ui.screens.PermissionsScreen
import com.halo.app.ui.theme.BrainToolTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var hasRecordAudioPermission by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasRecordAudioPermission = isGranted
        if (isGranted) {
            startAlphaLoop()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check if we already have permission before launching UI
        val currentPermission = checkSelfPermission(Manifest.permission.RECORD_AUDIO)
        hasRecordAudioPermission = currentPermission == android.content.pm.PackageManager.PERMISSION_GRANTED
        
        if (hasRecordAudioPermission) {
            startAlphaLoop()
        }

        setContent {
            BrainToolTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (hasRecordAudioPermission) {
                        // For MVP, we will directly render the HomeScreen
                        HomeScreen(viewModel = ManualDependencyGraph.homeViewModel)
                    } else {
                        PermissionsScreen(
                            onPermissionsGranted = {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        )
                    }
                }
            }
        }
    }

    private fun startAlphaLoop() {
        lifecycleScope.launch {
            // Wake up the cognitive engine 
            ManualDependencyGraph.microkernel.boot()
            
            // Connect to wearable device (e.g. HeyCyan Glasses)
            ManualDependencyGraph.wearableHost.connect()
            
            // Start the physical hardware perception loop
            val audioEngine = ManualDependencyGraph.audioPerceptionEngine
            audioEngine.initialize(ManualDependencyGraph.engineContext)
            audioEngine.start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch {
            ManualDependencyGraph.wearableHost.disconnect()
            ManualDependencyGraph.audioPerceptionEngine.stop()
            ManualDependencyGraph.microkernel.shutdown()
        }
    }
}
