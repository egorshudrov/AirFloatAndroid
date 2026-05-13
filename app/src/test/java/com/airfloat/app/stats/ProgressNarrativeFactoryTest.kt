package com.airfloat.app.stats

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ProgressNarrativeFactoryTest {
    private val zoneId: ZoneId = ZoneId.of("Europe/Bratislava")

    @Test
    fun builds_distinct_narrative_for_pr_context() {
        val snapshot =
            ProgressSnapshot(
                rangeLabel = "RANGE 7D",
                comparisonLabel = "vs previous 7 days",
                totalSessions = 3,
                totalReps = 847,
                averageRepsPerSession = 282,
                averageCompletionRate = 91,
                totalKcal = 244,
                currentStreakDays = 4,
                activeDays = 3,
                activeDaysTarget = 7,
                precisionSignal = 91,
                sessionDelta = 1,
                repsDelta = 147,
                completionDelta = 8,
                loadDeltaPercent = 18,
                trendPoints = emptyList(),
                qualityPoints = emptyList(),
                heatmapCells = emptyList(),
                distributionPoints = emptyList(),
                topVolumeBest = null,
                topQualityBest = null,
                topEnduranceBest = null,
                recentSessions = emptyList()
            )
        val state =
            StateSnapshot(
                lastSessionExercise = "press_barbell",
                lastSessionQuality = LastSessionQuality.GREAT,
                currentStreak = 4,
                streakRisk = false,
                bestExercise = "press_barbell",
                weakestExercise = "situp",
                loadTrend = LoadTrend.RISING,
                bodyRecompSignal = BodyRecompSignal.ACTIVE,
                hasNewPR = true,
                daysSinceLastCheckIn = 2,
                daysSinceLastSession = 0,
                hoursSinceLastSession = 5,
                liveContext = LiveContextCalculator.getLiveContext(
                    nowEpochMs = LocalDate.of(2026, 3, 23).atTime(16, 0).atZone(zoneId).toInstant().toEpochMilli(),
                    zoneId = zoneId
                )
            )
        val sessions =
            listOf(
                session("s1", 2026, 3, 23, 120),
                session("s2", 2026, 3, 20, 94)
            )

        val narrative = ProgressNarrativeFactory.build(snapshot, state, sessions, zoneId)

        assertTrue(narrative.chapterHeader.contains("NEW PERSONAL RECORD"))
        assertTrue(narrative.progressArc.contains("147"))
    }

    private fun session(
        id: String,
        year: Int,
        month: Int,
        day: Int,
        reps: Int
    ): WorkoutSessionRecord =
        WorkoutSessionRecord(
            id = id,
            timestampMs = LocalDate.of(year, month, day).atStartOfDay(zoneId).toInstant().toEpochMilli(),
            exerciseKey = "press",
            presetKey = "press_barbell",
            goalReps = reps,
            completed = true,
            reps = reps,
            successfulAttempts = reps,
            failedAttempts = 0,
            durationMs = 120_000L,
            estimatedKcal = 20f,
            completionRate = 94
        )
}
