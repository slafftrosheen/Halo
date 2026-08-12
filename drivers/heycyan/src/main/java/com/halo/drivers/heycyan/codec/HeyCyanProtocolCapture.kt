package com.halo.drivers.heycyan.codec

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * Singleton-like manager for controlled HeyCyan protocol capture.
 *
 * It allows enabling "Correlated Capture Mode" which groups TX/RX frames
 * around high-level operations like PHOTO or VIDEO_START.
 */
object HeyCyanProtocolCapture {
    
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
        
        android.util.Log.i("HeyCyanCapture", transaction.dump())
        currentOperation = null
    }

    fun recordFrame(
        characteristicUuid: UUID,
        payload: ByteArray,
        direction: PacketDirection,
        writeType: String? = null
    ) {
        if (!_isCaptureEnabled.value) return
        
        val frame = RawHeyCyanFrame(
            timestampMillis = System.currentTimeMillis(),
            direction = direction,
            characteristicUuid = characteristicUuid,
            bytes = payload.copyOf(),
            operation = currentOperation,
            writeType = writeType
        )
        
        synchronized(allFrames) {
            allFrames.add(frame)
        }
        
        // Immediate hex dump to Logcat
        val prefix = if (direction == PacketDirection.TX) "TX" else "RX"
        android.util.Log.d("HeyCyanWire", "$prefix $characteristicUuid ${frame.hex()}")
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
}
