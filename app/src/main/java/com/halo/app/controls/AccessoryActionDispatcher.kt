package com.halo.app.controls

import com.halo.sdk.host.*
import kotlinx.coroutines.CancellationException

/** Resolves existing owners only. No tool or approval entrypoint is accepted here. */
class AccessoryActionDispatcher(private val execute: suspend (AccessoryActionSelection, ControlEndpoint) -> AccessoryActionResult) {
    suspend fun dispatch(selection: AccessoryActionSelection, endpoint: ControlEndpoint): AccessoryActionResult {
        if (!endpoint.connected) return AccessoryActionResult(AccessoryActionOutcome.BLOCKED, "Device disconnected")
        if (selection.action in setOf(AccessoryAction.SystemDefault, AccessoryAction.DoNothing))
            return AccessoryActionResult(AccessoryActionOutcome.IGNORED, "Managed by system")
        if (selection.action !in endpoint.availableActions)
            return AccessoryActionResult(AccessoryActionOutcome.UNAVAILABLE, "Required owner or capability unavailable")
        return try { execute(selection, endpoint) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { AccessoryActionResult(AccessoryActionOutcome.FAILED, "Control action failed") }
    }
}
