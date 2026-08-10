package com.halo.drivers.heycyan.protocol

import java.util.UUID

object HeyCyanProtocolConstants {

    val PRIMARY_SERVICE: UUID =
        UUID.fromString("7905FFF0-B5CE-4E99-A40F-4B1E122D00D0")

    val SECONDARY_SERVICE: UUID =
        UUID.fromString("6E40FFF0-B5A3-F393-E0A9-E50E24DCCA9E")

    val CCCD: UUID =
        UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

    object EventType {
        const val BATTERY = 0x01
        const val MEDIA_COUNTS = 0x02
        const val VERSION = 0x03
        const val MODE_CHANGED = 0x04
        const val WIFI_CREDENTIALS = 0x05
        const val AI_IMAGE = 0x06
    }

    object Mode {
        const val PHOTO = 0x01
        const val VIDEO = 0x02
        const val VIDEO_STOP = 0x03
        const val AUDIO = 0x04
        const val AUDIO_STOP = 0x05
        const val AI_PHOTO = 0x06
        const val TRANSFER = 0x07
    }
}