package com.halo.host.android.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.halo.sdk.runtime.ContextTrust
import com.halo.sdk.runtime.RectValue
import com.halo.sdk.runtime.ScreenContextNode
import com.halo.sdk.runtime.ScreenContextSnapshot
import com.halo.sdk.runtime.UiSemanticType
import java.security.MessageDigest
import java.util.UUID

/** Produces a bounded, redacted semantic projection of Android's live accessibility tree. */
class AccessibilitySnapshotFormatter(
    private val maxNodes: Int = 160,
    private val maxDepth: Int = 16,
    private val maxTotalChars: Int = 16_000,
    private val maxNodeChars: Int = 400,
) {
    fun format(
        root: AccessibilityNodeInfo,
        packageName: String?,
        screenWidth: Int,
        screenHeight: Int,
        capturedAtMs: Long = System.currentTimeMillis(),
        snapshotId: String = UUID.randomUUID().toString(),
    ): ScreenContextSnapshot {
        val nodes = mutableListOf<ScreenContextNode>()
        var chars = 0
        var reason: String? = null

        fun visit(node: AccessibilityNodeInfo, depth: Int, path: String) {
            if (reason != null) return
            if (nodes.size >= maxNodes) { reason = "max_nodes"; return }
            if (chars >= maxTotalChars) { reason = "max_text"; return }
            if (depth > maxDepth) { reason = "max_depth"; return }

            val sensitive = node.isPassword
            val text = if (sensitive) null else normalize(node.text?.toString())
            val hint = if (sensitive) null else normalize(node.contentDescription?.toString())
            val viewId = normalize(node.viewIdResourceName)?.take(200)
            val role = semanticType(node)
            val useful = text != null || hint != null || viewId != null || node.isClickable ||
                node.isEditable || node.isScrollable || node.isCheckable || sensitive

            if (useful) {
                val rect = Rect().also(node::getBoundsInScreen)
                val remaining = (maxTotalChars - chars).coerceAtLeast(0)
                val safeText = text?.take(minOf(maxNodeChars, remaining))
                chars += safeText?.length ?: 0
                val safeHint = hint?.take(minOf(maxNodeChars, (maxTotalChars - chars).coerceAtLeast(0)))
                chars += safeHint?.length ?: 0
                nodes += ScreenContextNode(
                    text = safeText,
                    contentDescription = safeHint,
                    className = node.className?.toString()?.take(160),
                    viewId = viewId,
                    clickable = node.isClickable,
                    editable = node.isEditable,
                    bounds = RectValue(rect.left, rect.top, rect.right, rect.bottom),
                    scrollable = node.isScrollable,
                    checkable = node.isCheckable,
                    checked = node.isChecked,
                    selected = node.isSelected,
                    focused = node.isFocused,
                    enabled = node.isEnabled,
                    token = tokenForNode(snapshotId, packageName, path, node),
                    semanticType = role,
                    depth = depth,
                    sensitive = sensitive,
                )
            }

            for (index in 0 until node.childCount) {
                if (reason != null) break
                val child = node.getChild(index) ?: continue
                try {
                    visit(child, depth + 1, "$path.$index")
                } finally {
                    child.recycle()
                }
            }
        }

        visit(root, 0, "0")
        return ScreenContextSnapshot(
            packageName = packageName,
            activityName = null,
            title = null,
            webUri = null,
            nodes = nodes,
            capturedAtMs = capturedAtMs,
            provenance = "ANDROID_ACCESSIBILITY_SEMANTIC",
            trust = ContextTrust.UNTRUSTED_CONTENT,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            truncated = reason != null,
            truncationReason = reason,
            snapshotId = snapshotId,
        )
    }

    private fun normalize(value: String?): String? = value
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    private fun semanticType(node: AccessibilityNodeInfo): UiSemanticType {
        val name = node.className?.toString().orEmpty().lowercase()
        return when {
            node.isEditable || "edittext" in name -> UiSemanticType.INPUT
            "switch" in name || "toggle" in name -> UiSemanticType.SWITCH
            "checkbox" in name || node.isCheckable -> UiSemanticType.CHECKBOX
            "button" in name -> UiSemanticType.BUTTON
            "image" in name -> UiSemanticType.IMAGE
            "recyclerview" in name || "listview" in name -> UiSemanticType.LIST
            node.isScrollable -> UiSemanticType.SCROLL_CONTAINER
            "textview" in name -> if (node.isClickable) UiSemanticType.LINK else UiSemanticType.TEXT
            node.isClickable -> UiSemanticType.BUTTON
            node.childCount > 0 -> UiSemanticType.CONTAINER
            else -> UiSemanticType.UNKNOWN
        }
    }

    internal fun tokenForNode(snapshotId: String, packageName: String?, path: String, node: AccessibilityNodeInfo): String =
        tokenFor(
            snapshotId = snapshotId,
            packageName = packageName,
            path = path,
            role = semanticType(node),
            viewId = normalize(node.viewIdResourceName)?.take(200),
            text = if (node.isPassword) null else normalize(node.text?.toString())?.take(120),
            hint = if (node.isPassword) null else normalize(node.contentDescription?.toString())?.take(120),
        )

    private fun tokenFor(
        snapshotId: String,
        packageName: String?,
        path: String,
        role: UiSemanticType,
        viewId: String?,
        text: String?,
        hint: String?,
    ): String {
        val input = listOf(snapshotId, packageName.orEmpty(), path, role.name, viewId.orEmpty(), text.orEmpty(), hint.orEmpty()).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return "ui_" + digest.take(6).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
