package com.halo.host.android.capability

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.text.format.DateFormat
import com.halo.runtime.cognition.Capability
import com.halo.runtime.cognition.DeviceTask
import com.halo.runtime.cognition.Outcome
import com.halo.runtime.cognition.Task
import java.util.Date

class AndroidDeviceCapability(private val context: Context) : Capability {
    override val id: String = "capability.device.android"

    private var isTorchOn = false

    override suspend fun supports(task: Task): Boolean {
        return task is DeviceTask
    }

    override suspend fun execute(task: Task): Outcome {
        require(task is DeviceTask)
        val action = task.action.lowercase().trim()
        return try {
            when {
                action == "time" -> {
                    val formatted = DateFormat.getTimeFormat(context).format(Date())
                    Outcome.Success("It's $formatted.")
                }

                action.contains("flashlight_on") || action.contains("turn_on_flashlight") -> {
                    if (setFlashlightState(true)) {
                        Outcome.Success("Flashlight turned on")
                    } else {
                        Outcome.PermanentFailure("No flashlight is available on this device")
                    }
                }

                action.contains("flashlight_off") || action.contains("turn_off_flashlight") -> {
                    if (setFlashlightState(false)) {
                        Outcome.Success("Flashlight turned off")
                    } else {
                        Outcome.PermanentFailure("No flashlight is available on this device")
                    }
                }

                action.contains("flashlight") -> {
                    val requestedState = !isTorchOn
                    if (setFlashlightState(requestedState)) {
                        Outcome.Success(if (requestedState) "Flashlight turned on" else "Flashlight turned off")
                    } else {
                        Outcome.PermanentFailure("No flashlight is available on this device")
                    }
                }

                action == "volumeup" || action.contains("volume_up") -> {
                    adjustVolume(AudioManager.ADJUST_RAISE)
                    Outcome.Success("Volume increased")
                }

                action == "volumedown" || action.contains("volume_down") -> {
                    adjustVolume(AudioManager.ADJUST_LOWER)
                    Outcome.Success("Volume decreased")
                }

                else -> Outcome.PermanentFailure("Unknown device action: ${task.action}")
            }
        } catch (e: CameraAccessException) {
            Outcome.PermanentFailure("Camera access denied or camera in use")
        } catch (e: SecurityException) {
            Outcome.PermanentFailure("Permission denied to modify device state")
        } catch (e: Exception) {
            Outcome.RetryableFailure(e.message ?: "Failed to execute device action")
        }
    }

    private fun setFlashlightState(enabled: Boolean): Boolean {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return false

        cameraManager.setTorchMode(cameraId, enabled)
        isTorchOn = enabled
        return true
    }

    private fun adjustVolume(direction: Int) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
    }
}
