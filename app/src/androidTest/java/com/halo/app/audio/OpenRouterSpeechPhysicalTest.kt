package com.halo.app.audio

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.halo.app.MainActivity
import com.halo.app.ai.PrivacyMode
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.ui.mini.HaloHostVisibility
import com.halo.app.ui.shell.HaloScreenCatalog
import com.halo.providers.openrouter.OpenRouterSpeechProvider
import com.halo.sdk.provider.SpeechRequest
import com.halo.sdk.provider.withRemoteDispatchPolicy
import com.halo.sdk.provider.RemoteDispatchRequest
import com.halo.sdk.provider.RemoteOperation
import com.halo.sdk.provider.RemoteDataCategory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit paid-network opt-in, fixed non-private phrase; no playback, key readout or setting changes. */
@RunWith(AndroidJUnit4::class)
class OpenRouterSpeechPhysicalTest {
    @Test fun selectedOpenRouterVoiceProducesDecodableAudio() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("openRouterLive") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertFalse(context.getSystemService(KeyguardManager::class.java).isDeviceLocked)
        context.startActivity(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(HaloScreenCatalog.EXTRA_SCREEN, "settings/voice/output"))
        withTimeout(60_000) {
            Graph.kidsPreferences.loadedState.first { it }
            while (!HaloHostVisibility.active.value) delay(100)
        }
        assertFalse(Graph.kidsPreferences.enabled.value)
        assertEquals(PrivacyMode.CLOUD, Graph.privacyModes.mode.value)
        assertEquals(OpenRouterSpeechProvider.ID, Graph.speechSynthesisRegistry.selector.ttsSelection.value.id)
        fun receipt(value: String) = instrumentation.sendStatus(0, Bundle().apply { putString("stream", "$value\n") })
        val diagnose = InstrumentationRegistry.getArguments().getString("diagnoseHttp") == "true"
        val actual = Graph.speechSynthesisRegistry.providers.filterIsInstance<OpenRouterSpeechProvider>().single()
        // Separate explicit diagnostic client retains the production dispatch guard and selected profile.
        // Only the fixed test phrase is submitted; no credential, token exchange or personal content.
        val provider = if (!diagnose) actual else OpenRouterSpeechProvider(Graph.privacyModes.storage,
            SpeechMp3Decoder()::decode, OpenRouterSpeechProvider.defaultClient()
                .withRemoteDispatchPolicy(Graph.remoteDispatchPolicy) {
                    RemoteDispatchRequest(OpenRouterSpeechProvider.ID, RemoteOperation.HOSTED_SPEECH, setOf(RemoteDataCategory.TEXT))
                }.newBuilder().addInterceptor { chain ->
                    val response = chain.proceed(chain.request())
                    receipt("OPENROUTER_HTTP status=${response.code}")
                    if (response.code == 400) {
                        val body = response.peekBody(4096).string()
                        val error = runCatching { org.json.JSONObject(body).optJSONObject("error") }.getOrNull()
                        val detail = error?.optString("message").orEmpty().take(1000)
                            .replace(Regex("(?i)(?:sk-|bearer\\s+)[A-Za-z0-9_.-]+"), "[redacted]")
                            .replace(Regex("[A-Za-z0-9_./+-]{48,}"), "[redacted]")
                            .replace(Regex("[A-Za-z0-9_.+-]+@[A-Za-z0-9_.-]+"), "[redacted]")
                            .replace(Regex("[\\r\\n]+"), " ")
                        receipt("OPENROUTER_VALIDATION $detail")
                    }
                    response
                }.build())
        try {
            val pcm = withTimeout(75_000) { provider.synthesizeToPcm(SpeechRequest("This is Halo. The online voice test is ready.", language = "en")) }
            assertTrue(pcm.chunks.sumOf { it.size.toLong() } > 0)
            receipt("OPENROUTER_PCM sampleRate=${pcm.sampleRateHz} channels=${pcm.channelCount} bytes=${pcm.chunks.sumOf { it.size.toLong() }} played=false")
            InstrumentationRegistry.getArguments().getString("openRouterTranscriptionModel")?.let { model ->
                assertEquals(com.halo.sdk.provider.PcmEncoding.SIGNED_16_LE, pcm.encoding)
                val backing = Graph.privacyModes.storage
                val testStorage = object : com.halo.sdk.runtime.Storage by backing {
                    override fun getString(key: String, defaultValue: String?) =
                        if (key == com.halo.providers.openrouter.OpenRouterRecognitionProvider.MODEL) model else backing.getString(key, defaultValue)
                }
                val recognizer = com.halo.providers.openrouter.OpenRouterRecognitionProvider(testStorage, { "en" }, Graph.remoteDispatchPolicy,
                    OpenRouterSpeechProvider.defaultClient().withRemoteDispatchPolicy(Graph.remoteDispatchPolicy) { request ->
                        RemoteDispatchRequest(com.halo.providers.openrouter.OpenRouterRecognitionProvider.ID, RemoteOperation.HOSTED_SPEECH,
                            setOf(if (request.method == "GET") RemoteDataCategory.ACCOUNT_METADATA else RemoteDataCategory.AUDIO))
                    })
                try {
                    assertTrue(recognizer.refreshModels().any { it.id == model })
                    // Generated non-private fixture only. Never read microphone or stored user recordings.
                    val frames = pcm.chunks.map { bytes ->
                        val samples = ShortArray(bytes.size / 2)
                        java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)
                        com.halo.sdk.host.AudioFrame(samples, pcm.sampleRateHz, pcm.channelCount, 0)
                    }
                    val result = withContext(recognizer.captureContext().context) {
                        recognizer.recognize(com.halo.sdk.host.AudioBuffer(frames, "generated-openrouter-fixture"))
                    }
                    val text = result.text.lowercase(java.util.Locale.ROOT)
                    assertTrue("Generated phrase must be recognized", "voice test" in text && "ready" in text)
                    receipt("OPENROUTER_STT fixtureMatched=true model=$model microphone=false settingsChanged=false")
                } finally { recognizer.close() }
            }
            Unit
        } catch (failure: Exception) {
            // Never include exception messages, HTTP bodies, credentials or speech content.
            receipt("OPENROUTER_FAILURE type=${failure.javaClass.simpleName} code=${(failure as? com.halo.sdk.provider.SpeechProviderException)?.code}")
            failure.stackTrace.take(16).forEach { receipt("OPENROUTER_FRAME ${it.className}.${it.methodName}:${it.lineNumber}") }
            throw AssertionError("Selected OpenRouter voice did not produce verified PCM")
        } finally { provider.stopCurrent(); if (diagnose) provider.close() }
    }
}
