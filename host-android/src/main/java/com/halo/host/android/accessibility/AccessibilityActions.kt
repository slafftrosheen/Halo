package com.halo.host.android.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.halo.runtime.cognition.ui.*

/** Platform dispatch only. Effect verification belongs to UiPostconditionVerifier. */
class AccessibilityActions(private val service: HaloAccessibilityService) {
    private val resolver = UiTargetResolver(service)
    fun dispatch(intent: UiActionIntent, text: String? = null): Pair<UiDispatchStatus, UiFailure?> {
        val result = if (intent is UiActionIntent.Global) {
            val failure = resolver.validateSnapshot(intent.snapshotId, requireInspectable = false)
            if (failure != null || intent.uiRevision != service.currentActionableRevision)
                UiDispatchStatus.NOT_DISPATCHED to (failure ?: UiFailure.SNAPSHOT_SUPERSEDED)
            else {
                val action = AndroidGlobalActions.id(service, intent.operation)
                    ?: return UiDispatchStatus.NOT_DISPATCHED to UiFailure.ACTION_NOT_SUPPORTED
                (if (service.performGlobalAction(action)) UiDispatchStatus.DISPATCHED else UiDispatchStatus.NOT_DISPATCHED) to null
            }
        } else resolver.dispatch(intent) { node ->
            if (intent is UiActionIntent.SetChecked && node.isCheckable && node.isChecked == intent.checked)
                UiDispatchStatus.ALREADY_SATISFIED
            else {
                val ok = when (intent) {
                    is UiActionIntent.Activate -> if (intent.longPress)
                        node.isLongClickable && node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
                        else node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    is UiActionIntent.SetChecked -> node.isCheckable && node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    is UiActionIntent.Scroll -> node.isScrollable && node.performAction(if (intent.forward)
                        AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                    is UiActionIntent.SetText -> node.isEditable && !node.isPassword && text != null && text.length <= 2000 &&
                        uiDigest(text) == intent.textDigest && node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
                            Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) })
                    is UiActionIntent.Global -> false
                }
                if (ok) UiDispatchStatus.DISPATCHED else UiDispatchStatus.NOT_DISPATCHED
            }
        }
        if (result.first == UiDispatchStatus.DISPATCHED) {
            UiOperatorDiagnostics.update { it.copy(actionDispatchCount = it.actionDispatchCount + 1) }
            android.util.Log.i("HaloUi", "HALO_UI_ACTION_DISPATCH operation=${intent.operation} fingerprint=${intent.target?.stableFingerprint?.take(8)} revision=${intent.uiRevision} package=${intent.target?.packageName}")
            service.markActionDispatched()
        } else service.invalidateSnapshot()
        return result
    }
    // Legacy host adapters retain source compatibility while using exact-generation resolution.
    private fun legacy(snapshotId: String, token: String, operation: UiOperation,
        intent: (ResolvedUiTarget) -> UiActionIntent, text: String? = null): Boolean {
        val target = (resolver.resolve(snapshotId, token, operation) as? UiTargetOutcome.Resolved)?.target ?: return false
        return dispatch(intent(target), text).first in setOf(UiDispatchStatus.DISPATCHED, UiDispatchStatus.ALREADY_SATISFIED)
    }
    fun clickToken(snapshotId: String, token: String) = legacy(snapshotId, token, UiOperation.ACTIVATE, { UiActionIntent.Activate(it) })
    fun scrollToken(snapshotId: String, token: String, forward: Boolean) = legacy(snapshotId, token, UiOperation.SCROLL, { UiActionIntent.Scroll(it, forward) })
    fun setTextToken(snapshotId: String, token: String, text: String) = legacy(snapshotId, token, UiOperation.SET_TEXT,
        { UiActionIntent.SetText(it, text.length, uiDigest(text)) }, text)
    fun scroll(forward: Boolean): Boolean {
        val snapshot = service.latestSemanticSnapshot ?: return false
        val target = snapshot.nodes.filter { it.scrollable && it.enabled && !it.sensitive }.singleOrNull() ?: return false
        return scrollToken(snapshot.snapshotId, target.token ?: return false, forward)
    }
    private fun global(operation: UiOperation): Boolean {
        val snapshot = service.latestSemanticSnapshot ?: return false
        return dispatch(UiActionIntent.Global(snapshot.snapshotId, snapshot.uiRevision, operation)).first == UiDispatchStatus.DISPATCHED
    }
    fun globalBack() = global(UiOperation.BACK)
    fun globalHome() = global(UiOperation.HOME)
}
