package com.airfloat.app.squat

import android.graphics.PointF
import android.util.Log
import com.airfloat.app.pose.ConditionCode
import com.airfloat.app.pose.RepRejectReason
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class SquatCounterResult(
    val reps: Int,
    val progress: Float,
    val conditionCode: ConditionCode,
    val repRejectReason: RepRejectReason? = null,
    val isCycleActive: Boolean = false,
    val lastRepAtMs: Long = 0L
)

/**
 * Lightweight squat rep FSM based on knee angles (MediaPipe landmarks).
 * Phase order: top -> down -> bottom -> top.
 */
class SquatCounter(
    private val topThresholdDeg: Float = 145f,
    private val bottomThresholdDeg: Float = 100f,
    private val minRepDurationMs: Long = 400L,
    private val minDepthTravelDeg: Float = 26f,
    private val minBottomTravelDeg: Float = 40f,
    private val minBothSidesTravelDeg: Float = 20f,
    private val minSymmetryRatio: Float = 0.38f,
    private val minSymmetryBothTravelDeg: Float = 30f,
    private val minAsymmetryCheckTravelDeg: Float = 35f,
    private val emaAlpha: Float = 0.35f,
    private val trackingLostFramesForCondition: Int = 3,
    private val minCycleFrames: Int = 6,
    private val minInterRepGapMs: Long = 320L,
    private val startCycleDeltaDeg: Float = 8f,
    private val minStartDropPerFrameDeg: Float = 0.6f,
    private val minTopReadyAngleDeg: Float = 132f,
    private val topReadySlackDeg: Float = 8f,
    private val startDebounceFrames: Int = 2,
    private val startAsymmetryWindowFrames: Int = 4,
    private val startAsymmetryTravelDeg: Float = 20f,
    private val startAsymmetryMinRatio: Float = 0.28f
) {

    private enum class Phase {
        IDLE_TOP,
        DESCENDING,
        ASCENDING
    }

    // BlazePose landmarks
    private val lHip = 23
    private val rHip = 24
    private val lKnee = 25
    private val rKnee = 26
    private val lAnkle = 27
    private val rAnkle = 28

    private var reps = 0
    private var phase = Phase.IDLE_TOP
    private var emaLeft: Float? = null
    private var emaRight: Float? = null
    private var trackingGapFrames = 0
    private var hasSeenValidPose = false

    private var cycleStartTs = 0L
    private var cycleStartAvg = 0f
    private var cycleStartLeft = 0f
    private var cycleStartRight = 0f
    private var cycleMinLeft = Float.POSITIVE_INFINITY
    private var cycleMinRight = Float.POSITIVE_INFINITY
    private var cycleFrameCount = 0
    private var lastCycleEndTs = 0L
    private var lastRepTs = 0L
    private var cycleStartedAsymmetric = false
    private var cycleReachedBottomByAbsolute = false
    private var prevDrive: Float? = null
    private var topReady = false
    private var startBelowThresholdFrames = 0

    fun reset() {
        reps = 0
        phase = Phase.IDLE_TOP
        emaLeft = null
        emaRight = null
        trackingGapFrames = 0
        hasSeenValidPose = false
        lastCycleEndTs = 0L
        lastRepTs = 0L
        cycleStartedAsymmetric = false
        prevDrive = null
        topReady = false
        startBelowThresholdFrames = 0
        resetCycle()
    }

    fun update(normPoints: List<PointF>, nowTs: Long): SquatCounterResult {
        val angles = extractKneeAngles(normPoints)
        if (angles == null) {
            onTrackingGap()
            return SquatCounterResult(
                reps = reps,
                progress = 0f,
                conditionCode = if (trackingGapFrames >= trackingLostFramesForCondition) {
                    ConditionCode.TRACKING_LOST
                } else {
                    ConditionCode.BAD_START
                }
            )
        }

        trackingGapFrames = 0
        hasSeenValidPose = true

        val (leftRaw, rightRaw) = angles
        val left = ema(emaLeft, leftRaw, emaAlpha)
        val right = ema(emaRight, rightRaw, emaAlpha)
        emaLeft = left
        emaRight = right
        // For 30-45deg camera angle one leg is often noisier; use the bent leg as cycle driver.
        val drive = min(left, right)
        val progress = angleToProgress(drive)

        var rejectReason: RepRejectReason? = null

        when (phase) {
            Phase.IDLE_TOP -> {
                // Start a cycle when we leave top stance with meaningful movement.
                if (drive >= minTopReadyAngleDeg) {
                    topReady = true
                }
                val gapOk = (nowTs - lastCycleEndTs) >= minInterRepGapMs
                val descending = prevDrive?.let { (it - drive) >= minStartDropPerFrameDeg } ?: false
                val nearTop = drive >= (minTopReadyAngleDeg - topReadySlackDeg)
                val canStart =
                    topReady &&
                        gapOk &&
                        descending &&
                        drive <= topThresholdDeg - startCycleDeltaDeg
                if (nearTop) {
                    startBelowThresholdFrames = 0
                } else if (canStart) {
                    startBelowThresholdFrames += 1
                } else {
                    startBelowThresholdFrames = 0
                }
                if (startBelowThresholdFrames >= startDebounceFrames) {
                    phase = Phase.DESCENDING
                    cycleStartTs = nowTs
                    cycleStartAvg = drive
                    cycleStartLeft = left
                    cycleStartRight = right
                    cycleMinLeft = left
                    cycleMinRight = right
                    cycleFrameCount = 1
                    startBelowThresholdFrames = 0
                }
            }

            Phase.DESCENDING -> {
                cycleFrameCount += 1
                if (left < cycleMinLeft) cycleMinLeft = left
                if (right < cycleMinRight) cycleMinRight = right
                updateStartAsymmetryLatch(left, right)

                val reachedBottomByAbsolute = drive <= bottomThresholdDeg
                val reachedBottomByTravel = (cycleStartAvg - drive) >= minBottomTravelDeg
                if (reachedBottomByAbsolute || reachedBottomByTravel) {
                    cycleReachedBottomByAbsolute = reachedBottomByAbsolute
                    phase = Phase.ASCENDING
                } else if (drive >= cycleStartAvg - 2f) {
                    if (cycleStartedAsymmetric) {
                        Log.i(
                            "AirFloatTune",
                            "squat cycleIgnored reason=EARLY_ASYMMETRY travelL=${fmt1((cycleStartLeft - cycleMinLeft).coerceAtLeast(0f))} travelR=${fmt1((cycleStartRight - cycleMinRight).coerceAtLeast(0f))}"
                        )
                    } else {
                        // Returned to top before bottom: attempt failed if travel was meaningful.
                        rejectReason = validateAttempt(nowTs, reachedBottom = false)
                    }
                    phase = Phase.IDLE_TOP
                    lastCycleEndTs = nowTs
                    topReady = drive >= minTopReadyAngleDeg
                    resetCycle()
                }
            }

            Phase.ASCENDING -> {
                cycleFrameCount += 1
                if (left < cycleMinLeft) cycleMinLeft = left
                if (right < cycleMinRight) cycleMinRight = right
                updateStartAsymmetryLatch(left, right)

                val returnedNearStart = drive >= (cycleStartAvg - 2f)
                if (returnedNearStart) {
                    if (cycleStartedAsymmetric) {
                        Log.i(
                            "AirFloatTune",
                            "squat cycleIgnored reason=EARLY_ASYMMETRY travelL=${fmt1((cycleStartLeft - cycleMinLeft).coerceAtLeast(0f))} travelR=${fmt1((cycleStartRight - cycleMinRight).coerceAtLeast(0f))}"
                        )
                    } else {
                        val reject = validateAttempt(nowTs, reachedBottom = true)
                        if (reject == null) {
                            reps += 1
                            lastRepTs = nowTs
                            logRep()
                        } else {
                            rejectReason = reject
                        }
                    }
                    phase = Phase.IDLE_TOP
                    lastCycleEndTs = nowTs
                    topReady = drive >= minTopReadyAngleDeg
                    resetCycle()
                }
            }
        }
        prevDrive = drive

        val condition = when {
            rejectReason == RepRejectReason.INSUFFICIENT_TOP -> ConditionCode.RANGE_TOO_SMALL
            !hasSeenValidPose -> ConditionCode.BAD_START
            else -> ConditionCode.OK
        }

        return SquatCounterResult(
            reps = reps,
            progress = progress,
            conditionCode = condition,
            repRejectReason = rejectReason,
            isCycleActive = phase != Phase.IDLE_TOP,
            lastRepAtMs = lastRepTs
        )
    }

    private fun onTrackingGap() {
        trackingGapFrames += 1
        phase = Phase.IDLE_TOP
        prevDrive = null
        topReady = false
        startBelowThresholdFrames = 0
        resetCycle()
    }

    private fun resetCycle() {
        cycleStartTs = 0L
        cycleStartAvg = 0f
        cycleStartLeft = 0f
        cycleStartRight = 0f
        cycleMinLeft = Float.POSITIVE_INFINITY
        cycleMinRight = Float.POSITIVE_INFINITY
        cycleFrameCount = 0
        cycleStartedAsymmetric = false
        cycleReachedBottomByAbsolute = false
    }

    private fun validateAttempt(nowTs: Long, reachedBottom: Boolean): RepRejectReason? {
        if (!cycleMinLeft.isFinite() || !cycleMinRight.isFinite()) {
            return RepRejectReason.TRACKING_LOST
        }

        // Ignore micro-cycles from jitter before duration-based rejection.
        if (cycleFrameCount < minCycleFrames) {
            Log.i(
                "AirFloatTune",
                "squat repRejected reason=TOO_FAST frames=$cycleFrameCount minFrames=$minCycleFrames"
            )
            return RepRejectReason.TOO_FAST
        }

        val durationMs = if (cycleStartTs > 0L) nowTs - cycleStartTs else Long.MAX_VALUE
        if (durationMs in 1 until minRepDurationMs) {
            Log.i("AirFloatTune", "squat repRejected reason=TOO_FAST durationMs=$durationMs minMs=$minRepDurationMs")
            return RepRejectReason.TOO_FAST
        }

        val travelLeft = (cycleStartLeft - cycleMinLeft).coerceAtLeast(0f)
        val travelRight = (cycleStartRight - cycleMinRight).coerceAtLeast(0f)
        val hi = max(travelLeft, travelRight)
        val lo = min(travelLeft, travelRight)

        if (!reachedBottom || hi < minDepthTravelDeg) {
            Log.i(
                "AirFloatTune",
                "squat repRejected reason=INSUFFICIENT_DEPTH travelL=${fmt1(travelLeft)} travelR=${fmt1(travelRight)} minDepth=${fmt1(minDepthTravelDeg)} reachedBottom=$reachedBottom"
            )
            return RepRejectReason.INSUFFICIENT_TOP
        }

        val driveTravel = (cycleStartAvg - min(cycleMinLeft, cycleMinRight)).coerceAtLeast(0f)
        if (!cycleReachedBottomByAbsolute && driveTravel < minBottomTravelDeg) {
            Log.i(
                "AirFloatTune",
                "squat repRejected reason=INSUFFICIENT_DEPTH driveTravel=${fmt1(driveTravel)} minTravelBottom=${fmt1(minBottomTravelDeg)} reachedBottomByAbs=false"
            )
            return RepRejectReason.INSUFFICIENT_TOP
        }

        if (lo < minBothSidesTravelDeg) {
            Log.i(
                "AirFloatTune",
                "squat repRejected reason=ASYMMETRIC_RANGE travelL=${fmt1(travelLeft)} travelR=${fmt1(travelRight)} minBoth=${fmt1(minBothSidesTravelDeg)}"
            )
            return RepRejectReason.ASYMMETRIC_RANGE
        }

        val ratio = lo / hi.coerceAtLeast(1f)
        // Asymmetry is meaningful only on medium/full reps; short travel is too noisy.
        // Check asymmetry only when both sides had meaningful travel.
        val minTravelForAsymmetry = max(minDepthTravelDeg, minSymmetryBothTravelDeg)
        if (hi >= minAsymmetryCheckTravelDeg && lo >= minTravelForAsymmetry && ratio < minSymmetryRatio) {
            Log.i(
                "AirFloatTune",
                "squat repRejected reason=ASYMMETRIC_RANGE travelL=${fmt1(travelLeft)} travelR=${fmt1(travelRight)} ratio=${fmt2(ratio)} minRatio=${fmt2(minSymmetryRatio)} minCheckTravel=${fmt1(minAsymmetryCheckTravelDeg)} minBothTravel=${fmt1(minTravelForAsymmetry)}"
            )
            return RepRejectReason.ASYMMETRIC_RANGE
        }

        return null
    }

    private fun logRep() {
        val ampL = cycleStartLeft - cycleMinLeft
        val ampR = cycleStartRight - cycleMinRight
        Log.i(
            "AirFloatTune",
            "squat rep=$reps minL=${fmt1(cycleMinLeft)} maxL=${fmt1(cycleStartLeft)} ampL=${fmt1(ampL)} minR=${fmt1(cycleMinRight)} maxR=${fmt1(cycleStartRight)} ampR=${fmt1(ampR)} top=${fmt1(topThresholdDeg)} bottom=${fmt1(bottomThresholdDeg)}"
        )
    }

    private fun updateStartAsymmetryLatch(currentLeft: Float, currentRight: Float) {
        if (cycleStartedAsymmetric) return
        if (cycleFrameCount <= 0 || cycleFrameCount > startAsymmetryWindowFrames) return

        val travelLeft = (cycleStartLeft - currentLeft).coerceAtLeast(0f)
        val travelRight = (cycleStartRight - currentRight).coerceAtLeast(0f)
        val hi = max(travelLeft, travelRight)
        if (hi < startAsymmetryTravelDeg) return

        val lo = min(travelLeft, travelRight)
        val ratio = lo / hi.coerceAtLeast(1f)
        if (ratio < startAsymmetryMinRatio) {
            cycleStartedAsymmetric = true
            Log.i(
                "AirFloatTune",
                "squat earlyAsymmetry latched travelL=${fmt1(travelLeft)} travelR=${fmt1(travelRight)} ratio=${fmt2(ratio)} frames=$cycleFrameCount"
            )
        }
    }

    private fun angleToProgress(avgKneeAngle: Float): Float {
        val span = (topThresholdDeg - bottomThresholdDeg).coerceAtLeast(1f)
        return ((avgKneeAngle - bottomThresholdDeg) / span).coerceIn(0f, 1f)
    }

    private fun extractKneeAngles(points: List<PointF>): Pair<Float, Float>? {
        if (points.size <= rAnkle) return null
        val left = angleDeg(points[lHip], points[lKnee], points[lAnkle]) ?: return null
        val right = angleDeg(points[rHip], points[rKnee], points[rAnkle]) ?: return null
        return left to right
    }

    private fun angleDeg(a: PointF, b: PointF, c: PointF): Float? {
        val bax = a.x - b.x
        val bay = a.y - b.y
        val bcx = c.x - b.x
        val bcy = c.y - b.y
        val normBA = sqrt(bax * bax + bay * bay)
        val normBC = sqrt(bcx * bcx + bcy * bcy)
        if (normBA < 1e-6f || normBC < 1e-6f) return null
        var cosTheta = (bax * bcx + bay * bcy) / (normBA * normBC)
        cosTheta = cosTheta.coerceIn(-1f, 1f)
        return (acos(cosTheta) * (180f / Math.PI.toFloat()))
    }

    private fun ema(prev: Float?, newValue: Float, alpha: Float): Float {
        if (prev == null) return newValue
        return alpha * newValue + (1f - alpha) * prev
    }

    private fun fmt1(v: Float): String = String.format(java.util.Locale.US, "%.1f", v)
    private fun fmt2(v: Float): String = String.format(java.util.Locale.US, "%.2f", v)
}
