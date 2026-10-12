package com.halo.app.di.tools.host

import android.content.Context
import com.halo.cognition.language.NativeScreenActionIntent
import com.halo.cognition.language.NativeScreenVerb
import com.halo.host.android.accessibility.HaloAccessibilityService
import com.halo.host.android.accessibility.ScreenCaptureOutcome
import com.halo.host.android.resolver.AndroidApplicationResolver
import com.halo.runtime.cognition.native.NativeScreenTargetResolution
import com.halo.runtime.cognition.native.NativeForegroundObservation
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
                        if (snapshot.inspectability != UiInspectability.INSPECTABLE || snapshot.truncated)
                            return@withContext NativeScreenTargetResolution.NeedsUser(
                                "This screen cannot be inspected safely. Please use the app manually.")
                        // Only exact accessible labels can authorize a scripted step.
                        // Editable values and substring guesses are never target names.
                        val label = intent.target.trim()
                        val scrolling = intent.verb == NativeScreenVerb.SCROLL_FORWARD ||
                            intent.verb == NativeScreenVerb.SCROLL_BACKWARD
                        val eligible = if (scrolling) snapshot.nodes.filter {
                            !it.sensitive && it.enabled && !it.token.isNullOrBlank() && it.scrollable
                        } else UiTargetSearch.findExact(snapshot, label)
                        val matches = eligible.filter { node ->
                            when (intent.verb) {
                                NativeScreenVerb.FIND -> true
                                NativeScreenVerb.TAP -> node.clickable
                                NativeScreenVerb.ENTER_TEXT -> node.editable
                                NativeScreenVerb.SCROLL_FORWARD, NativeScreenVerb.SCROLL_BACKWARD -> node.scrollable
                                NativeScreenVerb.BACK -> false
                            }
                        }
                        if (matches.size != 1) return@withContext NativeScreenTargetResolution.NeedsUser(
                            if (matches.isEmpty()) "I could not find an exact, actionable '$label' on this screen."
                            else if (scrolling) "Several scrollable panels are visible. Scroll the desired panel manually."
                            else "Several controls match '$label'. Please name a more specific target.")
                        val token = matches.single().token!!
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
            if (observation !is NativeScreenTargetResolution.NeedsUser ||
                expectedPackage == null || !observation.reason.startsWith("Waiting for the requested app")) return observation
            if (attempt + 1 < MAX_FOREGROUND_CHECKS) delay(FOREGROUND_RETRY_MS)
        }
        return NativeScreenTargetResolution.NeedsUser(
            "The requested app did not reach the foreground. Open it and retry the screen step.")
    }
}
