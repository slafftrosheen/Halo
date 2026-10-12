package com.halo.runtime.cognition.native

import com.halo.cognition.language.NativeScreenActionIntent
import com.halo.cognition.language.NativeScreenVerb
import com.halo.runtime.cognition.ui.UiTargetSearch
import com.halo.sdk.runtime.ScreenContextNode
import com.halo.sdk.runtime.ScreenContextSnapshot
import com.halo.sdk.runtime.UiInspectability

/**
 * A pure, provider-independent admission gate for the NEXT scripted screen step.
 *
 * Missing controls may appear as the app settles: observing again is harmless.
 * Ambiguity, protected/unreadable trees and unsafe targets never cause searching,
 * tapping, scrolling or choosing a candidate.
 */
object NativeScreenTargetReadiness {
    sealed interface Decision {
        data class Ready(val node: ScreenContextNode) : Decision
        /** The requested exact semantic target is not yet exposed. Observation only. */
        data object RetryObservation : Decision
        /** Never retry ambiguous, unsafe or non-inspectable selections. */
        data class NeedsUser(val reason: Reason) : Decision
    }
    enum class Reason { UNINSPECTABLE, AMBIGUOUS, UNSAFE, MULTIPLE_SCROLL_PANELS, INVALID_REQUEST }

    fun evaluate(snapshot: ScreenContextSnapshot, intent: NativeScreenActionIntent): Decision {
        if (snapshot.inspectability != UiInspectability.INSPECTABLE || snapshot.truncated)
            return Decision.NeedsUser(Reason.UNINSPECTABLE)
        val scrolling = intent.verb in setOf(NativeScreenVerb.SCROLL_FORWARD, NativeScreenVerb.SCROLL_BACKWARD)
        val candidates = if (scrolling) snapshot.nodes.filter {
            !it.sensitive && it.enabled && !it.token.isNullOrBlank() && it.scrollable
        } else {
            if (intent.target.isBlank() || intent.target.length > 200)
                return Decision.NeedsUser(Reason.INVALID_REQUEST)
            UiTargetSearch.findExact(snapshot, intent.target.trim())
        }
        val actionable = candidates.filter { node ->
            when (intent.verb) {
                NativeScreenVerb.FIND -> true
                NativeScreenVerb.TAP -> node.clickable
                NativeScreenVerb.ENTER_TEXT -> node.editable
                NativeScreenVerb.SCROLL_FORWARD, NativeScreenVerb.SCROLL_BACKWARD -> node.scrollable
                NativeScreenVerb.BACK -> false // handled as a global action before this gate
            }
        }
        if (actionable.size > 1) return Decision.NeedsUser(
            if (scrolling) Reason.MULTIPLE_SCROLL_PANELS else Reason.AMBIGUOUS)
        if (actionable.size == 1) return Decision.Ready(actionable.single())
        // A visible exact match with the wrong capability is not expected to
        // become actionable by waiting; treat it as a user correction instead.
        if (candidates.isNotEmpty()) return Decision.NeedsUser(Reason.UNSAFE)
        return Decision.RetryObservation
    }
}
