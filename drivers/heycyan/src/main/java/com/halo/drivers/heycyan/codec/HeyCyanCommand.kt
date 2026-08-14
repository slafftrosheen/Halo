package com.halo.drivers.heycyan.codec

/**
 * Semantic command builders for verified HeyCyan BLE commands.
 *
 * Each builder constructs a complete, verified wire frame with exact
 * action byte, payload, length, and CRC16 LE.
 */
object HeyCyanCommand {

    /**
     * Constructs a PHOTO command wire frame.
     * Output: BC 41 03 00 10 50 02 01 01
     */
    fun photo(): ByteArray = HeyCyanWireFrameCodec.encode(
        action = HeyCyanWireFrameCodec.ACTION_GLASSES_CONTROL,
        payload = HeyCyanWireFrameCodec.PAYLOAD_PHOTO
    )

    /**
     * Constructs a VIDEO_START command wire frame.
     * Output: BC 41 03 00 50 51 02 01 02
     */
    fun videoStart(): ByteArray = HeyCyanWireFrameCodec.encode(
        action = HeyCyanWireFrameCodec.ACTION_GLASSES_CONTROL,
        payload = HeyCyanWireFrameCodec.PAYLOAD_VIDEO_START
    )

    /**
     * Constructs a VIDEO_STOP command wire frame.
     * Output: BC 41 03 00 91 91 02 01 03
     */
    fun videoStop(): ByteArray = HeyCyanWireFrameCodec.encode(
        action = HeyCyanWireFrameCodec.ACTION_GLASSES_CONTROL,
        payload = HeyCyanWireFrameCodec.PAYLOAD_VIDEO_STOP
    )

    fun recordStart(): ByteArray = HeyCyanWireFrameCodec.encode(
        action = HeyCyanWireFrameCodec.ACTION_GLASSES_CONTROL,
        payload = HeyCyanWireFrameCodec.PAYLOAD_RECORD_START
    )

    fun recordStop(): ByteArray = HeyCyanWireFrameCodec.encode(
        action = HeyCyanWireFrameCodec.ACTION_GLASSES_CONTROL,
        payload = HeyCyanWireFrameCodec.PAYLOAD_RECORD_STOP
    )

    fun aiThumbnail(): ByteArray = HeyCyanWireFrameCodec.encode(
        action = HeyCyanWireFrameCodec.ACTION_GLASSES_CONTROL,
        payload = HeyCyanWireFrameCodec.PAYLOAD_AI_THUMBNAIL
    )

    /**
     * Constructs a WIFI_TRANSFER command wire frame.
     * Output: BC 41 04 00 93 5C 02 01 04 01
     */
    fun wifiTransferMode(): ByteArray = HeyCyanWireFrameCodec.encode(
        action = HeyCyanWireFrameCodec.ACTION_GLASSES_CONTROL,
        payload = HeyCyanWireFrameCodec.PAYLOAD_WIFI_TRANSFER
    )

    /**
     * Constructs a VOICE_ROUTING command wire frame.
     * Output: BC 41 03 00 90 57 02 01 0B
     */
    fun prepareVoiceRouting(): ByteArray = HeyCyanWireFrameCodec.encode(
        action = HeyCyanWireFrameCodec.ACTION_GLASSES_CONTROL,
        payload = HeyCyanWireFrameCodec.PAYLOAD_VOICE_ROUTING
    )

    /**
     * Constructs a MEDIA_COUNT command wire frame.
     * Output: BC 41 02 00 01 13 02 04
     */
    fun mediaCount(): ByteArray = HeyCyanWireFrameCodec.encode(
        action = HeyCyanWireFrameCodec.ACTION_GLASSES_CONTROL,
        payload = HeyCyanWireFrameCodec.PAYLOAD_MEDIA_COUNT
    )

    /**
     * Constructs an AI command wire frame with thumbnail size parameter (0..6).
     * Format payload: 02 01 06 <thumbnailSize> <thumbnailSize> 02
     */
    fun ai(thumbnailSize: Int = 0): ByteArray {
        require(thumbnailSize in 0..6) {
            "Invalid thumbnailSize: $thumbnailSize. Documented vendor range is 0..6."
        }
        val sizeByte = thumbnailSize.toByte()
        val payload = byteArrayOf(0x02, 0x01, 0x06, sizeByte, sizeByte, 0x02)
        return HeyCyanWireFrameCodec.encode(
            action = HeyCyanWireFrameCodec.ACTION_GLASSES_CONTROL,
            payload = payload
        )
    }
}
