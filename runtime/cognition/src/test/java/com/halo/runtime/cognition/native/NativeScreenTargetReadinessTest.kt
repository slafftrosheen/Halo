package com.halo.runtime.cognition.native

import com.halo.cognition.language.NativeScreenActionIntent
import com.halo.cognition.language.NativeScreenVerb
import com.halo.sdk.runtime.*
import org.junit.Assert.*
import org.junit.Test

class NativeScreenTargetReadinessTest {
    private fun node(label: String, token: String = "fresh",
        clickable: Boolean = false, editable: Boolean = false, scrollable: Boolean = false,
        sensitive: Boolean = false, enabled: Boolean = true, labelHint: String? = null
    ) = ScreenContextNode(text = label, contentDescription = null, className = null,
        viewId = null, clickable = clickable, editable = editable, bounds = null,
        scrollable = scrollable, sensitive = sensitive, enabled = enabled, token = token, labelHint = labelHint)

    private fun snapshot(vararg nodes: ScreenContextNode, truncated: Boolean = false,
        inspectability: UiInspectability = UiInspectability.INSPECTABLE
    ) = ScreenContextSnapshot(packageName = "com.example.mail", activityName = "Inbox",
        title = null, webUri = null, nodes = nodes.toList(), capturedAtMs = 1000L,
        truncated = truncated, inspectability = inspectability)

    private fun intent(verb: NativeScreenVerb, target: String = "Search") =
        NativeScreenActionIntent(verb, target)

    @Test fun absentTargetMayOnlyTriggerReadOnlyResampling() {
        val decision = NativeScreenTargetReadiness.evaluate(
            snapshot(node("Inbox", clickable = true)), intent(NativeScreenVerb.TAP))
        assertEquals(NativeScreenTargetReadiness.Decision.RetryObservation, decision)
    }

    @Test fun exactUniqueCapabilityIsRequiredForTapAndType() {
        val screen = snapshot(node("Search", token = "generation-5", clickable = true,
            editable = true, labelHint = "Search"))
        val tap = NativeScreenTargetReadiness.evaluate(screen, intent(NativeScreenVerb.TAP))
        val type = NativeScreenTargetReadiness.evaluate(screen, intent(NativeScreenVerb.ENTER_TEXT))
        assertEquals("generation-5", (tap as NativeScreenTargetReadiness.Decision.Ready).node.token)
        assertEquals("generation-5", (type as NativeScreenTargetReadiness.Decision.Ready).node.token)
    }

    @Test fun duplicateOrUnsupportedTargetDoesNotWaitOrGuess() {
        val duplicate = snapshot(
            node("Search", "one", clickable = true), node("Search", "two", clickable = true))
        assertEquals(
            NativeScreenTargetReadiness.Decision.NeedsUser(NativeScreenTargetReadiness.Reason.AMBIGUOUS),
            NativeScreenTargetReadiness.evaluate(duplicate, intent(NativeScreenVerb.TAP)))
        val wrongCapability = snapshot(node("Search", clickable = true))
        assertEquals(
            NativeScreenTargetReadiness.Decision.NeedsUser(NativeScreenTargetReadiness.Reason.UNSAFE),
            NativeScreenTargetReadiness.evaluate(wrongCapability, intent(NativeScreenVerb.ENTER_TEXT)))
    }

    @Test fun redactedOrTruncatedTreesNeverPermitActions() {
        val sensitive = snapshot(node("Search", clickable = true, sensitive = true))
        assertEquals(
            NativeScreenTargetReadiness.Decision.NeedsUser(NativeScreenTargetReadiness.Reason.UNSAFE),
            NativeScreenTargetReadiness.evaluate(sensitive, intent(NativeScreenVerb.TAP)))
        val truncated = snapshot(node("Search", clickable = true), truncated = true)
        assertEquals(
            NativeScreenTargetReadiness.Decision.NeedsUser(NativeScreenTargetReadiness.Reason.UNINSPECTABLE),
            NativeScreenTargetReadiness.evaluate(truncated, intent(NativeScreenVerb.TAP)))
        val missingToken = snapshot(node("Search", token = "", clickable = true))
        assertEquals(
            NativeScreenTargetReadiness.Decision.NeedsUser(NativeScreenTargetReadiness.Reason.UNSAFE),
            NativeScreenTargetReadiness.evaluate(missingToken, intent(NativeScreenVerb.TAP)))
        val disabled = snapshot(node("Search", clickable = true, enabled = false))
        assertEquals(
            NativeScreenTargetReadiness.Decision.NeedsUser(NativeScreenTargetReadiness.Reason.UNSAFE),
            NativeScreenTargetReadiness.evaluate(disabled, intent(NativeScreenVerb.TAP)))
    }

    @Test fun scrollsRequireOneUniqueLiveScrollContainer() {
        val one = snapshot(node("List", "scroll", scrollable = true))
        assertTrue(NativeScreenTargetReadiness.evaluate(one,
            intent(NativeScreenVerb.SCROLL_FORWARD)) is NativeScreenTargetReadiness.Decision.Ready)
        val multiple = snapshot(node("Left", "a", scrollable = true),
            node("Right", "b", scrollable = true))
        assertEquals(
            NativeScreenTargetReadiness.Decision.NeedsUser(NativeScreenTargetReadiness.Reason.MULTIPLE_SCROLL_PANELS),
            NativeScreenTargetReadiness.evaluate(multiple, intent(NativeScreenVerb.SCROLL_BACKWARD)))
    }

    @Test fun findingDoesNotAuthorizeMutation() {
        val screen = snapshot(node("Search"))
        assertTrue(NativeScreenTargetReadiness.evaluate(screen,
            intent(NativeScreenVerb.FIND)) is NativeScreenTargetReadiness.Decision.Ready)
        assertEquals(NativeScreenTargetReadiness.Decision.NeedsUser(
            NativeScreenTargetReadiness.Reason.UNSAFE),
            NativeScreenTargetReadiness.evaluate(screen, intent(NativeScreenVerb.TAP)))
    }
}
