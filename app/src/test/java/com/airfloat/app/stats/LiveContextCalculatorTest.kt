package com.airfloat.app.stats

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class LiveContextCalculatorTest {
    private val zoneId: ZoneId = ZoneId.of("Europe/Bratislava")
    private val date: LocalDate = LocalDate.of(2026, 3, 22)

    @Test
    fun maps_morning_preworkout_and_night_contexts() {
        val morning =
            LiveContextCalculator.getLiveContext(
                nowEpochMs = date.atTime(8, 15).atZone(zoneId).toInstant().toEpochMilli(),
                zoneId = zoneId
            )
        val preWorkout =
            LiveContextCalculator.getLiveContext(
                nowEpochMs = date.atTime(16, 30).atZone(zoneId).toInstant().toEpochMilli(),
                zoneId = zoneId
            )
        val night =
            LiveContextCalculator.getLiveContext(
                nowEpochMs = date.atTime(22, 10).atZone(zoneId).toInstant().toEpochMilli(),
                zoneId = zoneId
            )

        assertEquals(TimeOfDaySignal.MORNING, morning.timeOfDay)
        assertEquals(TimeOfDaySignal.PRE_WORKOUT, preWorkout.timeOfDay)
        assertEquals(TimeOfDaySignal.NIGHT, night.timeOfDay)
        assertEquals(DayOfWeekSignal.SUNDAY, morning.dayOfWeek)
    }
}
