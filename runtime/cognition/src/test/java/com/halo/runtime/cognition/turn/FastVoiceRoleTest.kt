package com.halo.runtime.cognition.turn

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.workload.*
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.ResponseDelivery
import org.junit.Assert.*
import org.junit.Test

class FastVoiceRoleTest {
    private fun turn(text: String = "what is RAM?", voice: Boolean = true, metadata: Map<String, String> = emptyMap(),
        mode: CognitiveTurnMode = CognitiveTurnMode.FRESH) = CognitiveTurnRequest(
            InteractionId("voice"), "request", AiRequest(text = text), AiContextPolicy.CONSERVATIVE,
            if (voice) ResponseDelivery.SPEECH_AND_VISUAL else ResponseDelivery.VISUAL_ONLY, null, mapOf("inputModality" to if (voice) "VOICE" else "TEXT") + metadata, mode = mode)
    private fun role(turn: CognitiveTurnRequest): AiModelRole? {
        val plan = DefaultAiWorkloadPlanner().plan(WorkloadPlanningRequest(turn.request, turn.interactionId, metadata = turn.metadata))
        return preferFastVoiceRole(turn, plan).inference.preferredRole
    }
    @Test fun simpleVoicePrefersFastButTextKeepsChat() {
        assertEquals(AiModelRole.FAST, role(turn()))
        assertEquals(AiModelRole.CHAT, role(turn(voice = false)))
    }
    @Test fun typedHomeWithSpeechDeliveryKeepsChat() {
        val home = turn().copy(metadata = mapOf("input" to "keyboard"))
        assertEquals(InteractionInputModality.TEXT, home.inputModality())
        assertEquals(AiModelRole.CHAT, role(home))
        assertEquals(1024, com.halo.runtime.cognition.context.localInferencePerformance(true, 4096, home.inputModality() == InteractionInputModality.VOICE)!!.history.maxConversationTokens)
    }
    @Test fun strongerOwnersRemainIntact() {
        assertEquals(AiModelRole.AGENTIC, role(turn("set a timer for five minutes", metadata = mapOf("requiresTools" to "true"))))
        assertEquals(AiModelRole.VISION, role(turn("what am I looking at?")))
        assertEquals(AiModelRole.REASONING, role(turn(metadata = mapOf("preferredRole" to "REASONING"))))
        assertEquals(AiModelRole.CHAT, role(turn(mode = CognitiveTurnMode.CONTINUE_AFTER_TOOL)))
        assertEquals(AiModelRole.CHAT, role(turn(mode = CognitiveTurnMode.RECOVER_PROVIDER)))
        assertNotEquals(AiModelRole.FAST, role(turn(metadata = mapOf("workspaceObjectId" to "document"))))
        assertNotEquals(AiModelRole.FAST, role(turn(metadata = mapOf("hasScreenContext" to "true"))))
    }
}
