package com.airfloat.app.stats

import java.time.DayOfWeek
import java.time.LocalDate

enum class PlannedDayType {
    TRAIN,
    REST
}

data class ProgramSchedule(
    val restDaysOfWeek: Set<DayOfWeek>,
    val dateOverrides: Map<LocalDate, PlannedDayType>
)
