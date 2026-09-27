package com.halo.host.android.accessibility

import com.halo.sdk.runtime.UiScreenRegion
import com.halo.sdk.runtime.UiSemanticType

/**
 * Pure projection rules for Android's accessibility tree: widget class to semantic role,
 * coarse on-screen position, and which containers are inert enough to collapse.
 *
 * Deliberately free of Android types so the rules can be checked without a device, and so the
 * formatter stays the only place that talks to [android.view.accessibility.AccessibilityNodeInfo].
 */
object AccessibilityProjectionRules {

    /**
     * Resolves the semantic role of one node.
     *
     * Platform flags that describe what a node *is* (editability) are trusted before its class
     * name, the class name is resolved through an exact table and then a bounded suffix table
     * (never a loose substring search, which turns `SomethingButtonish` into a button), and
     * [parentType] is consulted so a row inside a [UiSemanticType.LIST] is a
     * [UiSemanticType.LIST_ITEM] rather than an anonymous container.
     */
    fun classify(
        className: String?,
        parentType: UiSemanticType?,
        editable: Boolean,
        scrollable: Boolean,
        checkable: Boolean,
        clickable: Boolean,
        hasChildren: Boolean,
    ): UiSemanticType {
        if (editable) return UiSemanticType.INPUT

        val declared = declaredType(className)
        if (declared != null) {
            // A TextView that responds to touch is a link, not inert text.
            return if (declared == UiSemanticType.TEXT && clickable) UiSemanticType.LINK else declared
        }

        if (parentType == UiSemanticType.LIST) return UiSemanticType.LIST_ITEM
        if (scrollable) return UiSemanticType.SCROLL_CONTAINER
        if (checkable) return UiSemanticType.CHECKBOX
        if (clickable) return UiSemanticType.BUTTON
        if (hasChildren) return UiSemanticType.CONTAINER
        return UiSemanticType.UNKNOWN
    }

    /** Screen thirds from a node's centre; null when the display size is not usable. */
    fun regionOf(centerX: Int, centerY: Int, screenWidth: Int, screenHeight: Int): UiScreenRegion? {
        if (screenWidth <= 0 || screenHeight <= 0) return null
        val column = ((centerX.toLong() * 3) / screenWidth).coerceIn(0L, 2L).toInt()
        val row = ((centerY.toLong() * 3) / screenHeight).coerceIn(0L, 2L).toInt()
        return REGIONS[row * 3 + column]
    }

    /**
     * A container carries no information of its own and none of its own state, so a model learns
     * nothing from the line and pays a node of its budget for it. Its children are still emitted.
     */
    fun isInertContainer(role: UiSemanticType, hasLabel: Boolean, selected: Boolean, focused: Boolean): Boolean =
        role == UiSemanticType.CONTAINER && !hasLabel && !selected && !focused

    private fun declaredType(className: String?): UiSemanticType? {
        if (className == null) return null
        val normalized = className.lowercase()
        EXACT_TYPES[normalized]?.let { return it }
        val simpleName = normalized.substringAfterLast('.')
        return SUFFIX_TYPES.entries.firstOrNull { (suffix, _) -> simpleName.endsWith(suffix) }?.value
    }

    private val REGIONS = arrayOf(
        UiScreenRegion.TOP_LEFT, UiScreenRegion.TOP, UiScreenRegion.TOP_RIGHT,
        UiScreenRegion.LEFT, UiScreenRegion.CENTER, UiScreenRegion.RIGHT,
        UiScreenRegion.BOTTOM_LEFT, UiScreenRegion.BOTTOM, UiScreenRegion.BOTTOM_RIGHT,
    )

