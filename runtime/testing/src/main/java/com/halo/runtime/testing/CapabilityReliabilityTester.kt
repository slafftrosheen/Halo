package com.halo.runtime.testing

import com.halo.runtime.cognition.Capability
import com.halo.runtime.cognition.Outcome
import com.halo.runtime.cognition.Task
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.seconds

data class ReliabilityReport(
    val capabilityId: String,
    val runs: Int,
    val successRate: Double,
    val minLatencyMs: Long,
    val maxLatencyMs: Long,
    val averageLatencyMs: Long,
    val medianLatencyMs: Long,
    val p95LatencyMs: Long,
    val p99LatencyMs: Long,
    val stdDeviationMs: Double,
    val failureReasons: Map<String, Int>,
    val deviceModel: String = android.os.Build.MODEL ?: "Unknown",
    val androidVersion: String = android.os.Build.VERSION.RELEASE ?: "Unknown",
    val runtimeVersion: String = "Alpha-1",
    val timestamp: Long = System.currentTimeMillis()
)

class CapabilityReliabilityTester {

    suspend fun runReliabilityTest(capability: Capability, task: Task, iterations: Int = 100): ReliabilityReport {
        var successes = 0
        val latencies = mutableListOf<Long>()
        val failures = mutableMapOf<String, Int>()

        for (i in 1..iterations) {
            val start = System.nanoTime()
            val outcome = try {
                withTimeout(5.seconds) {
                    capability.execute(task)
                }
            } catch (e: CancellationException) {
                // withTimeout throws TimeoutCancellationException which extends CancellationException
                Outcome.Cancelled("Execution timed out or was cancelled")
            } catch (e: Exception) {
                Outcome.PermanentFailure(e.message ?: "Execution failed")
            }
            
            val latencyMs = (System.nanoTime() - start) / 1_000_000
            
            latencies.add(latencyMs)
            
            if (outcome is Outcome.Success) {
                successes++
            } else if (outcome is Outcome.PermanentFailure) {
                val reason = outcome.reason
                failures[reason] = failures.getOrDefault(reason, 0) + 1
            } else if (outcome is Outcome.RetryableFailure) {
                val reason = outcome.reason
                failures[reason] = failures.getOrDefault(reason, 0) + 1
            } else if (outcome is Outcome.Cancelled) {
                val reason = outcome.reason ?: "Cancelled"
                failures[reason] = failures.getOrDefault(reason, 0) + 1
            }
        }

        latencies.sort()
        val min = latencies.firstOrNull() ?: 0L
        val max = latencies.lastOrNull() ?: 0L
        val median = if (latencies.isNotEmpty()) latencies[latencies.size / 2] else 0L
        val p95Index = (latencies.size * 0.95).toInt().coerceAtMost(latencies.size - 1)
        val p95 = if (latencies.isNotEmpty()) latencies[p95Index] else 0L
        val p99Index = (latencies.size * 0.99).toInt().coerceAtMost(latencies.size - 1)
        val p99 = if (latencies.isNotEmpty()) latencies[p99Index] else 0L
        val avg = if (latencies.isNotEmpty()) latencies.sum() / latencies.size else 0L

        val variance = if (latencies.isNotEmpty()) {
            latencies.map { (it - avg).toDouble().pow(2) }.sum() / latencies.size
        } else 0.0
        val stdDev = sqrt(variance)

        return ReliabilityReport(
            capabilityId = capability.id,
            runs = iterations,
            successRate = if (iterations > 0) successes.toDouble() / iterations else 0.0,
            minLatencyMs = min,
            maxLatencyMs = max,
            averageLatencyMs = avg,
            medianLatencyMs = median,
            p95LatencyMs = p95,
            p99LatencyMs = p99,
            stdDeviationMs = stdDev,
            failureReasons = failures
        )
    }
}
