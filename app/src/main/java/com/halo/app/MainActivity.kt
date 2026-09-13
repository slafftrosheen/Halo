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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    private var uiGraphError by mutableStateOf<String?>(null)
    private var runtimeError by mutableStateOf<String?>(null)
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
                        uiGraphError != null -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Text(
                                    text = "Halo could not initialize the interface.",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = uiGraphError ?: "Unknown startup error",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                                Button(
                                    onClick = {
                                        runtimeError = null
                                        startUiGraphBootstrap(force = true)
                                        startRuntimeLoop(force = true)
                                    },
                                    modifier = Modifier.padding(top = 16.dp)
                                ) {
                                    Text("Retry")
                                }
                            }
                        }
                        runtimeError != null -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Text(
                                    text = "Halo runtime could not start.",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = runtimeError ?: "Unknown runtime startup error",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                                Button(
                                    onClick = {
                                        runtimeError = null
                                        startRuntimeLoop(force = true)
                                    },
                                    modifier = Modifier.padding(top = 16.dp)
                                ) {
                                    Text("Retry runtime")
                                }
                            }
                        }
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

    private fun startUiGraphBootstrap(force: Boolean = false) {
        if (!force && (uiGraphReady || uiGraphJob?.isActive == true)) return
        if (force) uiGraphJob?.cancel()

        uiGraphReady = false
        uiGraphError = null

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
                withContext(Dispatchers.Main.immediate) {
                    uiGraphError = error.message ?: error::class.java.simpleName
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

    private fun startRuntimeLoop(force: Boolean = false) {
        if (!force && runtimeLoopStarted) return
        if (force) {
            connectivityJob?.cancel()
            hostConnectJob?.cancel()
            buddyBotConnectJob?.cancel()
        }
        runtimeLoopStarted = true
        runtimeError = null

        connectivityJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Runtime startup is explicitly dependent on successful UI graph bootstrap.
                uiGraphJob?.join()
                check(uiGraphReady) {
                    uiGraphError ?: "UI graph bootstrap did not complete successfully"
                }

                val host = ManualDependencyGraph.wearableHost
                host.connectivity.collect { state ->
                    val connected = state is com.halo.sdk.host.ConnectionState.Connected ||
                        state is com.halo.sdk.host.ConnectionState.Ready
                    if (connected && !runtime.isStarted()) {
                        runtime.start()
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                android.util.Log.e("MainActivity", "Runtime startup loop failed", error)
                withContext(Dispatchers.Main.immediate) {
                    runtimeError = error.message ?: error::class.java.simpleName
                    runtimeLoopStarted = false
                }
            }
        }

        hostConnectJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                ManualDependencyGraph.wearableHost.connect()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                android.util.Log.e("MainActivity", "Wearable host connection failed", error)
            }
        }

        buddyBotConnectJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                val buddyBot = ManualDependencyGraph.buddyBotHost
                buddyBot.ensureDeviceRegistered()
                val device = ManualDependencyGraph.deviceRegistry.getDevice(buddyBot.deviceId)
                if (device?.autoConnect == true) {
                    buddyBot.connect()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                android.util.Log.e("MainActivity", "BuddyBot connection failed", error)
            }
        }
    }

    override fun onDestroy() {
        uiGraphJob?.cancel()
        connectivityJob?.cancel()
        hostConnectJob?.cancel()
        buddyBotConnectJob?.cancel()
        lifecycleScope.launch(Dispatchers.IO) { runtime.stop() }
        super.onDestroy()
    }
}
