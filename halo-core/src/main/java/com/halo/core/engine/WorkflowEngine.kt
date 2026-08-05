package com.halo.core.engine

interface WorkflowEngine : Engine {
    suspend fun execute(intent: String)
}
