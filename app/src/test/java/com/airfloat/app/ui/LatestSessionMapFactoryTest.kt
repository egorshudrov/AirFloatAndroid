package com.airfloat.app.ui

import com.airfloat.app.stats.WorkoutSessionAttemptRecord
import com.airfloat.app.stats.WorkoutSessionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class LatestSessionMapFactoryTest {
    private val zoneId: ZoneId = ZoneId.of("Europe/Bratislava")

    @Test
    fun auto_selects_last_miss_when_session_has_misses() {
        val session =
            session(
                id = "with_miss",
                completion = 78,
                attempts =
                    listOf(
                        attempt(1, 1, true, "First clean lock."),
                        attempt(2, 2, true, "Second clean lock."),
                        attempt(3, 2, false, "Elbows drifted out under fatigue."),
                        attempt(4, 3, true, "Recovered on the next rep."),
                        attempt(5, 3, false, "Final rep broke bar path.")
                    )
            )

        val model = LatestSessionMapFactory.build(session, zoneId)

        println("MAP_WITH_MISS=$model")

        assertFalse(model.isLegacy)
        assertEquals(4, model.selectedIndex)
        assertEquals("ATTEMPT 05", model.selectedAttempt.title)
        assertEquals("MISS", model.selectedAttempt.badge)
        assertEquals(LatestAttemptTone.MISS, model.selectedAttempt.tone)
    }

    @Test
    fun auto_selects_last_clean_when_session_has_no_misses() {
        val session =
            session(
                id = "no_miss",
                completion = 96,
                attempts =
                    listOf(
                        attempt(1, 1, true, "Strong opening rep."),
                        attempt(2, 2, true, "Tempo stayed locked."),
                        attempt(3, 3, true, "Best rep of the set.")
                    )
            )

        val model = LatestSessionMapFactory.build(session, zoneId)

        println("MAP_WITHOUT_MISS=$model")

        assertFalse(model.isLegacy)
        assertEquals(2, model.selectedIndex)
        assertEquals("ATTEMPT 03", model.selectedAttempt.title)
        assertEquals("CLEAN", model.selectedAttempt.badge)
        assertEquals(LatestAttemptTone.CLEAN, model.selectedAttempt.tone)
        assertTrue(model.sessionMeta.contains("3 ATTEMPTS"))
    }

    private fun session(
        id: String,
        completion: Int,
        attempts: List<WorkoutSessionAttemptRecord>
    ): WorkoutSessionRecord =
        WorkoutSessionRecord(
            id = id,
            timestampMs = LocalDate.of(2026, 3, 23).atStartOfDay(zoneId).toInstant().toEpochMilli(),
            exerciseKey = "press",
            presetKey = "press_barbell",
            goalReps = 5,
            completed = completion >= 80,
            reps = attempts.count { it.success },
            successfulAttempts = attempts.count { it.success },
            failedAttempts = attempts.count { !it.success },
            durationMs = 41_000L,
            estimatedKcal = 12.4f,
            completionRate = completion,
            attempts = attempts
        )

    private fun attempt(
        index: Int,
        repSnapshot: Int,
        success: Boolean,
        detail: String
    ): WorkoutSessionAttemptRecord =
        WorkoutSessionAttemptRecord(
            index = index,
            repSnapshot = repSnapshot,
            success = success,
            elapsedMs = index * 7_500L,
            estimatedKcal = index * 1.12f,
            detail = detail
        )
}
