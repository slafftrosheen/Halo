package com.halo.sdk.runtime.wearables

import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Android GATT/RFCOMM implementations own permissions, sockets and callback fencing. */
data class NativePacket(val bytes: ByteArray, val channel: String = "command")
interface NativeByteLink {
    val availableChannels: Set<String> get() = setOf("command")
    val incoming: Flow<NativePacket>
    val connected: StateFlow<Boolean>
    suspend fun open()
    suspend fun write(bytes: ByteArray)
    suspend fun close()
}
class NativeDeviceRejected(val status: Int) : IOException("Device rejected the command (status $status)")

/** One selected physical endpoint, one response owner, no raw-command API.
 * A timeout retires the connection: these protocols lack a reliable logical
 * transaction ID, so a late response must never satisfy a subsequent request.
 * All multi-command capture/transfer work keeps the workflow lease until cleanup.
 */
class NativeWearableDriver(
    override val deviceId: String,
    val family: NativeFamily,
    private val linkFactory: () -> NativeByteLink,
    private val requestTimeoutMs: Long = 30_000L,
    private val connectionTimeoutMs: Long = 60_000L,
) : NativeDeviceDriver {
    override val protocolId: String get() = family.name
    private class Pending(val command: NativeCommand, val result: CompletableDeferred<WearableFrame>)
    private class Attempt(val link: NativeByteLink) {
        val pending = AtomicReference<Pending?>(null)
        val photo = AtomicReference<CompletableDeferred<ByteArray>?>(null)
        val jpeg = NativeJpegAssembler()
        val eye = EyeVueWire.Decoder()
        val tune = TuneBudsWire.Decoder()
        var sequence = 0
        var packetSize = 20
        var reader: Job? = null
        var monitor: Job? = null
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val workflow = Mutex()
    private val active = AtomicReference<Attempt?>(null)
    private val stateLock = Any()
    private val mutableState = MutableStateFlow(NativeSnapshot())
    override val state: StateFlow<NativeSnapshot> = mutableState.asStateFlow()

    override suspend fun connect() = workflow.withLock {
        require(deviceId.isNotBlank())
        if (state.value.stage == NativeStage.READY && active.get()?.link?.connected?.value == true) return@withLock
        check(active.get() == null) { "Previous connection is not yet retired" }
        val attempt = Attempt(linkFactory())
        check(active.compareAndSet(null, attempt))
        publish(attempt) { NativeSnapshot(stage = NativeStage.CONNECTING) }
        try {
            attempt.reader = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    attempt.link.incoming.collect { packet ->
                        if (active.get() !== attempt) return@collect
                        if (family == NativeFamily.EYEVUE && packet.channel == "photo") {
                            acceptEyePhoto(attempt, packet.bytes)
                        } else {
                            val frames = when (family) {
                                NativeFamily.EYEVUE -> attempt.eye.append(packet.bytes)
                                NativeFamily.TUNEBUDS -> attempt.tune.append(packet.bytes)
                            }
                            frames.forEach { acceptFrame(attempt, it) }
                        }
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { retire(attempt, error) }
            }
            attempt.monitor = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                var wasConnected = false
                attempt.link.connected.collect { connected ->
                    if (connected) wasConnected = true
                    else if (wasConnected) retire(attempt, IOException("Native Bluetooth link disconnected"))
                }
            }
            withTimeout(connectionTimeoutMs) { attempt.link.open() }
            requireActive(attempt)
            publish(attempt) { it.copy(stage = NativeStage.HANDSHAKE) }
            transact(attempt, command(NativeAction.DEVICE_INFO))
            transact(attempt, command(NativeAction.BATTERY))
            requireActive(attempt)
            check(state.value.batteryPercent != null) { "No valid device-state response" }
            publish(attempt) { it.copy(stage = NativeStage.READY) }
        } catch (error: Exception) {
            retire(attempt, error)
            throw error
        }
    }

    override suspend fun disconnect() {
        val attempt = synchronized(stateLock) {
            val previous = active.getAndSet(null)
            mutableState.value = NativeSnapshot()
            previous
        } ?: return
        cleanup(attempt, CancellationException("Native device disconnected"))
    }

    override suspend fun dispose() { disconnect(); scope.cancel() }

    override suspend fun execute(action: NativeAction): NativeReceipt = workflow.withLock {
        val attempt = readyAttempt()
        if (action in setOf(NativeAction.PHOTO, NativeAction.AI_PHOTO, NativeAction.START_VIDEO, NativeAction.START_AUDIO)) {
            check(state.value.cameraAvailable != false) { "Selected firmware reports no camera subsystem" }
        }
        if (family == NativeFamily.TUNEBUDS && action in setOf(
                NativeAction.STOP_AUDIO, NativeAction.STOP_VIDEO, NativeAction.END_TRANSFER)) {
            closeTuneCamera(attempt)
            NativeReceipt(NativeConfirmation.ACKNOWLEDGED)
        } else transact(attempt, command(action))
    }

    override suspend fun captureAiPhoto(): ByteArray = workflow.withLock {
        val attempt = readyAttempt()
        check(state.value.cameraAvailable != false && state.value.aiPhotoAvailable) { "Selected connection does not expose an AI photo channel" }
        val picture = CompletableDeferred<ByteArray>()
        attempt.jpeg.start()
        check(attempt.photo.compareAndSet(null, picture))
        try {
            transact(attempt, command(NativeAction.AI_PHOTO))
            withTimeout(requestTimeoutMs) { picture.await() }
        } catch (error: Exception) {
            // Do not reuse an incomplete image stream for a later capture.
            retire(attempt, error)
            throw error
        } finally {
            attempt.photo.compareAndSet(picture, null)
            picture.cancel()
            attempt.jpeg.reset()
        }
    }

    suspend fun <T> withEyeVueMedia(
        p2p: Boolean,
        preview: Boolean = false,
        block: suspend (ssid: String, project: String) -> T,
    ): T = workflow.withLock {
        check(family == NativeFamily.EYEVUE)
        val attempt = readyAttempt()
        try {
            publish(attempt) { it.copy(wifiSsid = null) }
            val action = if (preview) {
                if (p2p) NativeAction.PREVIEW_P2P else NativeAction.PREVIEW_AP
            } else if (p2p) NativeAction.MEDIA_P2P else NativeAction.MEDIA_AP
            transact(attempt, command(action))
            val ssid = state.value.wifiSsid ?: error("Device supplied no Wi-Fi SSID")
            block(ssid, state.value.model ?: error("Unknown EyeVue project"))
        } finally {
            withContext(NonCancellable) {
                if (active.get() === attempt) {
                    try { transact(attempt, command(NativeAction.END_TRANSFER)) }
                    catch (_: Exception) { /* transact retires an uncertain transport */ }
                }
            }
        }
    }

    suspend fun <T> withTuneBudsMedia(
        ssid: String,
        password: String,
        block: suspend (endpoint: String) -> T,
    ): T = workflow.withLock {
        check(family == NativeFamily.TUNEBUDS)
        val attempt = readyAttempt()
        try {
            closeTuneCamera(attempt)
            publish(attempt) { it.copy(mediaEndpoint = null, wifiFailure = null) }
            transact(attempt, NativeCommand(0xe6, NativeCommands.hotspot(ssid, password),
                setOf(0xe6), 2, statusResponse = true))
            withTimeout(45_000L) {
                var tries = 0
                while (true) {
                    state.value.wifiFailure?.let { throw IOException("Glasses Wi-Fi failed (state $it)") }
                    try {
                        transact(attempt, NativeCommand(0xe7, responses = setOf(0xe7), responseType = 2,
                            statusResponse = true))
                        break
                    } catch (busy: NativeDeviceRejected) {
                        if (!NativeCommands.isTuneBudsBusy(busy.status) || ++tries >= 15) throw busy
                        delay(1_000)
                    }
                }
                val ready = state.first { it.mediaEndpoint != null || it.wifiFailure != null || it.stage != NativeStage.READY }
                requireActive(attempt)
                check(ready.wifiFailure == null) { "Glasses Wi-Fi failed (state ${ready.wifiFailure})" }
                ready.mediaEndpoint ?: error("No device media endpoint")
            }.let { endpoint ->
                coroutineScope {
                    val monitor = launch {
                        val failed = state.first { it.wifiFailure != null || it.stage != NativeStage.READY }
                        throw IOException("Media link lost: ${failed.error ?: failed.wifiFailure}")
                    }
                    try { block(endpoint) } finally { monitor.cancel() }
                }
            }
        } finally {
            // Keep the caller's hotspot alive until the camera close is acknowledged.
            withContext(NonCancellable) {
                if (active.get() === attempt) {
                    try { closeTuneCamera(attempt) } catch (error: Exception) { retire(attempt, error) }
                }
            }
        }
    }

    private suspend fun closeTuneCamera(attempt: Attempt) {
        repeat(10) { index ->
            try { transact(attempt, command(NativeAction.END_TRANSFER)); return }
            catch (busy: NativeDeviceRejected) {
                // E2 busy-saving is ONLY status 1. Do not reuse E7's 1/3/5 rule here.
                if (busy.status != 1 || index == 9) throw busy
                delay(1_000)
            }
        }
    }

    private fun command(action: NativeAction): NativeCommand = when (family) {
        NativeFamily.EYEVUE -> NativeCommands.eyeVue(action)
        NativeFamily.TUNEBUDS -> NativeCommands.tuneBuds(action)
    }

    private fun readyAttempt(): Attempt {
        check(state.value.stage == NativeStage.READY) { "Device is not ready" }
        val attempt = active.get() ?: error("Device is disconnected")
        requireActive(attempt)
        return attempt
    }

    private fun requireActive(attempt: Attempt) {
        check(active.get() === attempt && attempt.link.connected.value) { "Device connection changed" }
    }

    private suspend fun transact(attempt: Attempt, spec: NativeCommand): NativeReceipt {
        requireActive(attempt)
        val pending = if (spec.responses.isEmpty()) null else Pending(spec, CompletableDeferred())
        if (pending != null) check(attempt.pending.compareAndSet(null, pending)) { "Command slot is occupied" }
        try {
            return withTimeout(requestTimeoutMs) {
                val packets = when (family) {
                    NativeFamily.EYEVUE -> listOf(EyeVueWire.encode(spec.command, spec.payload))
                    NativeFamily.TUNEBUDS -> TuneBudsWire.encode(spec.command, spec.payload,
                        packetSize = attempt.packetSize, sequence = attempt.sequence).also {
                        attempt.sequence = it.nextSequence
                    }.frames
                }
                packets.forEach { requireActive(attempt); attempt.link.write(it) }
                if (pending == null) NativeReceipt(NativeConfirmation.DISPATCHED)
                else {
                    val response = pending.result.await()
                    requireActive(attempt)
                    if (spec.statusResponse) {
                        val status = response.payload.firstOrNull()?.toInt()?.and(255)
                            ?: throw IOException("Command response has no status")
                        if (status != 0) throw NativeDeviceRejected(status)
                    }
                    NativeReceipt(if (spec.statusResponse) NativeConfirmation.ACKNOWLEDGED
                        else NativeConfirmation.RECEIVED, response)
                }
            }
        } catch (rejected: NativeDeviceRejected) { throw rejected }
        catch (error: Exception) { retire(attempt, error); throw error }
        finally { if (pending != null) { attempt.pending.compareAndSet(pending, null); pending.result.cancel() } }
    }

    private fun acceptFrame(attempt: Attempt, frame: WearableFrame) {
        if (active.get() !== attempt) return
        when (family) {
            NativeFamily.EYEVUE -> when (frame.command) {
                0x17, 0x53 -> {
                    val battery = NativeCommands.eyeVueBattery(frame) ?: throw IOException("Invalid battery response")
                    publish(attempt) { it.copy(batteryPercent = battery.first, charging = battery.second) }
                }
                0x64 -> {
                    require(frame.payload.size >= 8) { "Invalid EyeVue identity response" }
                    val project = frame.payload.copyOfRange(0, 4).toString(Charsets.UTF_8).trim('\u0000', ' ')
                    require(project.isNotBlank())
                    publish(attempt) { it.copy(model = project, cameraAvailable = true, aiPhotoAvailable = "photo" in attempt.link.availableChannels) }
                }
                0x16, 0x42 -> {
                    require(frame.payload.size >= 2)
                    val count = ((frame.payload[0].toInt() and 255) shl 8) or (frame.payload[1].toInt() and 255)
                    publish(attempt) { it.copy(aggregateMediaCount = count) }
                }
                0x45 -> {
                    require(frame.payload.size >= 3)
                    publish(attempt) { it.copy(videoRecording = frame.payload[2].toInt() == 1,
                        audioRecording = frame.payload[1].toInt() == 1) }
                }
                0x25 -> {
                    require(frame.payload.size in 1..32) { "Invalid Wi-Fi SSID" }
                    val ssid = frame.payload.toString(Charsets.UTF_8).trim('\u0000', ' ')
                    require(ssid.isNotBlank())
                    publish(attempt) { it.copy(wifiSsid = ssid) }
                }
            }
            NativeFamily.TUNEBUDS -> when {
                frame.command == 0x27 && frame.type == 2 -> {
                    val fields = TuneBudsWire.tlv(frame.payload)
                    fields[0xff]?.firstOrNull()?.toInt()?.and(255)?.takeIf { it in 6..255 }?.let { attempt.packetSize = it }
                    val battery = fields[1]?.firstOrNull()?.toInt()?.and(255)
                    if (battery != null) require((battery and 127) <= 100) { "Invalid battery response" }
                    val version = fields[2]?.takeIf { it.size == 4 }?.reversed()?.joinToString(".") { (it.toInt() and 255).toString() }
                    publish(attempt) { it.copy(
                        batteryPercent = battery?.and(127) ?: it.batteryPercent,
                        charging = battery?.let { value -> value and 128 != 0 } ?: it.charging,
                        model = fields[0x86]?.toString(Charsets.UTF_8)?.trim('\u0000', ' ')?.takeIf(String::isNotBlank) ?: it.model,
                        firmware = version ?: it.firmware,
                        cameraAvailable = fields[0xa0]?.firstOrNull()?.let { value -> value.toInt() == 1 } ?: it.cameraAvailable,
                        aiPhotoAvailable = fields[0xa0]?.firstOrNull()?.let { value -> value.toInt() == 1 } ?: it.aiPhotoAvailable,
                    ) }
                }
                frame.command == 0xef && frame.type == 3 -> {
                    require(frame.payload.size == 12)
                    publish(attempt) { it.copy(mediaCounts = listOf(0, 4, 8).map { offset -> NativeCommands.unsigned32(frame.payload, offset) }) }
                }
                frame.command == 0xe7 && frame.type == 3 -> {
                    require(frame.payload.size in 1..1024)
                    val endpoint = frame.payload.toString(Charsets.UTF_8).trim('\u0000', ' ')
                    // Android downloader validates private IP + selected network before opening it.
                    publish(attempt) { it.copy(mediaEndpoint = endpoint) }
                }
                frame.command == 0xec && frame.type == 3 -> {
                    val status = frame.payload.firstOrNull()?.toInt()?.and(255) ?: return
                    if (status in setOf(0, 4, 5)) publish(attempt) { it.copy(wifiFailure = status) }
                }
                frame.command == 0xe3 && frame.type == 3 && attempt.photo.get() != null -> {
                    require(frame.payload.isNotEmpty())
                    val status = frame.payload[0].toInt() and 255
                    require(status == 0 || status == 1) { "AI photo failed (status $status)" }
                    attempt.jpeg.append(frame.payload.copyOfRange(1, frame.payload.size))
                    if (status == 0) attempt.photo.get()?.complete(attempt.jpeg.finish())
                }
            }
        }
        val pending = attempt.pending.get() ?: return
        if (frame.command in pending.command.responses &&
            (pending.command.responseType == null || frame.type == pending.command.responseType)) {
            pending.result.complete(frame)
        }
    }

    private fun acceptEyePhoto(attempt: Attempt, bytes: ByteArray) {
        val result = attempt.photo.get() ?: return
        if (bytes.size < 8) return
        when (bytes[4].toInt() and 255) {
            0x97 -> attempt.jpeg.start()
            0x98 -> {
                require(bytes.size >= 12)
                attempt.jpeg.append(bytes.copyOfRange(9, bytes.size - 3))
            }
            0x99 -> result.complete(attempt.jpeg.finish())
        }
    }

    private fun publish(attempt: Attempt, update: (NativeSnapshot) -> NativeSnapshot) = synchronized(stateLock) {
        if (active.get() === attempt) mutableState.value = update(mutableState.value)
    }

    private suspend fun retire(attempt: Attempt, error: Exception) {
        synchronized(stateLock) {
            if (active.compareAndSet(attempt, null)) {
                mutableState.value = NativeSnapshot(stage = NativeStage.ERROR,
                    error = when (error) {
                        is TimeoutCancellationException -> "Device response timed out; reconnect before retrying"
                        is CancellationException -> "Operation cancelled; reconnect before retrying"
                        else -> error.message?.take(200) ?: "Device transport failed"
                    })
            }
        }
        cleanup(attempt, error)
    }

    private suspend fun cleanup(attempt: Attempt, error: Exception) = withContext(NonCancellable) {
        attempt.pending.getAndSet(null)?.result?.completeExceptionally(error)
        attempt.photo.getAndSet(null)?.completeExceptionally(error)
        try { withTimeout(5_000) { attempt.link.close() } } catch (_: Exception) { }
        attempt.reader?.cancel()
        attempt.monitor?.cancel()
        attempt.jpeg.reset()
    }
}
