package com.halo.drivers.heycyan.protocol

/**
 * Authoritative mapping of HeyCyan hardware command actions.
 * Verified directly from vendor SDK `LargeDataHandler.java`.
 */
object HeyCyanCommandCatalog {
    const val ACTION_BT_MAC_PROTOCOL = 0x2E.toByte()
    const val ACTION_SYNC_TIME = 0x40.toByte()
    const val ACTION_GLASSES_CONTROL = 0x41.toByte()
    const val ACTION_GLASSES_BATTERY = 0x42.toByte()
    const val ACTION_DEVICE_INFO = 0x43.toByte()
    const val ACTION_DEVICE_AI_VOICE = 0x44.toByte()
    const val ACTION_DEVICE_HEART_BEAT = 0x45.toByte()
    const val ACTION_DEVICE_WEAR = 0x46.toByte()
    const val ACTION_DEVICE_WEAR_SUPPORT = 0x47.toByte()
    const val ACTION_VOICE_STATUS = 0x4C.toByte()
    const val ACTION_BT_CONNECT = 0x4D.toByte()
    const val ACTION_VOLUME_CONTROL = 0x51.toByte()
    const val ACTION_SPEAK_SOUND_SWITCH = 0x52.toByte()
    const val ACTION_GPT_UPLOAD = 0x59.toByte()
    const val ACTION_DEVICE_DATA_REPORTING = 0x73.toByte()
    const val ACTION_OTA_SOC = 0xFC.toByte()
    const val ACTION_PICTURE_THUMBNAILS = 0xFD.toByte()
}
