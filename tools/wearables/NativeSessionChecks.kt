package checks

import com.halo.sdk.runtime.wearables.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

private class FakeLink(private val answer: (ByteArray) -> List<ByteArray>) : NativeByteLink {
    private val messages = MutableSharedFlow<NativePacket>(extraBufferCapacity = 32)
    override val incoming: Flow<NativePacket> = messages
    override val connected = MutableStateFlow(false)
    var closed = false
    val writes = mutableListOf<ByteArray>()
    override suspend fun open() { connected.value = true }
    override suspend fun write(bytes: ByteArray) {
        check(connected.value)
        writes += bytes.copyOf()
        answer(bytes).forEach { messages.emit(NativePacket(it)) }
    }
    override suspend fun close() { closed = true; connected.value = false }
    suspend fun inject(bytes: ByteArray) { messages.emit(NativePacket(bytes)) }
}

fun main() = runBlocking {
    var passed = 0
    suspend fun test(name: String, body: suspend () -> Unit) {
        body(); passed++; println("PASS $name")
    }
    fun eyeReply(bytes: ByteArray): List<ByteArray> {
        val command = EyeVueWire.Decoder().append(bytes).single().command
        return when (command) {
            0x64 -> listOf(EyeVueWire.encode(0x64, "SK01ACME".toByteArray()))
            0x17 -> listOf(EyeVueWire.encode(0x53, byteArrayOf(1, 76)))
            else -> emptyList()
        }
    }
    test("EyeVue ready requires real decoded identity and battery responses") {
        val link = FakeLink(::eyeReply)
        val driver = NativeWearableDriver("eye-1", NativeFamily.EYEVUE, { link }, requestTimeoutMs = 1000)
        driver.connect()
        check(driver.state.value.stage == NativeStage.READY)
        check(driver.state.value.batteryPercent == 76)
        check(driver.state.value.model == "SK01")
        driver.disconnect()
        check(link.closed && driver.state.value.batteryPercent == null)
    }
    test("no notifications is not a successful pairing") {
        val link = FakeLink { emptyList() }
        val driver = NativeWearableDriver("eye-2", NativeFamily.EYEVUE, { link }, requestTimeoutMs = 30)
        check(runCatching { driver.connect() }.isFailure)
        check(driver.state.value.stage == NativeStage.ERROR && link.closed)
    }
    test("EyeVue photo write is dispatched, not falsely confirmed") {
        val link = FakeLink(::eyeReply)
        val driver = NativeWearableDriver("eye-3", NativeFamily.EYEVUE, { link }, requestTimeoutMs = 1000)
        driver.connect()
        val receipt = driver.execute(NativeAction.PHOTO)
        check(receipt.confirmation == NativeConfirmation.DISPATCHED)
        val frame = EyeVueWire.Decoder().append(link.writes.last()).single()
        check(frame.command == 0x22 && frame.payload.contentEquals(byteArrayOf(0x31)))
        driver.disconnect()
    }
    test("media start uses 0x39, never live-preview 0x67") {
        check(NativeCommands.eyeVue(NativeAction.MEDIA_AP).command == 0x39)
        check(NativeCommands.eyeVue(NativeAction.PREVIEW_AP).command == 0x67)
        check(NativeCommands.eyeVue(NativeAction.END_TRANSFER).payload.contentEquals(byteArrayOf(0x30, 1)))
    }
    test("TuneBuds handshake and ACK are decoded independently of transport delivery") {
        var sequence = 0
        val link = FakeLink { bytes ->
            val request = TuneBudsWire.Decoder().append(bytes).single()
            val payload = if (request.command == 0x27) byteArrayOf(1, 1, 85.toByte(), 0xff.toByte(), 1, 20,
                0xa0.toByte(), 1, 1) else byteArrayOf(0)
            TuneBudsWire.encode(request.command, payload, type = 2, sequence = sequence).also {
                sequence = it.nextSequence
            }.frames
        }
        val driver = NativeWearableDriver("tune-1", NativeFamily.TUNEBUDS, { link }, requestTimeoutMs = 1000)
        driver.connect()
        check(driver.state.value.stage == NativeStage.READY)
        check(driver.state.value.batteryPercent == 85)
        check(driver.execute(NativeAction.PHOTO).confirmation == NativeConfirmation.ACKNOWLEDGED)
        driver.disconnect()
    }
    test("bad battery cannot authorize READY") {
        val link = FakeLink { bytes ->
            val cmd = EyeVueWire.Decoder().append(bytes).single().command
            if (cmd == 0x64) listOf(EyeVueWire.encode(cmd, "SK01ACME".toByteArray()))
            else listOf(EyeVueWire.encode(0x53, byteArrayOf(0, 127)))
        }
        val driver = NativeWearableDriver("bad", NativeFamily.EYEVUE, { link }, requestTimeoutMs = 100)
        check(runCatching { driver.connect() }.isFailure)
        check(driver.state.value.stage != NativeStage.READY)
        driver.disconnect()
    }
    test("disconnected driver refuses physical actions") {
        val link = FakeLink(::eyeReply)
        val driver = NativeWearableDriver("off", NativeFamily.EYEVUE, { link })
        check(runCatching { driver.execute(NativeAction.PHOTO) }.isFailure)
        check(link.writes.isEmpty())
        driver.disconnect()
    }
    test("failed requests retire transport so late replies cannot complete future actions") {
        val link = FakeLink(::eyeReply)
        val driver = NativeWearableDriver("late", NativeFamily.EYEVUE, { link }, requestTimeoutMs = 40)
        driver.connect()
        check(runCatching { driver.execute(NativeAction.MEDIA_COUNTS) }.isFailure)
        check(link.closed && driver.state.value.stage == NativeStage.ERROR)
        link.inject(EyeVueWire.encode(0x42, byteArrayOf(1, 2)))
        check(runCatching { driver.execute(NativeAction.PHOTO) }.isFailure)
        driver.disconnect()
    }
    test("hotspot configuration encodes UTF8 byte lengths and only verified busy codes retry") {
        val bytes = NativeCommands.hotspot("Halo ā", "12345678")
        check(bytes[4].toInt() == "Halo ā".toByteArray().size)
        check(listOf(1, 3, 5).all(NativeCommands::isTuneBudsBusy))
        check(!NativeCommands.isTuneBudsBusy(4))
    }
    test("thumbnail assembler rejects corrupt and oversized JPEG") {
        val photo = NativeJpegAssembler(limit = 32)
        photo.start()
        photo.append(byteArrayOf(1, 2, 3))
        check(runCatching { photo.finish() }.isFailure)
        photo.start()
        check(runCatching { photo.append(ByteArray(33)) }.isFailure)
        photo.start(); photo.append(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 1, 0xff.toByte(), 0xd9.toByte()))
        check(photo.finish().size == 5)
    }
    println("Native session checks: $passed passed")
}
