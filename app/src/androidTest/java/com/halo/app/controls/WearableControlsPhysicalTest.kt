package com.halo.app.controls

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.sdk.host.AccessoryAction
import com.halo.sdk.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** One opt-in physical suite. No key injection, AI request, microphone probe, or fabricated hearing result. */
@RunWith(AndroidJUnit4::class)
class WearableControlsPhysicalTest {
    @Test fun consolidatedControlsSuite() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("controlsPhysical") == "1")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val report = JSONObject().put("suite", "WEARABLE-CONTROLS-PHYSICAL-003")
            .put("heycyan", "NOT_AVAILABLE").put("wearOs", "NOT_AVAILABLE")
            .put("audibleHeadphonesOnly", "UNVERIFIED").put("idleMusicAndVolume", "UNVERIFIED")
            .put("nativeAssistantGesture", "UNVERIFIED")
        val controls = Graph.scopedSpeechMediaControls
        val mappingBefore = Graph.storage.getString(AccessoryControlMappingRepository.KEY)
        val enabledBefore = controls.enabled.value
        val transaction = UUID.randomUUID().toString()
        try {
            val accessories = Graph.audioAccessoryRegistry.accessories.value
            report.put("connectedHeadsets", accessories.count { it.kind == AudioDeviceKind.HEADSET })
            report.put("headsetInputs", accessories.count { it.kind == AudioDeviceKind.HEADSET && it.canInput })
            report.put("headsetOutputs", accessories.count { it.kind == AudioDeviceKind.HEADSET && it.canOutput })
            controls.configure(AccessoryAction.SystemDefault)
            delay(500)
            report.put("idleControlEndpoint", Graph.accessoryControls.endpoints.value.any { it.deviceClass == "SCOPED_MEDIA" && it.connected })
            controls.configure(AccessoryAction.StopSpeaking)
            Graph.haloSpeechOutputCoordinator.start()
            val result = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeoutOrNull(90_000) { Graph.accessoryControls.feedback.first {
                    it.control == "PLAY_PAUSE" && it.action == "StopSpeaking" && it.outcome.startsWith("EXECUTED")
                } }
            }
            Graph.eventBus.publish(TtsRequested(text = ("Halo physical controls test. Press your headphone play pause button once to stop this speech. " ).repeat(12),
                transactionId = transaction, target = SpeechOutputTarget.HostDefault, fallback = SpeechOutputFallback.DISALLOW_PHONE))
            val feedback = result.await()
            report.put("platformScopedControlDelivery", if (feedback != null) "PASS" else "UNVERIFIED")
            report.put("physicalButtonObserved", "UNVERIFIED")
            report.put("observedEvidence", feedback?.evidence ?: "NONE")
            report.put("outcome", feedback?.outcome?.substringBefore(" · ") ?: "NONE")
            delay(500)
            report.put("speechStopped", !Graph.haloSpeechOutputCoordinator.playbackActive.value)
            report.put("releasedAfterSpeech", Graph.accessoryControls.endpoints.value.none { it.deviceClass == "SCOPED_MEDIA" && it.connected })
        } finally {
            Graph.haloSpeechOutputCoordinator.stop("Physical controls suite ended")
            controls.configure(AccessoryAction.SystemDefault)
            Graph.storage.putString(AccessoryControlMappingRepository.KEY, mappingBefore)
            controls.enabled.value = enabledBefore
            Graph.storage.putBoolean("accessory_scoped_media_enabled", enabledBefore)
            // This isolated instrumentation process exits; production reloads the original mapping document.
            File(context.filesDir, "wearable-controls-physical-003.json").writeText(report.toString(2), Charsets.UTF_8)
        }
    }
}
