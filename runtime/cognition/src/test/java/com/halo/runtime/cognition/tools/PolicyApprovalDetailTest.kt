package com.halo.runtime.cognition.tools

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.AgentRunId
import com.halo.runtime.cognition.agent.ToolInvocationId
import com.halo.runtime.cognition.ui.UiTargetPreview
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PolicyApprovalDetailTest {
    private val call = ToolCall("settings-call", "halo_screen_settings", mapOf("settings" to mapOf("hidePrivate" to false)))
    private val preview = UiTargetPreview("halo Screen", null, "Show private details in full screen",
        "People nearby may see personal and private cards", null, null)
    private fun authorizer() = PolicyToolAuthorizer(
        defaultPolicyProvider = { ToolApprovalPolicy.ALWAYS_ALLOW },
        descriptorProvider = { name -> ToolDescriptor(ToolId(name), ToolDefinition(name, name, "Sensitive configuration"),
            ToolCategory.HOST_CONTROL,
            security = ToolSecurityPolicy(ToolRiskClass.SENSITIVE, ConfirmationPolicy.ALWAYS, ReplaySafety.NEVER_REPLAY),
            presentation = ToolPresentation(title = "Change Screen settings", actionVerb = "Apply", targetArgument = "mode")) })

    @Test fun emptyHostGrantSummaryCannotEraseDurableScreenDisclosureAndAlwaysConfirmation() = runTest {
        for (emptySummary in listOf("", " \n\t ")) {
            val decision = authorizer().authorizeDurable(call, AgentRunId("run"), InteractionId("interaction"),
                ToolInvocationId("invocation"), "exact-digest", presentationContext = preview,
                grantSummary = emptySummary, newRequestId = { "approval" })
            assertTrue(decision is AuthorizationDecision.NeedsUserInput)
            val request = (decision as AuthorizationDecision.NeedsUserInput).request
            assertEquals(preview.description, request.detail)
            assertTrue(request.detail!!.contains("People nearby"))
            assertEquals("exact-digest", request.argumentsDigest)
            assertEquals(call, decision.toolCall)
        }
    }

    @Test fun emptySummaryWithoutPreflightFallsBackToOrdinarySafeArgumentDetail() = runTest {
        val decision = authorizer().authorizeDurable(call.copy(arguments = mapOf("mode" to "dark")),
            AgentRunId("run"), InteractionId("interaction"), ToolInvocationId("invocation"), "digest",
            grantSummary = "", newRequestId = { "approval" }) as AuthorizationDecision.NeedsUserInput
        assertEquals("Apply dark", decision.request.detail)
    }

    @Test fun actualRoutineGrantSummaryStillTakesPrecedenceWithoutRewritingItsContents() = runTest {
        val routine = ToolCall("routine", "halo_create_routine", mapOf("name" to "Morning reminder"))
        val summary = "  Morning reminder\nEvery weekday at 08:00\nDeliver in Activity  "
        val decision = authorizer().authorizeDurable(routine, AgentRunId("run"), InteractionId("interaction"),
            ToolInvocationId("invocation"), "routine-digest", presentationContext = preview,
            grantSummary = summary, newRequestId = { "routine-approval" }) as AuthorizationDecision.NeedsUserInput
        assertEquals(summary, decision.request.detail)
        assertEquals("routine-digest", decision.request.argumentsDigest)
        assertEquals(routine, decision.toolCall)
    }
}
