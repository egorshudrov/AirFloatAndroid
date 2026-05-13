package com.airfloat.app.ui

import com.airfloat.app.stats.ProgressSnapshot
import com.airfloat.app.stats.ProgressRange
import org.junit.Assert.assertEquals
import org.junit.Test

class LoadVelocityHeroFactoryTest {
    @Test
    fun builds_three_hero_states() {
        val rising = LoadVelocityHeroFactory.fromSnapshot(snapshot(loadDelta = 18, completionDelta = 12, sessionDelta = 1, repsDelta = 147))
        val falling = LoadVelocityHeroFactory.fromSnapshot(snapshot(loadDelta = -16, completionDelta = -9, sessionDelta = -1, repsDelta = -132))
        val plateau = LoadVelocityHeroFactory.fromSnapshot(snapshot(loadDelta = 3, completionDelta = 0, sessionDelta = 0, repsDelta = 8))

        println("HERO_RISING=$rising")
        println("HERO_FALLING=$falling")
        println("HERO_PLATEAU=$plateau")

        assertEquals(LoadVelocityHeroTone.RISING, rising.tone)
        assertEquals(LoadVelocityHeroTone.FALLING, falling.tone)
        assertEquals(LoadVelocityHeroTone.PLATEAU, plateau.tone)
    }

    private fun snapshot(
        loadDelta: Int,
        completionDelta: Int,
        sessionDelta: Int,
        repsDelta: Int
    ): ProgressSnapshot =
        ProgressSnapshot(
            rangeLabel = "RANGE 7D",
            comparisonLabel = "vs previous 7 days",
            totalSessions = 3,
            totalReps = 847,
            averageRepsPerSession = 282,
            averageCompletionRate = 89,
            totalKcal = 244,
            currentStreakDays = 4,
            activeDays = 3,
            activeDaysTarget = 7,
            precisionSignal = 89,
            sessionDelta = sessionDelta,
            repsDelta = repsDelta,
            completionDelta = completionDelta,
            loadDeltaPercent = loadDelta,
            trendPoints = emptyList(),
            qualityPoints = emptyList(),
            heatmapCells = emptyList(),
            distributionPoints = emptyList(),
            topVolumeBest = null,
            topQualityBest = null,
            topEnduranceBest = null,
            recentSessions = emptyList()
        )
}
