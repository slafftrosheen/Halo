package com.halo.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.ui.shell.AppShell
import com.halo.app.ui.shell.PermissionsScreen
import com.halo.app.ui.theme.BrainToolTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var hasRequiredPermissions by mutableStateOf(false)
    private var uiGraphReady by mutableStateOf(false)
    private lateinit var runtime: HaloRuntime
    private var runtimeLoopStarted = false
    private var uiGraphJob: Job? = null
    private var connectivityJob: Job? = null
    private var hostConnectJob: Job? = null
    private var buddyBotConnectJob: Job? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasRequiredPermissions = areRequiredPermissionsGranted()
        if (hasRequiredPermissions) {
            startUiGraphBootstrap()
            startRuntimeLoop()
        }
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
                    when {
                        !hasRequiredPermissions -> PermissionsScreen(
                            onBack = {},
                            onRequestPermissions = {
                                permissionLauncher.launch(requiredPermissions())
                            }
                        )
                        !uiGraphReady -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                        else -> AppShell()
                    }
                }
            }
        }

        if (hasRequiredPermissions) {
            startUiGraphBootstrap()
            startRuntimeLoop()
        }
    }

    private fun startUiGraphBootstrap() {
        if (uiGraphReady || uiGraphJob?.isActive == true) return

        // Home's ViewModel reaches RuntimeSnapshot/Observer/Engine/Provider graphs.
        // Materialize that dependency chain on IO so AppShell never triggers it
        // synchronously from the Compose main thread.
        uiGraphJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                ManualDependencyGraph.homeViewModel
                withContext(Dispatchers.Main.immediate) {
                    uiGraphReady = true
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                android.util.Log.e("MainActivity", "UI graph bootstrap failed", error)
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
        if (runtimeLoopStarted) return
        runtimeLoopStarted = true

        // Keep host construction, transport observation, and runtime startup off
        // the UI dispatcher. Runtime.start() initializes the cognitive/audio graph
        // and must not be allowed to recreate the startup jank seen in the log.
        connectivityJob = lifecycleScope.launch(Dispatchers.IO) {
            val host = ManualDependencyGraph.wearableHost

            host.connectivity.collect { state ->
                val connected = state is com.halo.sdk.host.ConnectionState.Connected ||
                    state is com.halo.sdk.host.ConnectionState.Ready
                if (connected && !runtime.isStarted()) {
                    // Do not initialize the runtime concurrently with the UI graph.
                    // The UI graph is the first consumer of the same engine/provider
                    // dependency chain; let it finish before starting the runtime.
                    uiGraphJob?.join()
                    if (!runtime.isStarted()) {
                        runtime.start()
                    }
                }
            }
        }

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
        uiGraphJob?.cancel()
        connectivityJob?.cancel()
        hostConnectJob?.cancel()
        buddyBotConnectJob?.cancel()
        lifecycleScope.launch { runtime.stop() }
        super.onDestroy()
    }
}
