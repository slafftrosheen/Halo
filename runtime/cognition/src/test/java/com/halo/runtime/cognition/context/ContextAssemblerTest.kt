package com.halo.runtime.cognition.context

import com.halo.runtime.cognition.blackboard.Confidence
import com.halo.runtime.cognition.blackboard.DefaultBlackboard
import com.halo.runtime.cognition.blackboard.Knowledge
import com.halo.runtime.cognition.blackboard.KnowledgeId
import com.halo.runtime.cognition.blackboard.KnowledgePayload
import com.halo.runtime.cognition.blackboard.KnowledgeSource
import com.halo.runtime.cognition.blackboard.ObservationKnowledge
import com.halo.runtime.cognition.conversation.InMemoryConversationStore
import com.halo.runtime.cognition.tools.EchoToolExecutor
import com.halo.runtime.cognition.tools.ToolRegistry
import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.AiContextPolicy
import com.halo.sdk.runtime.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ContextAssemblerTest {

    private fun createKnowledge(payload: KnowledgePayload): Knowledge {
        return Knowledge(
            id = KnowledgeId(UUID.randomUUID().toString()),
            payload = payload,
            confidence = Confidence(1.0f, 1.0f, 1.0f),
            source = KnowledgeSource.USER,
            createdAt = Clock.System.now()
        )
    }

    @Test
    fun `assemble respects conversation policy`() = runTest {
        val store = InMemoryConversationStore()
        store.append("Hello", "Hi there")
        val blackboard = DefaultBlackboard()
        val assembler = DefaultContextAssembler(store, blackboard)

        val enabledResult = assembler.assemble(
            policy = AiContextPolicy(includeConversation = true),
            capabilities = setOf(AiCapability.TEXT)
        )
        assertEquals(2, enabledResult.context.conversation.size)

        val disabledResult = assembler.assemble(
            policy = AiContextPolicy(includeConversation = false),
            capabilities = setOf(AiCapability.TEXT)
        )
        assertTrue(disabledResult.context.conversation.isEmpty())
    }

    @Test
    fun `assemble bounds recent events to MAX_RECENT_AI_EVENTS`() = runTest {
        val store = InMemoryConversationStore()
        val blackboard = DefaultBlackboard()

        // Publish 25 observation knowledge entries to blackboard
        for (i in 1..25) {
            val obs = Observation(
                modality = Modality.TEXT,
                source = Source.USER,
                payload = SpeechObservationPayload("Message $i", "en-US")
            )
            blackboard.publish(createKnowledge(ObservationKnowledge(obs)))
        }

        val assembler = DefaultContextAssembler(store, blackboard)
        val result = assembler.assemble(
            policy = AiContextPolicy(includeRecentEvents = true),
            capabilities = setOf(AiCapability.TEXT)
        )

        assertTrue(result.context.recentEvents.size <= DefaultContextAssembler.MAX_RECENT_AI_EVENTS)
    }

    @Test
    fun `provider without TOOLS capability receives no tools and records omission`() = runTest {
        val store = InMemoryConversationStore()
        val blackboard = DefaultBlackboard()
        val registry = ToolRegistry(listOf(EchoToolExecutor()))
        val assembler = DefaultContextAssembler(store, blackboard, registry)

        val result = assembler.assemble(
            policy = AiContextPolicy(includeAvailableTools = true),
            capabilities = setOf(AiCapability.TEXT) // No TOOLS capability
        )

        assertTrue(result.context.availableTools.isEmpty())
        assertTrue(result.omissions.any { it.source == "TOOLS" && it.reason.contains("lacks TOOLS capability") })
    }

    @Test
    fun `provider with TOOLS capability receives tool definitions when policy enabled`() = runTest {
        val store = InMemoryConversationStore()
        val blackboard = DefaultBlackboard()
        val registry = ToolRegistry(listOf(EchoToolExecutor()))
        val assembler = DefaultContextAssembler(store, blackboard, registry)

        val result = assembler.assemble(
            policy = AiContextPolicy(includeAvailableTools = true),
            capabilities = setOf(AiCapability.TEXT, AiCapability.TOOLS)
        )

        assertEquals(1, result.context.availableTools.size)
        assertEquals("halo.echo", result.context.availableTools[0].id)
    }

    @Test
    fun `fresh image is returned when provider supports images`() = runTest {
        val store = InMemoryConversationStore()
        val blackboard = DefaultBlackboard()

        val imageBytes = byteArrayOf(1, 2, 3, 4)
        val imageObs = Observation(
            modality = Modality.IMAGE,
            source = Source.CAMERA,
            payload = ImageObservationPayload(imageBytes, "image/jpeg"),
            timestamp = System.currentTimeMillis()
        )
        blackboard.publish(createKnowledge(ObservationKnowledge(imageObs)))

        val assembler = DefaultContextAssembler(store, blackboard)
        val result = assembler.assemble(
            policy = AiContextPolicy(includeImages = true),
            capabilities = setOf(AiCapability.TEXT, AiCapability.IMAGE)
        )

        assertNotNull(result.image)
        assertEquals("image/jpeg", result.image?.mimeType)
        assertArrayEquals(imageBytes, result.image?.imageBytes)
    }

    @Test
    fun `image context is omitted if provider lacks IMAGE capability`() = runTest {
        val store = InMemoryConversationStore()
        val blackboard = DefaultBlackboard()

        val imageObs = Observation(
            modality = Modality.IMAGE,
            source = Source.CAMERA,
            payload = ImageObservationPayload(byteArrayOf(1, 2, 3), "image/jpeg"),
            timestamp = System.currentTimeMillis()
        )
        blackboard.publish(createKnowledge(ObservationKnowledge(imageObs)))

        val assembler = DefaultContextAssembler(store, blackboard)
        val result = assembler.assemble(
            policy = AiContextPolicy(includeImages = true),
            capabilities = setOf(AiCapability.TEXT) // Text-only provider
        )

        assertTrue(result.omissions.any { it.source == "IMAGE" && it.reason.contains("lacks IMAGE") })
    }

    @Test
    fun `image context is omitted for unsupported MIME type`() = runTest {
        val blackboard = DefaultBlackboard()
        val assembler = DefaultContextAssembler(InMemoryConversationStore(), blackboard)
        val image = Observation(
            modality = Modality.IMAGE,
            source = Source.CAMERA,
            timestamp = System.currentTimeMillis(),
            payload = ImageObservationPayload(byteArrayOf(1, 2, 3), "image/gif")
        )
        blackboard.publish(createKnowledge(ObservationKnowledge(image)))

        val result = assembler.assemble(
            policy = AiContextPolicy(includeImages = true),
            capabilities = setOf(AiCapability.IMAGE)
        )

        assertNull(result.image)
        assertTrue(result.omissions.any { it.reason.contains("Unsupported image MIME type") })
    }

    @Test
    fun `stale image observation is omitted based on age limit`() = runTest {
        val store = InMemoryConversationStore()
        val blackboard = DefaultBlackboard()

        val staleImageObs = Observation(
            modality = Modality.IMAGE,
            source = Source.CAMERA,
            payload = ImageObservationPayload(byteArrayOf(1, 2, 3), "image/jpeg"),
            timestamp = System.currentTimeMillis() - 70_000L // 70 seconds old (> 60s limit)
        )
        blackboard.publish(createKnowledge(ObservationKnowledge(staleImageObs)))

        val assembler = DefaultContextAssembler(store, blackboard)
        val result = assembler.assemble(
            policy = AiContextPolicy(includeImages = true),
            capabilities = setOf(AiCapability.TEXT, AiCapability.MULTIMODAL)
        )

        assertTrue(result.omissions.any { it.source == "IMAGE" && it.reason.contains("stale") })
    }

    @Test
    fun `sensitive data in speech text is sanitized in recent events`() = runTest {
        val store = InMemoryConversationStore()
        val blackboard = DefaultBlackboard()

        val secretObs = Observation(
            modality = Modality.TEXT,
            source = Source.USER,
            payload = SpeechObservationPayload("My key is sk-or-v1-12345678901234567890", "en-US")
        )
        blackboard.publish(createKnowledge(ObservationKnowledge(secretObs)))

        val assembler = DefaultContextAssembler(store, blackboard)
        val result = assembler.assemble(
            policy = AiContextPolicy(includeRecentEvents = true),
            capabilities = setOf(AiCapability.TEXT)
        )

        assertEquals(1, result.context.recentEvents.size)
        val event = result.context.recentEvents[0] as SpeechRecognized
        assertFalse(event.text.contains("sk-or-v1-12345678901234567890"))
        assertTrue(event.text.contains("[REDACTED_API_KEY]"))
    }
}
