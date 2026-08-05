package com.halo.runtime.cognition.executive

import java.util.UUID

data class StrategicGoal(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val objective: String,
    val weight: Float // How important is this strategic goal relative to others
)
