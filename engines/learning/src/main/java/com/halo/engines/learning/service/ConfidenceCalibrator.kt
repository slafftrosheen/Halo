package com.halo.engines.learning.service

import com.halo.runtime.cognition.learning.Skill

class ConfidenceCalibrator {
    fun calibrate(skill: Skill): Skill {
        // Simple linear calibration bridging raw success rate and reported confidence
        val calibratedConfidence = (skill.confidence + skill.successRate) / 2.0f
        return skill.copy(confidence = calibratedConfidence)
    }
}
