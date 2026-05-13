package com.airfloat.app.stats

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressSectionRankerTest {
    @Test
    fun ranks_sections_for_three_signal_scenarios() {
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

        val rankedA = ProgressSectionRanker.rankSections(scenarioA)
        val rankedB = ProgressSectionRanker.rankSections(scenarioB)
        val rankedC = ProgressSectionRanker.rankSections(scenarioC)

        println("RANK_SCENARIO_A=$rankedA")
        println("RANK_SCENARIO_B=$rankedB")
        println("RANK_SCENARIO_C=$rankedC")

        assertEquals(ProgressSectionKey.EXERCISE_LENS, rankedA.first().key)
        assertEquals(ProgressSectionKey.CONSISTENCY_HEATMAP, rankedB.first().key)
        assertEquals(ProgressSectionKey.LATEST_SESSION_MAP, rankedC.first().key)
        assertEquals(ProgressSectionKey.BODY_METRICS, rankedC[1].key)
        assertEquals(ProgressSectionKey.OUTPUT_TREND, rankedC[2].key)
    }
}
