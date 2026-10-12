package com.halo.host.android.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import com.halo.runtime.cognition.ui.*
import com.halo.sdk.runtime.UiInspectability

/** A fingerprint compares evidence; only the exact snapshot token/revision can authorize traversal. */
class UiTargetResolver(private val service: HaloAccessibilityService) {
    private val formatter = AccessibilitySnapshotFormatter()
    fun validateSnapshot(snapshotId: String, requireInspectable: Boolean = true): UiFailure? {
        val snapshot = service.latestSemanticSnapshot ?: return rejected(if (snapshotId == service.lastInvalidatedSnapshotId)
            UiFailure.SNAPSHOT_SUPERSEDED else UiFailure.SNAPSHOT_NOT_FOUND)
        if (snapshot.snapshotId != snapshotId || service.latestSemanticSnapshotId != snapshotId) return rejected(UiFailure.SNAPSHOT_SUPERSEDED)
        if (System.currentTimeMillis() - snapshot.capturedAtMs > 30_000) return rejected(UiFailure.SNAPSHOT_EXPIRED)
        if (snapshot.uiRevision != service.currentActionableRevision) return rejected(UiFailure.SNAPSHOT_SUPERSEDED)
        // A globally supported Android action does not select a semantic node.
        // Foreground package, revision, TTL and protected-window checks still apply.
        if (requireInspectable && snapshot.inspectability != UiInspectability.INSPECTABLE)
            return rejected(UiFailure.USER_TAKEOVER_REQUIRED)
        val root = service.rootInActiveWindow ?: return rejected(UiFailure.NO_ACTIVE_WINDOW)
        return try {
            val pkg = root.packageName?.toString()
            when {
                AccessibilitySafetyPolicy.isPackageBlocked(pkg) -> rejected(if (pkg?.contains("permissioncontroller") == true)
                    UiFailure.SYSTEM_PERMISSION_SURFACE else UiFailure.PACKAGE_BLOCKED)
                pkg != snapshot.packageName -> rejected(UiFailure.SNAPSHOT_SUPERSEDED)
                else -> null
            }
        } finally { root.recycle() }
    }
    fun resolve(snapshotId: String, token: String, operation: UiOperation): UiTargetOutcome {
        validateSnapshot(snapshotId)?.let { return UiTargetOutcome.Rejected(it) }
        val snapshot = service.latestSemanticSnapshot ?: return UiTargetOutcome.Rejected(UiFailure.SNAPSHOT_NOT_FOUND)
        val node = snapshot.nodes.singleOrNull { it.token == token } ?: return UiTargetOutcome.Rejected(UiFailure.TOKEN_NOT_FOUND)
        val failure = when {
            node.sensitive -> UiFailure.TARGET_SENSITIVE
            !node.enabled -> UiFailure.TARGET_DISABLED
            operation == UiOperation.LONG_PRESS && !node.longClickable -> UiFailure.ACTION_NOT_SUPPORTED
            operation == UiOperation.ACTIVATE && !node.clickable -> UiFailure.ACTION_NOT_SUPPORTED
            operation == UiOperation.SET_TEXT && !node.editable -> UiFailure.ACTION_NOT_SUPPORTED
            operation == UiOperation.SET_CHECKED && !node.checkable -> UiFailure.ACTION_NOT_SUPPORTED
            operation == UiOperation.SCROLL && !node.scrollable -> UiFailure.ACTION_NOT_SUPPORTED
            else -> null
        }
        if (failure != null) return UiTargetOutcome.Rejected(failure)
        val target = ResolvedUiTarget(snapshotId, snapshot.uiRevision, snapshot.packageName, token, node.semanticType,
            node.safeTargetLabel(), node.region, node.enabled, node.clickable, node.editable, node.scrollable,
            node.checkable, node.checked.takeIf { node.checkable }, node.sensitive, node.stableFingerprint, node.longClickable)
        android.util.Log.i("HaloUi", "HALO_UI_TARGET_RESOLVED id=${snapshotId.take(8)} operation=$operation role=${target.semanticType} region=${target.region} labelPresent=${target.label != null} sensitive=false")
        return UiTargetOutcome.Resolved(target)
    }
    /** Main-thread caller performs resolution and the platform action without a suspension boundary. */
    fun dispatch(intent: UiActionIntent, action: (AccessibilityNodeInfo) -> UiDispatchStatus): Pair<UiDispatchStatus, UiFailure?> {
        val target = intent.target ?: return UiDispatchStatus.NOT_DISPATCHED to UiFailure.ACTION_NOT_SUPPORTED
        val result = resolve(intent.snapshotId, target.token, intent.operation)
        if (result is UiTargetOutcome.Rejected) return UiDispatchStatus.NOT_DISPATCHED to result.failure
        val resolved = (result as UiTargetOutcome.Resolved).target
        if (resolved.stableFingerprint != target.stableFingerprint || resolved.uiRevision != intent.uiRevision)
            return UiDispatchStatus.NOT_DISPATCHED to UiFailure.SNAPSHOT_SUPERSEDED
        val root = service.rootInActiveWindow ?: return UiDispatchStatus.NOT_DISPATCHED to UiFailure.NO_ACTIVE_WINDOW
        var visited = 0
        fun visit(node: AccessibilityNodeInfo, path: String, depth: Int): UiDispatchStatus? {
            if (++visited > 1024 || depth > 16) return null
            if (formatter.tokenForNode(intent.snapshotId, target.packageName, path, node) == target.token) {
                if (service.currentActionableRevision != intent.uiRevision || !node.isEnabled || node.isPassword) return UiDispatchStatus.NOT_DISPATCHED
                return action(node)
            }
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                try { visit(child, "$path.$index", depth + 1)?.let { return it } } finally { child.recycle() }
            }
            return null
        }
        return try {
            val status = visit(root, "0", 0) ?: UiDispatchStatus.NOT_DISPATCHED
            status to if (status == UiDispatchStatus.NOT_DISPATCHED) {
                if (service.currentActionableRevision != intent.uiRevision) UiFailure.SNAPSHOT_SUPERSEDED else UiFailure.ACTION_NOT_SUPPORTED
            } else null
        } finally { root.recycle() }
    }
    private fun rejected(reason: UiFailure): UiFailure {
        if (reason in setOf(UiFailure.SNAPSHOT_EXPIRED, UiFailure.SNAPSHOT_SUPERSEDED, UiFailure.SNAPSHOT_NOT_FOUND))
            UiOperatorDiagnostics.update { it.copy(staleSnapshotCount = it.staleSnapshotCount + 1) }
        android.util.Log.i("HaloUi", "HALO_UI_SNAPSHOT_REJECTED reason=$reason expectedRevision=${service.latestSemanticSnapshot?.uiRevision} currentRevision=${service.currentActionableRevision}")
        return reason
    }
}
