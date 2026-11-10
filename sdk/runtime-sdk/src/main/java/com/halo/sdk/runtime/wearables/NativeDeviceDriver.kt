package com.halo.sdk.runtime.wearables

import kotlinx.coroutines.flow.StateFlow

/** Common operations consumed by the session owner; wire protocols remain in their own drivers. */
interface NativeDeviceDriver {
    val deviceId: String
    val protocolId: String
    val state: StateFlow<NativeSnapshot>
    suspend fun connect()
    suspend fun disconnect()
    suspend fun dispose()
    suspend fun execute(action: NativeAction): NativeReceipt
    suspend fun captureAiPhoto(): ByteArray
}
