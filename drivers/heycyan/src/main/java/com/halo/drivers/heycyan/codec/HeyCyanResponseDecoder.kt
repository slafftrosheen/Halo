package com.halo.drivers.heycyan.codec

import com.halo.drivers.heycyan.protocol.HeyCyanCommandCatalog
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * The unified response model.
 */
sealed class HeyCyanResponse {
    data class Battery(val level: Int, val charging: Boolean) : HeyCyanResponse()
    data class DeviceInfo(
        val twsFwLength: Int,
        val twsHwLength: Int,
        val socFwLength: Int,
        val socHwLength: Int,
        val strings: String
    ) : HeyCyanResponse()
    
    data class VolumeControl(
        val musicMin: Int, val musicMax: Int, val musicCurrent: Int,
        val callMin: Int, val callMax: Int, val callCurrent: Int,
        val systemMin: Int, val systemMax: Int, val systemCurrent: Int,
        val volumeType: Int
    ) : HeyCyanResponse()
    
    data class ClassBluetooth(val mac: ByteArray, val name: String) : HeyCyanResponse()
    
    sealed class GlassControl : HeyCyanResponse() {
        data class MediaCounts(val images: Int, val videos: Int, val records: Int) : GlassControl()
        data class WorkType(val glassWorkType: Int, val errorCode: Int, val workTypeIng: Int) : GlassControl()
        data class Video(val angle: Int, val duration: Int) : GlassControl()
        data class P2pIp(val ip: String) : GlassControl()
        data class Recording(val duration: Int) : GlassControl()
        data class Unknown(val dataType: Int, val raw: ByteArray) : GlassControl()
    }
    
    data class VoiceState(val payload: ByteArray) : HeyCyanResponse()
    data class Unknown(val action: Byte, val payload: ByteArray) : HeyCyanResponse()
}

/**
 * Authoritative response decoder based on BigDataBeanFactory.
 * It takes an already-verified BC frame (starting with 0xBC) and routes
 * based on the action byte.
 */
class HeyCyanResponseDecoder {

    fun decode(frame: ByteArray): HeyCyanResponse? = runCatching {
        if (frame.size < 6 || frame[0] != 0xBC.toByte()) return null
        
        val action = frame[1]
        val buffer = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN)

