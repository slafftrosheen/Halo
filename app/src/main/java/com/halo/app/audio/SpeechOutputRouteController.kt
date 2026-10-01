package com.halo.app.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.util.Log
import com.halo.sdk.runtime.SpeechOutputTarget

import com.halo.host.android.audio.BluetoothAudioRouteMonitor
import com.halo.host.android.audio.AndroidBluetoothAudioRouteMonitor
import com.halo.host.android.audio.BluetoothAudioEndpointType
import com.halo.sdk.runtime.*
import kotlinx.coroutines.CancellationException

data class SpeechRouteRequest(
    val transactionId: String,
    val target: SpeechOutputTarget,
    val fallback: SpeechOutputFallback,
    val sourceCaptureId: String?
)

class SpeechOutputRouteController(
    context: Context,
    private val wearableRouteAdapter: WearableAudioRouteAdapter,
    private val routeMonitor: BluetoothAudioRouteMonitor = AndroidBluetoothAudioRouteMonitor(context),
    private val audioPairingCoordinator: HeyCyanAudioPairingCoordinator? = null
) {
    private val audioManager = context.applicationContext.getSystemService(android.media.AudioManager::class.java)
    @Volatile private var lastGlassesDeviceId: String? = null

    suspend fun prewarmRoute(deviceId: String) {
        if (wearableRouteAdapter.deviceId == deviceId) {
            audioPairingCoordinator?.ensureRestored(deviceId)
            try {
                wearableRouteAdapter.prepareAudioTransport()
            } catch (e: Exception) {
                Log.w("HaloAudio", "Prewarm prepareAudioTransport failed for $deviceId", e)
            }
            routeMonitor.refreshEndpoints()
            Log.i("HaloAudio", "HALO_AUDIO_PREWARM_STATE device=$deviceId pairingState=${audioPairingCoordinator?.state?.value}")
        }
    }

    suspend fun prepareRoute(request: SpeechRouteRequest): SpeechRoutePreparation {
        val target = request.target
        return when (target) {
            is SpeechOutputTarget.Phone -> {
                Log.i("HaloAudio", "AUDIO_ROUTE_REQUEST transaction=${request.transactionId} target=Phone")
                val previousDevice = lastGlassesDeviceId
                val vendorCommandSent = if (previousDevice != null && wearableRouteAdapter.deviceId == previousDevice) {
                    try {
                        wearableRouteAdapter.releaseOutputRoute().isSuccess
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Log.w("HaloAudio", "Ignoring wearable route reset failure for phone speech", error)
                        false
                    }
                } else false
                lastGlassesDeviceId = null
                val speaker = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    ?: return SpeechRoutePreparation.Unavailable(target, SpeechRouteFailureReason.AUDIO_TRANSPORT_NOT_CONNECTED)
                SpeechRoutePreparation.Ready(
                    target = target,
                    evidence = SpeechRouteEvidence(
                        vendorRouteCommandSent = vendorCommandSent,
                        androidDeviceType = speaker.type,
                        androidDeviceName = speaker.productName.toString(),
                        androidDeviceAddress = null,
                        androidDeviceId = speaker.id,
                        verifiedAtMs = System.currentTimeMillis()
                    )
                )
            }
            is SpeechOutputTarget.Device -> {
                Log.i("HaloAudio", "AUDIO_ROUTE_REQUEST transaction=${request.transactionId} target=Device device=${target.deviceId} kind=${target.kind}")

                if (target.kind != AudioDeviceKind.WEARABLE) {
                    return SpeechRoutePreparation.Ready(
                        target,
                        SpeechRouteEvidence(
                            vendorRouteCommandSent = false,
                            androidDeviceType = null,
                            androidDeviceName = null,
                            androidDeviceAddress = null,
                            androidDeviceId = target.platformEndpointId
                        )
                    )
                }
                
                val targetDeviceId = target.deviceId ?: return SpeechRoutePreparation.Unavailable(
                    target = target,
                    reason = SpeechRouteFailureReason.WRONG_DEVICE
                )
                
                val activeDevice = wearableRouteAdapter.deviceId
                if (activeDevice == null || activeDevice != targetDeviceId) {
                    Log.i("HaloAudio", "AUDIO_ROUTE_FAILED target=wearable reason=WRONG_DEVICE")
                    return SpeechRoutePreparation.Unavailable(
                        target = target,
                        reason = SpeechRouteFailureReason.WRONG_DEVICE
                    )
                }

                // Restore persisted Classic-audio identity independently of Devices UI.
                audioPairingCoordinator?.ensureRestored(targetDeviceId)

                // 1. Let the device adapter prepare its transport.
                val btResult = wearableRouteAdapter.prepareAudioTransport()
                if (btResult.isFailure) {
                    Log.w("HaloAudio", "WEARABLE_AUDIO_PREPARE device=${targetDeviceId} result=failed", btResult.exceptionOrNull())
                    return SpeechRoutePreparation.Unavailable(target, SpeechRouteFailureReason.DEVICE_DISCONNECTED)
                }

                when (audioPairingCoordinator?.state?.value) {
                    HeyCyanAudioPairingState.NotAssociated,
                    HeyCyanAudioPairingState.Discovering,
                    HeyCyanAudioPairingState.AwaitingUserConfirmation,
                    HeyCyanAudioPairingState.Unknown -> return SpeechRoutePreparation.Pending(
                        target = target,
                        reason = SpeechRoutePendingReason.PAIRING_REQUIRED
                    )
                    is HeyCyanAudioPairingState.Bonding -> return SpeechRoutePreparation.Pending(
                        target = target,
                        reason = SpeechRoutePendingReason.PAIRING_REQUIRED
                    )
                    is HeyCyanAudioPairingState.Failed -> return SpeechRoutePreparation.Unavailable(
                        target = target,
                        reason = SpeechRouteFailureReason.AUDIO_TRANSPORT_NOT_CONNECTED
                    )
                    is HeyCyanAudioPairingState.Associated,
                    is HeyCyanAudioPairingState.AudioConnected,
                    null -> Unit
                }
                Log.i("HaloAudio", "HEYCYAN_CLASSIC_ENABLE device=${targetDeviceId} result=success")

                // 2. Send RouteAudioToGlasses command
                val routeResult = wearableRouteAdapter.requestOutputRoute()
                if (routeResult.isFailure) {
                    Log.i("HaloAudio", "HEYCYAN_VENDOR_ROUTE device=${targetDeviceId} route=GLASSES result=failed")
                    return SpeechRoutePreparation.Unavailable(
                        target = target,
                        reason = SpeechRouteFailureReason.COMMAND_FAILED
                    )
                }
                Log.i("HaloAudio", "HEYCYAN_VENDOR_ROUTE device=${targetDeviceId} route=GLASSES result=success")
                lastGlassesDeviceId = targetDeviceId

                // 3. Await/verify Android Bluetooth audio endpoint
                val endpoint = routeMonitor.awaitEndpoint(
                    predicate = { ep -> ep.type != BluetoothAudioEndpointType.SCO &&
                        audioPairingCoordinator?.matchesEndpoint(targetDeviceId, ep) == true },
                    timeoutMs = 5_000L
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
                        androidDeviceType = when (endpoint.type) {
                            com.halo.host.android.audio.BluetoothAudioEndpointType.A2DP -> AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                            com.halo.host.android.audio.BluetoothAudioEndpointType.SCO -> AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                            com.halo.host.android.audio.BluetoothAudioEndpointType.BLE_HEADSET -> AudioDeviceInfo.TYPE_BLE_HEADSET
                            com.halo.host.android.audio.BluetoothAudioEndpointType.BLE_SPEAKER -> AudioDeviceInfo.TYPE_BLE_SPEAKER
                        },
                        androidDeviceName = endpoint.name,
                        androidDeviceAddress = endpoint.address,
                        verifiedAtMs = System.currentTimeMillis()
                    )
                )
            }
        }
    }
}
