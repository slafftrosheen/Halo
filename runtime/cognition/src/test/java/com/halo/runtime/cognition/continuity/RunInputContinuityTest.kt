package com.halo.runtime.cognition.continuity

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.*
import com.halo.runtime.cognition.turn.InteractionInputModality
import com.halo.sdk.host.ObservationSource
import com.halo.sdk.runtime.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

/** Code-only pass: input/delivery separation regressions are written but not executed. */
class RunInputContinuityTest {
    private val at = Instant.ofEpochMilli(1_800_000_000_000L)
    private val id = AgentRunId("owned-run")
    private fun run(modality: InteractionInputModality? = null, input: SpeechInputRoute? = null,
        origin: ObservationOrigin? = null) = AgentRun(id, InteractionId("owned-interaction"), "Synthetic task",
        AgentRunState.SUSPENDED, origin, at, at,
        steps = listOf(AgentStep.RunPrepared(AgentStepId("prepared"), id, at,
            AgentRunKind.INTERACTIVE, RunRecoveryPolicy.USER_RESUME, inputModality = modality, inputRoute = input)),
        speechOutputTarget = SpeechOutputTarget.Device("speaker", 12, "Synthetic output", AudioDeviceKind.WEARABLE))

    @Test fun keyboardTaskDoesNotBecomeVoiceBecausePlaybackTargetsADevice() {
        val run = run(InteractionInputModality.TEXT)
        assertEquals(InteractionInputModality.TEXT, RunContinuityResolver.inputModality(run))
        assertNull(RunContinuityResolver.inputRoute(run))
        assertEquals(run.speechOutputTarget, RunContinuityResolver.delivery(run).target)
    }

    @Test fun originalPhoneMicrophoneIsNotReplacedByOutputAccessory() {
        val run = run(InteractionInputModality.VOICE, SpeechInputRoute.Phone)
        assertEquals(SpeechInputRoute.Phone, RunContinuityResolver.inputRoute(run))
    }

    @Test fun legacyUnknownInputStaysUnknownInsteadOfInferringFromOutput() {
        val run = run()
        assertEquals(InteractionInputModality.UNKNOWN, RunContinuityResolver.inputModality(run))
        assertNull(RunContinuityResolver.inputRoute(run))
    }

    @Test fun historicalPhoneCaptureCanRecoverItsKnownInputWithoutUsingSpeakerIdentity() {
        val run = run(origin = ObservationOrigin(ObservationSource.AndroidPhoneMic, "owned-phone"))
        assertEquals(InteractionInputModality.VOICE, RunContinuityResolver.inputModality(run))
        assertEquals(SpeechInputRoute.Phone, RunContinuityResolver.inputRoute(run))
    }
}
