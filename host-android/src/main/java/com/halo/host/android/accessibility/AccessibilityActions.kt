package com.halo.host.android.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo

class AccessibilityActions(private val service: HaloAccessibilityService) {
    private val formatter = AccessibilitySnapshotFormatter()

    fun clickToken(snapshotId: String, token: String): Boolean = recordDispatch(withAllowedRoot { root, packageName ->
        if (service.latestSemanticSnapshotId != snapshotId) return@withAllowedRoot false
        val capturedAt = service.latestSemanticSnapshotAtMs ?: return@withAllowedRoot false
        if (System.currentTimeMillis() - capturedAt > TOKEN_MAX_AGE_MS) return@withAllowedRoot false
        performOnToken(root, snapshotId, packageName, token) { target ->
            target.isEnabled && target.isClickable && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    })

    fun scrollToken(snapshotId: String, token: String, forward: Boolean): Boolean =
        recordDispatch(withAllowedRoot { root, packageName ->
            if (service.latestSemanticSnapshotId != snapshotId) return@withAllowedRoot false
            val capturedAt = service.latestSemanticSnapshotAtMs ?: return@withAllowedRoot false
            if (System.currentTimeMillis() - capturedAt > TOKEN_MAX_AGE_MS) return@withAllowedRoot false
            val actionId = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            performOnToken(root, snapshotId, packageName, token) { target ->
                target.isEnabled && target.isScrollable && target.performAction(actionId)
            }
        })

    fun setTextToken(snapshotId: String, token: String, text: String): Boolean =
        recordDispatch(withAllowedRoot { root, packageName ->
            if (service.latestSemanticSnapshotId != snapshotId) return@withAllowedRoot false
            val capturedAt = service.latestSemanticSnapshotAtMs ?: return@withAllowedRoot false
            if (System.currentTimeMillis() - capturedAt > TOKEN_MAX_AGE_MS) return@withAllowedRoot false
            performOnToken(root, snapshotId, packageName, token) { target ->
                if (!target.isEnabled || !target.isEditable || target.isPassword) return@performOnToken false
                val arguments = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            }
        })

    fun clickNode(viewId: String): Boolean {
        val root = service.rootInActiveWindow ?: return false
        if (AccessibilitySafetyPolicy.isPackageBlocked(root.packageName?.toString())) { root.recycle(); return false }
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
        val target = nodes.firstOrNull() ?: run { root.recycle(); return false }
        val result = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        target.recycle()
        root.recycle()
        return result
    }

    fun clickText(text: String): Boolean {
        val root = service.rootInActiveWindow ?: return false
        if (AccessibilitySafetyPolicy.isPackageBlocked(root.packageName?.toString())) { root.recycle(); return false }
        val nodes = root.findAccessibilityNodeInfosByText(text)
        val target = nodes.firstOrNull { it.isClickable } ?: nodes.firstOrNull() ?: run { root.recycle(); return false }
        val result = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        target.recycle()
        root.recycle()
        return result
    }

    fun clickCoordinates(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 100)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return service.dispatchGesture(gesture, null, null)
    }

    fun setText(viewId: String, text: String): Boolean {
        val root = service.rootInActiveWindow ?: return false
        if (AccessibilitySafetyPolicy.isPackageBlocked(root.packageName?.toString())) { root.recycle(); return false }
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
        val target = nodes.firstOrNull { it.isEditable } ?: run { root.recycle(); return false }
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val result = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        target.recycle()
        root.recycle()
        return result
    }

    fun scroll(forward: Boolean): Boolean {
        val root = service.rootInActiveWindow ?: return false
        if (AccessibilitySafetyPolicy.isPackageBlocked(root.packageName?.toString())) { root.recycle(); return false }
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        val result = root.performAction(action)
        root.recycle()
        return result
    }

    fun globalBack(): Boolean = recordDispatch(service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))

    fun globalHome(): Boolean = recordDispatch(service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))

    private fun recordDispatch(dispatched: Boolean): Boolean {
        if (dispatched) service.markActionDispatched()
        return dispatched
    }

    private inline fun withAllowedRoot(block: (AccessibilityNodeInfo, String?) -> Boolean): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val packageName = root.packageName?.toString()
        if (AccessibilitySafetyPolicy.isPackageBlocked(packageName)) {
            root.recycle()
            return false
        }
        return try { block(root, packageName) } finally { root.recycle() }
    }

    private fun performOnToken(
        root: AccessibilityNodeInfo,
        snapshotId: String,
        packageName: String?,
        token: String,
        action: (AccessibilityNodeInfo) -> Boolean,
    ): Boolean {
        fun visit(node: AccessibilityNodeInfo, path: String): Boolean {
            if (formatter.tokenForNode(snapshotId, packageName, path, node) == token) return action(node)
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                try {
                    if (visit(child, "$path.$index")) return true
                } finally {
                    child.recycle()
                }
            }
            return false
        }
        return visit(root, "0")
    }

    private companion object {
        const val TOKEN_MAX_AGE_MS = 30_000L
    }
}
