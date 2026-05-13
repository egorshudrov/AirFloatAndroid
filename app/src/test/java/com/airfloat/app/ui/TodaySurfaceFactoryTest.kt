package com.airfloat.app.ui

import com.airfloat.app.stats.AppState
import com.airfloat.app.stats.LiveContextSnapshot
import com.airfloat.app.stats.RecommendedIntensity
import com.airfloat.app.stats.SessionSummary
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class TodaySurfaceFactoryTest {
    private val zoneId = ZoneId.of("Europe/Bratislava")
    private val nowEpochMs = 1_774_176_800_000L

    @Test
    fun build_formatsDateFromProvidedZoneAndTime() {
        val state =
            TodaySurfaceFactory.build(
                appState = baselineAppState(),
                sessions = emptyList(),
                zoneId = zoneId,
                nowEpochMs = nowEpochMs
            )

        assertEquals("MAR", state.dateMonthLabel)
        assertEquals("22", state.dateDayLabel)
    }

    @Test
    fun build_returnsProvidedAppStateForRendering() {
        val expectedState =
            baselineAppState(
                lastSession =
                    SessionSummary(
                        id = "session-1",
                        exerciseKey = "pushup",
                        completed = true,
                        reps = 24,
                        precision = 91,
                        failedAttempts = 1,
                        successfulAttempts = 24,
                        endTimeMs = nowEpochMs - (3L * 60L * 60L * 1000L)
                    ),
                currentStreak = 5,
                recommendedExercise = "pushup",
                recommendedIntensity = RecommendedIntensity.HEAVY,
                hasNewPR = true,
                lastProgressRead = nowEpochMs - 1_000L,
                currentRank = "ELITE",
                avgPrecision = 88,
                precisionDeltaThisWeek = 6,
                sessionStreak = 4,
                qualityStreak = 3,
                hasEliteSession = true,
                eliteSessionCount = 2,
                exercisePrecision = mapOf("pushup" to 91),
                exerciseLastSeen = mapOf("pushup" to "TODAY"),
                exerciseRole = mapOf("pushup" to "recommended")
            )

        val state =
            TodaySurfaceFactory.build(
                appState = expectedState,
                sessions = emptyList(),
                zoneId = zoneId,
                nowEpochMs = nowEpochMs
            )

        assertEquals(expectedState, state.appState)
    }

    @Test
    fun build_keepsDateOutputStableRegardlessOfSessions() {
        val sessions =
            listOf(
                session("bar-1", hoursAgo = 12, presetKey = HomeFragment.LAUNCH_PRESS_BARBELL, precision = 92),
                session("push-1", hoursAgo = 24, presetKey = HomeFragment.LAUNCH_PUSHUP, precision = 88)
            )

        val state =
            TodaySurfaceFactory.build(
                appState = baselineAppState(recommendedExercise = "press_barbell"),
                sessions = sessions,
                zoneId = zoneId,
                nowEpochMs = nowEpochMs
            )

        assertEquals("MAR", state.dateMonthLabel)
        assertEquals("22", state.dateDayLabel)
        assertEquals("press_barbell", state.appState.recommendedExercise)
    }

    private fun baselineAppState(
        lastSession: SessionSummary? = null,
        currentStreak: Int = 0,
        streakRisk: Boolean = false,
        recommendedExercise: String = "press_barbell",
        recommendedIntensity: RecommendedIntensity = RecommendedIntensity.NORMAL,
        timeContext: LiveContextSnapshot = LiveContextSnapshot(),
        hasNewPR: Boolean = false,
        lastProgressRead: Long = 0L,
        currentRank: String = "RAW",
        avgPrecision: Int = 0,
        precisionDeltaThisWeek: Int = 0,
        sessionStreak: Int = 0,
        qualityStreak: Int = 0,
        hasEliteSession: Boolean = false,
        eliteSessionCount: Int = 0,
        exercisePrecision: Map<String, Int> = emptyMap(),
        exerciseLastSeen: Map<String, String> = emptyMap(),
        exerciseRole: Map<String, String> = emptyMap()
    ): AppState =
        AppState(
            lastSession = lastSession,
            currentStreak = currentStreak,
            streakRisk = streakRisk,
            recommendedExercise = recommendedExercise,
            recommendedIntensity = recommendedIntensity,
            timeContext = timeContext,
            hasNewPR = hasNewPR,
            lastProgressRead = lastProgressRead,
            currentRank = currentRank,
            avgPrecision = avgPrecision,
            precisionDeltaThisWeek = precisionDeltaThisWeek,
            sessionStreak = sessionStreak,
            qualityStreak = qualityStreak,
            hasEliteSession = hasEliteSession,
            eliteSessionCount = eliteSessionCount,
            exercisePrecision = exercisePrecision,
            exerciseLastSeen = exerciseLastSeen,
            exerciseRole = exerciseRole
        )

    private fun session(
        id: String,
        hoursAgo: Long,
        presetKey: String,
        reps: Int = 20,
        precision: Int
    ) = com.airfloat.app.stats.WorkoutSessionRecord(
        id = id,
        timestampMs = nowEpochMs - (hoursAgo * 60L * 60L * 1000L),
        exerciseKey = presetKey,
        presetKey = presetKey,
        goalReps = reps,
        completed = true,
        reps = reps,
        successfulAttempts = reps,
        failedAttempts = 0,
        durationMs = 12L * 60L * 1000L,
        estimatedKcal = reps * 0.4f,
        completionRate = precision
    )
}
