package com.airfloat.app.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StateSnapshotCalculatorTest {
    private val zoneId: ZoneId = ZoneId.of("Europe/Bratislava")
    private val today: LocalDate = LocalDate.of(2026, 3, 23)
    private val nowEpochMs =
        today.atTime(9, 0).atZone(zoneId).toInstant().toEpochMilli()

    @Test
    fun builds_state_snapshot_from_mock_data() {
        val sessions =
            listOf(
                session("s1", "press", "press_barbell", 2026, 3, 22, reps = 128, completion = 94, success = 13, completed = true),
                session("s2", "pushup", "pushup", 2026, 3, 21, reps = 92, completion = 82, success = 9, completed = true),
                session("s3", "press", "press_dumbbell", 2026, 3, 20, reps = 74, completion = 79, success = 8, completed = true),
                session("s4", "situp", "situp", 2026, 3, 19, reps = 66, completion = 76, success = 7, completed = true),
                session("s5", "press", "press_barbell", 2026, 3, 18, reps = 104, completion = 87, success = 10, completed = true),
                session("s6", "squat_beta", "squat_beta", 2026, 3, 17, reps = 38, completion = 61, success = 4, completed = false),
                session("s7", "press", "press_barbell", 2026, 3, 11, reps = 71, completion = 73, success = 7, completed = false),
                session("s8", "pushup", "pushup", 2026, 3, 10, reps = 63, completion = 69, success = 6, completed = false)
            )
        val bodyRecords =
            listOf(
                body("b1", 2026, 3, 14, weight = 84.6f, waist = 84.0f, bodyFat = 17.9f),
                body("b2", 2026, 3, 18, weight = 84.0f, waist = 83.1f, bodyFat = 17.4f),
                body("b3", 2026, 3, 21, weight = 83.7f, waist = 82.3f, bodyFat = 17.0f)
            )

        val snapshot =
            StateSnapshotCalculator.getStateSnapshot(
                sessions = sessions,
                range = ProgressRange.DAYS_7,
                exercise = "press_barbell",
                bodyRecords = bodyRecords,
                zoneId = zoneId,
                nowEpochMs = nowEpochMs,
                today = today
            )

        println("STATE_SNAPSHOT=$snapshot")

        assertEquals("press_barbell", snapshot.lastSessionExercise)
        assertEquals(LastSessionQuality.GREAT, snapshot.lastSessionQuality)
        assertEquals(6, snapshot.currentStreak)
        assertTrue(snapshot.streakRisk)
        assertEquals("press_barbell", snapshot.bestExercise)
        assertEquals("squat_beta", snapshot.weakestExercise)
        assertEquals(LoadTrend.RISING, snapshot.loadTrend)
        assertEquals(BodyRecompSignal.NONE, snapshot.bodyRecompSignal)
        assertTrue(snapshot.hasNewPR)
        assertEquals(2, snapshot.daysSinceLastCheckIn)
        assertEquals(1, snapshot.daysSinceLastSession)
        assertEquals(33, snapshot.hoursSinceLastSession)
        assertEquals(TimeOfDaySignal.MORNING, snapshot.liveContext.timeOfDay)
        assertEquals(DayOfWeekSignal.MONDAY, snapshot.liveContext.dayOfWeek)
    }

    private fun session(
        id: String,
        exerciseKey: String,
        presetKey: String,
        year: Int,
        month: Int,
        day: Int,
        reps: Int,
        completion: Int,
        success: Int,
        completed: Boolean
    ): WorkoutSessionRecord =
        WorkoutSessionRecord(
            id = id,
            timestampMs = LocalDate.of(year, month, day).atStartOfDay(zoneId).toInstant().toEpochMilli(),
            exerciseKey = exerciseKey,
            presetKey = presetKey,
            goalReps = reps,
            completed = completed,
            reps = reps,
            successfulAttempts = success,
            failedAttempts = (reps - success).coerceAtLeast(0),
            durationMs = (reps * 2400L),
            estimatedKcal = reps * 0.35f,
            completionRate = completion
        )

    private fun body(
        id: String,
        year: Int,
        month: Int,
        day: Int,
        weight: Float,
        waist: Float,
        bodyFat: Float
    ): BodyWeightRecord =
        BodyWeightRecord(
            id = id,
            timestampMs = LocalDate.of(year, month, day).atStartOfDay(zoneId).toInstant().toEpochMilli(),
            weightKg = weight,
            waistCm = waist,
            bodyFatPercent = bodyFat
        )
}
