package com.halo.engines.interpretation

class InterpretationEngine(
    blackboard: com.halo.runtime.cognition.knowledge.Blackboard =
        com.halo.runtime.cognition.knowledge.InMemoryBlackboard(),
    workingMemory: com.halo.runtime.cognition.WorkingMemory =
        com.halo.runtime.cognition.WorkingMemory(blackboard),
    pipeline: com.halo.engines.interpretation.internal.RulePipeline =
        com.halo.engines.interpretation.internal.RulePipeline(
            listOf(
                com.halo.engines.interpretation.rules.ContactRule(),
                com.halo.engines.interpretation.rules.TimeRule(),
                com.halo.engines.interpretation.rules.SpeechRule()
            )
        )
) : com.halo.engines.interpretation.internal.DefaultInterpretationEngine(
    pipeline = pipeline,
    blackboard = blackboard,
    contextAssembler = com.halo.engines.interpretation.internal.ContextAssembler(workingMemory)
)
