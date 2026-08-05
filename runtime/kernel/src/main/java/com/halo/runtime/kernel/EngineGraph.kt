package com.halo.runtime.kernel

import com.halo.sdk.runtime.Engine

class EngineGraph {
    private val nodes = mutableMapOf<Class<out Engine>, EngineNode>()

    fun register(engine: Engine) {
        nodes[engine::class.java] = EngineNode(engine)
    }

    fun all() = nodes.values
}
