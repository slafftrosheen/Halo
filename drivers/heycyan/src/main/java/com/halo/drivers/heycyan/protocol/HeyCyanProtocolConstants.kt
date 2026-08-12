package com.halo.drivers.heycyan.protocol

import java.util.UUID

object HeyCyanProtocolConstants {

    /** Active Android AAR & Physical Capture GATT Service UUID */
    val PRIMARY_SERVICE: UUID =
        UUID.fromString("de5bf728-d711-4e47-af26-65e3012a5dc7")

    /** Active Android AAR & Physical Capture RX Notify Characteristic UUID */
    val NOTIFY_CHARACTERISTIC: UUID =
        UUID.fromString("de5bf729-d711-4e47-af26-65e3012a5dc7")

    /** Active Android AAR & Physical Capture TX Write Characteristic UUID */
    val WRITE_CHARACTERISTIC: UUID =
        UUID.fromString("de5bf72a-d711-4e47-af26-65e3012a5dc7")

    /** Alternate candidate iOS Primary Service UUID */
    val ALTERNATE_IOS_PRIMARY_SERVICE: UUID =
        UUID.fromString("7905FFF0-B5CE-4E99-A40F-4B1E122D00D0")

    /** Alternate candidate iOS Secondary Service UUID */
    val ALTERNATE_IOS_SECONDARY_SERVICE: UUID =
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