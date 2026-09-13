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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var hasRequiredPermissions by mutableStateOf(false)
    private lateinit var runtime: HaloRuntime
    private var runtimeLoopStarted = false
    private var connectivityJob: Job? = null
    private var hostConnectJob: Job? = null
    private var buddyBotConnectJob: Job? = null

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

        // Compose the first frame before constructing hosts/providers or starting
        // device auto-connect. The startup logs showed BLE/bootstrap work competing
        // with first composition and causing triple-digit skipped-frame bursts.
        setContent {
            BrainToolTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (hasRequiredPermissions) {
                        AppShell()
                    } else {
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

        if (hasRequiredPermissions) startRuntimeLoop()
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
        if (runtimeLoopStarted) return
        runtimeLoopStarted = true

        // Materialize the composition graph and collect transport state from a
        // background dispatcher. The previous version touched wearableHost from
        // the main dispatcher before host.connect() was moved to IO, so the lazy
        // HostGraph/ProviderGraph construction could still block first startup.
        connectivityJob = lifecycleScope.launch(Dispatchers.IO) {
            val host = ManualDependencyGraph.wearableHost

            host.connectivity.collect { state ->
                val connected = state is com.halo.sdk.host.ConnectionState.Connected ||
                    state is com.halo.sdk.host.ConnectionState.Ready
                if (connected && !runtime.isStarted()) {
                    withContext(Dispatchers.Main.immediate) {
                        if (!runtime.isStarted()) {
                            runtime.start()
                        }
                    }
                }
            }
        }

        // Use the same lazily-created host instance as the connectivity observer.
        // Both jobs are background-only; no BLE/bootstrap construction is allowed
        // to happen as a side effect of the UI dispatcher.
        hostConnectJob = lifecycleScope.launch(Dispatchers.IO) {
            ManualDependencyGraph.wearableHost.connect()
        }

        buddyBotConnectJob = lifecycleScope.launch(Dispatchers.IO) {
            val buddyBot = ManualDependencyGraph.buddyBotHost
            buddyBot.ensureDeviceRegistered()
            val device = ManualDependencyGraph.deviceRegistry.getDevice(buddyBot.deviceId)
            if (device?.autoConnect == true) {
                buddyBot.connect()
            }
        }
    }

    override fun onDestroy() {
        connectivityJob?.cancel()
        hostConnectJob?.cancel()
        buddyBotConnectJob?.cancel()
        lifecycleScope.launch { runtime.stop() }
        super.onDestroy()
    }
}
