package com.halo.sdk.runtime.wearables

import java.io.ByteArrayOutputStream
import java.util.Calendar
import java.util.TimeZone

/** Implemented native protocol families. Other brands must supply their own adapter. */
enum class NativeFamily { EYEVUE, TUNEBUDS }
enum class NativeStage { DISCONNECTED, CONNECTING, HANDSHAKE, READY, ERROR }
enum class NativeAction {
    BATTERY, DEVICE_INFO, MEDIA_COUNTS, STORAGE, PHOTO, AI_PHOTO,
    START_VIDEO, STOP_VIDEO, START_AUDIO, STOP_AUDIO, SYNC_TIME,
    MEDIA_AP, MEDIA_P2P, PREVIEW_AP, PREVIEW_P2P, END_TRANSFER,
}
enum class NativeConfirmation { DISPATCHED, ACKNOWLEDGED, RECEIVED }

data class NativeCommand(
    val command: Int,
    val payload: ByteArray = byteArrayOf(),
    val responses: Set<Int> = emptySet(),
    val responseType: Int? = null,
    val statusResponse: Boolean = false,
)
data class NativeReceipt(val confirmation: NativeConfirmation, val frame: WearableFrame? = null)
data class NativeSnapshot(
    val stage: NativeStage = NativeStage.DISCONNECTED,
    val batteryPercent: Int? = null,
    val charging: Boolean? = null,
    val model: String? = null,
    val firmware: String? = null,
    val mediaCounts: List<Long>? = null,
    val wifiSsid: String? = null,
    val mediaEndpoint: String? = null,
    val wifiFailure: Int? = null,
    val cameraAvailable: Boolean? = null,
    val aiPhotoAvailable: Boolean = false,
    val aggregateMediaCount: Int? = null,
    val videoRecording: Boolean? = null,
    val audioRecording: Boolean? = null,
    val error: String? = null,
)

/** Evidence: CyanBridge d7e726e EyevueProtocol, TuneBudsProtocol and manager flows.
 * No arbitrary opcode escapes or firmware/reset operations are exposed. */
object NativeCommands {
    fun eyeVue(action: NativeAction, now: Long = System.currentTimeMillis()): NativeCommand {
        fun value(cmd: Int, value: Int, responses: Set<Int> = emptySet()) =
            NativeCommand(cmd, byteArrayOf(value.toByte()), responses)
        return when (action) {
            NativeAction.BATTERY -> value(0x17, 0, setOf(0x17, 0x53))
            NativeAction.DEVICE_INFO -> NativeCommand(0x64, responses = setOf(0x64))
            NativeAction.MEDIA_COUNTS -> value(0x40, 0, setOf(0x42))
            NativeAction.STORAGE -> value(0x16, 0, setOf(0x16))
            NativeAction.PHOTO -> value(0x22, 0x31)
            NativeAction.AI_PHOTO -> value(0x22, 0x30)
            NativeAction.START_VIDEO -> value(0x23, 1)
            NativeAction.STOP_VIDEO -> value(0x24, 0)
            NativeAction.START_AUDIO -> value(0x34, 1)
            NativeAction.STOP_AUDIO -> value(0x34, 0)
            NativeAction.MEDIA_AP -> value(0x39, 0x30, setOf(0x25))
            NativeAction.MEDIA_P2P -> value(0x39, 0x31, setOf(0x25))
            NativeAction.PREVIEW_AP -> value(0x67, 0x30, setOf(0x25))
            NativeAction.PREVIEW_P2P -> value(0x67, 0x31, setOf(0x25))
            NativeAction.END_TRANSFER -> NativeCommand(0x44, byteArrayOf(0x30, 1))
            NativeAction.SYNC_TIME -> {
                val c = Calendar.getInstance().apply { timeInMillis = now }
                require(c.get(Calendar.YEAR) in 2000..2099)
                NativeCommand(0x59, byteArrayOf((c.get(Calendar.YEAR) - 2000).toByte(),
                    (c.get(Calendar.MONTH) + 1).toByte(), c.get(Calendar.DAY_OF_MONTH).toByte(),
                    c.get(Calendar.HOUR_OF_DAY).toByte(), c.get(Calendar.MINUTE).toByte(),
                    c.get(Calendar.SECOND).toByte()))
            }
        }
    }

