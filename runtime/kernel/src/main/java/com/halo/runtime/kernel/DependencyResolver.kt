package com.halo.runtime.kernel

class DependencyResolver {
    fun resolve(graph: EngineGraph): List<EngineNode> {
        val result = mutableListOf<EngineNode>()
        val visited = mutableSetOf<EngineNode>()

        fun visit(node: EngineNode) {
            if (node in visited) return
            visited += node
            node.dependencies.forEach(::visit)
            result += node
        }

        graph.all().forEach(::visit)
        return result
    }
}
