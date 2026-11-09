package com.halo.sdk.runtime.wearables

private var passed = 0
private fun case(name: String, body: () -> Unit) { body(); passed++; println("PASS $name") }
private fun hex(value: String) = value.split(' ').map { it.toInt(16).toByte() }.toByteArray()
private fun bytes(expected: String, actual: ByteArray) = check(hex(expected).contentEquals(actual)) { "Wrong bytes: ${actual.joinToString { "%02X".format(it) }}" }
private fun fails(body: () -> Unit) { check(runCatching(body).isFailure) }
fun main() {
    case("EyeVue media AP vector") { bytes("AB 55 00 03 39 30 69", EyeVueWire.encode(0x39, byteArrayOf(0x30))) }
    case("EyeVue live P2P differs from media") { bytes("AB 55 00 03 67 31 98", EyeVueWire.encode(0x67, byteArrayOf(0x31))) }
    case("EyeVue cleanup vector") { bytes("AB 55 00 04 44 30 01 75", EyeVueWire.encode(0x44, byteArrayOf(0x30, 1))) }
    case("EyeVue every split point") {
        val data = EyeVueWire.encode(0x53, byteArrayOf(1, 76))
        for (i in 0..data.size) {
            val decoder = EyeVueWire.Decoder()
            val frames = decoder.append(data.copyOfRange(0, i)) + decoder.append(data.copyOfRange(i, data.size))
            check(frames.size == 1 && frames.single().command == 0x53)
            check(frames.single().payload.contentEquals(byteArrayOf(1, 76)))
        }
    }
    case("EyeVue corrupt checksum never delivered") {
        val frame = EyeVueWire.encode(0x53, byteArrayOf(0, 50)); frame[frame.lastIndex] = 0
        check(EyeVueWire.Decoder().append(frame).isEmpty())
    }
    case("EyeVue coalesced noise and frames") {
        val frame = EyeVueWire.encode(0x64)
        check(EyeVueWire.Decoder().append(byteArrayOf(1, 2, 3) + frame + frame).size == 2)
    }
    case("EyeVue rejects unsupported payload size") { fails { EyeVueWire.encode(1, ByteArray(65534)) } }
    case("TuneBuds query vector") { bytes("00 27 01 00 02 01 00", TuneBudsWire.encode(0x27, byteArrayOf(1, 0)).frames.single()) }
    case("TuneBuds fragmented stream and sequence gaps") {
        val payload = ByteArray(44) { it.toByte() }
        val encoded = TuneBudsWire.encode(0xE3, payload, type = 3, packetSize = 20)
        val decoder = TuneBudsWire.Decoder()
        val stream = encoded.frames.mapIndexed { i, v -> v.copyOf().also { it[0] = ((i * 3) and 15).toByte() } }.fold(byteArrayOf()) { a, b -> a + b }
        val frames = stream.flatMap { decoder.append(byteArrayOf(it)) }
        check(frames.size == 1 && frames.single().payload.contentEquals(payload))
    }
    case("TuneBuds wrong logical fragment rejected") {
        val encoded = TuneBudsWire.encode(0xE3, ByteArray(30), type = 3)
        val decoder = TuneBudsWire.Decoder()
        check(decoder.append(encoded.frames.last()).isEmpty())
    }
    case("TuneBuds strips AI side envelopes") {
        val data = hex("55 AA 01 02 03 A5 5A") + TuneBudsWire.encode(0x27, byteArrayOf(1, 0), type = 2).frames.single()
        check(TuneBudsWire.Decoder().append(data).single().command == 0x27)
    }
    case("TuneBuds bounded fragments") { fails { TuneBudsWire.encode(1, ByteArray(241), packetSize = 20) } }
    case("TLV truncated values rejected") { fails { TuneBudsWire.tlv(byteArrayOf(1, 4, 0)) } }
    case("TLV values retain field IDs") { check(TuneBudsWire.tlv(byteArrayOf(1, 1, 65))[1]!!.contentEquals(byteArrayOf(65))) }
    case("unknown message type rejected") { check(TuneBudsWire.Decoder().append(hex("00 27 04 00 00")).isEmpty()) }
    println("$passed protocol checks passed")
}
