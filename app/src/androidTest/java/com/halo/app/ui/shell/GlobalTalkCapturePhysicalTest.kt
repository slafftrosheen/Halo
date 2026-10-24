package com.halo.app.ui.shell

import android.content.Intent
import android.content.res.Configuration
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.halo.app.MainActivity
import com.halo.app.ai.PrivacyMode
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.language.resolveUiText
import com.halo.sdk.runtime.SpeechState
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt-in real microphone ownership/lifecycle qualification, not a transcription/hearing test.
 * Run only in a consciously established quiet environment with no utterance. VAD can otherwise
 * endpoint ambient sound; any observed recognition is a FAILURE, never proof of pre-STT safety.
 * No setup, permissions, profiles, models, downloads or provider settings are changed.
 */
@RunWith(AndroidJUnit4::class)
class GlobalTalkCapturePhysicalTest {
    @Test fun realGlobalTalkKeepsCaptureAcrossWorkspaceAndCancelsOnBackground() {
        assumeTrue("NOT_RUN: requires explicit haloQuietCapture=true; no utterance in a quiet room",
            InstrumentationRegistry.getArguments().getString("haloQuietCapture") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        fun intent(route: String) = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(HaloScreenCatalog.EXTRA_SCREEN, route)
        fun labels(english: String): Pattern = Pattern.compile(listOf("en", "ru", "lv", "pl").map { tag ->
            val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
            resolveUiText(context.createConfigurationContext(config).resources, english)
        }.distinct().joinToString("|", prefix = "^(?:", postfix = ")$") { Pattern.quote(it) })

        ActivityScenario.launch<MainActivity>(intent("home")).use { scenario ->
            runBlocking { withTimeout(30_000) { Graph.kidsPreferences.loadedState.first { it } } }
            assumeTrue("NOT_RUN: existing adult setup required", !Graph.kidsPreferences.enabled.value && !Graph.setupProgress.shouldAutoLaunch())
            assumeTrue("NOT_RUN: already-Private mode required", Graph.privacyModes.mode.value == PrivacyMode.PRIVATE)
            val ready = runBlocking { withTimeoutOrNull(15_000) { Graph.homeViewModel.uiState.first { it.microphonePermissionGranted && it.speechReady } } }
            assumeTrue("NOT_RUN: existing microphone permission and local speech readiness required", ready != null)
            val initial = Graph.homeViewModel.uiState.value
            assumeTrue("NOT_RUN: existing microphone permission and local speech readiness required", initial.microphonePermissionGranted && initial.speechReady)
            assumeTrue("NOT_RUN: buffered recognition required; live recognition starts at capture admission", !Graph.audioPipeline.usesLiveRecognition)
            assumeTrue("NOT_RUN: another speech transaction owns input", !Graph.audioPerceptionEngine.inputStatus.value.capturing && !Graph.audioPipeline.recognitionInFlight)
            val talk = device.wait(Until.findObject(By.desc(labels("Talk"))), 15_000)
            assumeTrue("NOT_RUN: idle global Talk action required", talk != null)

            val recognitionObserved = AtomicBoolean(false)
            val timedOut = AtomicBoolean(false)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val observer = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                Graph.audioPipeline.state.collect { state ->
                    if (state == SpeechState.Recognizing) {
                        recognitionObserved.set(true)
                        Graph.audioPerceptionEngine.inputStatus.value.turn?.captureId?.let {
                            Graph.audioPerceptionEngine.cancelExternalInput(it)
                        }
                    }
                }
            }
            var attempted = false
            try {
                attempted = true
                scope.launch {
                    delay(8_000)
                    timedOut.set(true)
                    Graph.audioPerceptionEngine.inputStatus.value.turn?.captureId?.let {
                        Graph.audioPerceptionEngine.cancelExternalInput(it)
                    }
                }
                talk!!.click() // Real global orb: never click its subsequent Send voice action.
                val captured = runBlocking { withTimeout(5_000) { Graph.audioPerceptionEngine.inputStatus.first { it.capturing && it.turn != null } } }
                val captureId = requireNotNull(captured.turn).captureId
                assertTrue(captureId.isNotBlank())
                assertFalse(Graph.audioPipeline.recognitionInFlight)
                context.startActivity(intent("notebook"))
                assertTrue("Actual Workspace must render while the same capture owns input",
                    device.wait(Until.hasObject(By.desc(labels("Search your workspace"))), 5_000))
                val afterNavigation = Graph.audioPerceptionEngine.inputStatus.value
                assertTrue("Page navigation stopped the global microphone", afterNavigation.capturing)
                assertEquals(captureId, afterNavigation.turn?.captureId)
                scenario.moveToState(Lifecycle.State.CREATED)
                runBlocking { withTimeout(5_000) { Graph.audioPerceptionEngine.inputStatus.first { !it.capturing } } }
                assertFalse("Unexpected recognition occurred; this is not a no-STT capture result", recognitionObserved.get())
                assertFalse(Graph.audioPipeline.recognitionInFlight)
                assertFalse("Capture exceeded bounded qualification window", timedOut.get())
            } finally {
                try { if (attempted) {
                    // Canonical cancel path discards buffered input; never finish/send ambient audio.
                    instrumentation.runOnMainSync { Graph.homeViewModel.cancelVoiceInput() }
                    runBlocking {
                        withTimeout(5_000) {
                            Graph.audioPerceptionEngine.inputStatus.value.turn?.captureId?.let {
                                Graph.audioPerceptionEngine.cancelExternalInput(it)
                            }
                            Graph.audioPerceptionEngine.inputStatus.first { !it.capturing }
                        }
                    }
                } } finally {
                    observer.cancel()
                    scope.cancel()
                }
            }
        }
    }
}
