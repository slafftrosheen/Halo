package com.halo.host.android.assistant

import android.app.assist.AssistContent
import android.app.assist.AssistStructure
import android.text.InputType
import android.view.View
import com.halo.sdk.runtime.RectValue
import com.halo.sdk.runtime.ScreenContextNode
import com.halo.sdk.runtime.ScreenContextSnapshot

class ScreenContextExtractor(
    private val maxNodes: Int = 120,
    private val maxTotalChars: Int = 14 * 1024,
    private val maxTextChars: Int = 512,
    private val maxDescriptionChars: Int = 256,
    private val maxViewIdChars: Int = 256,
) {
    fun extract(structure: AssistStructure?, content: AssistContent?): ScreenContextSnapshot {
        val nodes = mutableListOf<ScreenContextNode>()
        val seen = mutableSetOf<String>()
        var remainingChars = maxTotalChars
        var packageName: String? = null
        var activityName: String? = null
        var title: String? = null

        if (structure != null) {
            val component = structure.activityComponent
            packageName = component?.packageName
            activityName = component?.className
            for (windowIndex in 0 until structure.windowNodeCount) {
                if (nodes.size >= maxNodes || remainingChars <= 0) break
                val window = structure.getWindowNodeAt(windowIndex)
                if (title == null) title = sanitize(window.title, maxTextChars)
                remainingChars = visit(window.rootViewNode, nodes, seen, remainingChars, 0, 0)
            }
        }

        return ScreenContextSnapshot(
            packageName = packageName,
            activityName = activityName,
            title = title,
            webUri = content?.webUri?.toString()?.take(maxTextChars),
            nodes = nodes,
            capturedAtMs = System.currentTimeMillis(),
        )
    }

    private fun visit(
        node: AssistStructure.ViewNode?,
        output: MutableList<ScreenContextNode>,
        seen: MutableSet<String>,
        budget: Int,
        parentLeft: Int,
        parentTop: Int,
    ): Int {
        if (node == null || node.visibility != View.VISIBLE || output.size >= maxNodes || budget <= 0) return budget
        var remaining = budget
        val inputType = node.inputType
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val password = variation in PASSWORD_VARIATIONS
        val text = if (password) null else sanitize(node.text, maxTextChars)?.take(remaining)
        remaining -= text?.length ?: 0
        val description = if (password) null else sanitize(node.contentDescription, maxDescriptionChars)?.take(remaining)
        remaining -= description?.length ?: 0
        val viewId = sanitize(node.idEntry, maxViewIdChars)?.take(remaining)
        remaining -= viewId?.length ?: 0
        val editable = inputType != 0
        val hasUsefulContent = text != null || description != null || viewId != null || node.isClickable || editable
        val absoluteLeft = parentLeft + node.left
        val absoluteTop = parentTop + node.top
        val signature = listOf(text, description, node.className, viewId, absoluteLeft, absoluteTop).joinToString("|")
        if (hasUsefulContent && output.size < maxNodes && seen.add(signature)) {
            output += ScreenContextNode(
                text = text,
                contentDescription = description,
                className = sanitize(node.className, 160),
                viewId = viewId,
                clickable = node.isClickable,
                editable = editable,
                bounds = RectValue(absoluteLeft, absoluteTop, absoluteLeft + node.width, absoluteTop + node.height),
            )
        }
        for (index in 0 until node.childCount) {
            if (output.size >= maxNodes || remaining <= 0) break
            remaining = visit(node.getChildAt(index), output, seen, remaining, absoluteLeft, absoluteTop)
        }
        return remaining
    }

    private fun sanitize(value: CharSequence?, limit: Int): String? = value?.toString()
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.take(limit)

    private companion object {
        val PASSWORD_VARIATIONS = setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_NUMBER_VARIATION_PASSWORD,
        )
    }
}
