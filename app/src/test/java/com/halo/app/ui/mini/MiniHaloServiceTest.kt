package com.halo.app.ui.mini

import android.app.Application
import android.app.Service
import android.content.Intent
import android.provider.Settings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class MiniHaloServiceTest {
    @Test fun deniedOverlayAccessDoesNotPublishARunningBubble() {
        val controller = Robolectric.buildService(FloatingHaloService::class.java).create()
        val service = controller.get()
        assertFalse(Settings.canDrawOverlays(service))
        assertEquals(Service.START_NOT_STICKY, service.onStartCommand(Intent(service,FloatingHaloService::class.java),0,1))
        assertFalse(FloatingHaloService.running.value)
        controller.destroy()
        assertFalse(FloatingHaloService.running.value)
    }
}
