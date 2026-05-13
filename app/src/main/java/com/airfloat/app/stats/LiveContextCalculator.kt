package com.airfloat.app.stats

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

enum class TimeOfDaySignal {
    MORNING,
    PRE_WORKOUT,
    NIGHT,
    DAYTIME
}

enum class DayOfWeekSignal {
    MONDAY,
    FRIDAY,
    SUNDAY,
    STANDARD
}

data class LiveContextSnapshot(
    val timeOfDay: TimeOfDaySignal = TimeOfDaySignal.DAYTIME,
    val dayOfWeek: DayOfWeekSignal = DayOfWeekSignal.STANDARD,
    val nightDimAlpha: Float = 0f
)

object LiveContextCalculator {
    fun getLiveContext(
        nowEpochMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): LiveContextSnapshot {
        val now = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId)
        val timeOfDay =
            when (now.hour) {
                in 5..10 -> TimeOfDaySignal.MORNING
                in 14..17 -> TimeOfDaySignal.PRE_WORKOUT
                in 21..23 -> TimeOfDaySignal.NIGHT
                else -> TimeOfDaySignal.DAYTIME
            }
        val dayOfWeek =
            when (now.dayOfWeek) {
                DayOfWeek.MONDAY -> DayOfWeekSignal.MONDAY
                DayOfWeek.FRIDAY -> DayOfWeekSignal.FRIDAY
                DayOfWeek.SUNDAY -> DayOfWeekSignal.SUNDAY
                else -> DayOfWeekSignal.STANDARD
            }

        return LiveContextSnapshot(
            timeOfDay = timeOfDay,
            dayOfWeek = dayOfWeek,
            nightDimAlpha = if (timeOfDay == TimeOfDaySignal.NIGHT) 0.05f else 0f
        )
    }
}
