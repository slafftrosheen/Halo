package com.halo.runtime.observer.replay

import com.halo.sdk.runtime.EventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class RuntimeReplayPlayer(
    private val eventBus: EventBus,
    private val scope: CoroutineScope
) {
    private val serializer = RuntimeJsonSerializer()

    fun playSession(file: File, fastForward: Boolean = false) {
        if (!file.exists()) return
        
        scope.launch {
            var lastEventTime: Long? = null
            
            file.useLines { lines ->
                lines.forEach { line ->
                    if (line.isNotBlank()) {
                        val event = try {
                            serializer.deserialize(line)
                        } catch (e: Exception) {
                            // Skip unparseable lines
                            null
                        }
                        
                        if (event != null) {
                            if (!fastForward) {
                                if (lastEventTime != null) {
                                    val delayMillis = event.timestamp - lastEventTime!!
                                    if (delayMillis > 0) {
                                        delay(delayMillis)
                                    }
                                }
                            }
                            eventBus.publish(event)
                            lastEventTime = event.timestamp
                        }
                    }
                }
            }
        }
    }
}
