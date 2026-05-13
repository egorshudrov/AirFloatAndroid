package com.airfloat.app.situp

import android.graphics.PointF
import com.airfloat.app.pose.ConditionCode
import com.airfloat.app.pose.RepRejectReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

@Ignore("Synthetic android.graphics.PointF motion is unreliable in local JVM unit tests.")
class SitupCounterTest {

    @Test
    fun fullRep_countsOnce() {
        val counter = syntheticCounter()
        val timeline =
            listOf(
                170f, 170f, 170f,
                160f, 150f, 140f, 130f, 120f, 110f, 100f,
                110f, 120f, 130f, 140f, 150f, 160f, 170f, 170f
            )

        val results = runSequence(counter, timeline, frameStepMs = 80L)
        assertEquals(1, results.last().reps)
        assertTrue(results.any { it.reps == 1 })
    }

    @Test
    fun shortIncompleteAttempt_isRejectedAndNotCounted() {
        val counter = syntheticCounter()
        val timeline =
            listOf(
                145f, 145f, 145f,
                138f, 132f, 128f, 124f, 120f,
                124f, 128f, 132f, 136f, 140f, 145f, 145f
            )

        val results = runSequence(counter, timeline, frameStepMs = 80L)
        assertEquals(0, results.last().reps)
        assertTrue(results.any { it.repRejectReason == RepRejectReason.INSUFFICIENT_TOP })
        assertTrue(results.any { it.conditionCode == ConditionCode.RANGE_TOO_SMALL })
    }

    private fun syntheticCounter(): SitupCounter =
        SitupCounter(
            emaAlpha = 1f,
            startDebounceFrames = 1,
            minStartDropPerFrameDeg = 0.1f,
            minCycleFrames = 4
        )

    private fun runSequence(
        counter: SitupCounter,
        driveAnglesDeg: List<Float>,
        frameStepMs: Long
    ): List<SitupCounterResult> {
        var nowMs = 0L
        return driveAnglesDeg.map { drive ->
            val result = counter.update(normPointsForAngles(drive, drive), nowMs)
            nowMs += frameStepMs
            result
        }
    }

    private fun normPointsForAngles(leftHipAngleDeg: Float, rightHipAngleDeg: Float): List<PointF> {
        val points = MutableList(33) { PointF(0f, 0f) }
        writeHipAngle(points, shoulderIdx = 11, hipIdx = 23, kneeIdx = 25, hipX = 0.35f, hipY = 0.55f, angleDeg = leftHipAngleDeg)
        writeHipAngle(points, shoulderIdx = 12, hipIdx = 24, kneeIdx = 26, hipX = 0.65f, hipY = 0.55f, angleDeg = rightHipAngleDeg)
        return points
    }

    private fun writeHipAngle(
        points: MutableList<PointF>,
        shoulderIdx: Int,
        hipIdx: Int,
        kneeIdx: Int,
        hipX: Float,
        hipY: Float,
        angleDeg: Float
    ) {
        val radius = 0.1f
        val radians = Math.toRadians(angleDeg.toDouble())
        val shoulder = PointF(hipX + radius, hipY)
        val knee =
            PointF(
                (hipX + cos(radians).toFloat() * radius),
                (hipY + sin(radians).toFloat() * radius)
            )
        points[shoulderIdx] = shoulder
        points[hipIdx] = PointF(hipX, hipY)
        points[kneeIdx] = knee
    }
}
