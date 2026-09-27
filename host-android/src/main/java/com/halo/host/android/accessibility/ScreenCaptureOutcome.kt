package com.halo.host.android.accessibility

import com.halo.sdk.runtime.ScreenContextSnapshot

/**
 * Why a screen observation produced a snapshot, or why it produced none.
 *
 * Halo used to answer this with a nullable snapshot, which collapsed three different truths —
 * nothing to observe, a package Halo refuses to observe, and a successfully observed screen into
 * one failure message. The distinction matters to a model: "try again in a moment", "never on this
 * app" and "there is nothing here" call for three different next actions.
 */
sealed interface ScreenCaptureOutcome {

    data class Captured(val snapshot: ScreenContextSnapshot) : ScreenCaptureOutcome

    /** No window currently holds input focus; usually a transition, so retrying is reasonable. */
    data object NoActiveWindow : ScreenCaptureOutcome

    /** The foreground package is blocked by Halo's own policy, by name or by sensitive-app marker. */
    data class PackageBlocked(
        val packageName: String?,
        val reason: PackageBlockReason,
    ) : ScreenCaptureOutcome
}
