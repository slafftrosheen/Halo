package com.halo.host.android.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.halo.sdk.runtime.ContextTrust
import com.halo.sdk.runtime.RectValue
import com.halo.sdk.runtime.ScreenContextNode
import com.halo.sdk.runtime.ScreenContextSnapshot

class HaloAccessibilityService : AccessibilityService() {

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
        // We capture on demand via captureSnapshot(), not on every event, to limit overhead.
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

        val nodes = mutableListOf<ScreenContextNode>()
        var totalTextLength = 0

        fun traverse(node: AccessibilityNodeInfo, depth: Int) {
            if (nodes.size >= 120 || totalTextLength >= 16_000 || depth > 10) return

            if (node.isPassword) {
                // Redact password node
                return
            }

            val text = node.text?.toString()
            val contentDesc = node.contentDescription?.toString()
            val className = node.className?.toString()
            val viewId = node.viewIdResourceName

            if (!text.isNullOrBlank() || !contentDesc.isNullOrBlank() || node.isClickable) {
                val rect = Rect()
                node.getBoundsInScreen(rect)

                val nodeText = text?.take(500)
                totalTextLength += nodeText?.length ?: 0

                nodes.add(
                    ScreenContextNode(
                        text = nodeText,
                        contentDescription = contentDesc?.take(500),
                        className = className,
                        viewId = viewId,
                        clickable = node.isClickable,
                        editable = node.isEditable,
                        bounds = RectValue(rect.left, rect.top, rect.right, rect.bottom),
                        scrollable = node.isScrollable,
                        checkable = node.isCheckable,
                        checked = node.isChecked,
                        selected = node.isSelected,
                        focused = node.isFocused,
                        enabled = node.isEnabled
                    )
                )
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                traverse(child, depth + 1)
                child.recycle()
            }
        }

        traverse(root, 0)
        root.recycle()

        return ScreenContextSnapshot(
            packageName = packageName,
            activityName = null,
            title = null,
            webUri = null,
            nodes = nodes,
            capturedAtMs = System.currentTimeMillis(),
            provenance = "ANDROID_ACCESSIBILITY",
            trust = ContextTrust.UNTRUSTED_CONTENT
        )
    }
}
