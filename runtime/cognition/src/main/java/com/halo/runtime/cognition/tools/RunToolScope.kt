package com.halo.runtime.cognition.tools

import com.halo.runtime.cognition.agent.AgentRunId
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Host-owned coroutine scope; models cannot supply or overwrite run authorization. */
class RunToolScope(val runId: AgentRunId, val targetIsLocal: Boolean = true) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<RunToolScope>
}
