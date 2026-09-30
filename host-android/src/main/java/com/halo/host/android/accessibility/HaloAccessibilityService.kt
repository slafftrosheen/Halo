package com.halo.host.android.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.SystemClock
import com.halo.sdk.runtime.ScreenContextSnapshot
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicLong
import com.halo.runtime.cognition.ui.*

class HaloAccessibilityService : AccessibilityService() {
    private val snapshotFormatter = AccessibilitySnapshotFormatter()
    @Volatile internal var latestSemanticSnapshotId: String? = null
        private set
    @Volatile internal var latestSemanticSnapshotAtMs: Long? = null
        private set
    @Volatile var latestSemanticSnapshot: ScreenContextSnapshot? = null
        private set
    private val uiRevision = AtomicLong(0L)
    val currentActionableRevision: Long get() = uiRevision.get()
    @Volatile internal var lastInvalidatedSnapshotId: String? = null
        private set
    @Volatile private var lastUiChangeElapsedMs: Long = 0L

    companion object {
        var instance: HaloAccessibilityService? = null
            private set

        val isEnabled: Boolean get() = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        UiOperatorDiagnostics.accessibilityConnected.value = true
        com.halo.runtime.cognition.routine.AutomationServiceSignals.foreground.availability.value = com.halo.runtime.cognition.routine.AutomationSourceStatus.AVAILABLE
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.packageName?.toString()?.let(com.halo.runtime.cognition.routine.AutomationServiceSignals::foregrounded)
        }
        // Track change timing only. The semantic tree is still captured on demand.
        uiRevision.incrementAndGet()
        lastUiChangeElapsedMs = SystemClock.elapsedRealtime()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        invalidateSnapshot()
        instance = null
        com.halo.runtime.cognition.routine.AutomationServiceSignals.foregroundDisconnected()
        UiOperatorDiagnostics.accessibilityConnected.value = false
        super.onDestroy()
    }

    /**
     * Observes the active screen and reports *why* when it cannot.
     * This is the entry point callers that must react differently to "nothing to observe" and
     * "Halo refuses to observe this app" should use; [captureSnapshot] stays for callers that
     * only need a baseline and treat every miss the same.
     */
    fun captureSnapshotOutcome(): ScreenCaptureOutcome {
        val root = rootInActiveWindow ?: run { invalidateSnapshot(); return ScreenCaptureOutcome.NoActiveWindow }
        val packageName = root.packageName?.toString()
        if (packageName == null) { root.recycle(); invalidateSnapshot(); return ScreenCaptureOutcome.NoActiveWindow }

        val blockReason = AccessibilitySafetyPolicy.blockReason(packageName)
        if (blockReason != null) {
            invalidateSnapshot()
            UiOperatorDiagnostics.update { it.copy(blockedPackageCount = it.blockedPackageCount + 1) }
            root.recycle()
            return ScreenCaptureOutcome.PackageBlocked(packageName, blockReason)
        }

        val metrics = resources.displayMetrics
        return try {
            val revision = currentActionableRevision
            val snapshot = snapshotFormatter.format(root, packageName, metrics.widthPixels, metrics.heightPixels, uiRevision = revision)
            if (revision != currentActionableRevision) { invalidateSnapshot(); return ScreenCaptureOutcome.NoActiveWindow }
            latestSemanticSnapshotId = snapshot.snapshotId
            latestSemanticSnapshotAtMs = snapshot.capturedAtMs
            latestSemanticSnapshot = snapshot
            UiOperatorDiagnostics.update { it.copy(screenReadCount = it.screenReadCount + 1) }
            android.util.Log.i("HaloUi", "HALO_UI_SNAPSHOT id=${snapshot.snapshotId.take(8)} revision=$revision package=$packageName nodes=${snapshot.nodes.size} inspectability=${snapshot.inspectability} truncated=${snapshot.truncated} protocol=V1")
            ScreenCaptureOutcome.Captured(snapshot)
        } finally {
            root.recycle()
        }
    }

    fun captureSnapshot(): ScreenContextSnapshot? =
        (captureSnapshotOutcome() as? ScreenCaptureOutcome.Captured)?.snapshot

    internal fun markActionDispatched() {
        invalidateSnapshot()
        uiRevision.incrementAndGet()
        lastUiChangeElapsedMs = SystemClock.elapsedRealtime()
    }
    internal fun invalidateSnapshot() {
        latestSemanticSnapshotId?.let { lastInvalidatedSnapshotId = it }
        latestSemanticSnapshotId = null; latestSemanticSnapshotAtMs = null; latestSemanticSnapshot = null
    }

    data class SettledCapture(val outcome: ScreenCaptureOutcome, val status: UiSettleStatus, val elapsedMs: Long)
    suspend fun captureSettledOutcome(policy: UiSettlePolicy): SettledCapture {
        val started = SystemClock.elapsedRealtime()
        var settled = false
        while (SystemClock.elapsedRealtime() - started < policy.maxWaitMs) {
            delay(50)
            val now = SystemClock.elapsedRealtime()
            if (now - started >= policy.minimumObservationDelayMs && now - lastUiChangeElapsedMs >= policy.quietWindowMs) {
                settled = true; break
            }
        }
        val outcome = captureSnapshotOutcome()
        val status = if (outcome !is ScreenCaptureOutcome.Captured) UiSettleStatus.UNOBSERVABLE else
            if (settled) UiSettleStatus.SETTLED else UiSettleStatus.SETTLE_TIMEOUT
        if (status == UiSettleStatus.SETTLE_TIMEOUT) UiOperatorDiagnostics.update { it.copy(settleTimeoutCount = it.settleTimeoutCount + 1) }
        return SettledCapture(outcome, status, SystemClock.elapsedRealtime() - started)
    }

    suspend fun captureSettledSnapshot(
        quietWindowMs: Long = 180L,
        deadlineMs: Long = 1_500L,
    ): ScreenContextSnapshot? {
        return (captureSettledOutcome(UiSettlePolicy(quietWindowMs, deadlineMs)).outcome as? ScreenCaptureOutcome.Captured)?.snapshot
    }
}
