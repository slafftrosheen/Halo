package com.halo.drivers.heycyan

import com.halo.drivers.heycyan.ble.HeyCyanGattClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID

/**
 * Typed event stream that wraps GattClient.incomingBytes, decodes via
 * HeyCyanEventDecoder, and exposes both a shared flow of all decoded events
 * and a suspend function for awaiting specific event types.
 *
 * This is the single source of decoded events for the entire HeyCyan driver.
 * Both the device controller (for command-response correlation) and the
 * driver (for device event emission) consume from this stream.
 */
class HeyCyanEventStream(
    private val gattClient: HeyCyanGattClient,
    private val eventDecoder: HeyCyanEventDecoder = HeyCyanEventDecoder()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var collectorJob: Job? = null

    private val _events = MutableSharedFlow<HeyCyanBleEvent>(
        replay = 0,
        extraBufferCapacity = 128
    )

    /** All decoded events from the device. */
    val events: SharedFlow<HeyCyanBleEvent> = _events.asSharedFlow()

    /** Filtered stream of unknown events for protocol analysis. */
    val unknownEvents: SharedFlow<HeyCyanBleEvent.Unknown>
        get() = _events.asSharedFlow().let { flow ->
            MutableSharedFlow<HeyCyanBleEvent.Unknown>(extraBufferCapacity = 64).also { unknown ->
                scope.launch {
                    flow.collect { event ->
                        if (event is HeyCyanBleEvent.Unknown) {
                            unknown.tryEmit(event)
                        }
                    }
                }
            }
        }

    /**
     * Starts collecting and decoding raw BLE notifications.
     * Must be called after GATT connection is established.
     */
    fun start() {
        if (collectorJob != null) return
        collectorJob = scope.launch {
            gattClient.incomingBytes.collect { (characteristic, payload) ->
                val decoded = eventDecoder.decode(
                    payload = payload,
                    characteristic = characteristic,
                    timestamp = System.currentTimeMillis()
                )
                _events.emit(decoded)
            }
        }
    }

    /**
     * Stops the event collection.
     */
    fun stop() {
        collectorJob?.cancel()
        collectorJob = null
    }

    /**
     * Suspends until an event of the specified type is received, or times out.
     *
     * @param T The expected event type
     * @param timeoutMillis Maximum time to wait for the event
     * @param filter Additional filtering predicate beyond type matching
     * @return The matching event
     * @throws kotlinx.coroutines.TimeoutCancellationException if timeout elapses
     */
    @Suppress("UNCHECKED_CAST")
    suspend inline fun <reified T : HeyCyanBleEvent> awaitEvent(
        timeoutMillis: Long = 5000L,
        crossinline filter: (T) -> Boolean = { true }
    ): T {
        return withTimeout(timeoutMillis) {
            events
                .filter { it is T && filter(it as T) }
                .map { it as T }
                .first()
        }
    }

    /**
     * Suspends until any battery event is received.
     */
    suspend fun awaitBattery(timeoutMillis: Long = 3000L): HeyCyanBleEvent.Battery {
        return awaitEvent(timeoutMillis)
    }

    /**
     * Suspends until a version event is received.
     */
    suspend fun awaitVersion(timeoutMillis: Long = 5000L): HeyCyanBleEvent.Version {
        return awaitEvent(timeoutMillis)
    }

    /**
     * Suspends until a media counts event is received.
     */
    suspend fun awaitMediaCounts(timeoutMillis: Long = 3000L): HeyCyanBleEvent.MediaCounts {
        return awaitEvent(timeoutMillis)
    }

    /**
     * Suspends until a mode changed event is received.
     */
    suspend fun awaitModeChanged(timeoutMillis: Long = 5000L): HeyCyanBleEvent.ModeChanged {
        return awaitEvent(timeoutMillis)
    }

    /**
     * Suspends until WiFi credentials are received.
     */
    suspend fun awaitWiFiCredentials(timeoutMillis: Long = 5000L): HeyCyanBleEvent.WiFiCredentials {
        return awaitEvent(timeoutMillis)
    }

    /**
     * Returns whether the event stream is actively collecting.
     */
    val isActive: Boolean get() = collectorJob?.isActive == true
}
