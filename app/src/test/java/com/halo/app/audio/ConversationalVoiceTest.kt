package com.halo.app.audio

import com.halo.engines.speech.ListeningStarted
import com.halo.sdk.runtime.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ConversationalVoiceTest {
    @Test fun acceptedCaptureIsPublishedBeforeOutputAndGenerationAreStopped() = runBlocking {
        val effects = mutableListOf<String>()
        val owner = VoiceInterruptionCoordinator({ VoiceExperienceSettings() },
            { effects += "stop" }, { effects += "cancel" },
            onAccepted = { effects += "accepted:${it.captureId}:${it.outputTransactionId}:${it.activation}" })
        owner.onEvent(TtsStarted(transactionId = "reply"))
        owner.onEvent(ListeningStarted(captureId = "next", activation = SpeechActivationKind.VOICE_ACTIVITY))
        owner.onEvent(SpeechActivityObserved("next", null, true, 100, true))
        assertTrue(effects.isEmpty())
        owner.onEvent(SpeechActivityObserved("stale", null, true, 900, true))
        assertTrue(effects.isEmpty())
        owner.onEvent(SpeechActivityObserved("next", null, true, 900, true))
        assertEquals(listOf("accepted:next:reply:VOICE_ACTIVITY", "stop", "cancel"), effects)
    }

    private fun prepared() = StreamingSpeechPrepared("response", "interaction", SpeechOutputTarget.Phone,
        SpeechOutputFallback.ALLOW_PHONE, "capture")
    @Test fun firstSentenceIsReadyBeforeFinalAndFinalNeverReplaysIt() {
        val stream = StreamingVoiceResponse(prepared())
        stream.accept("This is a useful first sentence. ")
        assertEquals("This is a useful first sentence.", stream.chunks.tryReceive().getOrNull())
        assertTrue(stream.started())
        assertEquals(StreamingVoiceResponse.Finish.SUFFIX, stream.finish("This is a useful first sentence. More useful detail."))
        assertEquals("More useful detail.", stream.remainder)
    }
    @Test fun shortAnswerUsesWholeResponseWithoutAChunk() {
        val stream = StreamingVoiceResponse(prepared()); stream.accept("42")
        assertTrue(stream.chunks.tryReceive().isFailure)
        assertEquals(StreamingVoiceResponse.Finish.FULL_RESPONSE, stream.finish("42"))
        assertFalse(stream.started())
    }
    @Test fun finalMismatchNeverReplaysAnAudibleAnswer() {
        val stream = StreamingVoiceResponse(prepared()); stream.accept("An initial complete sentence."); stream.started()
        assertEquals(StreamingVoiceResponse.Finish.MISMATCH, stream.finish("A different final answer."))
        assertTrue(stream.chunks.tryReceive().isFailure)
    }
    @Test fun longIncompleteRemainderCannotForceSentenceModeToSpeakAFragment() {
        val stream = StreamingVoiceResponse(prepared())
        stream.accept("A complete first sentence. " + "unfinished words ".repeat(30))
        assertEquals("A complete first sentence.", stream.chunks.tryReceive().getOrNull())
        assertTrue(stream.chunks.tryReceive().isFailure)
    }
    @Test fun cancellationDropsThreeQueuedChunksAndLateTokens() {
        val stream = StreamingVoiceResponse(prepared())
        stream.accept("First complete sentence. Second complete sentence. Third complete sentence.")
        assertEquals(3, stream.chunkCount); stream.abandon(); stream.accept("Late complete sentence.")
        assertTrue(stream.chunks.tryReceive().isFailure)
        assertEquals(StreamingVoiceResponse.Finish.STOP, stream.finish("Late final."))
    }
    @Test fun markupAndIncompleteSentencesDoNotReachSynthesis() {
        val stream = StreamingVoiceResponse(prepared()); stream.accept("A clause, with more")
        assertTrue(stream.chunks.tryReceive().isFailure)
        stream.accept("<think>secret reasoning. </think>")
        assertTrue(stream.chunks.tryReceive().isFailure)
        assertEquals(StreamingVoiceResponse.Finish.FULL_RESPONSE, stream.finish("A safe final answer."))
    }
    @Test fun shortBackchannelDoesNotInterruptButSustainedSpeechDoes() = runBlocking {
        var stops = 0; var cancels = 0
        val owner = VoiceInterruptionCoordinator({ VoiceExperienceSettings() }, { stops++ }, { cancels++ })
        owner.onEvent(TtsStarted(transactionId = "old"))
        owner.onEvent(ListeningStarted(captureId = "new", activation = SpeechActivationKind.VOICE_ACTIVITY))
        owner.onEvent(SpeechActivityObserved("new", null, true, 200, true))
        assertEquals(0, stops)
        owner.onEvent(SpeechActivityObserved("new", null, true, 500, true))
        assertEquals(1, stops); assertEquals(1, cancels)
        owner.onEvent(SpeechActivityObserved("new", null, true, 700, true)); assertEquals(1, stops)
    }
    @Test fun disabledOrdinaryInterruptionStillAcceptsPhysicalActivation() = runBlocking {
        var stops = 0
        val owner = VoiceInterruptionCoordinator({ VoiceExperienceSettings(spokenInterruptionEnabled = false) }, { stops++ }, {})
        owner.onEvent(TtsStarted(transactionId = "old"))
        owner.onEvent(ListeningStarted(captureId = "new"))
        owner.onEvent(SpeechActivityObserved("new", null, true, 700, true)); assertEquals(0, stops)
        owner.onEvent(ListeningStarted(captureId = "physical", activation = SpeechActivationKind.EXPLICIT_USER))
        assertEquals(1, stops)
    }
    @Test fun requestedSpeechAndStaleCaptureNeverEstablishInterruptionAuthority() = runBlocking {
        var stops = 0
        val owner = VoiceInterruptionCoordinator({ VoiceExperienceSettings() }, { stops++ }, {})
        owner.onEvent(TtsRequested(text = "hello", transactionId = "old"))
        owner.onEvent(ListeningStarted(captureId = "new", activation = SpeechActivationKind.EXPLICIT_USER))
        assertEquals(0, stops)
        owner.onEvent(TtsStarted(transactionId = "old"))
        owner.onEvent(SpeechActivityObserved("stale", null, true, 900, true)); assertEquals(0, stops)
        owner.onEvent(TtsCompleted(transactionId = "different"))
        owner.onEvent(SpeechActivityObserved("new", null, true, 900, true)); assertEquals(1, stops)
    }
    @Test fun realClassifierCanRejectBackchannelAndAbsentClassifierUsesDuration() = runBlocking {
        var stops = 0
        val classifier = object : BargeInSemanticClassifier {
            override val id = "fake"; override val available = true
            override suspend fun classify(sustainedSpeechMs: Long?, localVoicedSpeech: Boolean, outputActive: Boolean) =
                BargeInSemanticResult(BargeInSemanticClass.BACKCHANNEL, 0.9f, id)
        }
        val owner = VoiceInterruptionCoordinator({ VoiceExperienceSettings(semanticInterruptionEnabled = true) }, { stops++ }, {}, classifier)
        owner.onEvent(TtsStarted(transactionId = "old")); owner.onEvent(ListeningStarted(captureId = "new"))
        owner.onEvent(SpeechActivityObserved("new", null, true, 900, true)); assertEquals(0, stops)
    }
    @Test fun terminalStreamCannotBeCancelledOrFinalizedAgain() {
        val stream = StreamingVoiceResponse(prepared())
        stream.accept("A complete first sentence."); stream.started()
        assertEquals(StreamingVoiceResponse.Finish.SUFFIX, stream.finish("A complete first sentence."))
        assertTrue(stream.complete()); assertFalse(stream.complete()); assertFalse(stream.abandon())
        assertFalse(stream.started())
        assertEquals(StreamingVoiceResponse.Finish.STOP, stream.finish("A complete first sentence."))
    }
    @Test fun fallbackAndAudibleSuffixEachHaveOnlyOneFinalAuthority() {
        val buffered = StreamingVoiceResponse(prepared())
        buffered.accept("42")
        assertEquals(StreamingVoiceResponse.Finish.FULL_RESPONSE, buffered.finish("42"))
        assertEquals(StreamingVoiceResponse.Finish.STOP, buffered.finish("42"))
        val audible = StreamingVoiceResponse(prepared())
        audible.accept("First complete sentence."); audible.started()
        assertEquals(StreamingVoiceResponse.Finish.SUFFIX, audible.finish("First complete sentence. Final suffix."))
        assertEquals("Final suffix.", audible.remainder)
        assertEquals(StreamingVoiceResponse.Finish.STOP, audible.finish("First complete sentence. Final suffix."))
    }

}
