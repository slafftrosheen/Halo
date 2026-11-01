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
    com.halo.app.notification.NotificationBridgeOwner,
    com.halo.sdk.provider.RemoteDispatchPolicySource {

    private val resourceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var visibleActivities = 0
    private var backgroundRelease: Job? = null
    private lateinit var storageBootstrap: Deferred<Unit>
    private lateinit var automationBootstrap: Deferred<Unit>
    private val organizerGraphReady = kotlinx.coroutines.CompletableDeferred<Unit>()
    private val organizerRecovery = kotlinx.coroutines.sync.Mutex()
    @Volatile private var organizerRecovered = false
    private val normalStartupStarted = java.util.concurrent.atomic.AtomicBoolean(false)
    suspend fun awaitOrganizerBootstrap() {
        organizerGraphReady.await()
        organizerRecovery.lock()
        try {
            if (organizerRecovered) return
            val repository = ManualDependencyGraph.organizerRepository
            com.halo.app.everyday.organizer.android.OrganizerLockedDelivery.importAfterUnlock(this, repository)
            com.halo.app.everyday.organizer.android.LegacyOrganizerReminders(this, repository).migrate()
            ManualDependencyGraph.organizerDelivery.reconcileAll()
            com.halo.app.everyday.organizer.android.OrganizerScheduleWorker.maintain(this)
            organizerRecovered = true
        } finally { organizerRecovery.unlock() }
    }
    override val notificationBridge get() = ManualDependencyGraph.notificationBridge
    override val remoteDispatchPolicy get() = if (isModelBenchProcess() || !getSystemService(android.os.UserManager::class.java).isUserUnlocked) com.halo.sdk.provider.DenyRemoteDispatchPolicy
        else ManualDependencyGraph.remoteDispatchPolicy
    override suspend fun awaitRemoteDispatchPolicy(): com.halo.sdk.provider.RemoteDispatchPolicy {
        if (isModelBenchProcess() || !getSystemService(android.os.UserManager::class.java).isUserUnlocked) return com.halo.sdk.provider.DenyRemoteDispatchPolicy
        storageBootstrap.await()
        val kids = ManualDependencyGraph.kidsPreferences
        kids.getEnabled()
        return ManualDependencyGraph.remoteDispatchPolicy.also {
            it.reconcileFamilyAuthority(kids.loadedState.value, kids.enabled.value, kids.cloudAllowed.value)
        }
    }
    suspend fun awaitAutomationBootstrap() { automationBootstrap.await() }

    private val governor
        get() = ManualDependencyGraph.providerGraph.nativeResourceCoordinator

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.INFO else Log.WARN)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Native benchmark workers must not bootstrap Halo's normal application graph,
        // migrations, observers, providers or user-facing background jobs.
        if (isModelBenchProcess()) return

        if (!getSystemService(android.os.UserManager::class.java).isUserUnlocked) {
            val receiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
                    if (intent.action == android.content.Intent.ACTION_USER_UNLOCKED) {
                        unregisterReceiver(this)
                        initializeUnlocked()
                    }
                }
            }
            val filter = android.content.IntentFilter(android.content.Intent.ACTION_USER_UNLOCKED)
            if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
            else registerReceiver(receiver, filter)
            // Close the unlock-between-check-and-registration race.
            if (getSystemService(android.os.UserManager::class.java).isUserUnlocked) {
                runCatching { unregisterReceiver(receiver) }
                initializeUnlocked()
            }
            return
        }
        initializeUnlocked()
    }

    private fun initializeUnlocked() {
        if (!normalStartupStarted.compareAndSet(false, true)) return

        val gitCommit = runCatching { BuildConfig::class.java.getField("HALO_GIT_COMMIT").get(null) as? String }.getOrNull() ?: "unknown"
        val gitDirty = runCatching { BuildConfig::class.java.getField("HALO_GIT_DIRTY").get(null) as? Boolean }.getOrNull() ?: false
        val sourceId = runCatching { BuildConfig::class.java.getField("HALO_SOURCE_ID").get(null) as? String }.getOrNull() ?: gitCommit
        val buildLog = "HALO_BUILD versionName=${BuildConfig.VERSION_NAME} versionCode=${BuildConfig.VERSION_CODE} variant=${BuildConfig.BUILD_TYPE} sourceId=$sourceId commit=$gitCommit dirty=$gitDirty"
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
        organizerGraphReady.complete(Unit)
        // Organizer recovery is local and does not wait for model/provider or secret-store bootstrap.
        resourceScope.launch {
            try {
                awaitOrganizerBootstrap()
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (error: Exception) {
                Log.e("OrganizerRecovery", "Organizer recovery needs retry", error)
            }
        }
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
                governor.budgetReevaluation = {
                    kotlinx.coroutines.withContext(Dispatchers.IO) { pressureRecovery.update(source = "WORKLOAD_PROBE") }
                }
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
            awaitOrganizerBootstrap()
            ManualDependencyGraph.agentRunCoordinator.prepareOutstandingRunsForRecovery()
            ManualDependencyGraph.routineRepository.dao.interruptUnfinished()
            ManualDependencyGraph.notificationRepository.reconcilePolicies()
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

    private fun isModelBenchProcess(): Boolean {
        val processName = if (android.os.Build.VERSION.SDK_INT >= 28) {
            getProcessName()
        } else {
            getSystemService(ActivityManager::class.java)?.runningAppProcesses
                ?.firstOrNull { it.pid == android.os.Process.myPid() }?.processName
        }
        return processName == "$packageName:modelbench"
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

    private val pressureRecovery by lazy {
        com.halo.sdk.runtime.NativeMemoryPressureRecovery(resourceScope, sample = {
            val info = ActivityManager.MemoryInfo()
            getSystemService(ActivityManager::class.java).getMemoryInfo(info)
            com.halo.sdk.runtime.NativeMemorySample(info.availMem, info.totalMem, info.lowMemory, info.threshold)
        }, publish = { previous, next, info, source ->
            if (previous != next) Log.i("HALO_NATIVE_MEMORY_PRESSURE",
                "previous=$previous new=$next availMb=${info.availableBytes / 1048576} totalMb=${info.totalBytes / 1048576} lowMemory=${info.lowMemory} source=$source")
            governor.updateBudget(NativeResourceBudget(totalBytes = info.totalBytes, availableBytes = info.availableBytes,
                pressure = next, idleGraceMillis = com.halo.sdk.runtime.MobileNativeComputePolicy.idleGraceMillis(info.totalBytes),
                allowConcurrentHeavyweights = com.halo.sdk.runtime.MobileNativeComputePolicy.allowConcurrentHeavyweights(
                    info.totalBytes, info.availableBytes, next)))
        })
    }

    private fun refreshBudget(pressure: NativeMemoryPressure? = null) {
        if (!::storageBootstrap.isInitialized) return
        resourceScope.launch {
            if (runCatching { storageBootstrap.await() }.isFailure) return@launch
            pressureRecovery.update(pressure, if (pressure == null) "LIFECYCLE" else "MEMORY_CALLBACK")
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
        refreshBudget()
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
            awaitOrganizerBootstrap()
            com.halo.app.everyday.organizer.android.LegacyOrganizerReminders(this@HaloApplication, ManualDependencyGraph.organizerRepository).migrate()
            com.halo.app.everyday.organizer.android.OrganizerScheduleWorker.enqueue(this@HaloApplication, full = true)
        }
    }
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
