// halo DevKit 2.0 · Package B — task / approval / receipt motion binding (reference).
// Rule: animation follows REAL runtime events only. Stop cancels everything pending.
package com.halo.app.ui.fx

import com.halo.app.ui.screens.home.HomeToolReceiptKind

/** Lottie assets live in assets/halo/fx/<name>_<dark|light>.json, still SVGs in assets/halo/fx/static/. */
enum class HaloTaskFx(val asset: String, val marker: String?, val reducedStill: String) {
    STEP_RUNNING("halo_fx_task_step", "running", "halo_fx_task_step_running"),
    STEP_DONE("halo_fx_task_step", "done", "halo_fx_task_step_done"),
    NEEDS_YOU("halo_fx_approval_breathe", "needs_you", "halo_fx_approval_breathe_needs_you"), // Apricot. Never rotates.
    RECEIPT_CONFIRMED("halo_fx_receipt", "confirmed", "halo_fx_receipt_confirmed"),
    RECEIPT_UNCONFIRMED("halo_fx_receipt", "unconfirmed", "halo_fx_receipt_unconfirmed"),        // dashed — never a check
    STOP("halo_fx_stop_cancel", "stop", "halo_fx_stop_cancel_stop"),
}

/** Map the receipt the runtime actually produced. FAILED has no celebration: show the ERROR orb instead. */
fun receiptFx(kind: HomeToolReceiptKind): HaloTaskFx? = when (kind) {
    HomeToolReceiptKind.CONFIRMED -> HaloTaskFx.RECEIPT_CONFIRMED
    HomeToolReceiptKind.UNVERIFIED -> HaloTaskFx.RECEIPT_UNCONFIRMED
    HomeToolReceiptKind.FAILED -> null
}

/**
 * Global Stop contract: when the user taps Stop (or barges in), call this before anything else.
 * - plays STOP once (or swaps instantly with reduced motion)
 * - drops any queued success / bloom so a late receipt can't celebrate a cancelled task
 */
class HaloFxQueue {
    private val pending = ArrayDeque<HaloTaskFx>()
    var stopped = false; private set
    fun post(fx: HaloTaskFx) { if (!stopped || fx == HaloTaskFx.STOP) pending.addLast(fx) }
    fun stop() { pending.clear(); stopped = true; pending.addLast(HaloTaskFx.STOP) }
    fun resumeNewTurn() { stopped = false }
    fun next(): HaloTaskFx? = pending.removeFirstOrNull()
}
