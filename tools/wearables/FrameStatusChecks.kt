package checks

import com.halo.sdk.runtime.wearables.*
import com.halo.sdk.runtime.wearables.frame.*
import com.halo.sdk.runtime.embodiment.DeviceKind
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

private class Link(var model: String = "Frame", var firmware: String = "v24.046.1546", var battery: String = "87",
    override val maxWriteBytes: Int = 509) : NativeByteLink {
    val packets = Channel<NativePacket>(64)
    override val incoming = packets.receiveAsFlow()
    override val connected = MutableStateFlow(false)
    val writes = mutableListOf<String>()
    var closed = false
    var reply = true
    var split = true
    var onWrite: (String) -> Unit = {}
    override suspend fun open() { check(!closed); connected.value = true }
    override suspend fun close() { closed = true; connected.value = false; packets.close() }
    override suspend fun write(bytes: ByteArray) {
        check(connected.value && !closed)
        val command = bytes.toString(Charsets.US_ASCII)
        writes += command; onWrite(command)
        if (!reply) return
        val token = Regex("~H([a-f0-9]{32}):").find(command)!!.groupValues[1]
        val value = when {
            "frame.HARDWARE_VERSION" in command -> model
            "frame.FIRMWARE_VERSION" in command -> firmware
            "frame.battery_level()" in command -> battery
            else -> error("Unexpected Lua command: $command")
        }
        val response = "~H$token:$value:H~".toByteArray()
        packets.send(NativePacket("unrelated print\n".toByteArray()))
        packets.send(NativePacket(byteArrayOf(1, 2, 3)))
        if (split) response.toList().chunked(7).forEach { packets.send(NativePacket(it.toByteArray())) }
        else packets.send(NativePacket(response))
    }
}
private var count = 0
private suspend fun test(name: String, block: suspend () -> Unit) { block(); count++; println("PASS $name") }
private suspend fun fails(block: suspend () -> Unit) { var failed = false; try { block() } catch (_: Exception) { failed = true }; check(failed) }
private fun driver(link: Link, timeout: Long = 1000) = BrilliantFrameDriver("selected-address", { link }, requestTimeoutMs = timeout)

