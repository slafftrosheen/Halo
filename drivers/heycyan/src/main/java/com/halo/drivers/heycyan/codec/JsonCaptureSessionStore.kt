package com.halo.drivers.heycyan.codec

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Persists complete BLE sessions as a single, replayable JSON document.
 *
 * JSON stores UUIDs as strings and packet payloads as uppercase hex so captures
 * remain readable and do not depend on Android or Java platform adapters.
 */
class JsonCaptureSessionStore(
    private val directory: File,
    moshi: Moshi = defaultMoshi()
) : CaptureSessionStore {

    private val adapter = moshi.adapter(BleCaptureJson::class.java)

    override fun save(capture: BleCapture): File {
        require(capture.captureId.isNotBlank()) { "Capture ID must not be blank" }
        check(directory.exists() || directory.mkdirs()) {
            "Unable to create capture directory: ${directory.absolutePath}"
        }

        val target = File(directory, "heycyan-${safeFileName(capture.captureId)}.json")
        val temporary = File(directory, "${target.name}.tmp")
        temporary.writeText(adapter.toJson(capture.toJsonModel()))

        try {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: Exception) {
            check(temporary.renameTo(target)) {
                "Unable to finalize capture: ${target.absolutePath}"
            }
        }

        return target
    }

    override fun load(file: File): BleCapture {
        require(file.isFile) { "Capture file does not exist: ${file.absolutePath}" }
        return adapter.fromJson(file.readText())?.toDomain()
            ?: throw IllegalArgumentException("Capture file is empty: ${file.absolutePath}")
    }

    fun listCaptures(): List<File> = directory.listFiles { file ->
        file.isFile && file.extension == "json" && file.name.startsWith("heycyan-")
    }?.sortedBy { it.name } ?: emptyList()

    fun loadCapture(captureId: String): BleCapture = listCaptures()
        .asSequence()
        .map { file -> load(file) }
        .firstOrNull { capture -> capture.captureId == captureId }
        ?: throw IllegalArgumentException("Capture not found: $captureId")

    /**
     * Copies an already validated capture without reserializing it. This keeps
     * import lossless even when a newer producer adds fields this reader ignores.
     */
    fun importRaw(source: File, capture: BleCapture = load(source)): File {
        require(source.isFile) { "Capture file does not exist: ${source.absolutePath}" }
        check(directory.exists() || directory.mkdirs()) {
            "Unable to create capture directory: ${directory.absolutePath}"
        }

        val target = File(directory, "heycyan-${safeFileName(capture.captureId)}.json")
        if (source.toPath().toAbsolutePath().normalize() == target.toPath().toAbsolutePath().normalize()) {
            return target
        }
        if (target.exists()) {
            val existing = load(target)
            require(existing == capture) {
                "Capture ID already exists with different evidence: ${capture.captureId}"
            }
            return target
        }

        val temporary = File(directory, "${target.name}.tmp")
        Files.copy(source.toPath(), temporary.toPath(), StandardCopyOption.REPLACE_EXISTING)
        try {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: Exception) {
            check(temporary.renameTo(target)) {
                "Unable to finalize imported capture: ${target.absolutePath}"
            }
        }
        return target
    }

    private fun safeFileName(value: String): String = value.replace(
        Regex("[^A-Za-z0-9._-]"),
        "_"
    )

    private fun BleCapture.toJsonModel() = BleCaptureJson(
        schemaVersion = schemaVersion,
        captureId = captureId,
        startedAt = startedAt,
        endedAt = endedAt,
        scenario = scenario,
        deviceAddress = deviceAddress,
        deviceName = deviceName,
        metadata = metadata,
        advertisements = advertisements,
        packets = packets.map { packet ->
            BlePacketJson(
                sequence = packet.sequence,
                timestamp = packet.timestamp,
                direction = packet.direction,
                characteristic = packet.characteristic.toString(),
                payload = HexCodec.encode(packet.payload)
            )
        },
        services = services,
        requestedMtu = requestedMtu,
        mtu = mtu,
        timeline = timeline
    )

    private fun BleCaptureJson.toDomain() = BleCapture(
        schemaVersion = schemaVersion,
        captureId = captureId,
        startedAt = startedAt,
        endedAt = endedAt,
        scenario = scenario,
        deviceAddress = deviceAddress,
        deviceName = deviceName,
        metadata = metadata ?: CaptureMetadata(),
        advertisements = advertisements,
        packets = packets.mapIndexed { index, packet ->
            BlePacket(
                sequence = packet.sequence ?: index.toLong(),
                timestamp = packet.timestamp,
                direction = packet.direction,
                characteristic = java.util.UUID.fromString(packet.characteristic),
                payload = HexCodec.decode(packet.payload)
            )
        },
        services = services,
        requestedMtu = requestedMtu,
        mtu = mtu,
        timeline = timeline
    )

    private companion object {
        fun defaultMoshi(): Moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }
}

@JsonClass(generateAdapter = false)
private data class BleCaptureJson(
    val schemaVersion: Int,
    val captureId: String,
    val startedAt: Long,
    val endedAt: Long?,
    val scenario: String,
    val deviceAddress: String?,
    val deviceName: String?,
    val metadata: CaptureMetadata? = null,
    val advertisements: List<CaptureAdvertisement>,
    val packets: List<BlePacketJson>,
    val services: List<CaptureServiceInfo>,
    val requestedMtu: Int?,
    val mtu: Int?,
    val timeline: List<CaptureEvent>
)

@JsonClass(generateAdapter = false)
private data class BlePacketJson(
    val sequence: Long? = null,
    val timestamp: Long,
    val direction: PacketDirection,
    val characteristic: String,
    val payload: String
)