package com.halo.app.assistant

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.halo.sdk.runtime.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34], application = android.app.Application::class)
class NativeAssistantSessionTest {
    private val bridge = object : AssistantRuntimeBridge {
        override suspend fun begin(invocation: HostInvocation) = Unit
        override suspend fun attachScreen(interactionId: String, snapshot: ScreenContextSnapshot) = Unit
        override suspend fun attachScreenshot(interactionId: String, bitmap: Bitmap) = Unit
        override suspend fun startListening(interactionId: String, inputDeviceId: Int?) = true
        override suspend fun hideAssistantSurface(interactionId: String) = Unit
        override suspend fun cancelAssistantInteraction(interactionId: String, reason: String) = Unit
    }
    private fun session(scope: CoroutineScope) = HaloVoiceInteractionSession(
        ApplicationProvider.getApplicationContext<Context>(), bridge, applicationScope = scope)
    @Test fun commandNegotiationDoesNotAdvertiseArbitraryPhoneExecution() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val session = session(scope)
        assertArrayEquals(booleanArrayOf(true, false, true, false), session.onGetSupportedCommands(arrayOf(
            "com.halo.assistant.CAPABILITIES", "halo_volume", "com.halo.assistant.STOP", "android.intent.action.CALL")))
        assertEquals(0, session.onGetSupportedCommands(emptyArray()).size)
        scope.cancel()
    }
    @Test fun screenshotWithoutAnActiveInvocationIsIgnoredWithoutRecyclingTheSystemBitmap() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        session(scope).onHandleScreenshot(bitmap)
        assertFalse(bitmap.isRecycled)
        bitmap.recycle(); scope.cancel()
    }
}
