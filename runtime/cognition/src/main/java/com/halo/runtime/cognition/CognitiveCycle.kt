package com.halo.runtime.cognition

import com.halo.sdk.runtime.Engine

class CognitiveCycle(
    val perception: Engine,
    val interpretation: Engine,
    val reasoning: Engine,
    val planner: Engine,
    val executor: Engine
)
