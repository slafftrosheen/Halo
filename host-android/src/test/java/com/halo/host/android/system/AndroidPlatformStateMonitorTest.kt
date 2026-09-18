package com.halo.host.android.system

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

@RunWith(RobolectricTestRunner::class)
class AndroidPlatformStateMonitorTest {

    @Test
    fun microphonePermissionRefreshTracksGrantAndRevoke() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val shadowApplication = Shadows.shadowOf(application)
        shadowApplication.denyPermissions(Manifest.permission.RECORD_AUDIO)

        val monitor = AndroidPlatformStateMonitor(application)
        try {
            monitor.refreshMicrophonePermission()
            assertFalse(monitor.microphonePermissionGranted.value)

            shadowApplication.grantPermissions(Manifest.permission.RECORD_AUDIO)
            monitor.refreshMicrophonePermission()
            assertTrue(monitor.microphonePermissionGranted.value)

            shadowApplication.denyPermissions(Manifest.permission.RECORD_AUDIO)
            monitor.refreshMicrophonePermission()
            assertFalse(monitor.microphonePermissionGranted.value)
        } finally {
            monitor.close()
        }
    }
}
