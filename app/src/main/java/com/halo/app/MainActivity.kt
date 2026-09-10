package com.halo.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.ui.shell.AppShell
import com.halo.app.ui.shell.PermissionsScreen
import com.halo.app.ui.theme.BrainToolTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var hasRequiredPermissions by mutableStateOf(false)
    private lateinit var runtime: HaloRuntime

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasRequiredPermissions = areRequiredPermissionsGranted()
        if (hasRequiredPermissions) startRuntimeLoop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        ManualDependencyGraph.init(this)
        runtime = HaloRuntime(ManualDependencyGraph)
        hasRequiredPermissions = areRequiredPermissionsGranted()

        if (hasRequiredPermissions) startRuntimeLoop()

        setContent {
            BrainToolTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (hasRequiredPermissions) {
                        AppShell()
                    } else {
                        // One canonical permission surface is used for onboarding.
                        PermissionsScreen(
                            onBack = {},
                            onRequestPermissions = {
                                permissionLauncher.launch(requiredPermissions())
                            }
                        )
                    }
                }
            }
        }
    }

    private fun requiredPermissions(): Array<String> = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    }.toTypedArray()

    private fun areRequiredPermissionsGranted(): Boolean = requiredPermissions().all { permission ->
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun startRuntimeLoop() {
        lifecycleScope.launch {
            val host = ManualDependencyGraph.wearableHost
            host.connectivity.collect { state ->
                val connected = state is com.halo.sdk.host.ConnectionState.Connected ||
                    state is com.halo.sdk.host.ConnectionState.Ready
                if (connected && !runtime.isStarted()) {
                    runtime.start()
                }
            }
        }

        lifecycleScope.launch {
            ManualDependencyGraph.wearableHost.connect()
        }

        lifecycleScope.launch {
            val buddyBot = ManualDependencyGraph.buddyBotHost
            buddyBot.ensureDeviceRegistered()
            val device = ManualDependencyGraph.deviceRegistry.getDevice(buddyBot.deviceId)
            if (device?.autoConnect == true) {
                buddyBot.connect()
            }
        }
    }

    override fun onDestroy() {
        lifecycleScope.launch { runtime.stop() }
        super.onDestroy()
    }
}
