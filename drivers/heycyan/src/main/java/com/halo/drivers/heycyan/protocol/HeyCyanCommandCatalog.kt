package com.halo.drivers.heycyan.protocol

/**
 * Authoritative HeyCyan action identifiers extracted from the vendor SDK
 * `LargeDataHandler.java`.
 *
 * An action identifier alone does NOT make a command executable. The executor
 * must also have an evidence-backed payload definition before transmission is
 * permitted.
 */
object HeyCyanCommandCatalog {
    const val ACTION_BT_MAC_PROTOCOL = 0x2E.toByte()
    /** Action ID exists in the vendor SDK; executable payload remains unverified. */
    const val ACTION_SYNC_TIME = 0x40.toByte()
    const val ACTION_GLASSES_CONTROL = 0x41.toByte()
    const val ACTION_GLASSES_BATTERY = 0x42.toByte()
    const val ACTION_DEVICE_INFO = 0x43.toByte()
    const val ACTION_DEVICE_AI_VOICE = 0x44.toByte()
    const val ACTION_DEVICE_HEART_BEAT = 0x45.toByte()
    const val ACTION_DEVICE_WEAR = 0x46.toByte()
    const val ACTION_DEVICE_WEAR_SUPPORT = 0x47.toByte()
    const val ACTION_VOICE_STATUS = 0x48.toByte()
    const val ACTION_BT_CONNECT = 0x49.toByte()
    const val ACTION_VOLUME_CONTROL = 0x51.toByte()
    const val ACTION_SPEAK_SOUND_SWITCH = 0x52.toByte()
    const val ACTION_GPT_UPLOAD = 0x59.toByte()
    const val ACTION_DEVICE_DATA_REPORTING = 0x73.toByte()
    const val ACTION_OTA_SOC = 0xFC.toByte()
    const val ACTION_PICTURE_THUMBNAILS = 0xFD.toByte()
}
