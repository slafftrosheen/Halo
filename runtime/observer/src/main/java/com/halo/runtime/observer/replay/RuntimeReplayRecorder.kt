package com.halo.runtime.observer.replay

import com.halo.sdk.runtime.EventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RuntimeReplayRecorder(
    private val eventBus: EventBus,
    private val scope: CoroutineScope,
    private val outputDirectory: File
) {
    private var recordingJob: Job? = null
    private val serializer = RuntimeJsonSerializer()
    
    fun startRecording(): File {
        if (recordingJob?.isActive == true) throw IllegalStateException("Already recording")
        if (!outputDirectory.exists()) outputDirectory.mkdirs()
        
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outputFile = File(outputDirectory, "session_$timestamp.jsonl")
        val writer = FileWriter(outputFile, true)
        
        recordingJob = scope.launch {
            try {
                eventBus.events().collect { event ->
                    val json = serializer.serialize(event)
                    writer.appendLine(json)
                    writer.flush()
                }
            } finally {
                writer.close()
            }
        }
        
        return outputFile
    }
    
    fun stopRecording() {
        recordingJob?.cancel()
        recordingJob = null
    }
}
