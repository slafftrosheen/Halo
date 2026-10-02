package com.halo.app

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.ComponentCallbacks2
import android.os.Bundle
import android.os.StrictMode
import android.util.Log
import androidx.work.Configuration
import com.halo.app.backup.AutomaticBackupSchedule
import com.halo.app.backup.BrainBackupScheduler
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.security.AndroidKeystoreSecretStore
import com.halo.app.security.SecretMigration
import com.halo.app.security.SecretMigrationV2
import com.halo.app.storage.LocalAssetStorageMigration
import com.halo.app.storage.haloStorageLayout
import com.halo.runtime.cognition.agent.isTerminal
import com.halo.sdk.runtime.NativeMemoryPressure
import com.halo.sdk.runtime.NativeResourceBudget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect

class HaloApplication :
    Application(),
    Application.ActivityLifecycleCallbacks,
    Configuration.Provider,
    com.halo.host.android.device.CompanionPresenceOwner,
    com.halo.app.notification.NotificationBridgeOwner {

    private val resourceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var visibleActivities = 0
    private var backgroundRelease: Job? = null
    private lateinit var storageBootstrap: Deferred<Unit>
    private lateinit var automationBootstrap: Deferred<Unit>
    override val notificationBridge get() = ManualDependencyGraph.notificationBridge
    suspend fun awaitAutomationBootstrap() { automationBootstrap.await() }

    private val governor
        get() = ManualDependencyGraph.providerGraph.nativeResourceCoordinator

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.INFO else Log.WARN)
            .build()

    override fun onCreate() {
        super.onCreate()

        val gitCommit = runCatching { BuildConfig::class.java.getField("HALO_GIT_COMMIT").get(null) as? String }.getOrNull() ?: "unknown"
        val gitDirty = runCatching { BuildConfig::class.java.getField("HALO_GIT_DIRTY").get(null) as? Boolean }.getOrNull() ?: false
        val buildLog = "HALO_BUILD versionName=${BuildConfig.VERSION_NAME} versionCode=${BuildConfig.VERSION_CODE} variant=${BuildConfig.BUILD_TYPE} commit=$gitCommit dirty=$gitDirty"
        Log.i("HaloApplication", buildLog)
        println("[$buildLog]")

        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .detectCustomSlowCalls()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .detectActivityLeaks()
                    .penaltyLog()
                    .build()
            )
        }

        ManualDependencyGraph.init(this)
        storageBootstrap = resourceScope.async {
            val startedAt = android.os.SystemClock.elapsedRealtime()
            RuntimeStartupTrace.mark("application.storageBootstrap.begin", startedAt)
            try {
                val layout = haloStorageLayout(this@HaloApplication)
                layout.ensureDirectories()
                LocalAssetStorageMigration(this@HaloApplication, layout).migrate()
                val secretStore = AndroidKeystoreSecretStore(layout)
                SecretMigration(
                    this@HaloApplication,
                    secretStore,
                ).migrate()
                // Migrate every newer provider credential from legacy/plain storage before the
                // SecretBackedStorage facade begins serving those keys exclusively from Keystore.
                SecretMigrationV2(
                    com.halo.host.android.AndroidStorage(this@HaloApplication),
                    secretStore,
                ).migrate()
                ManualDependencyGraph.providerGraph.reloadPersistedSpeechSettings()
            } finally {
                RuntimeStartupTrace.mark("application.storageBootstrap.end", startedAt)
            }
        }
        resourceScope.launch { storageBootstrap.await(); ManualDependencyGraph.attentionController }
        resourceScope.launch {
            storageBootstrap.await()
            ManualDependencyGraph.devicePresenceRepository.presenceMap.collect { ManualDependencyGraph.attentionContextProvider.refresh() }
        }
        resourceScope.launch {
            storageBootstrap.await()
            ManualDependencyGraph.agentRunCoordinator.activeRuns.collect { ManualDependencyGraph.attentionContextProvider.interacting(it.isNotEmpty()) }
        }
        resourceScope.launch {
            storageBootstrap.await()
            var previous: Map<String, com.halo.runtime.cognition.agent.AgentRunState>? = null
            ManualDependencyGraph.agentRunStore.observeRuns().collect { runs ->
                val prior = previous
                previous = runs.associate { it.id.value to it.state }
                if (prior != null) runs.filter { run ->
                    run.interactionId.value.startsWith("routine:") &&
                    prior[run.id.value] != null && prior[run.id.value] != run.state &&
                        run.state in setOf(com.halo.runtime.cognition.agent.AgentRunState.COMPLETED,
                            com.halo.runtime.cognition.agent.AgentRunState.FAILED,
                            com.halo.runtime.cognition.agent.AgentRunState.INTERRUPTED)
                }.forEach { run ->
                    ManualDependencyGraph.attentionController.submit(com.halo.runtime.cognition.attention.AttentionRequest(
                        id = com.halo.runtime.cognition.attention.AttentionRequestId("run:${run.id.value}:${run.state}"),
                        source = com.halo.runtime.cognition.attention.AttentionSource.AgentCompletion,
                        interactionId = run.interactionId,
                        deliveryIntent = com.halo.runtime.cognition.attention.DeliveryIntent.Inform(
                            com.halo.runtime.cognition.attention.UserFacingContent("Action ${run.state.name.lowercase()}",
                                run.failureReason ?: "Execution recorded in Activity")),
                        deduplicationKey = "run:${run.id.value}",
                        expiresAt = java.time.Instant.now().plusSeconds(300)))
                }
            }
        }
        resourceScope.launch { storageBootstrap.await(); ManualDependencyGraph.devicePresenceBridge }
        // RUNTIME-018: startup classifies outstanding runs; it never auto-executes
        // consequential work. Recoverable runs become SUSPENDED/WAITING_* and are
        // surfaced as resumable; only genuinely uncertain runs are terminalized.
        resourceScope.launch {
            storageBootstrap.await()
            runCatching { ManualDependencyGraph.agentRunCoordinator.prepareOutstandingRunsForRecovery() }
                .onFailure { Log.w("HaloApplication", "Agent recovery classification failed", it) }
        }



        resourceScope.launch {
            try {
                storageBootstrap.await()

                val schedule = runCatching {
                    AutomaticBackupSchedule.valueOf(
                        ManualDependencyGraph.settingsRepository.settings.first().automaticBackup
                    )
                }.getOrDefault(AutomaticBackupSchedule.OFF)

                runCatching {
                    BrainBackupScheduler.schedule(this@HaloApplication, schedule)
                }.onFailure { error ->
                    Log.w("HaloApplication", "Could not reconcile automatic backup schedule", error)
                }

                ManualDependencyGraph.storageAccessRepository.validatePersistedAccess()
                ManualDependencyGraph.restoreReconciler.reconcileAfterSystemRestore()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Log.e("HaloApplication", "Background storage bootstrap failed", error)
            }
        }

        automationBootstrap = resourceScope.async {
            storageBootstrap.await()
            ManualDependencyGraph.agentRunCoordinator.prepareOutstandingRunsForRecovery()
            ManualDependencyGraph.routineRepository.dao.interruptUnfinished()
            ManualDependencyGraph.notificationRepository.reconcilePolicies()
            com.halo.host.android.reminder.ReminderRecordStore(this@HaloApplication).reconcile()
        }
        resourceScope.launch {
            try {
                automationBootstrap.await()
                ManualDependencyGraph.routineScheduler.reconcile()
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { Log.e("HaloApplication", "Routine reconciliation failed", e) }
        }
        resourceScope.launch {
            try {
                automationBootstrap.await()
                ManualDependencyGraph.automationRuntime.observe()
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { Log.e("HaloApplication", "Routine presence observer failed", e) }
        }
        resourceScope.launch {
            try {
                automationBootstrap.await()
                ManualDependencyGraph.continuationWatchRuntime.observe()
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { Log.e("HaloApplication", "Continuation watch runtime observer failed", e) }
        }
        resourceScope.launch {
            ManualDependencyGraph.agentRunStore.observeRuns().collect { runs ->
                runs.filter { it.state.isTerminal }.forEach { ManualDependencyGraph.interactionWorkingMemory.discard(it.id.value) }
            }
        }
        resourceScope.launch {
            try { automationBootstrap.await(); ManualDependencyGraph.reflectionCoordinator.observe() }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { Log.w("HaloApplication", "Reflection observer unavailable") }
        }
        registerActivityLifecycleCallbacks(this)
    }

    override fun onCompanionPresence(address: String, appeared: Boolean) {
        ManualDependencyGraph.devicePresenceBridge.companionPresence(address, appeared)
    }

    suspend fun awaitStorageBootstrap() {
        storageBootstrap.await()
    }

    override fun onTerminate() {
        unregisterActivityLifecycleCallbacks(this)
        resourceScope.cancel()
        super.onTerminate()
    }

    private fun refreshBudget(pressure: NativeMemoryPressure) {
        if (!::storageBootstrap.isInitialized) return
        resourceScope.launch {
            if (runCatching { storageBootstrap.await() }.isFailure) return@launch
            val info = ActivityManager.MemoryInfo()
            getSystemService(ActivityManager::class.java).getMemoryInfo(info)
            governor.updateBudget(
                NativeResourceBudget(
                    totalBytes = info.totalMem,
                    availableBytes = info.availMem,
                    pressure = if (info.lowMemory) NativeMemoryPressure.CRITICAL else pressure,
                    allowConcurrentHeavyweights =
                        !info.lowMemory && info.availMem > 3L * 1024 * 1024 * 1024
                )
            )
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level != ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            refreshBudget(NativeMemoryPressure.LOW)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        refreshBudget(NativeMemoryPressure.CRITICAL)
    }

    override fun onActivityStarted(activity: Activity) {
        visibleActivities++
        ManualDependencyGraph.attentionContextProvider.foreground(true)
        backgroundRelease?.cancel()
        refreshBudget(NativeMemoryPressure.NORMAL)
    }

    override fun onActivityStopped(activity: Activity) {
        visibleActivities--
        if (visibleActivities == 0) {
            ManualDependencyGraph.attentionContextProvider.foreground(false)
            backgroundRelease = resourceScope.launch {
                delay(30_000)
                if (runCatching { storageBootstrap.await() }.isSuccess) {
                    governor.releaseIdle("application_backgrounded")
                }
            }
        }
    }

    override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) {
        resourceScope.launch {
            notificationBridge.refresh()
            com.halo.host.android.reminder.ReminderRecordStore(this@HaloApplication).reconcile()
        }
    }
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
