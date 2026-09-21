package com.halo.engines.interpretation

class InterpretationEngine(
    blackboard: com.halo.runtime.cognition.blackboard.Blackboard,
    eventBus: com.halo.sdk.runtime.EventBus,
    workingMemory: com.halo.runtime.cognition.workingmemory.WorkingMemory =
        com.halo.runtime.cognition.workingmemory.DefaultWorkingMemory(blackboard),
    pipeline: com.halo.engines.interpretation.internal.RulePipeline =
        com.halo.engines.interpretation.internal.RulePipeline(
            com.halo.engines.interpretation.internal.RuleRegistry().apply {
                registerContextRule(com.halo.engines.interpretation.rules.ContactRule())
                registerContextRule(com.halo.engines.interpretation.rules.ReminderRule())
                registerContextRule(com.halo.engines.interpretation.rules.TimeRule())
                registerContextRule(com.halo.engines.interpretation.rules.SpeechRule())
            }
        )
) : com.halo.engines.interpretation.internal.DefaultInterpretationEngine(
    pipeline = pipeline,
    blackboard = blackboard,
    eventBus = eventBus
)
