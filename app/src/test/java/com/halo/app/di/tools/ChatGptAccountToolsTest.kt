package com.halo.app.di.tools

import com.halo.app.di.tools.host.ChatGptAccountTools
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ChatGptAccountToolsTest {
    @Test fun registeredAccountControlsHaveVersionedContractsAndHumanBrowserBoundary() {
        val tools = ChatGptAccountTools({ error("Review must not initialize account") }, { false })
        val descriptors = tools.definitions().map { requireNotNull(HaloToolDescriptors.assistant(it)) }
        val registry = ToolRegistry(listOf(tools), descriptors)
        assertEquals(emptyList<ToolOperabilityProblem>(), ToolOperabilityReview.inspect(
            listOf(ToolFeatureOperability("chatgpt-account", ChatGptAccountTools.NAMES)), registry.descriptors()))
        descriptors.filter { it.name != ChatGptAccountTools.STATUS }.forEach {
            assertEquals(ConfirmationPolicy.ALWAYS, it.security.confirmation)
            assertEquals(ReplaySafety.NEVER_REPLAY, it.security.replaySafety)
            assertEquals(setOf("sourceVersion"), it.operability!!.sourceVersionArguments)
        }
    }
    @Test fun deniedAndCredentialBearingRequestsCannotReachTheAccountOwner() = runBlocking {
        val denied = ChatGptAccountTools({ error("Must not initialize account") }, { false })
        assertTrue(denied.execute(ToolCall("denied", ChatGptAccountTools.STATUS, emptyMap())) is ToolExecutionResult.Rejected)
        val allowed = ChatGptAccountTools({ error("Must not initialize account") }, { true })
        for (arguments in listOf(mapOf("password" to "never"), mapOf("accessToken" to "never"), emptyMap())) {
            assertTrue(allowed.execute(ToolCall("invalid", ChatGptAccountTools.SIGN_IN, arguments)) is ToolExecutionResult.Rejected)
        }
    }
}
