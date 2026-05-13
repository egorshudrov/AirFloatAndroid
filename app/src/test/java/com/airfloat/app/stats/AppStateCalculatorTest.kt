package com.airfloat.app.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class AppStateCalculatorTest {
    private val zoneId = ZoneId.of("Europe/Bratislava")
    private val nowEpochMs = epoch(2026, 3, 22, 15, 0)

    @Test
    fun firstTimeScenario_buildsBaselineState() {
        val appState = AppStateCalculator.getAppState(emptyList(), timeContext())

        assertNull(appState.lastSession)
        assertEquals(0, appState.currentStreak)
        assertFalse(appState.streakRisk)
        assertEquals("press_barbell", appState.recommendedExercise)
        assertEquals(RecommendedIntensity.NORMAL, appState.recommendedIntensity)
        assertFalse(appState.hasNewPR)
    }

    @Test
    fun activeScenario_recommendsMomentumExercise() {
        val sessions =
            listOf(
                session("bar-1", hoursAgo = 12, exerciseKey = "press_barbell", reps = 20, precision = 92, successful = 20),
                session("bar-2", hoursAgo = 60, exerciseKey = "press_barbell", reps = 20, precision = 92, successful = 20),
                session("push-1", hoursAgo = 96, exerciseKey = "pushup", reps = 18, precision = 82, successful = 18)
            )

        val appState = AppStateCalculator.getAppState(sessions, timeContext())

        assertEquals("press_barbell", appState.lastSession?.exerciseKey)
        assertFalse(appState.streakRisk)
        assertEquals("press_barbell", appState.recommendedExercise)
        assertEquals(RecommendedIntensity.HEAVY, appState.recommendedIntensity)
        assertFalse(appState.hasNewPR)
    }

    @Test
    fun streakRiskScenario_switchesToLightReturn() {
        val sessions =
            listOf(
                session("press-old", hoursAgo = 48, exerciseKey = "press_barbell", reps = 16, precision = 83, successful = 16),
                session("push-old", hoursAgo = 96, exerciseKey = "pushup", reps = 22, precision = 84, successful = 22),
                session("sit-old", hoursAgo = 144, exerciseKey = "situp", reps = 14, precision = 78, successful = 14)
            )

        val appState = AppStateCalculator.getAppState(sessions, timeContext())

        assertTrue(appState.streakRisk)
        assertEquals(RecommendedIntensity.LIGHT, appState.recommendedIntensity)
        assertEquals("situp", appState.recommendedExercise)
        assertFalse(appState.hasNewPR)
    }

    @Test
    fun newPrScenario_surfacesBreakthrough() {
        val sessions =
            listOf(
                session("bar-new", hoursAgo = 10, exerciseKey = "press_barbell", reps = 28, precision = 91, successful = 28),
                session("bar-prev-1", hoursAgo = 72, exerciseKey = "press_barbell", reps = 22, precision = 88, successful = 22),
                session("bar-prev-2", hoursAgo = 120, exerciseKey = "press_barbell", reps = 20, precision = 86, successful = 20),
                session("push-prev", hoursAgo = 168, exerciseKey = "pushup", reps = 18, precision = 80, successful = 18)
            )

        val appState = AppStateCalculator.getAppState(sessions, timeContext())

        assertTrue(appState.hasNewPR)
        assertEquals("press_barbell", appState.recommendedExercise)
        assertEquals(RecommendedIntensity.HEAVY, appState.recommendedIntensity)
    }

    @Test
    fun seenPrScenario_clearsNewPrFlagAfterProgressRead() {
        val sessions =
            listOf(
                session("bar-new", hoursAgo = 10, exerciseKey = "press_barbell", reps = 28, precision = 91, successful = 28),
                session("bar-prev-1", hoursAgo = 72, exerciseKey = "press_barbell", reps = 22, precision = 88, successful = 22),
                session("bar-prev-2", hoursAgo = 120, exerciseKey = "press_barbell", reps = 20, precision = 86, successful = 20)
            )

        val latestEndMs = sessions.maxOf { it.timestampMs + it.durationMs }
        val appState =
            AppStateCalculator.getAppState(
                sessions = sessions,
                timeContext = timeContext(),
                lastProgressRead = latestEndMs
            )

        assertFalse(appState.hasNewPR)
        assertEquals(latestEndMs, appState.lastProgressRead)
    }

    @Test
    fun restSignalScenario_prefersRecoveryRead() {
        val nightContext = timeContext(now = epoch(2026, 3, 22, 23, 0))
        val sessions =
            listOf(
                session("bar-heavy", hoursAgo = 8, exerciseKey = "press_barbell", reps = 20, precision = 95, successful = 20, now = nightContext.nowEpochMs),
                session("bar-prev", hoursAgo = 36, exerciseKey = "press_barbell", reps = 20, precision = 95, successful = 20, now = nightContext.nowEpochMs),
                session("push-prev", hoursAgo = 60, exerciseKey = "pushup", reps = 18, precision = 88, successful = 18, now = nightContext.nowEpochMs),
                session("sit-prev", hoursAgo = 84, exerciseKey = "situp", reps = 12, precision = 76, successful = 12, now = nightContext.nowEpochMs)
            )

        val appState = AppStateCalculator.getAppState(sessions, nightContext)

        assertEquals(TimeOfDaySignal.NIGHT, appState.timeContext.timeOfDay)
        assertEquals(RecommendedIntensity.LIGHT, appState.recommendedIntensity)
        assertEquals("situp", appState.recommendedExercise)
        assertFalse(appState.streakRisk)
    }

    private fun timeContext(now: Long = nowEpochMs): AppTimeContext =
        AppTimeContext(nowEpochMs = now, zoneId = zoneId)

    private fun epoch(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int
    ): Long =
        ZonedDateTime.of(
            LocalDateTime.of(year, month, day, hour, minute),
            zoneId
        ).toInstant().toEpochMilli()

    private fun session(
        id: String,
        hoursAgo: Long,
        exerciseKey: String,
        reps: Int,
        precision: Int,
        successful: Int,
        now: Long = nowEpochMs
    ): WorkoutSessionRecord =
        WorkoutSessionRecord(
            id = id,
            timestampMs = now - (hoursAgo * 60L * 60L * 1000L),
            exerciseKey = exerciseKey,
            presetKey = exerciseKey,
            goalReps = reps,
            completed = true,
            reps = reps,
            successfulAttempts = successful,
            failedAttempts = (reps - successful).coerceAtLeast(0),
            durationMs = 14L * 60L * 1000L,
            estimatedKcal = reps * 0.42f,
            completionRate = precision
        )
}
