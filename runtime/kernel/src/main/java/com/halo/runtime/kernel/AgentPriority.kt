package com.halo.runtime.kernel

enum class AgentPriority(val level: Int) {
    Critical(100),
    High(75),
    Normal(50),
    Low(25),
    Idle(0)
}
