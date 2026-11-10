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
import android.widget.LinearLayout
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
    private var presentation: TextPresentation? = null
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

    override suspend fun show(endpointToken: String, text: String, expectedRevision: Long?, requestAllowed: () -> Boolean): ExternalDisplayReceipt =
        withContext(Dispatchers.Main.immediate) {
            refresh()
            val host = activity
            if (host == null || host.isFinishing || host.isDestroyed || !canPresent() || !requestAllowed())
                return@withContext reject("Open and unlock Halo with grown-up access to show a card.")
            val pages = ExternalDisplaySession.paginate(text)
                ?: return@withContext reject("Use 1–8000 characters and at most 80 pages. Reduce excess line breaks if needed.")
            val displayId = session.resolve(endpointToken, expectedRevision)
                ?: return@withContext reject("Display selection changed. Select the connected display again.")
            val display = manager?.getDisplay(displayId)?.takeIf { it.isValid }
                ?: return@withContext reject("The selected display is unavailable.")
            dismiss()
            val next = TextPresentation(host, display, pages.first(), pages.size)
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
                session.presented(endpointToken, pages).also { publish() }
            } catch (_: android.view.WindowManager.InvalidDisplayException) {
                failed(next)
            } catch (_: android.view.WindowManager.BadTokenException) {
                failed(next)
            } catch (_: SecurityException) {
                failed(next)
            }
        }

    override suspend fun page(endpointToken: String, page: Int, expectedRevision: Long, requestAllowed: () -> Boolean): ExternalDisplayReceipt =
        withContext(Dispatchers.Main.immediate) {
            refresh()
            if (!canPresent() || !requestAllowed())
                return@withContext reject("Display access changed. Open and unlock Halo to continue.")
            val content = session.pageText(endpointToken, page, expectedRevision)
                ?: return@withContext reject("The display, text or page changed. Read its current state and select a valid page.")
            val current = presentation?.takeIf { it.isShowing }
            if (current == null) {
                session.cleared()
                return@withContext reject("The display presentation is no longer available.")
            }
            current.showPage(content, page, state.value.pageCount)
            session.pagePresented(endpointToken, page, expectedRevision).also { publish() }
        }

    override suspend fun clear(expectedRevision: Long?, endpointToken: String?, requestAllowed: () -> Boolean): ExternalDisplayReceipt =
        withContext(Dispatchers.Main.immediate) {
            refresh()
            if (!requestAllowed()) return@withContext reject("Display access changed. Read its current state before stopping it.")
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

    private class TextPresentation(context: Context, display: Display, private val content: String, private val totalPages: Int) :
        Presentation(context, display, android.R.style.Theme_Material_NoActionBar) {
        private lateinit var pageText: TextView
        private lateinit var pageNumber: TextView
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            pageText = TextView(context).apply {
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setAutoSizeTextTypeUniformWithConfiguration(14, 36, 2, TypedValue.COMPLEX_UNIT_SP)
            }
            pageNumber = TextView(context).apply {
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            }
            setContentView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.BLACK)
                val inset = (32 * resources.displayMetrics.density).toInt()
                setPadding(inset, inset, inset, inset)
                addView(pageText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
                addView(pageNumber, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            })
            showPage(content, 1, totalPages)
        }

        fun showPage(content: String, page: Int, pageCount: Int) {
            pageText.text = content
            pageNumber.text = "$page / $pageCount"
        }
    }
}
