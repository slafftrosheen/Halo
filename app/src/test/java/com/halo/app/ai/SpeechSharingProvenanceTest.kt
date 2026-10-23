package com.halo.app.ai

import com.halo.app.audio.speechEventProvenance
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import com.halo.sdk.runtime.TtsRequested
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SpeechSharingProvenanceTest {
    @Test fun unknownOrInvalidSpeechEventMetadataNeverGrantsRemoteAuthority() {
        val event = TtsRequested(text = "synthetic", transactionId = "fixture")
        assertNull(speechEventProvenance(event))
        assertNull(speechEventProvenance(event.copy(remoteDataCategories = setOf("UNKNOWN"), remoteDispatchRevision = 7)))
        assertNull(speechEventProvenance(event.copy(remoteDataCategories = setOf("WORKSPACE"), remoteDispatchRevision = -1)))
        assertEquals(RemoteDispatchProvenance(setOf(RemoteDataCategory.WORKSPACE), 7),
            speechEventProvenance(event.copy(remoteDataCategories = setOf("WORKSPACE"), remoteDispatchRevision = 7)))
    }

    @Test fun locallyDerivedWorkspaceAnswerUsesOfflineSpeechWhenCloudWorkspaceIsDisabled() {
        val store = owner()
        store.edit(PrivacyMode.CLOUD) { it.copy(booleans = it.booleans + (KEY_CLOUD_WORKSPACE to false)) }
        val registry = registry(store.storage)
        val request = SpeechRequest("synthetic derived answer", remoteProvenance =
            RemoteDispatchProvenance(setOf(RemoteDataCategory.WORKSPACE), store.revision))
        val (provider, pinned) = registry.pin(request)
        assertEquals("provider.android.tts", provider.metadata.id)
        assertEquals(1.0, pinned.voiceSettings["_haloOfflineOnly"])
    }

    @Test fun currentPermittedAnswerKeepsSelectedHostedSpeechButStaleAnswerStaysLocal() {
        val store = owner(); val registry = registry(store.storage)
        val request = SpeechRequest("synthetic", remoteProvenance =
            RemoteDispatchProvenance(setOf(RemoteDataCategory.TEXT), store.revision))
        assertEquals("fixture.remote.speech", registry.pin(request).first.metadata.id)
        store.set(PrivacyMode.PRIVATE); store.set(PrivacyMode.CLOUD)
        assertEquals("provider.android.tts", registry.pin(request).first.metadata.id)
    }

    private fun owner() = PrivacyModeStore(Memory()).apply {
        acknowledgeSharing(); set(PrivacyMode.CLOUD)
    }
    private fun registry(storage: Storage): SpeechSynthesisRegistry {
        val selector = object : SpeechProviderSelector {
            override val sttSelection = MutableStateFlow(SpeechProviderSelection("AUTOMATIC"))
            override val ttsSelection = MutableStateFlow(SpeechProviderSelection("fixture.remote.speech"))
            override fun selectStt(id: String) { sttSelection.value = SpeechProviderSelection(id) }
            override fun selectTts(id: String) { ttsSelection.value = SpeechProviderSelection(id) }
        }
        return SpeechSynthesisRegistry(listOf(Voice("provider.android.tts", true), Voice("fixture.remote.speech", false)), selector, storage)
    }
    private class Voice(id: String, local: Boolean) : PcmSpeechSynthesisProvider {
        override val metadata = ProviderMetadata(id, local = local)
        override suspend fun synthesizeToPcm(request: SpeechRequest): PcmSpeech? = null
        override suspend fun synthesize(request: SpeechRequest) = SpeechResult(0, false)
        override fun stream(text: String) = emptyFlow<com.halo.sdk.host.AudioFrame>()
        override suspend fun health() = true
    }
    private class Memory : Storage {
        private val values = mutableMapOf<String, Any?>()
        override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { values[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun getInt(key: String, defaultValue: Int) = defaultValue
        override fun putInt(key: String, value: Int) {}
        override fun remove(key: String) { values.remove(key) }
        override fun clear() { values.clear() }
    }
}