    fun tuneBuds(action: NativeAction, now: Long = System.currentTimeMillis()): NativeCommand {
        fun command(cmd: Int, payload: ByteArray = byteArrayOf(), status: Boolean = true) =
            NativeCommand(cmd, payload, setOf(cmd), responseType = 2, statusResponse = status)
        return when (action) {
            NativeAction.BATTERY -> command(0x27, byteArrayOf(1, 0), status = false)
            NativeAction.DEVICE_INFO -> command(0x27,
                byteArrayOf(0xff.toByte(), 0, 0xa0.toByte(), 0, 1, 0, 2, 0, 0x86.toByte(), 0), false)
            NativeAction.MEDIA_COUNTS -> command(0xef)
            NativeAction.STORAGE -> command(0xea)
            NativeAction.PHOTO -> command(0xe1, byteArrayOf(0))
            NativeAction.AI_PHOTO -> command(0xe1, byteArrayOf(1))
            NativeAction.START_VIDEO -> command(0xe4)
            NativeAction.START_AUDIO -> command(0xe5)
            NativeAction.STOP_VIDEO, NativeAction.STOP_AUDIO, NativeAction.END_TRANSFER -> command(0xe2)
            NativeAction.SYNC_TIME -> {
                val seconds = now / 1000L
                require(seconds in 0..0xffffffffL)
                val offset = TimeZone.getDefault().getOffset(now) / 60000
                command(0xf0, byteArrayOf(seconds.toByte(), (seconds shr 8).toByte(),
                    (seconds shr 16).toByte(), (seconds shr 24).toByte(), offset.toByte(), (offset shr 8).toByte()))
            }
            else -> throw UnsupportedOperationException("TuneBuds uses its local-hotspot transfer workflow")
        }
    }

    fun hotspot(ssid: String, password: String, channel: Int = 0): ByteArray {
        val s = ssid.toByteArray(Charsets.UTF_8)
        val p = password.toByteArray(Charsets.UTF_8)
        require(s.size in 1..32 && p.size in 8..63 && channel in 0..255)
        require(s.none { it == 0.toByte() } && p.none { it == 0.toByte() })
        return byteArrayOf(1, 1, 0, 2, s.size.toByte()) + s +
            byteArrayOf(3, p.size.toByte()) + p + byteArrayOf(4, 1, channel.toByte())
    }

    fun isTuneBudsBusy(status: Int): Boolean = status in setOf(1, 3, 5)

    fun eyeVueBattery(frame: WearableFrame): Pair<Int, Boolean>? {
        val p = frame.payload
        if (p.size < 2) return null
        val result = when (frame.command) {
            0x17 -> ((p[0].toInt() and 15) * 10 + (p[1].toInt() and 15)) to (p.getOrNull(2)?.toInt() == 1)
            0x53 -> (p[1].toInt() and 255) to (p[0].toInt() == 1)
            else -> return null
        }
        return result.takeIf { it.first in 0..100 }
    }

    fun unsigned32(bytes: ByteArray, offset: Int): Long {
        require(offset >= 0 && offset <= bytes.size - 4)
        return (0..3).fold(0L) { value, index -> value or ((bytes[offset + index].toLong() and 255) shl (8 * index)) }
    }
}

/** Bounded assembly; media is not accepted merely because a final packet arrived. */
class NativeJpegAssembler(private val limit: Int = 8 * 1024 * 1024) {
    private val bytes = ByteArrayOutputStream()
    private var active = false
    @Synchronized fun start() { bytes.reset(); active = true }
    @Synchronized fun reset() { active = false; bytes.reset() }
    @Synchronized fun append(chunk: ByteArray) {
        check(active) { "Image stream has no start marker" }
        if (chunk.size > limit - bytes.size()) { reset(); error("Image exceeds transfer limit") }
        bytes.write(chunk)
    }
    @Synchronized fun finish(): ByteArray {
        check(active) { "Image stream has no start marker" }
        val result = bytes.toByteArray()
        reset()
        require(result.size >= 4 && result[0] == 0xff.toByte() && result[1] == 0xd8.toByte() &&
            result[result.lastIndex - 1] == 0xff.toByte() && result.last() == 0xd9.toByte()) { "Incomplete JPEG" }
        return result
    }
}
