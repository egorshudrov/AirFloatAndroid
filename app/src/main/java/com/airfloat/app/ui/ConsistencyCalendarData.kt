package com.airfloat.app.ui

import com.airfloat.app.stats.PlannedDayType
import com.airfloat.app.stats.WorkoutSessionRecord
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class CalendarDayData(
    val date: LocalDate,
    val state: DayState,
    val sessions: List<DaySession>,
    val avgScore: Int,
    val isPlannedRest: Boolean,
    val isMissed: Boolean,
    val plannedDayType: PlannedDayType,
    val hasDateOverride: Boolean
)

data class DaySession(
    val exerciseName: String,
    val completionRate: Int,
    val reps: Int
)

enum class DayState {
    TRAINED_PERFECT,
    TRAINED_HIGH,
    TRAINED_MID,
    TRAINED_LOW,
    PLANNED_REST,
    MISSED,
    REST,
    FUTURE,
    TODAY_EMPTY
}

object ConsistencyCalendarDataBuilder {
    fun buildCalendarMonth(
        year: Int,
        month: Int,
        sessions: List<WorkoutSessionRecord>,
        restDays: Set<DayOfWeek>,
        dateOverrides: Map<LocalDate, PlannedDayType> = emptyMap(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zoneId)
    ): List<CalendarDayData> {
        val targetMonth = YearMonth.of(year, month)
        val monthSessions =
            sessions
                .groupBy { sessionDate(it.timestampMs, zoneId) }
                .filterKeys { it.year == year && it.monthValue == month }

        return buildList(targetMonth.lengthOfMonth()) {
            for (day in 1..targetMonth.lengthOfMonth()) {
                val date = targetMonth.atDay(day)
                val dayRecords = monthSessions[date].orEmpty().sortedBy { it.timestampMs }
                val mappedSessions =
                    dayRecords.map { session ->
                        DaySession(
                            exerciseName = WorkoutSurfaceFactory.exerciseLabel(session.presetKey),
                            completionRate = session.completionRate.coerceIn(0, 100),
                            reps = session.reps.coerceAtLeast(0)
                        )
                    }
                val avgScore =
                    if (dayRecords.isEmpty()) {
                        0
                    } else {
                        dayRecords.map { it.completionRate.coerceIn(0, 100) }.average().toInt()
                    }
                val hasDateOverride = date in dateOverrides
                val plannedDayType =
                    resolvePlannedDayType(
                        date = date,
                        restDays = restDays,
                        dateOverrides = dateOverrides
                    )
                val isPlannedRest = plannedDayType == PlannedDayType.REST
                val isMissed = date.isBefore(today) && !isPlannedRest && dayRecords.isEmpty()

                add(
                    CalendarDayData(
                        date = date,
                        state =
                            resolveDayState(
                                date = date,
                                today = today,
                                hasTraining = dayRecords.isNotEmpty(),
                                avgScore = avgScore,
                                isPlannedRest = isPlannedRest,
                                isMissed = isMissed
                            ),
                        sessions = mappedSessions,
                        avgScore = avgScore,
                        isPlannedRest = isPlannedRest,
                        isMissed = isMissed,
                        plannedDayType = plannedDayType,
                        hasDateOverride = hasDateOverride
                    )
                )
            }
        }
    }

    fun resolvePlannedDayType(
        date: LocalDate,
        restDays: Set<DayOfWeek>,
        dateOverrides: Map<LocalDate, PlannedDayType>
    ): PlannedDayType =
        dateOverrides[date]
            ?: if (date.dayOfWeek in restDays) {
                PlannedDayType.REST
            } else {
                PlannedDayType.TRAIN
            }

    private fun resolveDayState(
        date: LocalDate,
        today: LocalDate,
        hasTraining: Boolean,
        avgScore: Int,
        isPlannedRest: Boolean,
        isMissed: Boolean
    ): DayState =
        when {
            hasTraining && avgScore >= 90 -> DayState.TRAINED_PERFECT
            hasTraining && avgScore >= 80 -> DayState.TRAINED_HIGH
            hasTraining && avgScore >= 70 -> DayState.TRAINED_MID
            hasTraining -> DayState.TRAINED_LOW
            date == today && !isPlannedRest -> DayState.TODAY_EMPTY
            isMissed -> DayState.MISSED
            isPlannedRest -> DayState.PLANNED_REST
            date.isAfter(today) -> DayState.FUTURE
            else -> DayState.REST
        }

    private fun sessionDate(
        timestampMs: Long,
        zoneId: ZoneId
    ): LocalDate = Instant.ofEpochMilli(timestampMs).atZone(zoneId).toLocalDate()
}
