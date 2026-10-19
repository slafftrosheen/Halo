package com.halo.engines.interpretation

class InterpretationEngine(
    blackboard: com.halo.runtime.cognition.blackboard.Blackboard,
    eventBus: com.halo.sdk.runtime.EventBus,
    clarificationCoordinator: com.halo.runtime.cognition.clarification.ClarificationCoordinator? = null,
    agentRunCoordinator: com.halo.runtime.cognition.agent.AgentRunCoordinator? = null,
    agentRunStore: com.halo.runtime.cognition.agent.AgentRunStore? = null,
    phoneFallbackEnabled: () -> Boolean = { false },
    /** Model-only advisory route, enabled by the Android host for final transcripts. */
    intentModelDecision: suspend (String) -> com.halo.engines.interpretation.rules.ModelIntentDecision? = { null },
    workingMemory: com.halo.runtime.cognition.workingmemory.WorkingMemory =
        com.halo.runtime.cognition.workingmemory.DefaultWorkingMemory(blackboard),
    pipeline: com.halo.engines.interpretation.internal.RulePipeline =
        com.halo.engines.interpretation.internal.RulePipeline(
            com.halo.engines.interpretation.internal.RuleRegistry().apply {
                registerContextRule(com.halo.engines.interpretation.rules.ContactRule())
                registerContextRule(com.halo.engines.interpretation.rules.ReminderRule())
                registerContextRule(com.halo.engines.interpretation.rules.TimeRule())
                registerContextRule(com.halo.engines.interpretation.rules.SpeechRule(intentModelDecision))
            }
        )
) : com.halo.engines.interpretation.internal.DefaultInterpretationEngine(
    pipeline = pipeline,
    blackboard = blackboard,
    eventBus = eventBus,
    clarificationCoordinator = clarificationCoordinator,
    agentRunCoordinator = agentRunCoordinator,
    agentRunStore = agentRunStore,
    phoneFallbackEnabled = phoneFallbackEnabled
)
