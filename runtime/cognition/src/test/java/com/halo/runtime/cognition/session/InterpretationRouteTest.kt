package com.halo.runtime.cognition.session

import com.halo.cognition.language.CallIntent
import com.halo.cognition.language.ConversationIntent
import com.halo.cognition.language.CurrentTimeIntent
import com.halo.cognition.language.Intent
import com.halo.cognition.language.NavigateIntent
import com.halo.cognition.language.OpenIntent
import com.halo.cognition.language.PauseIntent
import com.halo.cognition.language.PlayIntent
import com.halo.cognition.language.QuestionIntent
import com.halo.cognition.language.ReminderIntent
import com.halo.cognition.language.SearchIntent
import com.halo.cognition.language.SetAlarmIntent
import com.halo.cognition.language.ShowAlarmsIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AGENTIC-001: an actionable intent must always name something that will act on it.
 *
 * The previous behaviour logged "BYPASSING LLM (deterministic execution)" and returned,
 * which is only valid when a deterministic executor truly owns the intent.
 */
class InterpretationRouteTest {

    private val resolver = InterpretationRouteResolver()

    private val allIntents: List<Intent> = listOf(
        ConversationIntent, QuestionIntent, OpenIntent, CallIntent, NavigateIntent,
        PlayIntent, PauseIntent, ReminderIntent, SetAlarmIntent, ShowAlarmsIntent,
        SearchIntent, CurrentTimeIntent,
    )

    @Test
    fun `every recognized intent resolves to a route`() {
        allIntents.forEach { intent ->
            val route = resolver.resolve(intent)
            assertTrue("no route for ${intent::class.simpleName}", route is InterpretationExecutionRoute)
        }
    }

    @Test
    fun `action intents name the deterministic executor that owns them`() {
        val route = resolver.resolve(SetAlarmIntent)

        assertTrue(route is InterpretationExecutionRoute.DeterministicAction)
        assertEquals(
            "engine.execution/device.set_alarm",
            (route as InterpretationExecutionRoute.DeterministicAction).executorId,
        )
    }

    @Test
    fun `conversation and questions go to direct response route`() {
        assertTrue(resolver.resolve(ConversationIntent) is InterpretationExecutionRoute.DirectResponse)
        assertTrue(resolver.resolve(QuestionIntent) is InterpretationExecutionRoute.DirectResponse)
    }

    @Test
    fun `ambiguous AI intent is a nonexecuting clarification route`() {
        val intent = com.halo.cognition.language.IntentNeedsClarification("Do it or explain?")
        val route = resolver.resolve(intent)
        assertTrue(route is InterpretationExecutionRoute.Unsupported)
        assertEquals("Do it or explain?",(route as InterpretationExecutionRoute.Unsupported).reason)
    }

    @Test
    fun `system facts are resolved locally`() {
        assertTrue(resolver.resolve(CurrentTimeIntent) is InterpretationExecutionRoute.LocalFact)
    }

    @Test
    fun `an action intent with no deterministic owner is routed to the agent, never dropped`() {
        val noDeterministic = InterpretationRouteResolver(deterministicExecutors = emptyMap())

        val route = noDeterministic.resolve(SetAlarmIntent)

        assertTrue(route is InterpretationExecutionRoute.Agentic)
        val agentic = route as InterpretationExecutionRoute.Agentic
        assertTrue("the agent must be told what to do", !agentic.hint.isNullOrBlank())
    }

    @Test
    fun `a route resolver with no deterministic coverage still routes every action`() {
        val noDeterministic = InterpretationRouteResolver(deterministicExecutors = emptyMap())

        listOf(OpenIntent, CallIntent, SetAlarmIntent, NavigateIntent).forEach { intent ->
            assertTrue(
                "action intent vanished: ${intent::class.simpleName}",
                noDeterministic.resolve(intent) is InterpretationExecutionRoute.Agentic,
            )
        }
    }
}
