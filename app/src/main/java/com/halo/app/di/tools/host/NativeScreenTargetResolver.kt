package com.halo.app.di.tools.host

import android.content.Context
import com.halo.cognition.language.NativeScreenActionIntent
import com.halo.cognition.language.NativeScreenVerb
import com.halo.host.android.accessibility.HaloAccessibilityService
import com.halo.host.android.accessibility.ScreenCaptureOutcome
import com.halo.host.android.resolver.AndroidApplicationResolver
import com.halo.runtime.cognition.native.NativeScreenTargetResolution
import com.halo.runtime.cognition.native.NativeForegroundObservation
import com.halo.runtime.cognition.native.NativeScreenTargetReadiness
import com.halo.runtime.cognition.ui.UiTargetSearch
import com.halo.sdk.runtime.UiInspectability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Resolves an explicit spoken screen target on demand, never from an earlier generation.
 * Does not execute an action or grant permission: the NativeCapabilityDispatcher hands
 * the exact-generation token to SkillExecutionService for regular tool authorization.
 */
class NativeScreenTargetResolver(context: Context) {
    private val apps = AndroidApplicationResolver(context)
    private companion object {
        const val MAX_FOREGROUND_CHECKS = 13 // bounded three-second launch/transition settle
        const val FOREGROUND_RETRY_MS = 250L
    }

    suspend fun resolve(intent: NativeScreenActionIntent, expectedApp: String?): NativeScreenTargetResolution {
        val expectedPackage = if (expectedApp != null) {
            apps.resolve(expectedApp)?.packageName ?: return NativeScreenTargetResolution.NeedsUser(
                "The requested app is missing or ambiguous. Name its exact installed app.")
        } else null
        // A command without a preceding app launch stays in the first observed
        // package throughout this bounded read-only wait. A package switch is
        // NOT permission to tap a similarly named control in another app.
        var scopedPackage = expectedPackage
        // Launch dispatch is not navigation proof. Allow only a short, explicit,
        // on-demand settle window before inspecting the next requested UI step.
        for (attempt in 0 until MAX_FOREGROUND_CHECKS) {
            val observation = withContext(Dispatchers.Main.immediate) {
                val service = HaloAccessibilityService.instance
                    ?: return@withContext NativeScreenTargetResolution.NeedsUser(
                        "Enable Halo's accessibility service to interact with the screen.")
                when (val capture = service.captureSnapshotOutcome()) {
                    is ScreenCaptureOutcome.PackageBlocked -> NativeScreenTargetResolution.NeedsUser(
                        "This app is protected. Please complete the step yourself.")
                    ScreenCaptureOutcome.NoActiveWindow -> NativeScreenTargetResolution.NeedsUser(
                        if (expectedPackage == null) "No inspectable app is in the foreground."
                        else "Waiting for the requested app to reach the foreground.")
                    is ScreenCaptureOutcome.Captured -> {
                        val snapshot = capture.snapshot
                        if (expectedPackage != null && snapshot.packageName != expectedPackage)
                            return@withContext NativeScreenTargetResolution.NeedsUser(
                                "Waiting for the requested app to reach the foreground.")
                        if (expectedPackage == null) {
                            if (scopedPackage == null) scopedPackage = snapshot.packageName
                            else if (snapshot.packageName != scopedPackage)
                                return@withContext NativeScreenTargetResolution.NeedsUser(
                                    "The foreground app changed while waiting. Review the screen and retry.")
                        }
                        // The proof is produced by the Android host from this exact
                        // package-matched capture, not from AI output or the launch intent.
                        val foreground = if (expectedPackage != null && expectedApp != null)
                            NativeForegroundObservation(expectedApp, expectedPackage, snapshot.capturedAtMs)
                            else null
                        fun ready(tool: String, args: Map<String, Any>) =
                            NativeScreenTargetResolution.Ready(tool, args, foreground)
                        // Global Back is an Android action, not a target from the
                        // accessibility tree. A captured but unlabelled screen may
                        // still be backed out of; protected packages and absent
                        // windows remain blocked above. The tool preflight must
                        // validate the same generation and available global action.
                        if (intent.verb == NativeScreenVerb.BACK) {
                            return@withContext ready(
                                "halo_ui_global_action",
                                mapOf("snapshotId" to snapshot.snapshotId, "action" to "back"))
                        }
                        val readiness = NativeScreenTargetReadiness.evaluate(snapshot, intent)
                        val candidate = when (readiness) {
                            is NativeScreenTargetReadiness.Decision.Ready -> readiness.node
                            NativeScreenTargetReadiness.Decision.RetryObservation ->
                                return@withContext NativeScreenTargetResolution.NeedsUser(
                                    "Waiting for an exact actionable control to appear.")
                            is NativeScreenTargetReadiness.Decision.NeedsUser -> {
                                val message = when (readiness.reason) {
                                    NativeScreenTargetReadiness.Reason.UNINSPECTABLE ->
                                        "This screen cannot be inspected safely. Please use the app manually."
                                    NativeScreenTargetReadiness.Reason.AMBIGUOUS ->
                                        "Several controls match the requested target. Please be more specific."
                                    NativeScreenTargetReadiness.Reason.MULTIPLE_SCROLL_PANELS ->
                                        "Several scrollable panels are visible. Scroll the desired panel manually."
                                    NativeScreenTargetReadiness.Reason.UNSAFE ->
                                        "The matching control does not support this action."
                                    NativeScreenTargetReadiness.Reason.INVALID_REQUEST ->
                                        "Name an exact screen control up to 200 characters."
                                }
                                return@withContext NativeScreenTargetResolution.NeedsUser(message)
                            }
                        }
                        val label = intent.target.trim()
                        val token = candidate.token!!
                        val arguments: Map<String, Any> = mapOf("snapshotId" to snapshot.snapshotId, "token" to token)
                        when (intent.verb) {
                            NativeScreenVerb.FIND -> ready(
                                "halo_find_on_screen", mapOf("snapshotId" to snapshot.snapshotId, "query" to label,
                                    "exactOnly" to true))
                            NativeScreenVerb.TAP -> ready(
                                "halo_ui_click", arguments + ("expectedEffect" to "NONE"))
                            NativeScreenVerb.ENTER_TEXT -> {
                                val value = intent.value?.takeIf { it.isNotBlank() && it.length <= 2000 }
                                    ?: return@withContext NativeScreenTargetResolution.NeedsUser("Please specify text up to 2000 characters.")
                                ready(
                                    "halo_ui_set_text", arguments + ("text" to value))
                            }
                            NativeScreenVerb.SCROLL_FORWARD, NativeScreenVerb.SCROLL_BACKWARD ->
                                ready("halo_ui_scroll",
                                    arguments + ("direction" to
                                        if (intent.verb == NativeScreenVerb.SCROLL_FORWARD) "forward" else "backward"))
                            NativeScreenVerb.BACK -> NativeScreenTargetResolution.NeedsUser(
                                "The Back action is unavailable on this screen.")
                        }
                    }
                }
            }
            val waitingForApp = observation is NativeScreenTargetResolution.NeedsUser &&
                expectedPackage != null && observation.reason.startsWith("Waiting for the requested app")
            val waitingForControl = observation is NativeScreenTargetResolution.NeedsUser &&
                observation.reason == "Waiting for an exact actionable control to appear."
            if (!waitingForApp && !waitingForControl) return observation
            if (attempt + 1 < MAX_FOREGROUND_CHECKS) delay(FOREGROUND_RETRY_MS)
        }
        return NativeScreenTargetResolution.NeedsUser(
            "The requested app or exact control did not become available. Check the current screen and retry.")
    }
}
