package com.halo.app.audio

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/** Sampled process footprint, not a claim about inaccessible GPU allocation or every native peak. */
internal class NativeFootprintSampler(context: Context, private val modelId: String, private val sha256: String?,
    private val sourceStamp: String?, private val runId: String?) : AutoCloseable {
    private val manager = context.getSystemService(ActivityManager::class.java)
    private val output = File(context.filesDir, "installed-model-footprints.jsonl")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val peakPssKb = AtomicLong()
    val peakNativeHeapBytes = AtomicLong()
    init {
        scope.launch {
            while (isActive) {
                val pss = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }.totalPss.toLong()
                val heap = Debug.getNativeHeapAllocatedSize()
                val ram = ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }
                peakPssKb.updateAndGet { maxOf(it, pss) }
                peakNativeHeapBytes.updateAndGet { maxOf(it, heap) }
                output.appendText(JSONObject().put("atMs", System.currentTimeMillis())
                    .put("runId", runId).put("modelId", modelId).put("sha256", sha256).put("sourceStamp", sourceStamp)
                    .put("sampleIntervalMs", 200).put("processPssKb", pss).put("nativeHeapBytes", heap)
                    .put("availableRamBytes", ram.availMem).put("gpuMemory", "NOT_VISIBLE").toString() + "\n")
                delay(200)
            }
        }
    }
    override fun close() { scope.cancel() }
}
