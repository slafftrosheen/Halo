package com.halo.app

import android.app.*
import android.os.Bundle
import android.content.ComponentCallbacks2
import com.halo.app.di.ManualDependencyGraph
import com.halo.sdk.runtime.*
import kotlinx.coroutines.*

class HaloApplication : Application(), Application.ActivityLifecycleCallbacks {
    private val resourceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var visibleActivities = 0
    private var backgroundRelease: Job? = null
    private val governor get() = ManualDependencyGraph.providerGraph.nativeResourceCoordinator
    override fun onCreate() {
        super.onCreate()
        ManualDependencyGraph.init(this)
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
