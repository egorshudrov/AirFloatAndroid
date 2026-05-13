package com.airfloat.app.pushup

import android.graphics.PointF
import android.util.Log
import com.airfloat.app.pose.ConditionCode
import com.airfloat.app.pose.RepRejectReason
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class PushupCounterResult(
    val reps: Int,
    val progress: Float,
    val conditionCode: ConditionCode,
    val repRejectReason: RepRejectReason? = null,
    val isCycleActive: Boolean = false,
    val lastRepAtMs: Long = 0L
)

/**
 * Push-up rep FSM based on elbow angles.
 * Sequence: top -> down -> top.
 */
class PushupCounter(
    private val topThresholdDeg: Float = 142f,
    private val bottomThresholdDeg: Float = 98f,
    private val minBottomElbowDeg: Float = 35f,
    private val minLegExtensionDeg: Float = 140f,
    private val minRepDurationMs: Long = 300L,
    private val minDepthTravelDeg: Float = 22f,
    private val minBottomTravelDeg: Float = 36f,
    private val minBothSidesTravelDeg: Float = 10f,
    private val minSymmetryRatio: Float = 0.40f,
    private val minAsymmetryCheckTravelDeg: Float = 30f,
    private val emaAlpha: Float = 0.35f,
    private val trackingLostFramesForCondition: Int = 3,
    private val maxTrackingGapFramesBeforeReset: Int = 20,
    private val minCycleFrames: Int = 4,
    private val minInterRepGapMs: Long = 300L,
    private val startCycleDeltaDeg: Float = 6f,
    private val completionReturnSlackDeg: Float = 6f,
    private val startDebounceFrames: Int = 1,
    private val minStartDropPerFrameDeg: Float = 0.6f,
    private val minAttemptTravelForRejectDeg: Float = 10f,
    private val startAsymmetryWindowFrames: Int = 3,
    private val startAsymmetryTravelDeg: Float = 24f,
    private val startAsymmetryMinRatio: Float = 0.30f
) {

    private enum class Phase {
        IDLE_TOP,
        DESCENDING,
        ASCENDING
    }

    // BlazePose landmarks
    private val lShoulder = 11
    private val rShoulder = 12
    private val lElbow = 13
    private val rElbow = 14
    private val lWrist = 15
    private val rWrist = 16
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
    private var badStanceFrames = 0
    private val badStanceFramesForCondition = 3
    private var topReady = false
    private val topReadySlackDeg = 5f

    private var cycleStartTs = 0L
    private var cycleStartAvg = 0f
    private var cycleStartLeft = 0f
    private var cycleStartRight = 0f
    private var cycleMinLeft = Float.POSITIVE_INFINITY
    private var cycleMinRight = Float.POSITIVE_INFINITY
    private var cycleFrameCount = 0
    private var lastCycleEndTs = 0L
    private var lastRepTs = 0L
    private var prevDrive: Float? = null
    private var startBelowThresholdFrames = 0
    private var cycleStartedAsymmetric = false
    private var cycleReachedBottomByAbsolute = false

    fun reset() {
        reps = 0
        phase = Phase.IDLE_TOP
        emaLeft = null
        emaRight = null
        trackingGapFrames = 0
        hasSeenValidPose = false
        badStanceFrames = 0
        topReady = false
        lastCycleEndTs = 0L
        lastRepTs = 0L
        prevDrive = null
        startBelowThresholdFrames = 0
        cycleStartedAsymmetric = false
        resetCycle()
    }

    fun update(normPoints: List<PointF>, nowTs: Long): PushupCounterResult {
        val angles = extractAngles(normPoints)
        if (angles == null) {
            trackingGapFrames += 1
            if (trackingGapFrames > maxTrackingGapFramesBeforeReset) {
                onTrackingGapHard()
            }
            val fallbackDrive =
                when {
                    emaLeft != null && emaRight != null -> min(emaLeft ?: 0f, emaRight ?: 0f)
                    emaLeft != null -> emaLeft ?: 0f
                    emaRight != null -> emaRight ?: 0f
                    else -> topThresholdDeg
                }
            return PushupCounterResult(
                reps = reps,
                progress = angleToProgress(fallbackDrive),
                conditionCode = if (trackingGapFrames >= trackingLostFramesForCondition) {
                    ConditionCode.TRACKING_LOST
                } else {
                    ConditionCode.BAD_START
                },
                isCycleActive = phase != Phase.IDLE_TOP,
                lastRepAtMs = lastRepTs
            )
        }

        trackingGapFrames = 0
        hasSeenValidPose = true

        val (leftRaw, rightRaw, leftKnee, rightKnee) = angles
        val left = ema(emaLeft, leftRaw, emaAlpha)
        val right = ema(emaRight, rightRaw, emaAlpha)
        emaLeft = left
        emaRight = right
        val effectiveLeft = left ?: right
        val effectiveRight = right ?: left
        if (effectiveLeft == null || effectiveRight == null) {
            trackingGapFrames += 1
            if (trackingGapFrames > maxTrackingGapFramesBeforeReset) {
                onTrackingGapHard()
            }
            return PushupCounterResult(
                reps = reps,
                progress = 0f,
                conditionCode = ConditionCode.TRACKING_LOST,
                isCycleActive = phase != Phase.IDLE_TOP,
                lastRepAtMs = lastRepTs
            )
        }
        val drive = min(effectiveLeft, effectiveRight)
        val progress = angleToProgress(drive)

        // Reject kneeling/half-plank stance to avoid false reps when user drops to knees.
        val stanceKnee = listOfNotNull(leftKnee, rightKnee).minOrNull()
        val legsExtended = stanceKnee?.let { it >= minLegExtensionDeg } ?: true
        if (!legsExtended) {
            badStanceFrames += 1
            if (badStanceFrames >= badStanceFramesForCondition) {
                phase = Phase.IDLE_TOP
                topReady = false
                resetCycle()
                return PushupCounterResult(
                    reps = reps,
                    progress = 0f,
                    conditionCode = ConditionCode.BAD_START
                )
            }
        } else {
            badStanceFrames = 0
        }

        var rejectReason: RepRejectReason? = null

        when (phase) {
            Phase.IDLE_TOP -> {
                if (drive >= topThresholdDeg - topReadySlackDeg) {
                    topReady = true
                }
                val gapOk = (nowTs - lastCycleEndTs) >= minInterRepGapMs
                val descending = prevDrive?.let { (it - drive) >= minStartDropPerFrameDeg } ?: false
                if (topReady && gapOk && drive <= topThresholdDeg - startCycleDeltaDeg && descending) {
                    startBelowThresholdFrames += 1
                } else {
                    startBelowThresholdFrames = 0
                }
                if (startBelowThresholdFrames >= startDebounceFrames) {
                    phase = Phase.DESCENDING
                    cycleStartTs = nowTs
                    cycleStartAvg = drive
                    cycleStartLeft = effectiveLeft
                    cycleStartRight = effectiveRight
                    cycleMinLeft = effectiveLeft
                    cycleMinRight = effectiveRight
                    cycleFrameCount = 1
                    startBelowThresholdFrames = 0
                }
            }

            Phase.DESCENDING -> {
                cycleFrameCount += 1
                if (effectiveLeft < cycleMinLeft) cycleMinLeft = effectiveLeft
                if (effectiveRight < cycleMinRight) cycleMinRight = effectiveRight
                updateStartAsymmetryLatch(effectiveLeft, effectiveRight)

                val reachedBottomByAbsolute = drive <= bottomThresholdDeg
                val reachedBottomByTravel = (cycleStartAvg - drive) >= minBottomTravelDeg
                if (reachedBottomByAbsolute || reachedBottomByTravel) {
                    cycleReachedBottomByAbsolute = reachedBottomByAbsolute
                    phase = Phase.ASCENDING
                } else if (drive >= cycleStartAvg - 2f) {
                    if (cycleStartedAsymmetric) {
                        Log.i(
                            "AirFloatTune",
                            "pushup cycleIgnored reason=EARLY_ASYMMETRY travelL=${fmt1((cycleStartLeft - cycleMinLeft).coerceAtLeast(0f))} travelR=${fmt1((cycleStartRight - cycleMinRight).coerceAtLeast(0f))}"
                        )
                    } else {
                    val attemptTravel = attemptMaxTravel()
                    if (attemptTravel >= minAttemptTravelForRejectDeg) {
                        rejectReason = validateAttempt(nowTs, reachedBottom = false)
                    }
                    }
                    phase = Phase.IDLE_TOP
                    lastCycleEndTs = nowTs
                    topReady = drive >= topThresholdDeg - topReadySlackDeg
                    resetCycle()
                }
            }

            Phase.ASCENDING -> {
                cycleFrameCount += 1
                if (effectiveLeft < cycleMinLeft) cycleMinLeft = effectiveLeft
                if (effectiveRight < cycleMinRight) cycleMinRight = effectiveRight
                updateStartAsymmetryLatch(effectiveLeft, effectiveRight)

                val returnedNearStart = drive >= (cycleStartAvg - completionReturnSlackDeg)
                if (returnedNearStart) {
                    if (cycleStartedAsymmetric) {
                        Log.i(
                            "AirFloatTune",
                            "pushup cycleIgnored reason=EARLY_ASYMMETRY travelL=${fmt1((cycleStartLeft - cycleMinLeft).coerceAtLeast(0f))} travelR=${fmt1((cycleStartRight - cycleMinRight).coerceAtLeast(0f))}"
                        )
                    } else {
                        val attemptTravel = attemptMaxTravel()
                        if (attemptTravel >= minAttemptTravelForRejectDeg) {
                            val reject = validateAttempt(nowTs, reachedBottom = true)
                            if (reject == null) {
                                reps += 1
                                lastRepTs = nowTs
                                logRep()
                            } else {
                                rejectReason = reject
                            }
                        }
                    }
                    phase = Phase.IDLE_TOP
                    lastCycleEndTs = nowTs
                    topReady = drive >= topThresholdDeg - topReadySlackDeg
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

        return PushupCounterResult(
            reps = reps,
            progress = progress,
            conditionCode = condition,
            repRejectReason = rejectReason,
            isCycleActive = phase != Phase.IDLE_TOP,
            lastRepAtMs = lastRepTs
        )
    }

    private fun onTrackingGapHard() {
        phase = Phase.IDLE_TOP
        topReady = false
        badStanceFrames = 0
        prevDrive = null
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

        if (cycleFrameCount < minCycleFrames) {
            Log.i(
                "AirFloatTune",
                "pushup repRejected reason=TOO_FAST frames=$cycleFrameCount minFrames=$minCycleFrames"
            )
            return RepRejectReason.TOO_FAST
        }

        val durationMs = if (cycleStartTs > 0L) nowTs - cycleStartTs else Long.MAX_VALUE
        if (durationMs in 1 until minRepDurationMs) {
            Log.i(
                "AirFloatTune",
                "pushup repRejected reason=TOO_FAST durationMs=$durationMs minMs=$minRepDurationMs"
            )
            return RepRejectReason.TOO_FAST
        }

        val travelLeft = (cycleStartLeft - cycleMinLeft).coerceAtLeast(0f)
        val travelRight = (cycleStartRight - cycleMinRight).coerceAtLeast(0f)
        val hi = max(travelLeft, travelRight)
        val lo = min(travelLeft, travelRight)

        if (cycleMinLeft < minBottomElbowDeg || cycleMinRight < minBottomElbowDeg) {
            Log.i(
                "AirFloatTune",
                "pushup repRejected reason=INVALID_BOTTOM_ANGLE minL=${fmt1(cycleMinLeft)} minR=${fmt1(cycleMinRight)} minBottom=${fmt1(minBottomElbowDeg)}"
            )
            return RepRejectReason.INVALID_BOTTOM_ANGLE
        }

        if (!reachedBottom || hi < minDepthTravelDeg) {
            Log.i(
                "AirFloatTune",
                "pushup repRejected reason=INSUFFICIENT_DEPTH travelL=${fmt1(travelLeft)} travelR=${fmt1(travelRight)} minDepth=${fmt1(minDepthTravelDeg)} reachedBottom=$reachedBottom"
            )
            return RepRejectReason.INSUFFICIENT_TOP
        }

        val driveTravel = (cycleStartAvg - min(cycleMinLeft, cycleMinRight)).coerceAtLeast(0f)
        if (!cycleReachedBottomByAbsolute && driveTravel < minBottomTravelDeg) {
            Log.i(
                "AirFloatTune",
                "pushup repRejected reason=INSUFFICIENT_DEPTH driveTravel=${fmt1(driveTravel)} minTravelBottom=${fmt1(minBottomTravelDeg)} reachedBottomByAbs=false"
            )
            return RepRejectReason.INSUFFICIENT_TOP
        }

        if (lo < minBothSidesTravelDeg) {
            Log.i(
                "AirFloatTune",
                "pushup repRejected reason=ASYMMETRIC_RANGE travelL=${fmt1(travelLeft)} travelR=${fmt1(travelRight)} minBoth=${fmt1(minBothSidesTravelDeg)}"
            )
            return RepRejectReason.ASYMMETRIC_RANGE
        }

        val ratio = lo / hi.coerceAtLeast(1f)
        if (hi >= minAsymmetryCheckTravelDeg && lo >= minDepthTravelDeg && ratio < minSymmetryRatio) {
            Log.i(
                "AirFloatTune",
                "pushup repRejected reason=ASYMMETRIC_RANGE travelL=${fmt1(travelLeft)} travelR=${fmt1(travelRight)} ratio=${fmt2(ratio)} minRatio=${fmt2(minSymmetryRatio)}"
            )
            return RepRejectReason.ASYMMETRIC_RANGE
        }

        return null
    }

    private fun attemptMaxTravel(): Float {
        val travelLeft = (cycleStartLeft - cycleMinLeft).coerceAtLeast(0f)
        val travelRight = (cycleStartRight - cycleMinRight).coerceAtLeast(0f)
        return max(travelLeft, travelRight)
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
                "pushup earlyAsymmetry latched travelL=${fmt1(travelLeft)} travelR=${fmt1(travelRight)} ratio=${fmt2(ratio)} frames=$cycleFrameCount"
            )
        }
    }

    private fun logRep() {
        val ampL = cycleStartLeft - cycleMinLeft
        val ampR = cycleStartRight - cycleMinRight
        Log.i(
            "AirFloatTune",
            "pushup rep=$reps minL=${fmt1(cycleMinLeft)} maxL=${fmt1(cycleStartLeft)} ampL=${fmt1(ampL)} minR=${fmt1(cycleMinRight)} maxR=${fmt1(cycleStartRight)} ampR=${fmt1(ampR)} top=${fmt1(topThresholdDeg)} bottom=${fmt1(bottomThresholdDeg)}"
        )
    }

    private fun angleToProgress(avgElbowAngle: Float): Float {
        val span = (topThresholdDeg - bottomThresholdDeg).coerceAtLeast(1f)
        return ((avgElbowAngle - bottomThresholdDeg) / span).coerceIn(0f, 1f)
    }

    private fun extractAngles(points: List<PointF>): PushupAngles? {
        if (points.size <= rWrist) return null
        val leftElbow = angleDeg(points[lShoulder], points[lElbow], points[lWrist])
        val rightElbow = angleDeg(points[rShoulder], points[rElbow], points[rWrist])
        if (leftElbow == null && rightElbow == null) return null
        val leftKnee = if (points.size > lAnkle) angleDeg(points[lHip], points[lKnee], points[lAnkle]) else null
        val rightKnee = if (points.size > rAnkle) angleDeg(points[rHip], points[rKnee], points[rAnkle]) else null
        return PushupAngles(leftElbow, rightElbow, leftKnee, rightKnee)
    }

    private data class PushupAngles(
        val leftElbow: Float?,
        val rightElbow: Float?,
        val leftKnee: Float?,
        val rightKnee: Float?
    )

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

    private fun ema(prev: Float?, newValue: Float?, alpha: Float): Float? {
        if (newValue == null) return null
        if (prev == null) return newValue
        return alpha * newValue + (1f - alpha) * prev
    }

    private fun fmt1(value: Float): String {
        return String.format(java.util.Locale.US, "%.1f", value)
    }

    private fun fmt2(value: Float): String {
        return String.format(java.util.Locale.US, "%.2f", value)
    }
}
