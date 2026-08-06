package com.halo.host.android.capability

import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import com.halo.runtime.cognition.Capability
import com.halo.runtime.cognition.Task
import com.halo.runtime.cognition.DeviceTask
import com.halo.runtime.cognition.Outcome

class AndroidDeviceCapability(private val context: Context) : Capability {
    override val id: String = "capability.device.android"

    override suspend fun supports(task: Task): Boolean {
        return task is DeviceTask
    }

    override suspend fun execute(task: Task): Outcome {
        require(task is DeviceTask)
        return try {
            when (task.action.lowercase()) {
                "flashlight" -> {
                    toggleFlashlight()
                    Outcome.Success("Flashlight toggled")
                }
                "volumeup" -> {
                    adjustVolume(AudioManager.ADJUST_RAISE)
                    Outcome.Success("Volume increased")
                }
                "volumedown" -> {
                    adjustVolume(AudioManager.ADJUST_LOWER)
                    Outcome.Success("Volume decreased")
                }
                else -> Outcome.Failure("Unknown device action: ${task.action}")
            }
        } catch (e: Exception) {
            Outcome.Failure(e.message ?: "Failed to execute device action")
        }
    }

    private fun toggleFlashlight() {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList.firstOrNull()
        if (cameraId != null) {
            cameraManager.setTorchMode(cameraId, true)
        }
    }

    private fun adjustVolume(direction: Int) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }
}
