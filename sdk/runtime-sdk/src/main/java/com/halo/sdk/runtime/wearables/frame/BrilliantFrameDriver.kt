package com.halo.sdk.runtime.wearables.frame

import com.halo.sdk.runtime.wearables.*
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Frame REPL status adapter, deliberately separate from Brilliant Labs Halo.
 * It never sends break/reset, installs scripts, changes callbacks, captures media,
 * or starts display/audio streams. A busy user program requires explicit user action.
 */
class BrilliantFrameDriver(
    override val deviceId: String,
    private val linkFactory: () -> NativeByteLink,
    private val requestTimeoutMs: Long = 8_000,
    private val connectionTimeoutMs: Long = 60_000,
) : NativeDeviceDriver {
    override val protocolId = "BRILLIANT_FRAME"
    override val supportedActions = setOf(NativeAction.BATTERY, NativeAction.DEVICE_INFO)
    private class Pending(val decoder: FrameStatusProtocol.Reply, val result: CompletableDeferred<String>)
    private class Attempt(val link: NativeByteLink) {
        val pending = AtomicReference<Pending?>(null)
        val cleaned = AtomicBoolean(false)
        var reader: Job? = null
        var monitor: Job? = null
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val workflow = Mutex()
    private val guard = Any()
    private val disposed = AtomicBoolean(false)
    private val active = AtomicReference<Attempt?>(null)
    private val mutableState = MutableStateFlow(NativeSnapshot(cameraAvailable = false))
    override val state: StateFlow<NativeSnapshot> = mutableState.asStateFlow()

    override suspend fun connect() = workflow.withLock {
        check(!disposed.get()) { "Frame owner was disposed" }
        require(deviceId.isNotBlank())
        if (state.value.stage == NativeStage.READY && active.get()?.link?.connected?.value == true) return@withLock
        val attempt = Attempt(linkFactory())
        check(active.compareAndSet(null, attempt)) { "Previous Frame connection is not retired" }
        publish(attempt) { NativeSnapshot(stage = NativeStage.CONNECTING, cameraAvailable = false) }
        try {
            attempt.reader = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    attempt.link.incoming.collect { packet ->
                        if (active.get() === attempt && packet.channel == "command") {
                            attempt.pending.get()?.let { pending ->
                                pending.decoder.append(packet.bytes)?.let { pending.result.complete(it) }
                            }
                        }
                    }
                    retire(attempt, IOException("Frame notification stream ended"))
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { retire(attempt, error) }
            }
            attempt.monitor = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                var connected = false
                attempt.link.connected.collect { value ->
                    if (value) connected = true
                    else if (connected) retire(attempt, IOException("Frame disconnected"))
                }
            }
            withTimeout(connectionTimeoutMs) { attempt.link.open() }
            requireActive(attempt)
            publish(attempt) { it.copy(stage = NativeStage.HANDSHAKE) }
            information(attempt)
            battery(attempt)
            publish(attempt) { it.copy(stage = NativeStage.READY) }
            requireActive(attempt)
        } catch (error: Exception) { retire(attempt, error); throw error }
    }

    override suspend fun execute(action: NativeAction, expectedVersion: String?, requestAllowed: () -> Boolean): NativeReceipt = workflow.withLock {
        require(action in supportedActions) { "Frame adapter currently supports status only" }
        val attempt = active.get() ?: error("Frame is disconnected")
        synchronized(guard) {
            requireActive(attempt)
            check(state.value.stage == NativeStage.READY)
            if (!requestAllowed() || expectedVersion != null && expectedVersion != state.value.sourceVersion) throw NativeRequestRejected()
            mutableState.value = state.value.copy(sourceVersion = UUID.randomUUID().toString())
        }
        try {
            when (action) {
                NativeAction.BATTERY -> battery(attempt, requestAllowed)
                NativeAction.DEVICE_INFO -> information(attempt, requestAllowed)
                else -> error("Unsupported Frame command")
            }
            NativeReceipt(NativeConfirmation.RECEIVED)
        } catch (error: Exception) { retire(attempt, error); throw error }
    }

    override suspend fun captureAiPhoto(): ByteArray = throw UnsupportedOperationException("Frame capture is not implemented")

    private suspend fun information(attempt: Attempt, allowed: () -> Boolean = { true }) {
        val model = query(attempt, FrameStatusProtocol.Query.HARDWARE, allowed)
        check(model == "Frame") { "Selected device is not a Brilliant Labs Frame" }
        val firmware = FrameStatusProtocol.firmware(query(attempt, FrameStatusProtocol.Query.FIRMWARE, allowed))
        publish(attempt) { it.copy(model = model, firmware = firmware) }
    }
    private suspend fun battery(attempt: Attempt, allowed: () -> Boolean = { true }) {
        val value = FrameStatusProtocol.battery(query(attempt, FrameStatusProtocol.Query.BATTERY, allowed))
        publish(attempt) { it.copy(batteryPercent = value) }
    }
    private suspend fun query(attempt: Attempt, query: FrameStatusProtocol.Query, allowed: () -> Boolean): String {
        requireActive(attempt)
        val token = UUID.randomUUID().toString().replace("-", "")
        val bytes = FrameStatusProtocol.command(query, token)
        check(bytes.size <= attempt.link.maxWriteBytes) { "Frame needs a larger negotiated BLE MTU for status queries" }
        val pending = Pending(FrameStatusProtocol.Reply(token), CompletableDeferred())
        check(attempt.pending.compareAndSet(null, pending))
        try {
            check(allowed()) { "Frame access changed" }
            return withTimeout(requestTimeoutMs) {
                attempt.link.write(bytes)
                pending.result.await().also { requireActive(attempt) }
            }
        } finally { attempt.pending.compareAndSet(pending, null); pending.result.cancel() }
    }
    private fun requireActive(attempt: Attempt) {
        check(active.get() === attempt && attempt.link.connected.value) { "Frame connection changed" }
    }
    private fun publish(attempt: Attempt, update: (NativeSnapshot) -> NativeSnapshot) = synchronized(guard) {
        if (active.get() === attempt) mutableState.value = update(state.value).copy(sourceVersion = UUID.randomUUID().toString())
    }
    override suspend fun disconnect() {
        val previous = synchronized(guard) {
            val old = active.getAndSet(null)
            mutableState.value = NativeSnapshot(cameraAvailable = false)
            old
        }
        previous?.let { cleanup(it, CancellationException("Frame disconnected")) }
    }
    override suspend fun dispose() { disposed.set(true); disconnect(); scope.cancel() }
    private suspend fun retire(attempt: Attempt, error: Exception) {
        synchronized(guard) {
            if (active.compareAndSet(attempt, null)) mutableState.value = NativeSnapshot(stage = NativeStage.ERROR, cameraAvailable = false,
                error = if (error is TimeoutCancellationException) "Frame did not answer. It may be running another app; Halo will not interrupt it."
                    else "Frame status connection failed. Check the selected model, pairing and REPL availability.")
        }
        cleanup(attempt, error)
    }
    private suspend fun cleanup(attempt: Attempt, error: Exception) = withContext(NonCancellable) {
        if (!attempt.cleaned.compareAndSet(false, true)) return@withContext
        attempt.pending.getAndSet(null)?.result?.completeExceptionally(error)
        try { withTimeout(5_000) { attempt.link.close() } } catch (_: Exception) { }
        attempt.reader?.cancel()
        attempt.monitor?.cancel()
    }
}
