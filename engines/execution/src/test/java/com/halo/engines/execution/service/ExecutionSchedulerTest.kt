package com.halo.engines.execution.service

import com.halo.runtime.cognition.Capability
import com.halo.runtime.cognition.CapabilityMetadata
import com.halo.runtime.cognition.DeviceTask
import com.halo.runtime.cognition.Outcome
import com.halo.runtime.cognition.Task
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ExecutionSchedulerTest {
    private class CountingCapability(
        private val active: AtomicInteger,
        private val peak: AtomicInteger,
        private val executed: AtomicInteger,
    ) : Capability {
        override val id: String = "test.device"
        override val metadata = CapabilityMetadata(id = id)
        override suspend fun supports(task: Task): Boolean = task is DeviceTask
        override suspend fun execute(task: Task): Outcome {
            val running = active.incrementAndGet()
            peak.updateAndGet { maxOf(it, running) }
            try {
                delay(30)
                executed.incrementAndGet()
                return Outcome.Success("OK")
            } finally {
                active.decrementAndGet()
            }
        }
    }

    @Test fun parallelDependenciesShareBoundedAdmission() = runBlocking {
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val executed = AtomicInteger()
        val registry = CapabilityRegistry()
        registry.register(CountingCapability(active, peak, executed))
        val root = DeviceTask(action = "parent", isParallel = true,
            dependencies = List(12) { DeviceTask(action = "prerequisite-$it") })
        val result = ExecutionScheduler(registry).executeTaskGraph(root)
        assertTrue(result is Outcome.Success)
        assertEquals(13, executed.get())
        assertTrue("At most two device capabilities can run concurrently", peak.get() <= 2)
        assertEquals(0, active.get())
    }

    @Test fun duplicateDependencyIsRejectedBeforeAnyAction() = runBlocking {
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val executed = AtomicInteger()
        val registry = CapabilityRegistry()
        registry.register(CountingCapability(active, peak, executed))
        val shared = DeviceTask(action = "same")
        val root = DeviceTask(action = "parent", isParallel = true,
            dependencies = listOf(
                DeviceTask(action = "a", dependencies = listOf(shared)),
                DeviceTask(action = "b", dependencies = listOf(shared)),
            ))
        val result = ExecutionScheduler(registry).executeTaskGraph(root)
        assertTrue(result is Outcome.PermanentFailure)
        assertEquals(0, executed.get())
    }

    @Test fun excessiveFanoutIsRejectedBeforeAnyAction() = runBlocking {
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val executed = AtomicInteger()
        val registry = CapabilityRegistry()
        registry.register(CountingCapability(active, peak, executed))
        val root = DeviceTask(action = "parent", isParallel = true,
            dependencies = List(129) { DeviceTask(action = "dep-$it") })
        val result = ExecutionScheduler(registry).executeTaskGraph(root)
        assertTrue(result is Outcome.PermanentFailure)
        assertEquals(0, executed.get())
    }
}
