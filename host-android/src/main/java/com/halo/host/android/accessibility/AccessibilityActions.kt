package com.halo.host.android.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo

class AccessibilityActions(private val service: HaloAccessibilityService) {

    fun clickNode(viewId: String): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
        val target = nodes.firstOrNull() ?: run { root.recycle(); return false }
        val result = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        target.recycle()
        root.recycle()
        return result
    }

    fun clickText(text: String): Boolean {
        val root = service.rootInActiveWindow ?: return false
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
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        val result = root.performAction(action)
        root.recycle()
        return result
    }

    fun globalBack(): Boolean = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)

    fun globalHome(): Boolean = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
}