fun main() = runBlocking {
    test("exact explicit identity aliases never infer Frame from brand or display name") {
        check(DeviceKind.fromDeviceType("brilliant-frame") == DeviceKind.BRILLIANT_FRAME)
        check(DeviceKind.fromDeviceType("Frame") == DeviceKind.UNKNOWN)
        check(DeviceKind.fromDeviceType("Brilliant Halo") == DeviceKind.UNKNOWN)
    }
    test("commands contain only the fixed vendor expressions") {
        FrameStatusProtocol.Query.entries.forEach {
            val b = FrameStatusProtocol.command(it, "a".repeat(32))
            check(b.size <= 244 && b.none { v -> v.toInt() in 0..4 })
            check("reset" !in b.toString(Charsets.US_ASCII) && "update" !in b.toString(Charsets.US_ASCII))
        }
        fails { FrameStatusProtocol.command(FrameStatusProtocol.Query.BATTERY, "';frame.update();--") }
    }
    test("packet boundaries, optional newline, noise and binary notifications") {
        val token = "a".repeat(32); val parser = FrameStatusProtocol.Reply(token)
        check(parser.append(byteArrayOf(1) + "~H$token:0:H~".toByteArray()) == null)
        check(parser.append("noise😀~H${token.take(16)}".toByteArray()) == null)
        check(parser.append("${token.drop(16)}:87:H~\n".toByteArray()) == "87")
    }
    test("old nonce cannot satisfy a new request") {
        val parser = FrameStatusProtocol.Reply("a".repeat(32))
        check(parser.append("~H${"b".repeat(32)}:90:H~".toByteArray()) == null)
        check(parser.append("~H${"a".repeat(32)}:91:H~".toByteArray()) == "91")
    }
    test("malformed and oversized responses are bounded") {
        val p = FrameStatusProtocol.Reply("a".repeat(32))
        repeat(100) { check(p.append("noise".repeat(100).toByteArray()) == null) }
        fails { p.append(ByteArray(513)) }
        fails { FrameStatusProtocol.Reply("a".repeat(32)).append(("~H${"a".repeat(32)}:" + "x".repeat(68)).toByteArray()) }
        fails { FrameStatusProtocol.Reply("a".repeat(32)).append("~H${"a".repeat(32)}:x\ny:H~".toByteArray()) }
    }
    test("battery and firmware values use documented bounds") {
        check(FrameStatusProtocol.battery("1") == 1 && FrameStatusProtocol.battery("100") == 100)
        listOf("0", "101", "-1", "87.0", "NaN", " 87", "87\n").forEach { fails { FrameStatusProtocol.battery(it) } }
        listOf("", "x".repeat(65), "v1\n", "<script>").forEach { fails { FrameStatusProtocol.firmware(it) } }
    }
    test("READY needs three correlated replies and exposes status only") {
        val l = Link(); val d = driver(l); d.connect()
        check(d.state.value.stage == NativeStage.READY && l.writes.size == 3)
        check(d.state.value.model == "Frame" && d.state.value.batteryPercent == 87)
        check(d.state.value.cameraAvailable == false && !d.state.value.aiPhotoAvailable)
        check(d.supportedActions == setOf(NativeAction.BATTERY, NativeAction.DEVICE_INFO)); d.dispose()
    }
    test("shared Frame/Halo UUID is insufficient; wrong model closes the link") {
        val l = Link(model = "Halo"); val d = driver(l); fails { d.connect() }
        check(l.closed && d.state.value.stage == NativeStage.ERROR && l.writes.size == 1); d.dispose()
    }
    test("bad battery cannot promote READY") {
        val l = Link(battery = "255"); val d = driver(l); fails { d.connect() }
        check(l.closed && d.state.value.batteryPercent == null); d.dispose()
    }
    test("negotiated payload bound prevents splitting Lua into invalid scripts") {
        val l = Link(maxWriteBytes = 20); val d = driver(l); fails { d.connect() }
        check(l.closed && l.writes.isEmpty()); d.dispose()
    }
    test("busy REPL times out without break, reset, retry or script installation") {
        val l = Link().apply { reply = false }; val d = driver(l, 70); fails { d.connect() }
        check(l.closed && l.writes.size == 1 && d.state.value.error!!.contains("will not interrupt")); d.dispose()
    }
    test("unsupported camera and display operations produce no protocol writes") {
        val l = Link(); val d = driver(l); d.connect(); val n = l.writes.size
        fails { d.execute(NativeAction.PHOTO) }; fails { d.captureAiPhoto() }
        check(l.writes.size == n && d.state.value.stage == NativeStage.READY); d.dispose()
    }
    test("observed source is consumed; repeated refresh never writes again") {
        val l = Link(); val d = driver(l); d.connect(); val version = d.state.value.sourceVersion
        l.battery = "60"
        check(d.execute(NativeAction.BATTERY, version).confirmation == NativeConfirmation.RECEIVED)
        check(d.state.value.batteryPercent == 60 && version != d.state.value.sourceVersion)
        val n = l.writes.size; fails { d.execute(NativeAction.BATTERY, version) }; check(n == l.writes.size); d.dispose()
    }
    test("revoked access blocks refresh before any write") {
        val l = Link(); val d = driver(l); d.connect(); val n = l.writes.size
        fails { d.execute(NativeAction.BATTERY, d.state.value.sourceVersion) { false } }
        check(l.writes.size == n && d.state.value.stage == NativeStage.READY); d.dispose()
    }
    test("revocation between hardware and firmware reads fences the second write") {
        val l = Link(); val d = driver(l); d.connect(); var access = true
        l.onWrite = { access = false }; val n = l.writes.size
        fails { d.execute(NativeAction.DEVICE_INFO, d.state.value.sourceVersion) { access } }
        check(l.writes.size == n + 1 && l.closed); d.dispose()
    }
    test("cancellation retires uncertain reads and permits no same-link reuse") {
        val l = Link(); val d = driver(l); d.connect(); l.reply = false
        val operation = launch { d.execute(NativeAction.BATTERY) }
        withTimeout(1000) { while (l.writes.size < 4) yield() }
        operation.cancelAndJoin(); check(l.closed && d.state.value.stage == NativeStage.ERROR)
        fails { d.execute(NativeAction.BATTERY) }; d.dispose()
    }
    test("concurrent calls with the same version can dispatch only one request") {
        val l = Link(); val d = driver(l); d.connect(); val v = d.state.value.sourceVersion
        val results = List(2) { async { runCatching { d.execute(NativeAction.BATTERY, v) }.isSuccess } }.awaitAll()
        check(results.count { it } == 1 && l.writes.size == 4); d.dispose()
    }
    test("reconnect creates a new link and invalidates old source tokens") {
        val a = Link(); val b = Link(); var next = a
        val d = BrilliantFrameDriver("selected", { next }, requestTimeoutMs = 1000)
        d.connect(); val old = d.state.value.sourceVersion; d.disconnect(); next = b; d.connect()
        fails { d.execute(NativeAction.BATTERY, old) }; check(a.closed && b.writes.size == 3); d.dispose()
        fails { d.connect() }
    }
    test("notification EOF retires even if connected flow has not updated") {
        val l = Link(); val d = driver(l); d.connect(); l.packets.close()
        withTimeout(1000) { d.state.first { it.stage == NativeStage.ERROR } }; check(l.closed); d.dispose()
    }
    println("Frame status contracts: $count passed (synthetic transport; no hardware qualification)")
}
