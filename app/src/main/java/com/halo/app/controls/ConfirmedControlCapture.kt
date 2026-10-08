package com.halo.app.controls

import com.halo.sdk.host.*

/** Sequencing seam around the existing capture/store/attachment owners; no transport or inference. */
internal suspend fun <T : Any> confirmedControlCapture(action: AccessoryAction,
    captureAndSave: suspend () -> T?, verifiedVision: suspend () -> Boolean,
    attachForQuestion: suspend (T) -> Boolean, openQuestion: suspend () -> AccessoryActionResult): AccessoryActionResult {
    require(action == AccessoryAction.TakePhoto || action == AccessoryAction.CaptureAndAsk)
    val saved = captureAndSave() ?: return AccessoryActionResult(AccessoryActionOutcome.FAILED, "No confirmed image received")
    if (action == AccessoryAction.TakePhoto) return AccessoryActionResult(AccessoryActionOutcome.EXECUTED, "Photo received and saved")
    if (!verifiedVision()) return AccessoryActionResult(AccessoryActionOutcome.UNAVAILABLE, "Photo saved · verified local vision unavailable")
    if (!attachForQuestion(saved)) return AccessoryActionResult(AccessoryActionOutcome.BLOCKED, "Photo saved · remove the existing attachment first")
    return openQuestion()
}
