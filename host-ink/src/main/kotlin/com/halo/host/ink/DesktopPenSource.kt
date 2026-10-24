package com.halo.host.ink

import com.halo.sdk.runtime.ink.*
import kotlinx.coroutines.*
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Native host boundary only. Coordinates/axes are normalized before any runtime envelope. */
class DesktopPenSource(
    initial: InkDocument,
    private val localDraft: Path,
    private val sender: InkSenderHost,
    private val activation: InkActivationPolicy = InkActivationPolicy.QUALIFICATION_PENDING,
) : Closeable {
    private val editor = InkEditor(initial)
    private var pointer = -1L
    private var startedAt = 0L
    private var previousAt = 0L
    private var job: Job? = null
    private var loaded = false

    init { require(initial == sender.offer.document) }

    /** Requires an explicitly selected existing evdev pen. Never opens or injects keyboard/mouse events. */
    fun startLinux(devicePath: String, scope: CoroutineScope) {
        require(devicePath.matches(Regex("/dev/input/event[0-9]{1,5}")))
        start(scope) { readLinux(devicePath) }
    }
    /** Windows accepts WM_POINTER pen events only inside its own opt-in drawing window. */
    fun startWindows(scope: CoroutineScope) = start(scope) { readWindows() }

    private fun start(scope: CoroutineScope, read: () -> Unit) {
        check(activation.enabled() && job?.isActive != true)
        synchronized(NATIVE_OWNER) {
            check(!nativeInUse) { "Another pen surface is active" }
            System.loadLibrary("halo_ink_pen")
            prepareNative()
            loaded = true; nativeInUse = true; nativeOwner = this
        }
        job = scope.launch(Dispatchers.IO) {
            try {
                readPenUntilCancelled(stop = ::stopIfCurrent) {
                    if (activation.enabled()) read()
                }
            }
            finally {
                synchronized(this@DesktopPenSource) { editor.cancel(); pointer = -1 }
                sender.close()
                synchronized(NATIVE_OWNER) { if (nativeOwner === this@DesktopPenSource) { nativeInUse = false; nativeOwner = null } }
            }
        }
        job?.invokeOnCompletion {
            synchronized(NATIVE_OWNER) { if (nativeOwner === this) { nativeInUse = false; nativeOwner = null } }
        }
    }

    /** JNI calls on one host worker; no native packet or pointer handle crosses into runtime. */
    @Suppress("unused")
    @Synchronized fun onNativeSample(action: Int, pointerId: Long, atMs: Long, x: Int, y: Int,
        pressure: Int, tilt: Int, eraser: Boolean) {
        if (!activation.enabled()) { editor.cancel(); pointer = -1; if (loaded) stopNative(); return }
        if (action == CANCEL) { editor.cancel(); pointer = -1; sender.clearPreview(); return }
        if (action == DOWN) {
            editor.cancel(); pointer = -1
            if (atMs < 0 || !editor.begin(InkTool.STYLUS, eraser)) return
            pointer = pointerId; startedAt = atMs; previousAt = atMs
        }
        if (pointerId != pointer || pointer == -1L || atMs < previousAt) return
        val elapsed = atMs - startedAt
        val point = InkPoint(x, y, elapsed.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            pressure.takeIf { it >= 0 }, tilt.takeIf { it >= 0 })
        if (!InkLimits.valid(point)) { editor.cancel(); pointer = -1; return }
        previousAt = atMs
        editor.sample(point)
        if (action == UP) {
            pointer = -1
            if (editor.finish()) {
                // Atomic local recovery precedes transmission. A failed save never sends ink.
                InkLocalDraftStore(localDraft).save(editor.document)
                sender.checkpoint(editor.document)
            }
        } else if (action == MOVE || action == DOWN) {
            val saved = editor.document.strokes.map { it.strokeId }.toSet()
            editor.preview().strokes.lastOrNull()?.takeIf { it.strokeId !in saved }?.let(sender::preview)
        }
    }

    override fun close() {
        stopIfCurrent()
        job?.cancel(); sender.close()
    }
    private fun stopIfCurrent() {
        synchronized(NATIVE_OWNER) { if (loaded && nativeOwner === this) stopNative() }
    }
    private external fun prepareNative()
    private external fun readWindows()
    private external fun readLinux(devicePath: String)
    private external fun stopNative()

    companion object {
        const val DOWN = 1; const val MOVE = 2; const val UP = 3; const val CANCEL = 4
        private val NATIVE_OWNER = Any()
        private var nativeInUse = false
        private var nativeOwner: DesktopPenSource? = null
    }
}
