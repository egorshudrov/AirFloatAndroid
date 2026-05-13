package com.airfloat.app.ui

import com.airfloat.app.stats.PlannedDayType
import com.airfloat.app.stats.WorkoutSessionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class ConsistencyCalendarDataBuilderTest {
    private val zoneId: ZoneId = ZoneId.of("Europe/Bratislava")
    private val today: LocalDate = LocalDate.of(2025, 4, 18)

    @Test
    fun builds_april_2025_calendar_with_expected_states() {
        val calendar =
            ConsistencyCalendarDataBuilder.buildCalendarMonth(
                year = 2025,
                month = 4,
                sessions =
                    listOf(
                        session("s1", 2025, 4, 1, 92, 18, WorkoutFragment.SOURCE_PUSHUP),
                        session("s2", 2025, 4, 2, 84, 24, WorkoutFragment.SOURCE_SITUP),
                        session("s3", 2025, 4, 4, 74, 16, WorkoutFragment.SOURCE_SQUAT_BETA),
                        session("s4", 2025, 4, 7, 65, 14, WorkoutFragment.SOURCE_PRESS_DUMBBELL),
                        session("s5", 2025, 4, 10, 88, 20, WorkoutFragment.SOURCE_PUSHUP),
                        session("s6", 2025, 4, 10, 76, 22, WorkoutFragment.SOURCE_SITUP),
                        session("s7", 2025, 4, 18, 91, 19, WorkoutFragment.SOURCE_PRESS_BARBELL)
                    ),
                restDays = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
                dateOverrides = emptyMap(),
                zoneId = zoneId,
                today = today
            )

        println(
            calendar.joinToString(separator = "\n") { day ->
                buildString {
                    append(day.date)
                    append(" | ")
                    append(day.state)
                    append(" | avg=")
                    append(day.avgScore)
                    if (day.sessions.isNotEmpty()) {
                        append(" | ")
                        append(day.sessions.joinToString { "${it.exerciseName}:${it.reps}r/${it.completionRate}%" })
                    }
                }
            }
        )

        assertEquals(30, calendar.size)
        assertEquals(DayState.TRAINED_PERFECT, calendar.first { it.date == LocalDate.of(2025, 4, 1) }.state)
        assertEquals(DayState.TRAINED_HIGH, calendar.first { it.date == LocalDate.of(2025, 4, 2) }.state)
        assertEquals(DayState.TRAINED_MID, calendar.first { it.date == LocalDate.of(2025, 4, 4) }.state)
        assertEquals(DayState.PLANNED_REST, calendar.first { it.date == LocalDate.of(2025, 4, 5) }.state)
        assertEquals(DayState.MISSED, calendar.first { it.date == LocalDate.of(2025, 4, 3) }.state)
        assertEquals(DayState.TRAINED_HIGH, calendar.first { it.date == LocalDate.of(2025, 4, 10) }.state)
        assertEquals(82, calendar.first { it.date == LocalDate.of(2025, 4, 10) }.avgScore)
        assertEquals(2, calendar.first { it.date == LocalDate.of(2025, 4, 10) }.sessions.size)
        assertEquals(DayState.TRAINED_PERFECT, calendar.first { it.date == LocalDate.of(2025, 4, 18) }.state)
        assertEquals(DayState.FUTURE, calendar.first { it.date == LocalDate.of(2025, 4, 21) }.state)
        assertTrue(calendar.count { it.state == DayState.MISSED } > 0)
    }

    @Test
    fun date_overrides_win_over_weekly_template() {
        val calendar =
            ConsistencyCalendarDataBuilder.buildCalendarMonth(
                year = 2025,
                month = 4,
                sessions = emptyList(),
                restDays = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
                dateOverrides =
                    mapOf(
                        LocalDate.of(2025, 4, 9) to PlannedDayType.REST,
                        LocalDate.of(2025, 4, 12) to PlannedDayType.TRAIN,
                        LocalDate.of(2025, 4, 20) to PlannedDayType.REST
                    ),
                zoneId = zoneId,
                today = today
            )

        val manualRest = calendar.first { it.date == LocalDate.of(2025, 4, 9) }
        assertEquals(DayState.PLANNED_REST, manualRest.state)
        assertEquals(true, manualRest.isPlannedRest)
        assertEquals(false, manualRest.isMissed)
        assertEquals(PlannedDayType.REST, manualRest.plannedDayType)
        assertEquals(true, manualRest.hasDateOverride)

        val manualTrain = calendar.first { it.date == LocalDate.of(2025, 4, 12) }
        assertEquals(DayState.MISSED, manualTrain.state)
        assertEquals(false, manualTrain.isPlannedRest)
        assertEquals(true, manualTrain.isMissed)
        assertEquals(PlannedDayType.TRAIN, manualTrain.plannedDayType)
        assertEquals(true, manualTrain.hasDateOverride)

        val futureManualRest = calendar.first { it.date == LocalDate.of(2025, 4, 20) }
        assertEquals(DayState.PLANNED_REST, futureManualRest.state)
        assertEquals(true, futureManualRest.isPlannedRest)
        assertEquals(false, futureManualRest.isMissed)
        assertEquals(PlannedDayType.REST, futureManualRest.plannedDayType)
        assertEquals(true, futureManualRest.hasDateOverride)
    }

    private fun session(
        id: String,
        year: Int,
        month: Int,
        day: Int,
        completionRate: Int,
        reps: Int,
        presetKey: String
    ): WorkoutSessionRecord =
        WorkoutSessionRecord(
            id = id,
            timestampMs = LocalDate.of(year, month, day).atStartOfDay(zoneId).toInstant().toEpochMilli(),
            exerciseKey = presetKey,
            presetKey = presetKey,
            goalReps = reps,
            completed = true,
            reps = reps,
            successfulAttempts = reps,
            failedAttempts = 0,
            durationMs = 45_000L,
            estimatedKcal = 12.5f,
            completionRate = completionRate,
            attempts = emptyList()
        )
}
