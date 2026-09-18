package com.halo.app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.ui.shell.AppShell
import com.halo.app.ui.screens.discovery.requiredBluetoothDiscoveryPermissions
import com.halo.app.ui.shell.PermissionsScreen
import com.halo.app.ui.theme.AppearanceMode
import com.halo.app.ui.theme.HaloTheme
import com.halo.app.ui.workspace.HaloWorkspaceButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var hasRequiredPermissions by mutableStateOf(false)
    private var uiGraphReady by mutableStateOf(false)
    private var runtimeReady by mutableStateOf(false)
    private var uiGraphError by mutableStateOf<String?>(null)
    private var runtimeError by mutableStateOf<String?>(null)
    private lateinit var runtime: HaloRuntime
    private var runtimeLoopStarted = false
    private var uiGraphJob: Job? = null
    private var runtimeJob: Job? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasRequiredPermissions = areRequiredPermissionsGranted()
        RuntimeStartupTrace.mark("permissions.result granted=$hasRequiredPermissions")
        if (hasRequiredPermissions) {
            startUiGraphBootstrap()
            startRuntimeLoop()
        }
    }

    private val devicePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        runCatching {
            ManualDependencyGraph.platformStateMonitor.refreshBluetoothState()
            ManualDependencyGraph.pairingViewModel.refreshPrerequisites()
        }.onFailure { error ->
            android.util.Log.w("MainActivity", "Could not refresh pairing permissions", error)
        }
    }

    private val bluetoothEnableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        runCatching {
            ManualDependencyGraph.platformStateMonitor.refreshBluetoothState()
            ManualDependencyGraph.pairingViewModel.refreshPrerequisites()
        }.onFailure { error ->
            android.util.Log.w("MainActivity", "Could not refresh Bluetooth state", error)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val startedAt = android.os.SystemClock.elapsedRealtime()
        RuntimeStartupTrace.mark("activity.onCreate.begin", startedAt)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        RuntimeStartupTrace.mark("activity.graph.init.begin", startedAt)
        ManualDependencyGraph.init(this)
        RuntimeStartupTrace.mark("activity.graph.init.end", startedAt)
        runtime = ManualDependencyGraph.runtime
        RuntimeStartupTrace.mark("activity.runtime.attached", startedAt)
        hasRequiredPermissions = areRequiredPermissionsGranted()
        RuntimeStartupTrace.mark("activity.permissions.checked", startedAt)

        setContent {
            val appearanceMode by ManualDependencyGraph.appearancePreferences.mode.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (appearanceMode) {
                AppearanceMode.SYSTEM -> systemDark
                AppearanceMode.LIGHT -> false
                AppearanceMode.DARK -> true
            }

            HaloTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when {
                        !hasRequiredPermissions -> PermissionsScreen(
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
                                Text("Halo could not initialize the interface.", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    uiGraphError ?: "Unknown startup error",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                                HaloWorkspaceButton(
                                    text = "Retry",
                                    onClick = {
                                        runtimeError = null
                                        startUiGraphBootstrap(force = true)
                                        startRuntimeLoop(force = true)
                                    },
                                    modifier = Modifier.padding(top = 16.dp)
                                )
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
                                Text("Halo runtime could not start.", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    runtimeError ?: "Unknown runtime startup error",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                                HaloWorkspaceButton(
                                    text = "Retry runtime",
                                    onClick = {
                                        runtimeError = null
                                        startRuntimeLoop(force = true)
                                    },
                                    modifier = Modifier.padding(top = 16.dp)
                                )
                            }
                        }
                        !uiGraphReady || !runtimeReady -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }
                        else -> AppShell(
                            onRequestPermissions = {
                                permissionLauncher.launch(requiredPermissions())
                            },
                            onRequestBluetoothPermissions = {
                                devicePermissionLauncher.launch(requiredBluetoothDiscoveryPermissions())
                            },
                            onRequestBluetoothEnable = {
                                bluetoothEnableLauncher.launch(
                                    Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                                )
                            }
                        )
                    }
                }
            }
        }
        RuntimeStartupTrace.mark("activity.setContent.returned", startedAt)

        if (hasRequiredPermissions) {
            startUiGraphBootstrap()
            startRuntimeLoop()
        }
        RuntimeStartupTrace.mark("activity.onCreate.end", startedAt)
    }

    private fun startUiGraphBootstrap(force: Boolean = false) {
        if (!force && (uiGraphReady || uiGraphJob?.isActive == true)) return
        if (force) uiGraphJob?.cancel()

        uiGraphReady = false
        uiGraphError = null
        RuntimeStartupTrace.mark("activity.uiBootstrap.schedule force=$force")

        uiGraphJob = lifecycleScope.launch(Dispatchers.IO) {
            val startedAt = android.os.SystemClock.elapsedRealtime()
            RuntimeStartupTrace.mark("activity.uiBootstrap.begin", startedAt)
            try {
                ManualDependencyGraph.homeViewModel
                RuntimeStartupTrace.mark("activity.uiBootstrap.graphReady", startedAt)
                withContext(Dispatchers.Main.immediate) { uiGraphReady = true }
                RuntimeStartupTrace.mark("activity.uiBootstrap.end", startedAt)
            } catch (error: CancellationException) {
                RuntimeStartupTrace.mark("activity.uiBootstrap.cancelled", startedAt)
                throw error
            } catch (error: Throwable) {
                RuntimeStartupTrace.mark("activity.uiBootstrap.failed", startedAt)
                android.util.Log.e("MainActivity", "UI graph bootstrap failed", error)
                withContext(Dispatchers.Main.immediate) {
                    uiGraphError = error.message ?: error::class.java.simpleName
                }
            }
        }
    }

    /**
     * Phone-first permissions. Bluetooth and nearby-device permissions are requested
     * only when the Devices flow is actually used; they are not startup prerequisites.
     */
    private fun requiredPermissions(): Array<String> = arrayOf(Manifest.permission.RECORD_AUDIO)

    private fun areRequiredPermissionsGranted(): Boolean = requiredPermissions().all { permission ->
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Starts only the Android cognitive runtime. Physical embodiments are optional
     * and must be connected explicitly from the Devices flow.
     */
    private fun startRuntimeLoop(force: Boolean = false) {
        if (!force && runtimeLoopStarted) return
        if (force) runtimeJob?.cancel()

        runtimeLoopStarted = true
        runtimeReady = false
        runtimeError = null
        RuntimeStartupTrace.mark("activity.runtimeLoop.schedule force=$force")

        runtimeJob = lifecycleScope.launch(Dispatchers.IO) {
            val startedAt = android.os.SystemClock.elapsedRealtime()
            RuntimeStartupTrace.mark("activity.runtime.begin", startedAt)
            try {
                uiGraphJob?.join()
                RuntimeStartupTrace.mark("activity.runtime.uiBootstrapJoined", startedAt)
                check(uiGraphReady) { uiGraphError ?: "UI graph bootstrap did not complete successfully" }

                runtime.start()
                ManualDependencyGraph.deviceAutoConnectCoordinator.start()
                withContext(Dispatchers.Main.immediate) {
                    runtimeReady = true
                }
                RuntimeStartupTrace.mark("activity.runtime.ready", startedAt)
            } catch (error: CancellationException) {
                RuntimeStartupTrace.mark("activity.runtime.cancelled", startedAt)
                throw error
            } catch (error: Throwable) {
                RuntimeStartupTrace.mark("activity.runtime.failed", startedAt)
                android.util.Log.e("MainActivity", "Runtime startup failed", error)
                withContext(Dispatchers.Main.immediate) {
                    runtimeReady = false
                    runtimeError = error.message ?: error::class.java.simpleName
                    runtimeLoopStarted = false
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val permissionsNowGranted = areRequiredPermissionsGranted()
        val changed = permissionsNowGranted != hasRequiredPermissions
        hasRequiredPermissions = permissionsNowGranted

        if (permissionsNowGranted) {
            startUiGraphBootstrap()
            startRuntimeLoop()
        }

        if (changed) {
            RuntimeStartupTrace.mark(
                "activity.permissions.onResume granted=$permissionsNowGranted"
            )
        }
    }

    override fun onDestroy() {
        RuntimeStartupTrace.mark("activity.onDestroy.begin changingConfig=$isChangingConfigurations")
        runtimeReady = false
        uiGraphJob?.cancel()
        runtimeJob?.cancel()

        // HaloRuntime belongs to the Android process, not to an Activity. Stopping
        // or disposing it here made rotation race the next Activity and left lazy
        // engine instances permanently disposed. Process death already reclaims
        // platform resources; explicit product shutdown can call runtime.stop().
        super.onDestroy()
        RuntimeStartupTrace.mark("activity.onDestroy.end")
    }
}
