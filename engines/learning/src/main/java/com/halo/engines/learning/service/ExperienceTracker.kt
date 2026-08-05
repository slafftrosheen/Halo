package com.halo.engines.learning.service

import com.halo.runtime.cognition.learning.Skill
import com.halo.runtime.cognition.learning.ExecutionResult

class ExperienceTracker {
    fun record(skill: Skill, result: ExecutionResult): Skill {
        skill.recordExecution(result.success, result.latencyMs)
        return skill
    }
}
