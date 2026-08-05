package com.halo.runtime.engine

/**
 * The "Nothing Engine" is responsible for deciding whether an interruption
 * (like a notification or audio signal) should be suppressed based on the user's
 * current cognitive load and context.
 */
interface NothingEngine {
    fun shouldInterrupt(signal: InterruptionSignal, context: UserContext): Boolean
}

interface InterruptionSignal {
    val urgency: Int
    val source: String
}

interface UserContext {
    val attentionLevel: Int
    val isInMeeting: Boolean
    val isDriving: Boolean
}