    /** Exact class names first: these are the widgets whose role is not ambiguous. */
    private val EXACT_TYPES = mapOf(
        "android.widget.button" to UiSemanticType.BUTTON,
        "androidx.appcompat.widget.appcompatbutton" to UiSemanticType.BUTTON,
        "com.google.android.material.button.materialbutton" to UiSemanticType.BUTTON,
        "android.widget.edittext" to UiSemanticType.INPUT,
        "android.widget.autocompletetextview" to UiSemanticType.INPUT,
        "android.widget.multiautocompletetextview" to UiSemanticType.INPUT,
        "com.google.android.material.textfield.textinputedittext" to UiSemanticType.INPUT,
        "android.widget.textview" to UiSemanticType.TEXT,
        "android.widget.imageview" to UiSemanticType.IMAGE,
        "android.widget.recyclerview" to UiSemanticType.LIST,
        "androidx.recyclerview.widget.recyclerview" to UiSemanticType.LIST,
        "android.widget.listview" to UiSemanticType.LIST,
        "android.widget.gridview" to UiSemanticType.LIST,
        "androidx.viewpager2.widget.viewpager2" to UiSemanticType.LIST,
        "androidx.viewpager.widget.viewpager" to UiSemanticType.LIST,
        "android.widget.scrollview" to UiSemanticType.SCROLL_CONTAINER,
        "android.widget.horizontalscrollview" to UiSemanticType.SCROLL_CONTAINER,
        "androidx.core.widget.nestedscrollview" to UiSemanticType.SCROLL_CONTAINER,
        "android.widget.switch" to UiSemanticType.SWITCH,
        "androidx.appcompat.widget.switchcompat" to UiSemanticType.SWITCH,
        "com.google.android.material.switchmaterial.switchmaterial" to UiSemanticType.SWITCH,
        "android.widget.togglebutton" to UiSemanticType.SWITCH,
        "android.widget.checkbox" to UiSemanticType.CHECKBOX,
        "android.widget.radiobutton" to UiSemanticType.CHECKBOX,
        "android.widget.checkedtextview" to UiSemanticType.CHECKBOX,
        "com.google.android.material.tabs.tablayout\$tab" to UiSemanticType.TAB,
        "com.google.android.material.bottomnavigation.bottomnavigationitemview" to UiSemanticType.TAB,
        "com.google.android.material.chip.chip" to UiSemanticType.CHIP,
        "com.google.android.material.chip.filterchip" to UiSemanticType.CHIP,
        "androidx.appcompat.widget.toolbar" to UiSemanticType.TOOLBAR,
        "com.google.android.material.appbar.materialtoolbar" to UiSemanticType.TOOLBAR,
    )

    /**
     * A bounded suffix table for custom and library subclasses, ordered specific-first:
     * `AutoCompleteTextView` must be read before `TextView`, and `ToggleButton` before `Button`.
     * An entry only ever matches a whole suffix of a simple class name, so a name that merely
     * *contains* the word is not affected.
     */
    private val SUFFIX_TYPES = linkedMapOf(
        "togglebutton" to UiSemanticType.SWITCH,
        "switchcompat" to UiSemanticType.SWITCH,
        "switchmaterial" to UiSemanticType.SWITCH,
        "switch" to UiSemanticType.SWITCH,
        "checkbox" to UiSemanticType.CHECKBOX,
        "radiobutton" to UiSemanticType.CHECKBOX,
        "checkedtextview" to UiSemanticType.CHECKBOX,
        "textinputedittext" to UiSemanticType.INPUT,
        "edittext" to UiSemanticType.INPUT,
        "autocompletetextview" to UiSemanticType.INPUT,
        "searchview" to UiSemanticType.INPUT,
        "tab" to UiSemanticType.TAB,
        "chip" to UiSemanticType.CHIP,
        "dialog" to UiSemanticType.DIALOG,
        "toolbar" to UiSemanticType.TOOLBAR,
        "recyclerview" to UiSemanticType.LIST,
        "listview" to UiSemanticType.LIST,
        "gridview" to UiSemanticType.LIST,
        "viewpager2" to UiSemanticType.LIST,
        "viewpager" to UiSemanticType.LIST,
        "scrollview" to UiSemanticType.SCROLL_CONTAINER,
        "button" to UiSemanticType.BUTTON,
        "imageview" to UiSemanticType.IMAGE,
        "textview" to UiSemanticType.TEXT,
    )
}
