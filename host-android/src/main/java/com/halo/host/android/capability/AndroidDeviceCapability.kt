package com.halo.host.android.capability

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.content.Intent
import android.provider.AlarmClock
import android.text.format.DateFormat
import com.halo.cognition.language.TemporalValue
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

                action == "set_alarm" -> setSystemAlarm(task.parameters["target"] as? String ?: "", task.parameters)

                action == "show_alarms" -> {
                    context.startActivity(
                        Intent(AlarmClock.ACTION_SHOW_ALARMS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    Outcome.Success("Opened alarms")
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

    private fun setSystemAlarm(expression: String, parameters: Map<String, Any> = emptyMap()): Outcome {
        val temporalValue = parameters["temporal_value"] as? TemporalValue
        var hour = 0
        var minute = 0
        
        if (temporalValue is TemporalValue.TimeOfDay) {
             hour = temporalValue.time.hour
             minute = temporalValue.time.minute
        } else if (temporalValue is TemporalValue.DayPart) {
             return Outcome.NeedsClarification("What time in the ${temporalValue.part.name.lowercase()}?", "time")
        } else {
            val match = Regex("^(?:today\\s+|tomorrow\\s+|at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?$", RegexOption.IGNORE_CASE)
                .matchEntire(expression.trim())
                ?: return Outcome.PermanentFailure("I couldn't understand the alarm time")
            hour = match.groupValues[1].toIntOrNull()
                ?: return Outcome.PermanentFailure("Invalid alarm hour")
            minute = match.groupValues[2].ifBlank { "0" }.toIntOrNull()
                ?: return Outcome.PermanentFailure("Invalid alarm minute")
            val period = match.groupValues[3].lowercase()
            if (period.isNotBlank()) {
                if (hour !in 1..12) return Outcome.PermanentFailure("Invalid alarm hour")
                if (period == "pm" && hour < 12) hour += 12
                if (period == "am" && hour == 12) hour = 0
            }
        }
        if (hour !in 0..23 || minute !in 0..59) {
            return Outcome.PermanentFailure("Invalid alarm time")
        }
        context.startActivity(
            Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, "Halo alarm")
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        return Outcome.Success("Alarm set")
    }
}
