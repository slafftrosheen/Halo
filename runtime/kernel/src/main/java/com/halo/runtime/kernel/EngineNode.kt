package com.halo.runtime.kernel

import com.halo.sdk.runtime.Engine

data class EngineNode(
    val engine: Engine,
    val dependencies: MutableSet<EngineNode> = mutableSetOf(),
    val dependents: MutableSet<EngineNode> = mutableSetOf()
)
