package com.halo.app.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.halo.drivers.heycyan.RealHeyCyanDriver
import com.halo.drivers.heycyan.protocol.HeyCyanCommand
import com.halo.sdk.runtime.SpeechOutputTarget
import com.halo.sdk.runtime.embodiment.heycyan.HeyCyanOperation

class SpeechOutputRouteController(
    private val context: Context,
    private val heyCyanDriver: RealHeyCyanDriver
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    suspend fun prepareRoute(target: SpeechOutputTarget): Boolean {
        return when (target) {
            is SpeechOutputTarget.Phone -> {
                Log.i("HaloAudio", "HALO_AUDIO_ROUTE_REQUEST target=phone")
                // For phone, we don't send commands to HeyCyan. We could restore phone route.
                val result = heyCyanDriver.capabilityController.execute(HeyCyanOperation.RouteAudioToPhone)
                if (result.isSuccess) {
                    Log.i("HaloAudio", "HALO_AUDIO_ROUTE_PHONE command=0x52 payload=0202 result=success")
                } else {
                    Log.i("HaloAudio", "HALO_AUDIO_ROUTE_PHONE result=failed (device not ready or not connected)")
                }
                true
            }
            is SpeechOutputTarget.Wearable -> {
                Log.i("HaloAudio", "HALO_AUDIO_ROUTE_REQUEST target=wearable device=${target.deviceId}")
                // Send the vendor command RouteAudioToGlasses
                val result = heyCyanDriver.capabilityController.execute(HeyCyanOperation.RouteAudioToGlasses)
                if (result.isFailure) {
                    Log.e("HaloAudio", "HALO_AUDIO_ROUTE_FAILED transaction=NA reason=HeyCyan command failed")
                    return false
                }
                Log.i("HaloAudio", "HALO_AUDIO_ROUTE_HEYCYAN command=0x52 payload=0201 result=success")
                verifyAndroidMediaRoute()
                Log.i("HaloAudio", "HALO_AUDIO_ROUTE_READY target=wearable device=${target.deviceId}")
                true
            }
        }
    }

    private fun verifyAndroidMediaRoute() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .filter {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                }
                .joinToString { "type=${it.type},name=${it.productName}" }
            Log.i("HaloAudio", "HALO_AUDIO_ROUTE_ANDROID usage=MEDIA candidates=[$outputs]")
        } else {
            Log.i("HaloAudio", "HALO_AUDIO_ROUTE_ANDROID usage=MEDIA candidates=unknown(API<23)")
        }
    }
}
