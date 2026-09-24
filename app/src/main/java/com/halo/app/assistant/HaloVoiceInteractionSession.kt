package com.halo.app.assistant

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.halo.host.android.assistant.ScreenContextExtractor
import com.halo.sdk.runtime.HostInvocation
import com.halo.sdk.runtime.InvocationSource
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class HaloVoiceInteractionSession(
    context: Context,
    private val runtimeBridge: AssistantRuntimeBridge,
    private val contextExtractor: ScreenContextExtractor = ScreenContextExtractor(),
) : VoiceInteractionSession(context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var active: ActiveInvocation? = null
    private var statusText: TextView? = null
    private var contextText: TextView? = null

    private data class ActiveInvocation(
        val invocation: HostInvocation,
        val ready: CompletableDeferred<Unit> = CompletableDeferred(),
        var screenshotAttached: Boolean = false,
    )

    override fun onCreateContentView(): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(56, 48, 56, 40)
        setBackgroundColor(Color.rgb(248, 246, 252))
        addView(TextView(context).apply {
            text = "Halo"
            textSize = 24f
            setTextColor(Color.rgb(43, 38, 52))
            gravity = Gravity.CENTER
        }, matchWidth())
        addView(TextView(context).also { statusText = it }.apply {
            text = "Ready"
            textSize = 18f
            setTextColor(Color.rgb(79, 68, 95))
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 12)
        }, matchWidth())
        addView(TextView(context).also { contextText = it }.apply {
            text = "Waiting for screen context"
            textSize = 14f
            setTextColor(Color.rgb(107, 99, 117))
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 24)
        }, matchWidth())
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(Button(context).apply {
                text = "Stop"
                setOnClickListener { hide() }
            })
            addView(Button(context).apply {
                text = "Open Halo"
                setOnClickListener {
                    context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launchIntent ->
                        context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    hide()
                }
            })
        }, matchWidth())
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        active?.let { previous -> scope.launch { runtimeBridge.end(previous.invocation.interactionId, "superseded") } }
        val inputDeviceId = args?.getInt(Intent.EXTRA_ASSIST_INPUT_DEVICE_ID, -1)?.takeIf { it >= 0 }
        val invocation = HostInvocation(
            interactionId = UUID.randomUUID().toString(),
            source = InvocationSource.ANDROID_ASSISTANT,
            invokedAtMs = System.currentTimeMillis(),
            inputDeviceId = inputDeviceId,
            requestedAssistContext = showFlags and SHOW_WITH_ASSIST != 0,
            requestedScreenshot = showFlags and SHOW_WITH_SCREENSHOT != 0,
        )
        val current = ActiveInvocation(invocation)
        active = current
        statusText?.text = "Listening…"
        contextText?.text = if (invocation.requestedAssistContext) "Requesting screen context" else "Screen context not requested"
        scope.launch {
            try {
                runtimeBridge.begin(invocation)
                current.ready.complete(Unit)
                if (active === current) {
                    val listening = runtimeBridge.startListening(invocation.interactionId, inputDeviceId)
                    statusText?.text = if (listening) "Listening…" else "Microphone unavailable"
                }
            } catch (error: Throwable) {
                current.ready.completeExceptionally(error)
                if (active === current) statusText?.text = "Halo could not start"
            }
        }
    }

    override fun onHandleAssist(state: AssistState) {
        super.onHandleAssist(state)
        val current = active ?: return
        val snapshot = contextExtractor.extract(state.assistStructure, state.assistContent)
        scope.launch {
            runCatching { current.ready.await(); runtimeBridge.attachScreen(current.invocation.interactionId, snapshot) }
                .onSuccess {
                    if (active === current) contextText?.text = "Screen context attached · ${snapshot.packageName ?: "current app"}"
                }
                .onFailure { if (active === current) contextText?.text = "Screen context unavailable" }
        }
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        super.onHandleScreenshot(screenshot)
        val bitmap = screenshot?.copy(screenshot.config ?: Bitmap.Config.ARGB_8888, false) ?: return
        val current = active ?: return
        if (current.screenshotAttached) return
        scope.launch {
            runCatching { current.ready.await(); runtimeBridge.attachScreenshot(current.invocation.interactionId, bitmap) }
                .onSuccess { current.screenshotAttached = true }
            bitmap.recycle()
        }
    }

    override fun onHide() {
        val ending = active
        active = null
        if (ending != null) scope.launch { runtimeBridge.end(ending.invocation.interactionId, "hidden") }
        statusText?.text = "Ready"
        super.onHide()
    }

    override fun onDestroy() {
        active?.let { ending ->
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                runtimeBridge.end(ending.invocation.interactionId, "destroyed")
            }
        }
        active = null
        scope.cancel()
        super.onDestroy()
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )
}
