package com.halo.engines.interpretation

class InterpretationEngine(
    blackboard: com.halo.runtime.cognition.blackboard.Blackboard,
    workingMemory: com.halo.runtime.cognition.workingmemory.WorkingMemory =
        com.halo.runtime.cognition.workingmemory.DefaultWorkingMemory(blackboard),
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
    contextAssembler = com.halo.runtime.cognition.context.DefaultContextAssembler(
        blackboard = blackboard,
        workingMemory = workingMemory
    )
)
