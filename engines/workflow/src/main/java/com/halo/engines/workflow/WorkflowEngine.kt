package com.halo.engines.workflow
import com.halo.sdk.runtime.Engine
interface WorkflowEngine : Engine {
    suspend fun execute()
}
