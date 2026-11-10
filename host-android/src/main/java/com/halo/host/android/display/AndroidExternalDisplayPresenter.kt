package com.halo.host.android.display

import android.app.Activity
import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.widget.TextView
import com.halo.sdk.runtime.display.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID

/** Standard Android presentation output. Does not claim an XREAL USB, camera or pose driver. */
class AndroidExternalDisplayPresenter(context: Context, private val canPresent: () -> Boolean) : ExternalDisplayOutput {
    private val manager = context.getSystemService(DisplayManager::class.java)
    private val session = ExternalDisplaySession { UUID.randomUUID().toString() }
    private val mutableState = MutableStateFlow(session.state)
    override val state = mutableState.asStateFlow()
    private var activity: Activity? = null
    private var presentation: Presentation? = null
    private var listening = false
    private val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = refresh()
        override fun onDisplayRemoved(displayId: Int) = refresh(setOf(displayId))
        override fun onDisplayChanged(displayId: Int) = refresh(setOf(displayId))
    }

    /** Called by the resumed host activity, never by device discovery. */
    fun resume(host: Activity) {
        checkMainThread()
        if (activity !== host) { dismiss(); session.refresh(false, emptyList()) }
        activity = host
        if (!listening && manager != null) {
            manager.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
            listening = true
        }
        refresh()
    }

    fun pause(host: Activity) {
        checkMainThread()
        if (activity !== host) return
        dismiss()
        activity = null
        if (listening) manager?.unregisterDisplayListener(listener)
        listening = false
        session.refresh(false, emptyList())
        publish()
    }

    override suspend fun snapshot(): ExternalDisplayState = withContext(Dispatchers.Main.immediate) { refresh(); state.value }

    override suspend fun show(endpointToken: String, text: String, expectedRevision: Long?): ExternalDisplayReceipt =
        withContext(Dispatchers.Main.immediate) {
            refresh()
            val host = activity
            if (host == null || host.isFinishing || host.isDestroyed || !canPresent())
                return@withContext reject("Open and unlock Halo with grown-up access to show a card.")
            if (text.isBlank() || text.length > ExternalDisplaySession.MAX_TEXT_LENGTH)
                return@withContext reject("Use 1–400 characters for an external display card.")
            val displayId = session.resolve(endpointToken, expectedRevision)
                ?: return@withContext reject("Display selection changed. Select the connected display again.")
            val display = manager?.getDisplay(displayId)?.takeIf { it.isValid }
                ?: return@withContext reject("The selected display is unavailable.")
            dismiss()
            val next = TextPresentation(host, display, text)
            next.setOnDismissListener {
                if (presentation === next) {
                    presentation = null
                    session.cleared()
                    publish()
                }
            }
            try {
                next.show()
                presentation = next
                session.presented(endpointToken).also { publish() }
            } catch (_: android.view.WindowManager.InvalidDisplayException) {
                failed(next)
            } catch (_: android.view.WindowManager.BadTokenException) {
                failed(next)
            } catch (_: SecurityException) {
                failed(next)
            }
        }

    override suspend fun clear(expectedRevision: Long?, endpointToken: String?): ExternalDisplayReceipt =
        withContext(Dispatchers.Main.immediate) {
            refresh()
            if (expectedRevision != null && (expectedRevision != state.value.revision ||
                    endpointToken == null || session.resolve(endpointToken) == null || state.value.activeToken != endpointToken))
                return@withContext reject("Display output changed. Read the active display again before stopping it.")
            dismiss()
            session.cleared().also { publish() }
        }

    private fun refresh(invalidated: Set<Int> = emptySet()) {
        checkMainThread()
        val host = activity
        val foreground = host != null && !host.isFinishing && !host.isDestroyed
        val displays = if (!foreground) emptyList() else manager?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .orEmpty().filter { it.isValid && it.displayId != Display.DEFAULT_DISPLAY }.map {
                ExternalDisplayConnection(it.displayId, it.name, it.mode.physicalWidth, it.mode.physicalHeight)
            }
        session.refresh(foreground, displays, invalidated)
        if (session.state.activeToken != null && !canPresent()) session.cleared()
        if (session.state.activeToken == null) dismiss()
        publish()
    }

    private fun dismiss() {
        val previous = presentation
        presentation = null
        previous?.setOnDismissListener(null)
        previous?.dismiss()
    }
    private fun failed(next: Presentation): ExternalDisplayReceipt {
        next.setOnDismissListener(null)
        next.dismiss()
        session.cleared()
        return reject("Android could not open this display. Reconnect it and select it again.")
    }
    private fun reject(message: String) = session.rejected(message).also { publish() }
    private fun publish() { mutableState.value = session.state }
    private fun checkMainThread() { check(Looper.myLooper() == Looper.getMainLooper()) }

    private class TextPresentation(context: Context, display: Display, private val content: String) :
        Presentation(context, display, android.R.style.Theme_Material_NoActionBar) {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(TextView(context).apply {
                text = content
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.BLACK)
                gravity = Gravity.CENTER
                val inset = (32 * resources.displayMetrics.density).toInt()
                setPadding(inset, inset, inset, inset)
                setAutoSizeTextTypeUniformWithConfiguration(14, 36, 2, TypedValue.COMPLEX_UNIT_SP)
            })
        }
    }
}
