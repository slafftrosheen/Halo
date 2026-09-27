package com.halo.host.android.accessibility

import com.halo.sdk.runtime.UiScreenRegion
import com.halo.sdk.runtime.UiSemanticType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These rules have no Android dependency, so they are checkable as plain JVM tests.
 *
 * Not executed by the pass that wrote this file (that pass ran neither builds nor tests), so treat
 * these as the intended contract until they have actually been run once.
 */
class AccessibilityProjectionRulesTest {

    private fun classify(
        className: String?,
        parentType: UiSemanticType? = null,
        editable: Boolean = false,
        scrollable: Boolean = false,
        checkable: Boolean = false,
        clickable: Boolean = false,
        hasChildren: Boolean = false,
    ) = AccessibilityProjectionRules.classify(className, parentType, editable, scrollable, checkable, clickable, hasChildren)

    @Test
    fun `exact class names resolve to their role`() {
        assertEquals(UiSemanticType.BUTTON, classify("android.widget.Button"))
        assertEquals(UiSemanticType.BUTTON, classify("com.google.android.material.button.MaterialButton"))
        assertEquals(UiSemanticType.TEXT, classify("android.widget.TextView"))
        assertEquals(UiSemanticType.IMAGE, classify("android.widget.ImageView"))
        assertEquals(UiSemanticType.LIST, classify("androidx.recyclerview.widget.RecyclerView"))
        assertEquals(UiSemanticType.SCROLL_CONTAINER, classify("androidx.core.widget.NestedScrollView"))
        assertEquals(UiSemanticType.TAB, classify("com.google.android.material.tabs.TabLayout\$Tab"))
        assertEquals(UiSemanticType.TOOLBAR, classify("androidx.appcompat.widget.Toolbar"))
    }

    @Test
    fun `custom subclasses resolve through the bounded suffix table`() {
        assertEquals(UiSemanticType.BUTTON, classify("com.example.widgets.PrimaryButton"))
        assertEquals(UiSemanticType.IMAGE, classify("com.example.widgets.AvatarImageView"))
        assertEquals(UiSemanticType.CHIP, classify("com.example.widgets.FilterChip"))
        assertEquals(UiSemanticType.DIALOG, classify("androidx.appcompat.app.AlertDialog"))
        assertEquals(UiSemanticType.LIST, classify("com.example.widgets.InfiniteRecyclerView"))
    }

    /** A name that merely contains a role word is not that role: only whole suffixes match. */
    @Test
    fun `substring matches are not accepted`() {
        assertEquals(UiSemanticType.CONTAINER, classify("com.example.widgets.ButtonBar", hasChildren = true))
        assertEquals(
            UiSemanticType.UNKNOWN,
            classify("com.example.widgets.SwitchboardPanel", hasChildren = false),
        )
    }

    /** More specific suffixes must win over the generic ones they end with. */
    @Test
    fun `more specific suffixes take precedence`() {
        assertEquals(UiSemanticType.SWITCH, classify("android.widget.ToggleButton"))
        assertEquals(UiSemanticType.SWITCH, classify("androidx.appcompat.widget.SwitchCompat"))
        assertEquals(UiSemanticType.INPUT, classify("android.widget.AutoCompleteTextView"))
        assertEquals(UiSemanticType.CHECKBOX, classify("android.widget.CheckedTextView"))
    }

    @Test
    fun `platform flags decide what a class name cannot`() {
        assertEquals(UiSemanticType.INPUT, classify(className = null, editable = true))
        assertEquals(UiSemanticType.CHECKBOX, classify(className = null, checkable = true))
        assertEquals(UiSemanticType.SCROLL_CONTAINER, classify(className = null, scrollable = true))
        assertEquals(UiSemanticType.BUTTON, classify(className = null, clickable = true))
        assertEquals(UiSemanticType.CONTAINER, classify("android.widget.FrameLayout", hasChildren = true))
        assertEquals(UiSemanticType.UNKNOWN, classify("android.view.View"))
    }

    @Test
    fun `a touchable text view is a link`() {
        assertEquals(UiSemanticType.LINK, classify("android.widget.TextView", clickable = true))
    }

    @Test
    fun `rows of a list are list items and their own roles still win`() {
        assertEquals(UiSemanticType.LIST_ITEM, classify("android.widget.FrameLayout", parentType = UiSemanticType.LIST))
        assertEquals(UiSemanticType.LIST_ITEM, classify(null, parentType = UiSemanticType.LIST, clickable = true))
        // A labelled control inside a list is itself, not an anonymous row.
        assertEquals(UiSemanticType.BUTTON, classify("android.widget.Button", parentType = UiSemanticType.LIST))
        // Only a list confers row-ness.
        assertEquals(UiSemanticType.CONTAINER, classify("android.widget.FrameLayout", parentType = UiSemanticType.SCROLL_CONTAINER, hasChildren = true))
    }

    @Test
    fun `regions come from screen thirds`() {
        assertEquals(UiScreenRegion.TOP_LEFT, AccessibilityProjectionRules.regionOf(10, 10, 900, 1800))
        assertEquals(UiScreenRegion.CENTER, AccessibilityProjectionRules.regionOf(450, 900, 900, 1800))
        assertEquals(UiScreenRegion.BOTTOM_RIGHT, AccessibilityProjectionRules.regionOf(890, 1790, 900, 1800))
        assertEquals(UiScreenRegion.TOP, AccessibilityProjectionRules.regionOf(450, 10, 900, 1800))
        assertEquals(UiScreenRegion.RIGHT, AccessibilityProjectionRules.regionOf(890, 900, 900, 1800))
        assertNull(AccessibilityProjectionRules.regionOf(10, 10, 0, 0))
    }

    @Test
    fun `only unlabelled unmodified containers collapse`() {
        assertTrue(AccessibilityProjectionRules.isInertContainer(UiSemanticType.CONTAINER, hasLabel = false, selected = false, focused = false))
        assertFalse(AccessibilityProjectionRules.isInertContainer(UiSemanticType.CONTAINER, hasLabel = true, selected = false, focused = false))
        assertFalse(AccessibilityProjectionRules.isInertContainer(UiSemanticType.CONTAINER, hasLabel = false, selected = true, focused = false))
        assertFalse(AccessibilityProjectionRules.isInertContainer(UiSemanticType.CONTAINER, hasLabel = false, selected = false, focused = true))
        assertFalse(AccessibilityProjectionRules.isInertContainer(UiSemanticType.BUTTON, hasLabel = false, selected = false, focused = false))
    }
}
