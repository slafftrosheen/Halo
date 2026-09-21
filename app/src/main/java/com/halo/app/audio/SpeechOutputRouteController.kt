package com.halo.app.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.halo.drivers.heycyan.RealHeyCyanDriver
import com.halo.drivers.heycyan.protocol.HeyCyanCommand
import com.halo.drivers.heycyan.protocol.HeyCyanConnectionState
import com.halo.sdk.runtime.SpeechOutputTarget
import com.halo.sdk.runtime.embodiment.heycyan.HeyCyanOperation

import com.halo.host.android.audio.BluetoothAudioRouteMonitor
import com.halo.host.android.audio.AndroidBluetoothAudioRouteMonitor
import com.halo.sdk.runtime.*

data class SpeechRouteRequest(
    val transactionId: String,
    val target: SpeechOutputTarget,
    val fallback: SpeechOutputFallback,
    val sourceCaptureId: String?
)

class SpeechOutputRouteController(
    private val context: Context,
    private val heyCyanDriver: RealHeyCyanDriver,
    private val routeMonitor: BluetoothAudioRouteMonitor = AndroidBluetoothAudioRouteMonitor(context)
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    suspend fun prepareRoute(request: SpeechRouteRequest): SpeechRoutePreparation {
        val target = request.target
        return when (target) {
            is SpeechOutputTarget.Phone -> {
                Log.i("HaloAudio", "AUDIO_ROUTE_REQUEST transaction=${request.transactionId} target=Phone")
                heyCyanDriver.capabilityController.execute(HeyCyanOperation.RouteAudioToPhone)
                SpeechRoutePreparation.Ready(
                    target = target,
                    evidence = SpeechRouteEvidence(
                        vendorRouteCommandSent = true,
                        androidDeviceType = null,
                        androidDeviceName = "Phone Speaker",
                        androidDeviceAddress = null,
                        verifiedAtMs = System.currentTimeMillis()
                    )
                )
            }
            is SpeechOutputTarget.Wearable -> {
                Log.i("HaloAudio", "AUDIO_ROUTE_REQUEST transaction=${request.transactionId} target=Wearable device=${target.deviceId}")
                
                val activeDevice = heyCyanDriver.activeDeviceId
                if (activeDevice == null || activeDevice != target.deviceId) {
                    Log.i("HaloAudio", "AUDIO_ROUTE_FAILED target=wearable reason=WRONG_DEVICE")
                    return SpeechRoutePreparation.Unavailable(
                        target = target,
                        reason = SpeechRouteFailureReason.WRONG_DEVICE
                    )
                }

                if (heyCyanDriver.deviceState.value.connection != HeyCyanConnectionState.READY) {
                    Log.i("HaloAudio", "AUDIO_ROUTE_FAILED target=wearable reason=DEVICE_DISCONNECTED")
                    return SpeechRoutePreparation.Unavailable(
                        target = target,
                        reason = SpeechRouteFailureReason.DEVICE_DISCONNECTED
                    )
                }

                // 1. Enable Classic Bluetooth on glasses
                val btResult = heyCyanDriver.capabilityController.execute(HeyCyanOperation.EnableClassicBluetooth)
                if (btResult.isFailure) {
                    Log.w("HaloAudio", "HEYCYAN_CLASSIC_ENABLE device=${target.deviceId} result=failed")
                    return SpeechRoutePreparation.Unavailable(target, SpeechRouteFailureReason.COMMAND_FAILED)
                }
                Log.i("HaloAudio", "HEYCYAN_CLASSIC_ENABLE device=${target.deviceId} result=success")

                val endpointsBeforeRoute = routeMonitor.routes.value

                // 2. Send RouteAudioToGlasses command
                val routeResult = heyCyanDriver.capabilityController.execute(HeyCyanOperation.RouteAudioToGlasses)
                if (routeResult.isFailure) {
                    Log.i("HaloAudio", "HEYCYAN_VENDOR_ROUTE device=${target.deviceId} route=GLASSES result=failed")
                    return SpeechRoutePreparation.Unavailable(
                        target = target,
                        reason = SpeechRouteFailureReason.COMMAND_FAILED
                    )
                }
                Log.i("HaloAudio", "HEYCYAN_VENDOR_ROUTE device=${target.deviceId} route=GLASSES result=success")

                // 3. Await/verify Android Bluetooth audio endpoint
                val endpoint = routeMonitor.awaitEndpoint(
                    predicate = { ep ->
                        ep.connected && (
                            ep.address?.equals(target.deviceId, ignoreCase = true) == true ||
                                endpointsBeforeRoute.none { before ->
                                    before.address == ep.address && before.type == ep.type
                                }
                            )
                    },
                    timeoutMs = 2_000L
                )

                if (endpoint == null) {
                    Log.i("HaloAudio", "AUDIO_ROUTE_FAILED target=wearable reason=AUDIO_TRANSPORT_NOT_CONNECTED")
                    return SpeechRoutePreparation.Unavailable(
                        target = target,
                        reason = SpeechRouteFailureReason.AUDIO_TRANSPORT_NOT_CONNECTED
                    )
                }

                Log.i("HaloAudio", "AUDIO_ROUTE_READY target=wearable verified=true endpoint=${endpoint.name}")
                SpeechRoutePreparation.Ready(
                    target = target,
                    evidence = SpeechRouteEvidence(
                        vendorRouteCommandSent = true,
                        androidDeviceType = endpoint.type.ordinal,
                        androidDeviceName = endpoint.name,
                        androidDeviceAddress = endpoint.address,
                        verifiedAtMs = System.currentTimeMillis()
                    )
                )
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
