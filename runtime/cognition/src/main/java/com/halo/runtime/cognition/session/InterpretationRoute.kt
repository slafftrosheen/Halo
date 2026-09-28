package com.halo.runtime.cognition.session

import com.halo.cognition.language.CallIntent
import com.halo.cognition.language.ConversationIntent
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
import com.halo.cognition.language.SystemFactIntent

/**
 * AGENTIC-001 explicit interpretation routing.
 *
 * Previously CognitiveSession only submitted Conversation and Question intents and
 * everything else printed "BYPASSING LLM" and returned. That is only correct when a
 * deterministic executor has actually been given the intent. This contract makes the
 * decision a value instead of a side effect of a class-name check, and guarantees
 * that a recognized actionable intent always names something that will act on it.
 */
sealed interface InterpretationExecutionRoute {

    /** Answered directly from a local fact resolver; no model, no tool. */
    data object LocalFact : InterpretationExecutionRoute

    /**
     * The deterministic goal/plan/execution pipeline consumes this intent. Named so
     * traces say which engine is expected to produce the outcome.
     */
    data class DeterministicAction(
        val intent: Intent,
        val executorId: String,
    ) : InterpretationExecutionRoute

    /**
     * No deterministic executor owns this intent, so the cognitive agent path must
     * handle it (local model plus tools). This is the route that replaces the old
     * silent return.
     */
    data class Agentic(
        val intent: Intent,
        val hint: String? = null,
    ) : InterpretationExecutionRoute

    /** Recognized as actionable but nothing can act on it. The user is told. */
    data class Unsupported(
        val intent: Intent,
        val reason: String,
    ) : InterpretationExecutionRoute
}

/**
 * Deterministic ownership table.
 *
 * These intents already map to a goal in the deterministic reasoning strategy and to
 * a registered host capability, so Routing them into the model as well would execute
 * one utterance's side effect twice. Every other actionable intent must be handled by
 * the agentic path.
 */
class InterpretationRouteResolver(
    private val deterministicExecutors: Map<Class<out Intent>, String> = DEFAULT_DETERMINISTIC_EXECUTORS,
) {

    fun resolve(intent: Intent): InterpretationExecutionRoute {
        if (intent is SystemFactIntent) return InterpretationExecutionRoute.LocalFact
        deterministicExecutors[intent.javaClass]?.let { executorId ->
            return InterpretationExecutionRoute.DeterministicAction(intent, executorId)
        }
        return when (intent) {
            is ConversationIntent, is QuestionIntent -> InterpretationExecutionRoute.Agentic(intent)
            else -> InterpretationExecutionRoute.Agentic(
                intent = intent,
                hint = "The user asked for something the deterministic host path cannot do. " +
                    "Use the available Halo actions if one fits; otherwise say what you cannot do.",
            )
        }
    }

    companion object {
        val DEFAULT_DETERMINISTIC_EXECUTORS: Map<Class<out Intent>, String> = mapOf(
            OpenIntent::class.java to "engine.execution/device.open_app",
            CallIntent::class.java to "engine.execution/communication.call",
            NavigateIntent::class.java to "engine.execution/navigation",
            PlayIntent::class.java to "engine.execution/media.play",
            PauseIntent::class.java to "engine.execution/media.pause",
            ReminderIntent::class.java to "engine.execution/memory.reminder",
            SearchIntent::class.java to "engine.execution/web.search",
            SetAlarmIntent::class.java to "engine.execution/device.set_alarm",
            ShowAlarmsIntent::class.java to "engine.execution/device.show_alarms",
        )
    }
}
