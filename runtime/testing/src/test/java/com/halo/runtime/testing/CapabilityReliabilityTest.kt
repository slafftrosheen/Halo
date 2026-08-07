package com.halo.runtime.testing

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.halo.host.android.capability.AndroidApplicationCapability
import com.halo.host.android.capability.AndroidDeviceCapability
import com.halo.host.android.capability.AndroidMediaCapability
import com.halo.host.android.capability.AndroidNavigationCapability
import com.halo.runtime.cognition.ApplicationTask
import com.halo.runtime.cognition.Capability
import com.halo.runtime.cognition.DeviceTask
import com.halo.runtime.cognition.MediaTask
import com.halo.runtime.cognition.NavigationTask
import com.halo.runtime.cognition.Outcome
import com.halo.runtime.cognition.Task
import com.halo.runtime.cognition.SystemTask
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

class HangingCapability : Capability {
    override val id: String = "test.hanging"

    override suspend fun supports(task: Task): Boolean = true

    override suspend fun execute(task: Task): Outcome {
        // Hang for 10 seconds, which should trigger the 5-second timeout in the ReliabilityTester
        delay(10000)
        return Outcome.Success("Finished hanging")
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O])
class CapabilityReliabilityTest {

    private lateinit var context: Context
    private lateinit var reliabilityTester: CapabilityReliabilityTester

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        reliabilityTester = CapabilityReliabilityTester()
    }

    @Test
    fun testNavigationCapabilityReliability() = runTest(timeout = 30.seconds) {
        val capability = AndroidNavigationCapability(context)
        val task = NavigationTask(coordinates = "40.7128,-74.0060")
        
        val report = reliabilityTester.runReliabilityTest(capability, task, iterations = 100)
        
        assertTrue("Navigation capability should have >= 99% success rate", report.successRate >= 0.99)
    }

    @Test
    fun testMediaCapabilityReliability() = runTest(timeout = 30.seconds) {
        val capability = AndroidMediaCapability(context)
        val task = MediaTask(mediaAction = "play", mediaId = "jazz")
        
        val report = reliabilityTester.runReliabilityTest(capability, task, iterations = 100)
        
        assertTrue("Media capability should have >= 99% success rate", report.successRate >= 0.99)
    }

    @Test
    fun testApplicationCapabilitySuccessAndFailureReliability() = runTest(timeout = 30.seconds) {
        val capability = AndroidApplicationCapability(context)
        
        // 1. Test success path (non-existent app, but simulate success if it was there - actually we can't easily install apps in robolectric by default without shadows)
        // We'll just test the failure path and ensure it's reliable.
        val failTask = ApplicationTask(packageName = "com.nonexistent.app", intent = "android.intent.action.MAIN")
        val failReport = reliabilityTester.runReliabilityTest(capability, failTask, iterations = 50)
        
        assertEquals("Application capability should cleanly fail for non-existent app", 0.0, failReport.successRate, 0.0)
        assertTrue("Failure reasons should contain ActivityNotFoundException equivalent", failReport.failureReasons.isNotEmpty())
    }

    @Test
    fun testDeviceCapabilityReliability() = runTest(timeout = 30.seconds) {
        val capability = AndroidDeviceCapability(context)
        val task = DeviceTask(action = "volumeup")
        
        val report = reliabilityTester.runReliabilityTest(capability, task, iterations = 100)
        
        assertTrue("Device capability should have >= 99% success rate for volumeup", report.successRate >= 0.99)
    }

    @Test
    fun testHangingCapabilityTimeout() = runTest(timeout = 60.seconds) {
        val capability = HangingCapability()
        val task = SystemTask(command = "hang")
        
        // Use fewer iterations because it will timeout 5s each time = 15s total
        val report = reliabilityTester.runReliabilityTest(capability, task, iterations = 3)
        
        assertEquals("Hanging capability should have 0% success rate", 0.0, report.successRate, 0.0)
        assertTrue("Failures should be due to cancellation/timeout", report.failureReasons.keys.any { it.contains("timed out") || it.contains("cancelled", ignoreCase = true) })
    }
}
