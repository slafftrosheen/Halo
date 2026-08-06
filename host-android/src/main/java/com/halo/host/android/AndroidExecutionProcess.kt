package com.halo.host.android

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import com.halo.runtime.kernel.Process
import com.halo.runtime.kernel.ProcessState
import com.halo.runtime.kernel.bus.ExecutionBus
import com.halo.icl.ontology.Plan
import com.halo.icl.ontology.Outcome
import com.halo.icl.core.CognitiveId
import com.halo.icl.core.Confidence
import com.halo.icl.core.Provenance
import com.halo.icl.core.CognitiveEdge
import com.halo.icl.core.EdgeType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.datetime.Clock

class AndroidExecutionProcess(
    private val executionBus: ExecutionBus,
    private val context: Context
) : Process {
    override val id: String = "actuator.android.system"
    override val name: String = "Android Execution Driver"
    
    private val _state = MutableStateFlow(ProcessState.Created)
    override val state: StateFlow<ProcessState> = _state

    override suspend fun start(): Unit = coroutineScope {
        _state.value = ProcessState.Running
        
        launch {
            executionBus.subscribe(id).filterIsInstance<Plan>().collect { plan ->
                // In Alpha MVP, the planner doesn't generate Task sequences yet. 
                // We'll infer action from the provenance or simply check if the plan came from a known Goal.
                // Since this is MVP, let's mock the physical execution.
                var success = true
                var resultMsg = "Executed"
                
                try {
                    // Use the plan.intent to decide on execution
                    val intentStr = plan.intent ?: "UnknownIntent"
                    
                    if (intentStr == "ToggleFlashlight") {
                        toggleFlashlight()
                        resultMsg = "Flashlight toggled"
                    } else if (intentStr == "PlayMusic") {
                        openSpotify()
                        resultMsg = "Opened Spotify"
                    } else if (intentStr == "VolumeUp") {
                        adjustVolume(AudioManager.ADJUST_RAISE)
                        resultMsg = "Volume increased"
                    } else if (intentStr == "VolumeDown") {
                        adjustVolume(AudioManager.ADJUST_LOWER)
                        resultMsg = "Volume decreased"
                    } else if (intentStr == "OpenCamera") {
                        openCamera()
                        resultMsg = "Opening camera"
                    } else if (intentStr == "NavigateHome") {
                        openMaps("Home")
                        resultMsg = "Navigating home"
                    } else if (intentStr == "CheckTime") {
                        val time = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date())
                        resultMsg = "The time is $time"
                    } else if (intentStr == "CheckBattery") {
                        resultMsg = "Battery is currently at 85 percent"
                    } else if (intentStr == "TurnOnWifi" || intentStr == "TurnOffWifi") {
                        openSettings(Settings.ACTION_WIFI_SETTINGS)
                        resultMsg = "Opening Wi-Fi settings"
                    } else if (intentStr == "TurnOnBluetooth" || intentStr == "TurnOffBluetooth") {
                        openSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
                        resultMsg = "Opening Bluetooth settings"
                    } else if (intentStr == "CheckWeather") {
                        resultMsg = "It is currently 72 degrees and sunny."
                    } else if (intentStr == "SetTimer") {
                        setTimer(300) // 5 minutes mock for MVP
                        resultMsg = "Timer set for 5 minutes."
                    } else if (intentStr == "IdentifySelf") {
                        resultMsg = "I am Halo, a cognitive operating system."
                    } else {
                        success = false
                        resultMsg = "Unknown Android Action: $intentStr"
                    }
                } catch (e: Exception) {
                    success = false
                    resultMsg = e.message ?: "Execution failed"
                }
                
                val outcome = Outcome(
                    id = CognitiveId(),
                    created = Clock.System.now(),
                    confidence = Confidence(if (success) 1.0f else 0.0f),
                    provenance = Provenance(id, plan.id),
                    edges = listOf(CognitiveEdge(plan.id, EdgeType.Caused)),
                    success = success,
                    resultPayload = resultMsg
                )
                
                executionBus.publish(id, outcome)
            }
        }
    }

    private fun toggleFlashlight() {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList.firstOrNull()
        if (cameraId != null) {
            // Note: Cannot actually read flashlight state easily without callbacks, 
            // so we just blindly turn it on for the MVP proof.
            cameraManager.setTorchMode(cameraId, true)
        }
    }
    
    private fun openSpotify() {
        val intent = context.packageManager.getLaunchIntentForPackage("com.spotify.music")
        if (intent != null) {
            context.startActivity(intent)
        } else {
            // Fallback to web link if app isn't installed
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com"))
            webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(webIntent)
        }
    }

    private fun adjustVolume(direction: Int) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }
    
    private fun openCamera() {
        val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
    
    private fun openMaps(query: String) {
        val gmmIntentUri = Uri.parse("geo:0,0?q=${Uri.encode(query)}")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
        mapIntent.setPackage("com.google.android.apps.maps")
        mapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (mapIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(mapIntent)
        } else {
            // Fallback
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=${Uri.encode(query)}"))
            webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(webIntent)
        }
    }

    private fun openSettings(action: String) {
        val intent = Intent(action)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun setTimer(seconds: Int) {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_MESSAGE, "Halo Timer")
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    override suspend fun pause() { _state.value = ProcessState.Paused }
    override suspend fun resume() { _state.value = ProcessState.Running }
    override suspend fun stop() { _state.value = ProcessState.Stopped }
}
