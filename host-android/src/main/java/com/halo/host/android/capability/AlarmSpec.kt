package com.halo.host.android.capability

import com.halo.cognition.language.DayRelation
import java.time.LocalTime

data class AlarmSpec(
    val time: LocalTime,
    val dayRelation: DayRelation? = null,
    val label: String? = "Halo alarm",
)
