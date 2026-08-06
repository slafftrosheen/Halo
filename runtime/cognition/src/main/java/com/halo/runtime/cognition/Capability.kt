package com.halo.runtime.cognition

interface Capability {
    val id: String
    
    suspend fun supports(task: Task): Boolean
    suspend fun execute(task: Task): Outcome
}
