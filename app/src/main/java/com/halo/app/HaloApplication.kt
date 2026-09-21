package com.halo.app

import android.app.*
import android.os.Bundle
import android.os.StrictMode
import android.content.ComponentCallbacks2
import com.halo.app.backup.BrainBackupScheduler
import com.halo.app.di.ManualDependencyGraph
import com.halo.sdk.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class HaloApplication : Application(), Application.ActivityLifecycleCallbacks {
    private val resourceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var visibleActivities = 0
    private var backgroundRelease: Job? = null
    private val governor get() = ManualDependencyGraph.providerGraph.nativeResourceCoordinator
    override fun onCreate() {
        super.onCreate()
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
        val layout = com.halo.app.storage.haloStorageLayout(this)
        kotlinx.coroutines.runBlocking(Dispatchers.IO) {
            com.halo.app.storage.LocalAssetStorageMigration(this@HaloApplication, layout).migrate()
            com.halo.app.security.SecretMigration(
                this@HaloApplication,
                com.halo.app.security.AndroidKeystoreSecretStore(layout),
            ).migrate()
        }
        ManualDependencyGraph.init(this)
        resourceScope.launch {
            val schedule = runCatching {
                com.halo.app.backup.AutomaticBackupSchedule.valueOf(
                    ManualDependencyGraph.settingsRepository.settings.first().automaticBackup
                )
            }.getOrDefault(com.halo.app.backup.AutomaticBackupSchedule.OFF)
            runCatching {
                BrainBackupScheduler.schedule(this@HaloApplication, schedule)
            }
            ManualDependencyGraph.storageAccessRepository.validatePersistedAccess()
            ManualDependencyGraph.restoreReconciler.reconcileAfterSystemRestore()
        }
        registerActivityLifecycleCallbacks(this)
        refreshBudget(NativeMemoryPressure.NORMAL)
    }
    private fun refreshBudget(pressure: NativeMemoryPressure) {
        val info = ActivityManager.MemoryInfo()
        getSystemService(ActivityManager::class.java).getMemoryInfo(info)
        resourceScope.launch {
            governor.updateBudget(NativeResourceBudget(info.totalMem, info.availMem,
                if (info.lowMemory) NativeMemoryPressure.CRITICAL else pressure,
                allowConcurrentHeavyweights = !info.lowMemory && info.availMem > 3L * 1024 * 1024 * 1024))
        }
    }
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level != ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) refreshBudget(NativeMemoryPressure.LOW)
    }
    override fun onLowMemory() { super.onLowMemory(); refreshBudget(NativeMemoryPressure.CRITICAL) }
    override fun onActivityStarted(activity: Activity) {
        visibleActivities++; backgroundRelease?.cancel(); refreshBudget(NativeMemoryPressure.NORMAL)
    }
    override fun onActivityStopped(activity: Activity) {
        visibleActivities--
        if (visibleActivities == 0) backgroundRelease = resourceScope.launch {
            delay(30_000); governor.releaseIdle("application_backgrounded")
        }
    }
    override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
