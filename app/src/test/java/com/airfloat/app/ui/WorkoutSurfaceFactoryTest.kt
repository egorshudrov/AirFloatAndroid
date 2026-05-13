package com.airfloat.app.ui

import com.airfloat.app.stats.AppState
import com.airfloat.app.stats.LiveContextSnapshot
import com.airfloat.app.stats.RecommendedIntensity
import com.airfloat.app.stats.WorkoutSessionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class WorkoutSurfaceFactoryTest {
    private val nowEpochMs = 1_774_176_800_000L
    private val zoneId = ZoneId.of("Europe/Bratislava")

    @Test
    fun build_fromTodayEntry_setsStartButtonFromRecommendedIntensity() {
        val sessions =
            listOf(
                session("press-1", hoursAgo = 10, presetKey = WorkoutFragment.SOURCE_PRESS_BARBELL, reps = 18, precision = 84),
                session("push-1", hoursAgo = 32, presetKey = WorkoutFragment.SOURCE_PUSHUP, reps = 22, precision = 78)
            )
        val state =
            WorkoutSurfaceFactory.build(
                subtitleText = "Start here",
                qualityButtonText = "Exercise AI: Off",
                qualityButtonVisible = false,
                qualityButtonEnabled = false,
                recordButtonText = "Record: Off",
                repsEnabled = true,
                repsHint = "Reps",
                sessions = sessions,
                appState =
                    AppState(
                        lastSession = null,
                        currentStreak = 4,
                        streakRisk = false,
                        recommendedExercise = "press_barbell",
                        recommendedIntensity = RecommendedIntensity.HEAVY,
                        timeContext = LiveContextSnapshot(),
                        hasNewPR = false,
                        lastProgressRead = nowEpochMs
                    ),
                todayEntryPresetKey = HomeFragment.LAUNCH_PRESS_BARBELL,
                selectedPresetKey = WorkoutFragment.SOURCE_PRESS_BARBELL,
                zoneId = zoneId
            )

        assertEquals("Start here", state.subtitleText)
        assertEquals("Start Session", state.startButtonText)
        assertEquals("BARBELL PRESS", state.selectedExerciseLabel)
        assertEquals(2, state.recentSessions.size)
        assertEquals("BARBELL PRESS", state.recentSessions.first().title)
        assertTrue(state.recentSessions.first().meta.contains("22 MAR"))
        assertTrue(state.recentSessions.first().meta.contains("18 REPS"))
    }

    private fun session(
        id: String,
        hoursAgo: Long,
        presetKey: String,
        reps: Int,
        precision: Int
    ): WorkoutSessionRecord =
        WorkoutSessionRecord(
            id = id,
            timestampMs = nowEpochMs - (hoursAgo * 60L * 60L * 1000L),
            exerciseKey = presetKey,
            presetKey = presetKey,
            goalReps = reps,
            completed = true,
            reps = reps,
            successfulAttempts = reps,
            failedAttempts = 0,
            durationMs = 14L * 60L * 1000L,
            estimatedKcal = reps * 0.41f,
            completionRate = precision
        )
}
