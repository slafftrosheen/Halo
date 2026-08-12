package com.halo.drivers.heycyan.protocol

/**
 * Commands documented in the HeyCyan vendor SDK documentation.
 *
 * IMPORTANT: These represent the semantic operation layer of the SDK,
 * NOT the verified BLE wire framing.
 *
 * DO NOT implement a wire-level encoder for these values until
 * physical TX captures have verified the packet encapsulation.
 */
enum class HeyCyanSdkCommand(val payload: ByteArray) {
    /** 02 01 01 */
    PHOTO(byteArrayOf(0x02, 0x01, 0x01)),

    /** 02 01 02 */
    VIDEO_START(byteArrayOf(0x02, 0x01, 0x02)),

    /** 02 01 03 */
    VIDEO_STOP(byteArrayOf(0x02, 0x01, 0x03)),

    /** 02 01 08 */
    RECORD_START(byteArrayOf(0x02, 0x01, 0x08)),

    /** 02 01 0C */
    RECORD_STOP(byteArrayOf(0x02, 0x01, 0x0C)),

    /** 02 04 */
    MEDIA_COUNT(byteArrayOf(0x02, 0x04));

    companion object {
        /**
         * Marker for commands whose wire framing is not yet established.
         * Used to prevent premature implementation of device control.
         */
        const val SDK_COMMAND_REFERENCE_ONLY = true
    }
}
