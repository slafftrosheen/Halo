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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.halo.app.di.CapabilityRefreshReason
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.ui.shell.AppShell
import com.halo.app.language.uiText
import com.halo.app.ui.presence.HaloSignalFace
import com.halo.app.ui.presence.HaloSignalPresentation
import com.halo.app.ui.presence.HaloSignalState
import com.halo.app.ui.fx.HaloFxAnimation
import com.halo.app.ui.fx.HaloFxMoment
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

open class MainActivity : ComponentActivity() {
    private var compactWindow by mutableStateOf(false)
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
                SideEffect {
                    // Follow Halo's explicit appearance choice, including when it differs from Android.
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !darkTheme
                        isAppearanceLightNavigationBars = !darkTheme
                    }
                }

                val assistantPreferences by ManualDependencyGraph.assistantPreferences.state.collectAsState()
                val palettes by ManualDependencyGraph.palettePreferences.state.collectAsState()
                HaloTheme(darkTheme = darkTheme, fontScale = assistantPreferences.fontScale,
                    palette = if (darkTheme) palettes.dark else palettes.light) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = if (compactWindow) MaterialTheme.shapes.large else androidx.compose.ui.graphics.RectangleShape,
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
                            ) {
                                // Boot is not speech capture, model reasoning or a phone action:
                                // the genuine IDLE open ring breathes while startup is in progress.
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    // Startup animation belongs ONLY to the real Android
                                    // bootstrap interval. It must not replay when a user
                                    // navigates to Home or resumes a completed runtime.
                                    if (!compactWindow) {
                                        HaloFxAnimation(
                                            moment = HaloFxMoment.SPLASH,
                                            modifier = Modifier.size(120.dp),
                                        )
                                    } else {
                                        HaloSignalFace(
                                            presentation = HaloSignalPresentation(
                                                state = HaloSignalState.IDLE,
                                                label = "Getting ready…",
                                            ),
                                            size = 120.dp,
                                            contentDescription = uiText("Getting ready…"),
                                        )
                                    }
                                    Text("Halo", style = MaterialTheme.typography.headlineMedium,
                                        color = MaterialTheme.colorScheme.onBackground)
                                    Text(
                                        uiText(if (!uiGraphReady) "Getting ready…" else "Starting..."),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
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
                                onCompactChanged = ::applyCompactWindow,
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

    private fun applyCompactWindow(requestedCompact: Boolean) {
        val compact = requestedCompact || this is com.halo.app.ui.mini.MiniHaloActivity
        compactWindow = compact
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        if (compact) {
            val metrics = resources.displayMetrics
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.attributes = window.attributes.apply { dimAmount = .2f; gravity = android.view.Gravity.CENTER }
            window.setLayout(minOf((380 * metrics.density).toInt(), (metrics.widthPixels * .94f).toInt()),
                minOf((620 * metrics.density).toInt(), (metrics.heightPixels * .85f).toInt()))
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.attributes = window.attributes.apply { gravity = android.view.Gravity.CENTER }
            window.setLayout(android.view.WindowManager.LayoutParams.MATCH_PARENT, android.view.WindowManager.LayoutParams.MATCH_PARENT)
        }
    }

    private fun handleHaloScreenIntent(intent: Intent?) {
        val screen = intent?.getStringExtra(com.halo.app.ui.shell.HaloScreenCatalog.EXTRA_SCREEN) ?: return
        intent.removeExtra(com.halo.app.ui.shell.HaloScreenCatalog.EXTRA_SCREEN)
        if (screen in com.halo.app.ui.shell.HaloScreenCatalog.screens) {
            if (screen == "mini_halo") {
                com.halo.app.ui.mini.MiniHaloEntry.pendingDraft.value = intent.getStringExtra(com.halo.app.ui.mini.MiniHaloEntry.EXTRA_DRAFT)?.take(2048)
                intent.removeExtra(com.halo.app.ui.mini.MiniHaloEntry.EXTRA_DRAFT)
            }
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
        if (intent.action == Intent.ACTION_PROCESS_TEXT) {
            val selected = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.take(12_000)
            intent.action = Intent.ACTION_SEND
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_TEXT, selected)
            // This entry point copies a selected passage into Inbox; it never rewrites the source app.
        }
        val action = intent.action
        if (action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE) {
            val incoming = Intent(intent)
            // Consume once before asynchronous work: recreation cannot re-import completed items.
            intent.action = null
            android.widget.Toast.makeText(applicationContext, localizedUi("Saving to Inbox…"), android.widget.Toast.LENGTH_LONG).show()
            val activity = java.lang.ref.WeakReference(this)
            val app = applicationContext
            val importer = ManualDependencyGraph.sendIntentHandler
            // An explicit bounded import survives rotation; capture/microphone work never does.
            ManualDependencyGraph.infra.scope.launch(Dispatchers.IO) {
                try {
                    val receipt = importer.importToInbox(incoming)
                    withContext(Dispatchers.Main.immediate) {
                        val current = activity.get()?.takeUnless { it.isDestroyed || it.isFinishing }
                        val localized = android.content.res.Configuration(app.resources.configuration).apply {
                            setLocale(java.util.Locale.forLanguageTag(ManualDependencyGraph.appLanguagePreferences.language.value.tag))
                        }
                        val resources = app.createConfigurationContext(localized)
                        fun label(english: String, vararg args: Any) = resources.getString(requireNotNull(com.halo.app.language.uiCatalogue[english]), *args)
                        android.widget.Toast.makeText(
                            app,
                            label("%1\$d saved to Inbox · %2\$d could not be imported", receipt.objectIds.size, receipt.failed),
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                        val canvasId = ManualDependencyGraph.activeCanvasForCapture.value
                        if (current != null && receipt.objectIds.isNotEmpty()) android.app.AlertDialog.Builder(current)
                            .setTitle(label("Save to Inbox"))
                            .setMessage(label("Saved locally. You can place these same items on a Canvas without copying them again."))
                            .setPositiveButton(label("Save to Inbox")) { _, _ -> }
                            .apply { if (canvasId != null) setNeutralButton(label("Add to current Canvas")) { _, _ ->
                                current.lifecycleScope.launch {
                                    try { ManualDependencyGraph.notebookCanvasRepository.addReferences(canvasId, receipt.objectIds) }
                                    catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                                    catch (_: Exception) { android.widget.Toast.makeText(app, label("Saved to Inbox. Canvas linking failed; add the items from Inbox."), android.widget.Toast.LENGTH_LONG).show() }
                                }
                            } }.show()
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (e: Exception) {
                    Log.e("MainActivity", "Error handling shared intent", e)
                    withContext(Dispatchers.Main.immediate) {
                        val current = activity.get()?.takeUnless { it.isDestroyed || it.isFinishing } ?: return@withContext
                        android.widget.Toast.makeText(
                            app,
                            current.localizedUi("Halo couldn't import the shared content"),
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }
    }

    private fun localizedUi(english: String, vararg arguments: Any): String {
        val locale = java.util.Locale.forLanguageTag(ManualDependencyGraph.appLanguagePreferences.language.value.tag)
        val localized = createConfigurationContext(android.content.res.Configuration(resources.configuration).apply { setLocale(locale) })
        return com.halo.app.language.uiCatalogue[english]?.let { localized.getString(it, *arguments) }
            ?: String.format(locale, english, *arguments)
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
        com.halo.app.ui.mini.HaloHostVisibility.resumed(this)
        com.halo.app.nativeactions.HaloNativeWidget.refreshAsync(applicationContext)
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

    override fun onPause() {
        com.halo.app.ui.mini.HaloHostVisibility.hidden(this)
        super.onPause()
    }

    override fun onDestroy() {
        com.halo.app.ui.mini.HaloHostVisibility.hidden(this)
        // The wear bridge, like HaloRuntime, belongs to the process. Closing Mini
        // Halo must not tear down the full app's active device/session observers.
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
