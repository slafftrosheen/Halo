package com.halo.app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
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
import com.halo.app.di.CapabilityRefreshReason
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.ui.shell.AppShell
import com.halo.app.ui.screens.discovery.requiredBluetoothDiscoveryPermissions
import com.halo.app.ui.theme.AppearanceMode
import com.halo.app.ui.theme.HaloTheme
import com.halo.app.ui.workspace.HaloWorkspaceButton
import com.halo.sdk.runtime.HostInvocation
import com.halo.sdk.runtime.InvocationSource
import com.halo.sdk.runtime.skills.HostPermission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MainActivity : ComponentActivity() {
    private var uiGraphReady by mutableStateOf(false)
    private var runtimeReady by mutableStateOf(false)
    private var uiGraphError by mutableStateOf<String?>(null)
    private var runtimeError by mutableStateOf<String?>(null)
    private lateinit var runtime: HaloRuntime
    private var runtimeLoopStarted = false
    private var fullyDrawnReported = false
    private var uiGraphJob: Job? = null
    private var runtimeJob: Job? = null
    private var activityAssistJob: Job? = null
    private var activityAssistInteractionId: String? = null
    private val pendingSkillSpecialAccess = java.util.ArrayDeque<HostPermission>()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            ManualDependencyGraph.platformStateMonitor.refreshMicrophonePermission()
            refreshSkillPermissionTruth()
            RuntimeStartupTrace.mark(
                "permissions.microphone.result granted=" +
                    ManualDependencyGraph.platformStateMonitor.microphonePermissionGranted.value
            )
        }
    }

    private val skillPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            refreshSkillPermissionTruth()
            withContext(Dispatchers.Main.immediate) { launchNextSkillSpecialAccess() }
        }
    }

    private val skillSpecialAccessLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            refreshSkillPermissionTruth()
            withContext(Dispatchers.Main.immediate) { launchNextSkillSpecialAccess() }
        }
    }

    private val devicePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                ManualDependencyGraph.platformStateMonitor.refreshBluetoothState()
                ManualDependencyGraph.pairingViewModel.refreshPrerequisites()
                ManualDependencyGraph.runtimeDeviceCapabilityBridge.updateDeviceCapabilities()
            }.onFailure { error ->
                android.util.Log.w("MainActivity", "Could not refresh pairing permissions", error)
            }
        }
    }

    private val bluetoothEnableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                ManualDependencyGraph.platformStateMonitor.refreshBluetoothState()
                ManualDependencyGraph.pairingViewModel.refreshPrerequisites()
                ManualDependencyGraph.runtimeDeviceCapabilityBridge.updateDeviceCapabilities()
            }.onFailure { error ->
                android.util.Log.w("MainActivity", "Could not refresh Bluetooth state", error)
            }
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
        RuntimeStartupTrace.mark("activity.permissions.deferred", startedAt)

        setContent {
            com.halo.app.language.AppLanguageProvider(ManualDependencyGraph.appLanguagePreferences) {
                val appearanceMode by ManualDependencyGraph.appearancePreferences.mode.collectAsState()
                val systemDark = isSystemInDarkTheme()
                val darkTheme = when (appearanceMode) {
                    AppearanceMode.SYSTEM -> systemDark
                    AppearanceMode.LIGHT -> false
                    AppearanceMode.DARK -> true
                }

                val assistantPreferences by ManualDependencyGraph.assistantPreferences.state.collectAsState()
                HaloTheme(darkTheme = darkTheme, fontScale = assistantPreferences.fontScale) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        when {
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
                                    permissionLauncher.launch(microphonePermissions())
                                },
                                onRequestBluetoothPermissions = {
                                    devicePermissionLauncher.launch(requiredBluetoothDiscoveryPermissions())
                                },
                                onRequestBluetoothEnable = {
                                    bluetoothEnableLauncher.launch(
                                        Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                                    )
                                },
                                onRequestSkillPermissions = ::requestSkillPermissions,
                            )
                        }
                    }
                }
            }
        }
        RuntimeStartupTrace.mark("activity.setContent.returned", startedAt)

        startUiGraphBootstrap()
        startRuntimeLoop()
        handleHaloScreenIntent(intent)
        handleIncomingShareIntent(intent)
        handleAssistantIntent(intent)
        RuntimeStartupTrace.mark("activity.onCreate.end", startedAt)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleHaloScreenIntent(intent)
        handleIncomingShareIntent(intent)
        handleAssistantIntent(intent)
    }

    private fun handleHaloScreenIntent(intent: Intent?) {
        val screen = intent?.getStringExtra(com.halo.app.ui.shell.HaloScreenCatalog.EXTRA_SCREEN) ?: return
        intent.removeExtra(com.halo.app.ui.shell.HaloScreenCatalog.EXTRA_SCREEN)
        if (screen in com.halo.app.ui.shell.HaloScreenCatalog.screens) {
            ManualDependencyGraph.explicitHaloScreenRequested = true
            ManualDependencyGraph.requestedHaloScreen.value = screen
        }
    }

    /** OEM fallback for devices that route the default-assistant contract through an activity. */
    private fun handleAssistantIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_ASSIST) return
        intent.action = null

        val interactionId = UUID.randomUUID().toString()
        activityAssistInteractionId = interactionId
        activityAssistJob?.cancel()
        activityAssistJob = lifecycleScope.launch {
            val coordinator = ManualDependencyGraph.assistantInvocationCoordinator
            try {
                val inputDeviceId = intent.getIntExtra(Intent.EXTRA_ASSIST_INPUT_DEVICE_ID, -1)
                    .takeIf { it >= 0 }
                coordinator.begin(
                    HostInvocation(
                        interactionId = interactionId,
                        source = InvocationSource.ANDROID_ASSISTANT,
                        invokedAtMs = System.currentTimeMillis(),
                        inputDeviceId = inputDeviceId,
                        requestedAssistContext = false,
                        requestedScreenshot = false,
                    )
                )
                coordinator.startListening(interactionId, inputDeviceId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Log.e("MainActivity", "Activity assistant fallback failed", error)
                coordinator.cancelAssistantInteraction(interactionId, "startup_failed")
                if (activityAssistInteractionId == interactionId) activityAssistInteractionId = null
            }
        }
    }

    private fun handleIncomingShareIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        if (action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val imported = ManualDependencyGraph.sendIntentHandler.handleIntent(intent)
                    withContext(Dispatchers.Main.immediate) {
                        if (this@MainActivity.intent === intent) this@MainActivity.intent.action = null
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            if (imported > 0) "$imported item${if (imported == 1) "" else "s"} saved to Notebook"
                            else "Nothing supported was shared",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error handling shared intent", e)
                    withContext(Dispatchers.Main.immediate) {
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            "Halo couldn't import the shared content",
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }
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
                (application as? HaloApplication)?.awaitStorageBootstrap()
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

    private fun requestSkillPermissions(
        declaredPermissions: Set<HostPermission>,
        toolIds: Set<String>,
    ) {
        val checker = ManualDependencyGraph.skillPermissionChecker
        val requested = checker.accessPlan(declaredPermissions, toolIds)
            .filterNot(checker::granted)
        pendingSkillSpecialAccess.clear()

        val runtimePermissions = linkedSetOf<String>()
        requested.forEach { permission ->
            val normal = checker.runtimePermissions(permission)
            if (normal.isNotEmpty()) {
                normal.filterTo(runtimePermissions) {
                    checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
                }
            } else if (checker.specialAccessIntent(permission) != null) {
                pendingSkillSpecialAccess.addLast(permission)
            }
        }

        if (runtimePermissions.isNotEmpty()) {
            skillPermissionLauncher.launch(runtimePermissions.toTypedArray())
        } else {
            launchNextSkillSpecialAccess()
        }
    }

    private fun launchNextSkillSpecialAccess() {
        val checker = ManualDependencyGraph.skillPermissionChecker
        while (pendingSkillSpecialAccess.isNotEmpty()) {
            val permission = pendingSkillSpecialAccess.removeFirst()
            if (checker.granted(permission)) continue
            val intent = checker.specialAccessIntent(permission) ?: continue
            skillSpecialAccessLauncher.launch(intent)
            return
        }
        lifecycleScope.launch(Dispatchers.IO) { refreshSkillPermissionTruth() }
    }

    private suspend fun refreshSkillPermissionTruth() {
        ManualDependencyGraph.skillRegistry.refreshAvailability()
        val publisher = ManualDependencyGraph.hostGraph.capabilityPublisher
        publisher.refresh(CapabilityRefreshReason.PERMISSION_RESULT)
        publisher.publishInto(ManualDependencyGraph.capabilityCatalogue)
        runCatching { ManualDependencyGraph.platformStateMonitor.refreshMicrophonePermission() }
        runCatching { ManualDependencyGraph.platformStateMonitor.refreshBluetoothState() }
    }

    /**
     * Microphone access is capability-scoped, not an application startup prerequisite.
     * Bluetooth/nearby permissions are likewise requested only from the Devices flow.
     */
    private fun microphonePermissions(): Array<String> =
        arrayOf(Manifest.permission.RECORD_AUDIO)

    /**
     * Starts the Android cognitive runtime. Physical embodiments remain optional:
     * only saved devices with autoConnect=true may be constructed/retried by the
     * process-level DeviceAutoConnectCoordinator.
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
                // RUNTIME-019: (re)build surface projections from durable state.
                ManualDependencyGraph.interactionSurfaceCoordinator.start()
                ManualDependencyGraph.interactionSurfaceCoordinator.rebuild()
                ManualDependencyGraph.wearEndpointBridge.start()
                withContext(Dispatchers.Main.immediate) {
                    runtimeReady = true
                    if (!fullyDrawnReported) {
                        fullyDrawnReported = true
                        reportFullyDrawn()
                    }
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
        lifecycleScope.launch(Dispatchers.IO) {
            ManualDependencyGraph.platformStateMonitor.refreshMicrophonePermission()
            lifecycleScope.launch { ManualDependencyGraph.hostGraph.capabilityPublisher.refresh(
                CapabilityRefreshReason.PERMISSION_RESULT) }
            ManualDependencyGraph.hostGraph.capabilityPublisher.publishInto(ManualDependencyGraph.capabilityCatalogue)
            ManualDependencyGraph.runtimeDeviceCapabilityBridge.updateDeviceCapabilities()
        }
        startUiGraphBootstrap()
        startRuntimeLoop()
    }

    override fun onDestroy() {
        ManualDependencyGraph.wearEndpointBridge.stop()
        RuntimeStartupTrace.mark("activity.onDestroy.begin changingConfig=$isChangingConfigurations")
        runtimeReady = false
        uiGraphJob?.cancel()
        runtimeJob?.cancel()
        activityAssistJob?.cancel()
        if (!isChangingConfigurations) {
            activityAssistInteractionId?.let { interactionId ->
                ManualDependencyGraph.infra.scope.launch {
                    ManualDependencyGraph.assistantInvocationCoordinator
                        .cancelAssistantInteraction(interactionId, "activity_destroyed")
                }
            }
        }
        activityAssistInteractionId = null

        // HaloRuntime belongs to the Android process, not to an Activity. Stopping
        // or disposing it here made rotation race the next Activity and left lazy
        // engine instances permanently disposed. Process death already reclaims
        // platform resources; explicit product shutdown can call runtime.stop().
        super.onDestroy()
        RuntimeStartupTrace.mark("activity.onDestroy.end")
    }
}
