package com.halo.host.android.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.SystemClock
import com.halo.sdk.runtime.ScreenContextSnapshot
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicLong

class HaloAccessibilityService : AccessibilityService() {
    private val snapshotFormatter = AccessibilitySnapshotFormatter()
    @Volatile internal var latestSemanticSnapshotId: String? = null
        private set
    @Volatile internal var latestSemanticSnapshotAtMs: Long? = null
        private set
    @Volatile var latestSemanticSnapshot: ScreenContextSnapshot? = null
        private set
    private val uiRevision = AtomicLong(0L)
    @Volatile private var lastUiChangeElapsedMs: Long = 0L

    companion object {
        var instance: HaloAccessibilityService? = null
            private set

        val isEnabled: Boolean get() = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Track change timing only. The semantic tree is still captured on demand.
        uiRevision.incrementAndGet()
        lastUiChangeElapsedMs = SystemClock.elapsedRealtime()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    fun captureSnapshot(): ScreenContextSnapshot? {
        val root = rootInActiveWindow ?: return null
        val packageName = root.packageName?.toString()

        if (AccessibilitySafetyPolicy.isPackageBlocked(packageName)) {
            root.recycle()
            return null
        }

        val metrics = resources.displayMetrics
        return try {
            snapshotFormatter.format(root, packageName, metrics.widthPixels, metrics.heightPixels).also {
                latestSemanticSnapshotId = it.snapshotId
                latestSemanticSnapshotAtMs = it.capturedAtMs
                latestSemanticSnapshot = it
            }
        } finally {
            root.recycle()
        }
    }

    internal fun markActionDispatched() {
        uiRevision.incrementAndGet()
        lastUiChangeElapsedMs = SystemClock.elapsedRealtime()
    }

    suspend fun captureSettledSnapshot(
        quietWindowMs: Long = 180L,
        deadlineMs: Long = 1_500L,
    ): ScreenContextSnapshot? {
        val started = SystemClock.elapsedRealtime()
        var observedRevision = uiRevision.get()
        while (SystemClock.elapsedRealtime() - started < deadlineMs) {
            delay(50L)
            val now = SystemClock.elapsedRealtime()
            val current = uiRevision.get()
            if (current != observedRevision) observedRevision = current
            if (now - lastUiChangeElapsedMs >= quietWindowMs) break
        }
        return captureSnapshot()
    }
}
