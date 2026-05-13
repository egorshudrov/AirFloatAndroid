package com.airfloat.app.stats

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveCoachingCalculatorStateSnapshotTest {
    @Test
    fun builds_adaptive_coach_for_three_ranker_scenarios() {
        val scenarioA =
            StateSnapshot(
                lastSessionExercise = "press_barbell",
                lastSessionQuality = LastSessionQuality.GREAT,
                currentStreak = 5,
                streakRisk = false,
                bestExercise = "press_barbell",
                weakestExercise = "situp",
                loadTrend = LoadTrend.RISING,
                bodyRecompSignal = BodyRecompSignal.NONE,
                hasNewPR = true,
                daysSinceLastCheckIn = 2
            )
        val scenarioB =
            StateSnapshot(
                lastSessionExercise = "pushup",
                lastSessionQuality = LastSessionQuality.POOR,
                currentStreak = 4,
                streakRisk = true,
                bestExercise = "pushup",
                weakestExercise = "squat_beta",
                loadTrend = LoadTrend.FALLING,
                bodyRecompSignal = BodyRecompSignal.NONE,
                hasNewPR = false,
                daysSinceLastCheckIn = 3
            )
        val scenarioC =
            StateSnapshot(
                lastSessionExercise = "press_dumbbell",
                lastSessionQuality = LastSessionQuality.OK,
                currentStreak = 1,
                streakRisk = false,
                bestExercise = "press_dumbbell",
                weakestExercise = "situp",
                loadTrend = LoadTrend.PLATEAU,
                bodyRecompSignal = BodyRecompSignal.NONE,
                hasNewPR = false,
                daysSinceLastCheckIn = 10
            )

        val coachA = AdaptiveCoachingCalculator.buildSnapshot(scenarioA)
        val coachB = AdaptiveCoachingCalculator.buildSnapshot(scenarioB)
        val coachC = AdaptiveCoachingCalculator.buildSnapshot(scenarioC)

        println("COACH_SCENARIO_A=$coachA")
        println("COACH_SCENARIO_B=$coachB")
        println("COACH_SCENARIO_C=$coachC")

        assertEquals("✦ NEW MILESTONE", coachA.headline)
        assertEquals("⚠ STREAK AT RISK", coachB.headline)
        assertEquals("ADAPTIVE COACH", coachC.headline)
    }
}
