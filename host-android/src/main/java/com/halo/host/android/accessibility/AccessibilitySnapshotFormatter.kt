package com.halo.host.android.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.halo.sdk.runtime.ContextTrust
import com.halo.sdk.runtime.RectValue
import com.halo.sdk.runtime.ScreenContextNode
import com.halo.sdk.runtime.ScreenContextSnapshot
import com.halo.sdk.runtime.UiInspectability
import com.halo.sdk.runtime.UiSemanticType
import java.security.MessageDigest
import java.util.UUID
import com.halo.runtime.cognition.ui.semanticIdentity
import com.halo.runtime.cognition.ui.uiDigest

/**
 * Produces a bounded, redacted semantic projection of Android's live accessibility tree.
 *
 * The projection is deliberately smaller than the tree: nodes a model can learn nothing from are
 * collapsed, children below the fold of a scrollable are summarized instead of dropped, and each
 * surviving node carries a coarse on-screen region rather than raw pixel geometry. Roles, regions
 * and the collapse rule live in [AccessibilityProjectionRules], which has no Android dependency.
 */
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
        uiRevision: Long = -1L,
    ): ScreenContextSnapshot {
        val nodes = mutableListOf<ScreenContextNode>()
        val omissions = linkedMapOf<String, Int>()
        var chars = 0
        var rawNodes = 0
        var readableNodes = 0
        var reason: String? = null

        fun omitted(kind: String) {
            omissions[kind] = (omissions[kind] ?: 0) + 1
        }

        /**
         * @param hidden summary of the nearest enclosing scrollable, when this node is one of its
         *   direct rows; [countsIntoHidden] says whether a pruned node is still an "item" of it.
         */
        fun visit(
            node: AccessibilityNodeInfo,
            depth: Int,
            path: String,
            parentType: UiSemanticType?,
            hidden: HiddenChildren?,
            countsIntoHidden: Boolean,
        ) {
            if (reason != null) return
            if (nodes.size >= maxNodes) { omitted("max_nodes"); reason = "max_nodes"; return }
            if (chars >= maxTotalChars) { omitted("max_text"); reason = "max_text"; return }
            if (depth > maxDepth) { omitted("max_depth"); reason = "max_depth"; return }

            rawNodes++
            val sensitive = node.isPassword
            val text = if (sensitive) null else normalize(node.text?.toString()) ?: "".takeIf { node.isEditable && node.text != null }
            val hint = if (sensitive) null else normalize(node.contentDescription?.toString())
            val viewId = normalize(node.viewIdResourceName)?.take(MAX_VIEW_ID_CHARS)
            val role = AccessibilityProjectionRules.classify(
                className = node.className?.toString(),
                parentType = parentType,
                editable = node.isEditable,
                scrollable = node.isScrollable,
                checkable = node.isCheckable,
                clickable = node.isClickable,
                hasChildren = node.childCount > 0,
            )
            val interactive = node.isClickable || node.isLongClickable || node.isEditable || node.isScrollable || node.isCheckable
            val rect = Rect().also(node::getBoundsInScreen)
            val outsideScreen = rect.right <= 0 || rect.bottom <= 0 || rect.left >= screenWidth || rect.top >= screenHeight
            val zeroArea = rect.width() <= 0 || rect.height() <= 0
            val prunedOffScreen = outsideScreen && !interactive
            val prunedZeroArea = zeroArea && !interactive
            val unlabeled = text == null && hint == null && viewId == null
            val collapsed = AccessibilityProjectionRules.isInertContainer(
                role = role,
                hasLabel = !unlabeled,
                selected = node.isSelected,
                focused = node.isFocused,
            )
            // A node the projection drops and that says nothing itself is transparent: whatever a
            // model would have seen below it is promoted, so its children still count as the
            // scrollable's rows. This is what keeps an off-screen recycler wrapper from hiding the
            // labelled row inside it.
            val transparent = collapsed || ((prunedOffScreen || prunedZeroArea) && unlabeled)
            val ownHidden = if (node.isScrollable) HiddenChildren() else null
            val childHidden = ownHidden ?: hidden
            val childCountsIntoHidden = ownHidden != null || (transparent && countsIntoHidden)

            var emittedIndex: Int? = null
            if (prunedOffScreen) {
                omitted("off_screen")
                if (countsIntoHidden && !transparent) hidden?.record(text ?: hint)
            } else if (prunedZeroArea) {
                omitted("zero_area")
            } else if (collapsed) {
                omitted("collapsed_container")
            } else if (text != null || hint != null || viewId != null || interactive || sensitive) {
                readableNodes++
                val remaining = (maxTotalChars - chars).coerceAtLeast(0)
                val safeText = text?.take(minOf(maxNodeChars, remaining))
                chars += safeText?.length ?: 0
                val safeHint = hint?.take(minOf(maxNodeChars, (maxTotalChars - chars).coerceAtLeast(0)))
                chars += safeHint?.length ?: 0
                emittedIndex = nodes.size
                nodes += ScreenContextNode(
                    text = safeText,
                    contentDescription = safeHint,
                    className = node.className?.toString()?.take(MAX_CLASS_NAME_CHARS),
                    viewId = viewId,
                    clickable = node.isClickable,
                    longClickable = node.isLongClickable,
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
                    region = AccessibilityProjectionRules.regionOf(rect.centerX(), rect.centerY(), screenWidth, screenHeight),
                    rowIndex = node.collectionItemInfo?.rowIndex?.takeIf { it >= 0 },
                    valueFullyExposed = !sensitive && node.text != null && text == safeText,
                    labelHint = if (sensitive) null else normalize(node.hintText?.toString())?.take(100),
                )
                val intrinsicRole = AccessibilityProjectionRules.classify(node.className?.toString(), null,
                    node.isEditable, node.isScrollable, node.isCheckable, node.isClickable, node.childCount > 0)
                nodes[emittedIndex] = nodes[emittedIndex].copy(stableFingerprint = semanticIdentity(packageName,
                    nodes[emittedIndex].copy(semanticType = intrinsicRole)))
            } else if (node.childCount == 0) {
                omitted("empty_leaf")
            }

            for (index in 0 until node.childCount) {
                if (reason != null) break
                val child = node.getChild(index) ?: continue
                try {
                    visit(child, depth + 1, "$path.$index", role, childHidden, childCountsIntoHidden)
                } finally {
                    child.recycle()
                }
            }

            if (ownHidden != null && emittedIndex != null && ownHidden.count > 0) {
                nodes[emittedIndex] = nodes[emittedIndex].copy(
                    hiddenChildCount = ownHidden.count,
                    hiddenChildPreviews = ownHidden.previews.toList(),
                )
            }
            if (ownHidden != null && emittedIndex != null) {
                val descendants = nodes.drop(emittedIndex + 1)
                nodes[emittedIndex] = nodes[emittedIndex].copy(visibleChildKeys = descendants
                    .filter { !it.sensitive && !it.editable && (it.semanticType == UiSemanticType.LIST_ITEM || it.rowIndex != null) }
                    .take(64).map { uiDigest(it.viewId.orEmpty() + "|" + it.text.orEmpty() + "|" + it.contentDescription.orEmpty()) },
                    visibleRowIndices = descendants.filter { !it.sensitive && !it.editable }.mapNotNull { it.rowIndex }.distinct().sorted().take(64))
            }
        }

        visit(root, 0, "0", null, null, false)
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
            omissionCounts = omissions.toMap(),
            snapshotId = snapshotId,
            uiRevision = uiRevision,
            navigationFingerprint = uiDigest(root.className.toString() + "|" + root.viewIdResourceName.orEmpty() + "|" +
                nodes.filter { it.semanticType == UiSemanticType.TOOLBAR }.joinToString { it.className.orEmpty() + ":" + it.viewId.orEmpty() }),
            inspectability = when {
                readableNodes > 0 -> UiInspectability.INSPECTABLE
                rawNodes <= 1 -> UiInspectability.EMPTY_OR_ROOT_ONLY
                else -> UiInspectability.NO_SEMANTIC_CONTENT
            },
        )
    }

    /**
     * The token is derived from the node, its true tree path and its *intrinsic* role only.
     * Parent-derived roles such as `LIST_ITEM` are display information, not identity: a token must
     * not change merely because a list gained or lost a wrapper, and the action path recomputes
     * tokens by walking the tree without parent context.
     */
    internal fun tokenForNode(snapshotId: String, packageName: String?, path: String, node: AccessibilityNodeInfo): String =
        tokenFor(
            snapshotId = snapshotId,
            packageName = packageName,
            path = path,
            role = AccessibilityProjectionRules.classify(
                className = node.className?.toString(),
                parentType = null,
                editable = node.isEditable,
                scrollable = node.isScrollable,
                checkable = node.isCheckable,
                clickable = node.isClickable,
                hasChildren = node.childCount > 0,
            ),
            viewId = normalize(node.viewIdResourceName)?.take(MAX_VIEW_ID_CHARS),
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

    private fun normalize(value: String?): String? = value
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    /** Bounded, content-free summary of the children a scrollable had to prune. */
    private class HiddenChildren {
        var count = 0
            private set
        val previews = mutableListOf<String>()

        fun record(label: String?) {
            count++
            if (label != null && previews.size < MAX_PREVIEWS) previews += label.take(MAX_PREVIEW_CHARS)
        }

        private companion object {
            const val MAX_PREVIEWS = 3
            const val MAX_PREVIEW_CHARS = 24
        }
    }

    private companion object {
        const val MAX_VIEW_ID_CHARS = 200
        const val MAX_CLASS_NAME_CHARS = 160
    }
}
