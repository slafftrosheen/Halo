package com.halo.drivers.heycyan.codec

import com.halo.sdk.runtime.RuntimeLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Manager for controlled HeyCyan protocol capture.
 *
 * It allows enabling "Correlated Capture Mode" which groups TX/RX frames
 * around high-level operations like PHOTO or VIDEO_START.
 */
class HeyCyanProtocolCapture(
    private val logger: RuntimeLogger? = null
) {
    
    private val _isCaptureEnabled = MutableStateFlow(false)
    val isCaptureEnabled: StateFlow<Boolean> = _isCaptureEnabled.asStateFlow()

    private val allFrames = mutableListOf<RawHeyCyanFrame>()
    private var currentOperation: String? = null
    private var operationStartedAt: Long = 0

    fun setCaptureEnabled(enabled: Boolean) {
        _isCaptureEnabled.value = enabled
        if (!enabled) {
            clear()
        }
    }

    fun startOperation(operation: String) {
        if (!_isCaptureEnabled.value) return
        currentOperation = operation
        operationStartedAt = System.currentTimeMillis()
    }

    fun endOperation() {
        if (!_isCaptureEnabled.value) return
        val op = currentOperation ?: return
        val now = System.currentTimeMillis()
        
        val transaction = HeyCyanTransaction(
            operation = op,
            txFrames = allFrames.filter { it.direction == PacketDirection.TX && it.timestampMillis >= operationStartedAt },
            rxFrames = allFrames.filter { it.direction == PacketDirection.RX && it.timestampMillis >= operationStartedAt },
            startedAtMillis = operationStartedAt,
            endedAtMillis = now
        )
        
        logger?.info("HeyCyanCapture", transaction.dump())
        currentOperation = null
    }

    fun recordFrame(
        characteristicUuid: UUID,
        payload: ByteArray,
        direction: PacketDirection,
        writeType: String? = null
    ) {
        if (!_isCaptureEnabled.value) return
        // Raw recording and hex formatting are forensic-only.
        
        val frame = RawHeyCyanFrame(
            timestampMillis = System.currentTimeMillis(),
            direction = direction,
            characteristicUuid = characteristicUuid,
            bytes = payload.copyOf(),
            operation = currentOperation,
            writeType = writeType
        )

        if (_isCaptureEnabled.value) {
            synchronized(allFrames) {
                allFrames.add(frame)
            }
        }
        
        // Immediate hex dump to Logcat (always if in debug)
        val prefix = if (direction == PacketDirection.TX) "TX" else "RX"
        val writeInfo = if (writeType != null) " ($writeType)" else ""
        try {
            android.util.Log.d("HeyCyanWire", "$prefix $characteristicUuid ${frame.hex()}$writeInfo")
        } catch (_: Throwable) {
            // Ignored in non-Android JVM unit test environments
        }
    }

    fun clear() {
        synchronized(allFrames) {
            allFrames.clear()
        }
        currentOperation = null
    }

    /** machine-readable JSONL line format */
    fun RawHeyCyanFrame.toJsonl(): String {
        return """{"timestamp": $timestampMillis, "direction": "$direction", "uuid": "$characteristicUuid", "bytes": "${hex()}", "operation": ${currentOperation?.let { "\"$it\"" } ?: "null"}}"""
    }

    companion object {
        /** Global instance for driver instrumentation */
        var instance = HeyCyanProtocolCapture()
    }
}
