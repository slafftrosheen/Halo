package com.halo.engines.executive.manager

import com.halo.runtime.cognition.executive.RiskAssessment

class ConfirmationEngine {
    fun requiresConfirmation(assessment: RiskAssessment): Boolean {
        return assessment.requiresConfirmation || assessment.score >= 0.8f
    }
}
