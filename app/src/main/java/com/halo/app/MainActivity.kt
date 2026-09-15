package com.halo.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.halo.app.ui.glass.components.HaloGlassButton
import com.halo.app.ui.glass.material.HaloTypography
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

    override fun onCreate(savedInstanceState: Bundle?) {
        val startedAt = android.os.SystemClock.elapsedRealtime()
        RuntimeStartupTrace.mark("activity.onCreate.begin", startedAt)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        RuntimeStartupTrace.mark("activity.graph.init.begin", startedAt)
        ManualDependencyGraph.init(this)
        RuntimeStartupTrace.mark("activity.graph.init.end", startedAt)
        runtime = HaloRuntime(ManualDependencyGraph)
        RuntimeStartupTrace.mark("activity.runtime.constructed", startedAt)
        hasRequiredPermissions = areRequiredPermissionsGranted()
        RuntimeStartupTrace.mark("activity.permissions.checked", startedAt)

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
                                Text("Halo could not initialize the interface.", style = HaloTypography.titleMedium)
                                Text(
                                    uiGraphError ?: "Unknown startup error",
                                    style = HaloTypography.bodyMedium,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                                HaloGlassButton(
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
                                Text("Halo runtime could not start.", style = HaloTypography.titleMedium)
                                Text(
                                    runtimeError ?: "Unknown runtime startup error",
                                    style = HaloTypography.bodyMedium,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                                HaloGlassButton(
                                    text = "Retry runtime",
                                    onClick = {
                                        runtimeError = null
                                        startRuntimeLoop(force = true)
                                    },
                                    modifier = Modifier.padding(top = 16.dp)
                                )
                            }
                        }
                        !uiGraphReady -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }
                        else -> AppShell()
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
        runtimeError = null
        RuntimeStartupTrace.mark("activity.runtimeLoop.schedule force=$force")

        runtimeJob = lifecycleScope.launch(Dispatchers.IO) {
            val startedAt = android.os.SystemClock.elapsedRealtime()
            RuntimeStartupTrace.mark("activity.runtime.begin", startedAt)
            try {
                uiGraphJob?.join()
                RuntimeStartupTrace.mark("activity.runtime.uiBootstrapJoined", startedAt)
                check(uiGraphReady) { uiGraphError ?: "UI graph bootstrap did not complete successfully" }

                if (!runtime.isStarted()) {
                    RuntimeStartupTrace.mark("activity.runtime.start.begin", startedAt)
                    runtime.start()
                    RuntimeStartupTrace.mark("activity.runtime.start.end", startedAt)
                }
                RuntimeStartupTrace.mark("activity.runtime.ready", startedAt)
            } catch (error: CancellationException) {
                RuntimeStartupTrace.mark("activity.runtime.cancelled", startedAt)
                throw error
            } catch (error: Throwable) {
                RuntimeStartupTrace.mark("activity.runtime.failed", startedAt)
                android.util.Log.e("MainActivity", "Runtime startup failed", error)
                withContext(Dispatchers.Main.immediate) {
                    runtimeError = error.message ?: error::class.java.simpleName
                    runtimeLoopStarted = false
                }
            }
        }
    }

    override fun onDestroy() {
        RuntimeStartupTrace.mark("activity.onDestroy.begin")
        uiGraphJob?.cancel()
        runtimeJob?.cancel()
        ManualDependencyGraph.infra.scope.launch(Dispatchers.IO) {
            RuntimeStartupTrace.mark("activity.runtime.stop.begin")
            runtime.stop()
            RuntimeStartupTrace.mark("activity.runtime.stop.end")
        }
        super.onDestroy()
        RuntimeStartupTrace.mark("activity.onDestroy.end")
    }
}
