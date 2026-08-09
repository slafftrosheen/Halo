package com.halo.runtime.testing

import com.halo.runtime.cognition.Capability
import com.halo.runtime.cognition.Outcome
import com.halo.runtime.cognition.SystemTask
import com.halo.runtime.cognition.Task
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class CapabilityReliabilityTest {
    @Test
    fun `successful capability produces a complete reliability report`() = runTest {
        val tester = CapabilityReliabilityTester(
            ReliabilityEnvironment("simulated-wearable", "not-applicable", "test")
        )

        val report = tester.runReliabilityTest(
            capability = SuccessfulCapability(),
            task = SystemTask(command = "test"),
            iterations = 5
        )

        assertEquals(5, report.runs)
        assertEquals(1.0, report.successRate, 0.0)
        assertEquals("simulated-wearable", report.deviceModel)
        assertEquals("test", report.runtimeVersion)
        assertTrue(report.minLatencyMs >= 0L)
    }

    @Test
    fun `failed capability records failure reasons`() = runTest {
        val report = CapabilityReliabilityTester().runReliabilityTest(
            capability = FailedCapability(),
            task = SystemTask(command = "fail"),
            iterations = 3
        )

        assertEquals(0.0, report.successRate, 0.0)
        assertEquals(3, report.failureReasons["expected failure"])
    }

    @Test
    fun `hanging capability is cancelled by the reliability timeout`() = runTest(timeout = 15.seconds) {
        val report = CapabilityReliabilityTester().runReliabilityTest(
            capability = HangingCapability(),
            task = SystemTask(command = "hang"),
            iterations = 1
        )

        assertEquals(0.0, report.successRate, 0.0)
        assertTrue(report.failureReasons.keys.any { it.contains("cancelled", ignoreCase = true) })
    }

    private class SuccessfulCapability : Capability {
        override val id = "test.success"
        override suspend fun supports(task: Task): Boolean = true
        override suspend fun execute(task: Task): Outcome = Outcome.Success("ok")
    }

    private class FailedCapability : Capability {
        override val id = "test.failure"
        override suspend fun supports(task: Task): Boolean = true
        override suspend fun execute(task: Task): Outcome = Outcome.PermanentFailure("expected failure")
    }

    private class HangingCapability : Capability {
        override val id = "test.hanging"
        override suspend fun supports(task: Task): Boolean = true
        override suspend fun execute(task: Task): Outcome {
            delay(10.seconds)
            return Outcome.Success("unexpected")
        }
    }
}
