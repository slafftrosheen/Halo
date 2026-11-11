package com.halo.sdk.runtime.wearables

import kotlinx.coroutines.flow.StateFlow

class NativeRequestRejected : IllegalStateException("Native device status or access changed")

/** Common operations consumed by the session owner; wire protocols remain in their own drivers. */
interface NativeDeviceDriver {
    val deviceId: String
    val protocolId: String
    val state: StateFlow<NativeSnapshot>
    val supportedActions: Set<NativeAction> get() = emptySet()
    suspend fun connect()
    suspend fun disconnect()
    suspend fun dispose()
    /** The observed version is consumed under the driver's workflow lease before any write. */
    suspend fun execute(action: NativeAction, expectedVersion: String? = null,
        requestAllowed: () -> Boolean = { true }): NativeReceipt
    suspend fun captureAiPhoto(): ByteArray
}
