package com.halo.app.audio

import android.media.*
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.halo.host.android.audio.AndroidCommunicationAudioRouter
import com.halo.host.android.audio.AudioDeviceSelector
import com.halo.sdk.provider.SpeechRequest
import com.halo.sdk.runtime.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** One opt-in physical route suite. Does not qualify models or assert unheard acoustic outcomes. */
@RunWith(AndroidJUnit4::class)
class WearableAccessPhysicalTest {
    @Test fun consolidatedAvailablePaths() = runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("wearablePhysical") == "1")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(AudioManager::class.java)
        val registry = AndroidAudioAccessoryRegistry(context)
        val provider = com.halo.providers.android.tts.AndroidTTSProvider(context)
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        val player = RoutedSpeechPlaybackProvider(context, provider, attributes)
        val adapter = object : WearableAudioRouteAdapter {
            override val deviceId: String? = null
            override suspend fun prepareAudioTransport() = Result.failure<Unit>(IllegalStateException("No managed device"))
            override suspend fun requestOutputRoute() = Result.failure<Unit>(IllegalStateException("No managed device"))
            override suspend fun releaseOutputRoute() = Result.success(Unit)
        }
        val routes = SpeechOutputRouteController(context, adapter, accessories = registry)
        val checks = JSONArray()
        fun record(name: String, status: String, kind: String, input: Int? = null, output: Int? = null, type: Int? = null) {
            checks.put(JSONObject().put("case", name).put("status", status).put("kind", kind)
                .put("inputIdDigest", input?.let(::digest)).put("outputIdDigest", output?.let(::digest))
                .put("outputType", type).put("phoneLeakObserved", JSONObject.NULL))
        }
        val inventory = JSONObject().put("model", Build.MODEL).put("api", Build.VERSION.SDK_INT)
            .put("savedHaloDevices", JSONArray(com.halo.app.di.ManualDependencyGraph.deviceSessionManager.registry.getSavedDevices().map { record -> JSONObject().put("kind", record.descriptor.kind.name).put("keyDigest", MessageDigest.getInstance("SHA-256").digest(record.identity.deviceId.toByteArray()).take(6).joinToString("") { "%02x".format(it) }) }))
            .put("accessories", JSONArray(registry.accessories.value.map { accessory -> JSONObject()
                .put("kind", accessory.kind.name).put("key", accessory.id).put("input", accessory.canInput)
                .put("output", accessory.canOutput).put("communication", accessory.communicationCapable) }))
        suspend fun play(name: String, target: SpeechOutputTarget, sentence: String) {
            try {
                val prepared = routes.prepareRoute(SpeechRouteRequest(name, target, SpeechOutputFallback.DISALLOW_PHONE, null))
                if (prepared !is SpeechRoutePreparation.Ready) { record(name, "FAIL", "UNAVAILABLE"); return }
                val result = player.synthesizeRouted(SpeechRequest(sentence), prepared.evidence, provider = provider)
                record(name, if (result is RoutedPlaybackResult.Completed) "PASS" else "FAIL", prepared.evidence.mode.name,
                    output = (result as? RoutedPlaybackResult.Completed)?.endpointId, type = (result as? RoutedPlaybackResult.Completed)?.evidence?.androidDeviceType ?: prepared.evidence.androidDeviceType)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { record(name, "FAIL", "ROUTE_OR_SYNTHESIS") }
        }
        suspend fun microphone(name: String, id: Int, kind: AudioDeviceKind?, communicationId: Int? = null, accessoryId: String? = null) {
            var recorder: AudioRecord? = null
            var lease: com.halo.host.android.audio.CommunicationAudioLease? = null
            try {
                if (communicationId != null) lease = AndroidCommunicationAudioRouter(manager)
                    .acquire(AudioDeviceSelector(deviceId = communicationId), true, false).getOrThrow()
                registry.refresh()
                val selectedId = if (accessoryId != null) registry.find(accessoryId)?.inputDeviceId ?: error("Input disappeared") else id
                val input = manager.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.id == selectedId }
                    ?: error("Input unavailable")
                recorder = AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(16000).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build()).setBufferSizeInBytes(16000).build()
                check(recorder.setPreferredDevice(input))
                recorder.startRecording()
                delay(300)
                val scratch = ShortArray(1600)
                check(recorder.read(scratch, 0, scratch.size) > 0)
                scratch.fill(0)
                check(recorder.routedDevice?.id == selectedId)
                record(name, "PASS", kind?.name ?: "PHONE", input = selectedId)
                if (communicationId != null) {
                    val accessory = registry.findInput(selectedId) ?: error("Accessory disappeared")
                    play("C_HEADSET_OUTPUT", SpeechOutputTarget.Device(accessory.id, null, null, accessory.kind), "Halo headset microphone route test.")
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { record(name, "FAIL", kind?.name ?: "PHONE", input = id) }
            finally { recorder?.let { runCatching { it.stop() }; it.release() }; lease?.release() }
        }
        try {
            val phoneMic = manager.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
            if (phoneMic != null) microphone("A_PHONE_MIC", phoneMic.id, null) else record("A_PHONE_MIC", "NOT_AVAILABLE", "PHONE")
            play("A_EXPLICIT_PHONE", SpeechOutputTarget.Phone, "Halo phone speaker baseline.")
            play("B_SYSTEM_DEFAULT", SpeechOutputTarget.HostDefault, "Halo Android selected headphone route test.")
            registry.accessories.value.filter { it.canOutput }.forEach { accessory ->
                play("EXACT_${accessory.kind}", SpeechOutputTarget.Device(accessory.id, null, null, accessory.kind), "Halo exact accessory output test.")
            }
            registry.accessories.value.filter { it.canInput }.forEach { accessory ->
                microphone("C_ACCESSORY_MIC", accessory.inputDeviceId!!, accessory.kind, accessory.communicationDeviceId, accessory.id)
            }
        } finally {
            player.stopCurrent(); provider.close(); registry.close()
            val report = JSONObject().put("suite", "WEARABLE-ACCESS-PHYSICAL-001").put("inventory", inventory)
                .put("platformChecks", checks).put("acousticAcceptance", "REQUIRES_HUMAN_OBSERVATION")
                .put("normalQuestionAndContinuityAcceptance", "REQUIRES_INTERACTIVE_USER_CHECK")
            File(context.filesDir, "wearable-access-physical-001.json").writeText(report.toString(2))
        }
    }
    private fun digest(id: Int) = MessageDigest.getInstance("SHA-256").digest(id.toString().toByteArray())
        .take(6).joinToString("") { "%02x".format(it) }
}