        return when (action) {
            HeyCyanCommandCatalog.ACTION_GLASSES_BATTERY -> {
                if (frame.size >= 8) {
                    HeyCyanResponse.Battery(
                        level = frame[6].toInt() and 0xFF,
                        charging = frame[7].toInt() != 0
                    )
                } else null
            }
            HeyCyanCommandCatalog.ACTION_DEVICE_INFO -> {
                if (frame.size >= 15) {
                    val twsFw = buffer.getShort(7).toInt() and 0xFFFF
                    val twsHw = buffer.getShort(9).toInt() and 0xFFFF
                    val socFw = buffer.getShort(11).toInt() and 0xFFFF
                    val socHw = buffer.getShort(13).toInt() and 0xFFFF
                    val strings = if (frame.size > 15) {
                        String(frame, 15, frame.size - 15, StandardCharsets.UTF_8)
                    } else ""
                    HeyCyanResponse.DeviceInfo(twsFw, twsHw, socFw, socHw, strings)
                } else null
            }
            HeyCyanCommandCatalog.ACTION_VOLUME_CONTROL -> {
                if (frame.size >= 20) {
                    HeyCyanResponse.VolumeControl(
                        musicMin = frame[8].toInt() and 0xFF,
                        musicMax = frame[9].toInt() and 0xFF,
                        musicCurrent = frame[10].toInt() and 0xFF,
                        callMin = frame[12].toInt() and 0xFF,
                        callMax = frame[13].toInt() and 0xFF,
                        callCurrent = frame[14].toInt() and 0xFF,
                        systemMin = frame[16].toInt() and 0xFF,
                        systemMax = frame[17].toInt() and 0xFF,
                        systemCurrent = frame[18].toInt() and 0xFF,
                        volumeType = frame[19].toInt() and 0xFF
                    )
                } else null
            }
            HeyCyanCommandCatalog.ACTION_BT_MAC_PROTOCOL -> {
                if (frame.size >= 13) {
                    val mac = frame.copyOfRange(6, 12)
                    val nameLen = frame[12].toInt() and 0xFF
                    val name = if (frame.size >= 13 + nameLen) {
                        String(frame, 13, nameLen, StandardCharsets.UTF_8)
                    } else ""
                    HeyCyanResponse.ClassBluetooth(mac, name)
                } else null
            }
            HeyCyanCommandCatalog.ACTION_GLASSES_CONTROL -> {
                if (frame.size >= 8) {
                    val dataType = frame[7].toInt() and 0xFF
                    when (dataType) {
                        1 -> {
                            if (frame.size >= 11) {
                                HeyCyanResponse.GlassControl.WorkType(
                                    frame[8].toInt() and 0xFF,
                                    frame[9].toInt() and 0xFF,
                                    frame[10].toInt() and 0xFF
                                )
                            } else null
                        }
                        2 -> {
                            if (frame.size >= 11) {
                                HeyCyanResponse.GlassControl.Video(
                                    frame[8].toInt() and 0xFF,
                                    buffer.getShort(9).toInt() and 0xFFFF
                                )
                            } else null
                        }
                        3 -> {
                            if (frame.size >= 13) {
                                // Replicating the vendor bug: data[10].data[11].data[12].data[12]
                                // Wait, user said: "Do not copy that bug into Halo. We should document it as a vendor implementation defect and compare it against the actual BLE/Wi-Fi notification we already discovered."
                                // Wait, the user actually said: "There is even a suspicious vendor bug in its P2P parser ... The fourth octet appears to accidentally use data[12] twice. Do not copy that bug into Halo."
                                // So we read 10, 11, 12, 13.
                                val ip = "${frame[10].toInt() and 0xFF}.${frame[11].toInt() and 0xFF}.${frame[12].toInt() and 0xFF}.${frame[13].toInt() and 0xFF}"
                                HeyCyanResponse.GlassControl.P2pIp(ip)
                            } else null
                        }
                        4 -> {
                            if (frame.size >= 14) {
                                HeyCyanResponse.GlassControl.MediaCounts(
                                    images = buffer.getShort(8).toInt() and 0xFFFF,
                                    videos = buffer.getShort(10).toInt() and 0xFFFF,
                                    records = buffer.getShort(12).toInt() and 0xFFFF
                                )
                            } else null
                        }
                        6 -> {
                            if (frame.size >= 10) {
                                HeyCyanResponse.GlassControl.Recording(
                                    buffer.getShort(8).toInt() and 0xFFFF
                                )
                            } else null
                        }
                        else -> {
                            HeyCyanResponse.GlassControl.Unknown(dataType, frame.copyOfRange(7, frame.size))
                        }
                    }
                } else null
            }
            HeyCyanCommandCatalog.ACTION_DEVICE_DATA_REPORTING -> {
                if (frame.size >= 7) {
                    val subType = frame[6].toInt() and 0xFF
                    when (subType) {
                        0x08 -> {
                            if (frame.size >= 11) {
                                val ip = "${frame[7].toInt() and 0xFF}.${frame[8].toInt() and 0xFF}.${frame[9].toInt() and 0xFF}.${frame[10].toInt() and 0xFF}"
                                HeyCyanResponse.GlassControl.P2pIp(ip)
                            } else {
                                HeyCyanResponse.VoiceState(frame.copyOfRange(6, frame.size))
                            }
                        }
                        0x09 -> {
                            val errorCode = if (frame.size >= 8) frame[7].toInt() and 0xFF else -1
                            HeyCyanResponse.GlassControl.WorkType(
                                glassWorkType = 0x07,
                                errorCode = errorCode,
                                workTypeIng = 0
                            )
                        }
                        else -> HeyCyanResponse.VoiceState(frame.copyOfRange(6, frame.size))
                    }
                } else {
                    HeyCyanResponse.VoiceState(frame.copyOfRange(6, frame.size))
                }
            }
            else -> {
                HeyCyanResponse.Unknown(action, frame.copyOfRange(6, frame.size))
            }
        }
    }.getOrNull()
}
