package com.halo.app.di.tools.host

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.halo.host.android.capability.AlarmCommandResolver
import com.halo.host.android.capability.AlarmExecutionPlan
import org.robolectric.Shadows
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * AGENTIC-001 regression tests for the escaped-interpolation bugs.
 *
 * `"Clarification required: \${plan.prompt}"` and `Uri.parse("tel:\$number")` are literal
 * Kotlin text: they compiled, passed review, and produced a question with no question in
 * it and a dial URI that contained the characters "tel:$number". These tests pin the
 * resolved values.
 */
@RunWith(RobolectricTestRunner::class)
class HostActionToolsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `clock lifecycle intents never claim to have changed clock state`() = runTest {
        val tools = AlarmTools(context)
        val show = tools.clockIntent("show_alarms")!!
        assertEquals(android.provider.AlarmClock.ACTION_SHOW_ALARMS, show.action)
        val next = tools.clockIntent("dismiss_next_alarm")!!
        assertEquals(android.provider.AlarmClock.ACTION_DISMISS_ALARM, next.action)
        assertEquals(android.provider.AlarmClock.ALARM_SEARCH_MODE_NEXT,
            next.getStringExtra(android.provider.AlarmClock.EXTRA_ALARM_SEARCH_MODE))
        assertEquals(android.provider.AlarmClock.ACTION_SNOOZE_ALARM,
            tools.clockIntent("snooze_alarm")!!.action)
        assertEquals(android.provider.AlarmClock.ACTION_DISMISS_TIMER,
            tools.clockIntent("dismiss_expired_timers")!!.action)
        assertTrue(tools.definitions().any { it.name == "halo_clock_view" })
        assertTrue(tools.definitions().any { it.name == "halo_clock_modify" })
        val rejected = tools.execute(ToolCall("bad-clock", "halo_clock_view",
            mapOf("action" to "dismiss_next_alarm")))
        assertTrue(rejected is ToolExecutionResult.Rejected)
        assertEquals(com.halo.runtime.cognition.tools.ToolRejectionKind.INVALID_ARGUMENTS,
            (rejected as ToolExecutionResult.Rejected).kind)
    }

    @Test
    fun `alarm clarification actually contains the resolver's question`() = runTest {
        val tools = AlarmTools(context)
        val expression = "in the evening"

        val result = tools.execute(
            ToolCall(
                id = "call-1",
                functionName = "halo_set_alarm",
                arguments = mapOf("timeExpression" to expression),
            )
        )

        val plan = AlarmCommandResolver().resolve(null, expression, "Halo alarm")
        val expectedPrompt = (plan as AlarmExecutionPlan.Clarification).prompt

        assertTrue(result is ToolExecutionResult.Rejected)
        val rejection = result as ToolExecutionResult.Rejected
        assertEquals(
            "the tool must return the real clarification prompt, not the template",
            expectedPrompt,
            rejection.reason,
        )
        assertFalse("the escaped \${...} must not survive", rejection.reason.contains("\${"))
    }

    @Test
    fun `an unparseable alarm time asks for a time instead of inventing one`() = runTest {
        val tools = AlarmTools(context)

        val result = tools.execute(
            ToolCall(
                id = "call-2",
                functionName = "halo_set_alarm",
                arguments = mapOf("timeExpression" to "sometime later"),
            )
        )

        assertTrue(result is ToolExecutionResult.Rejected)
        val rejection = result as ToolExecutionResult.Rejected
        assertTrue(rejection.reason.contains("What time"))
        assertEquals(
            com.halo.runtime.cognition.tools.ToolRejectionKind.INVALID_ARGUMENTS,
            rejection.kind,
        )
    }

    @Test
    fun `contact lookup reports the queried name rather than the literal template`() = runTest {
        Shadows.shadowOf(context as android.app.Application)
            .grantPermissions(android.Manifest.permission.READ_CONTACTS)
        val tools = CommunicationTools(context)

        val result = tools.execute(
            ToolCall(
                id = "call-3",
                functionName = "halo_find_contact",
                arguments = mapOf("query" to "Alice"),
            )
        )

        assertTrue(result is ToolExecutionResult.Success)
        val output = (result as ToolExecutionResult.Success).output
        assertTrue("the query must be interpolated, got: $output", output.contains("Alice"))
        assertFalse(output.contains("\$query"))
        assertTrue(output.startsWith("tool_result:"))
    }

    @Test
    fun `the dial uri carries the resolved number`() = runTest {
        val uri = CommunicationTools.dialUriFor("+37120000000")

        assertEquals("tel:+37120000000", uri.toString())
        assertTrue(uri.scheme == "tel")
        assertTrue(uri.schemeSpecificPart == "+37120000000")
    }

    @Test
    fun `dial numbers are normalized without losing the international prefix`() {
        assertEquals("+37120000000", CommunicationTools.canonicalDialNumber("+371 2000 0000"))
        assertEquals("+37120000000", CommunicationTools.canonicalDialNumber("+371-2000-0000"))
        assertEquals("5551234", CommunicationTools.canonicalDialNumber("(555) 1234"))
    }

    @Test
    fun `dialing needs an explicit target`() = runTest {
        val tools = CommunicationTools(context)

        val result = tools.execute(
            ToolCall(id = "call-4", functionName = "halo_dial_contact", arguments = emptyMap())
        )

        assertTrue(result is ToolExecutionResult.Rejected)
        assertEquals(
            com.halo.runtime.cognition.tools.ToolRejectionKind.INVALID_ARGUMENTS,
            (result as ToolExecutionResult.Rejected).kind,
        )
    }
}
