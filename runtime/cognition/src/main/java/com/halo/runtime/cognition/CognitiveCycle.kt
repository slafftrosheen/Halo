package com.halo.runtime.cognition

import com.halo.runtime.cognition.blackboard.Blackboard
import com.halo.sdk.runtime.Engine

class CognitiveCycle(
    val perception: Engine,
    val interpretation: Engine,
    val blackboard: Blackboard,
    val reasoning: Engine,
    val planner: Engine,
    val executor: Engine
)
