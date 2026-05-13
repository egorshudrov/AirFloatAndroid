package com.airfloat.app.situp

import android.graphics.PointF
import android.util.Log
import com.airfloat.app.pose.ConditionCode
import com.airfloat.app.pose.RepRejectReason
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class SitupCounterResult(
    val reps: Int,
    val progress: Float,
    val conditionCode: ConditionCode,
    val repRejectReason: RepRejectReason? = null,
    val isCycleActive: Boolean = false,
    val lastRepAtMs: Long = 0L
)

/**
 * Sit-up rep FSM based on hip flexion angle (shoulder-hip-knee).
 * Sequence: down (lying) -> up (sit) -> down (lying).
 */
class SitupCounter(
    private val topThresholdDeg: Float = 150f,
    private val bottomThresholdDeg: Float = 108f,
    private val minRepDurationMs: Long = 340L,
    private val minDepthTravelDeg: Float = 24f,
    private val minBottomTravelDeg: Float = 40f,
    private val minBothSidesTravelDeg: Float = 10f,
    private val minSymmetryRatio: Float = 0.45f,
    private val minAsymmetryCheckTravelDeg: Float = 34f,
    private val emaAlpha: Float = 0.35f,
    private val trackingLostFramesForCondition: Int = 3,
    private val maxTrackingGapFramesBeforeReset: Int = 20,
    private val minCycleFrames: Int = 6,
    private val minInterRepGapMs: Long = 300L,
    private val startCycleDeltaDeg: Float = 6f,
    private val maxStartArmMs: Long = 1200L,
    private val completionReturnSlackDeg: Float = 6f,
    private val minTopReadyAngleDeg: Float = 134f,
    private val startDebounceFrames: Int = 2,
    private val minStartDropPerFrameDeg: Float = 0.6f,
    private val startAsymmetryWindowFrames: Int = 4,
    private val startAsymmetryTravelDeg: Float = 10f
) {

    private enum class Phase {
        IDLE_TOP,
        DESCENDING,
        ASCENDING
    }

    // BlazePose landmarks.
    private val lShoulder = 11
    private val rShoulder = 12
    private val lHip = 23
    private val rHip = 24
    private val lKnee = 25
    private val rKnee = 26

    private var reps = 0
    private var phase = Phase.IDLE_TOP
    private var emaLeft: Float? = null
    private var emaRight: Float? = null
    private var trackingGapFrames = 0
    private var hasSeenValidPose = false
    private var topReady = false
    private val topReadySlackDeg = 12f
    private var topReadyLogged = false
    private var lastDiagLogTs = 0L
    private var lastDiagPhase: Phase? = null
    private var lastDiagCondition: ConditionCode? = null
    private var lastDiagTopReady = false
    private val diagLogIntervalMs = 250L
    private var observedTopLeft = Float.NEGATIVE_INFINITY
    private var observedTopRight = Float.NEGATIVE_INFINITY
    private var prevDrive: Float? = null
    private var startBelowThresholdFrames = 0
    private var armedTopLeft = Float.NaN
    private var armedTopRight = Float.NaN
    private var armedTopTs = 0L

    private var cycleStartTs = 0L
    private var cycleTopLeft = 0f
    private var cycleTopRight = 0f
    private var cycleMinLeft = Float.POSITIVE_INFINITY
    private var cycleMinRight = Float.POSITIVE_INFINITY
    private var cycleFrameCount = 0
    private var lastCycleEndTs = 0L
    private var lastRepTs = 0L
    private var cycleStartedAsymmetric = false
    private var cycleReachedBottomByAbsolute = false

    fun reset() {
        reps = 0
        phase = Phase.IDLE_TOP
        emaLeft = null
        emaRight = null
        trackingGapFrames = 0
        hasSeenValidPose = false
        topReady = false
        topReadyLogged = false
        lastDiagLogTs = 0L
        lastDiagPhase = null
        lastDiagCondition = null
        lastDiagTopReady = false
        observedTopLeft = Float.NEGATIVE_INFINITY
        observedTopRight = Float.NEGATIVE_INFINITY
        prevDrive = null
        startBelowThresholdFrames = 0
        resetStartArm()
        lastCycleEndTs = 0L
        lastRepTs = 0L
        cycleStartedAsymmetric = false
        resetCycle()
    }

    fun update(normPoints: List<PointF>, nowTs: Long): SitupCounterResult {
        val angles = extractHipAngles(normPoints)
        if (angles == null) {
            trackingGapFrames += 1
            if (
                trackingGapFrames == trackingLostFramesForCondition ||
                trackingGapFrames == maxTrackingGapFramesBeforeReset
            ) {
                Log.i(
                    "AirFloatTune",
                    "situp trackingGap frames=$trackingGapFrames phase=$phase topReady=$topReady reps=$reps"
                )
            }
            if (trackingGapFrames == maxTrackingGapFramesBeforeReset + 1) {
                onTrackingGapHard()
            }
            val condition =
                if (trackingGapFrames >= trackingLostFramesForCondition) {
                    ConditionCode.TRACKING_LOST
                } else {
                    ConditionCode.BAD_START
                }
            logDiagSnapshot(
                nowTs = nowTs,
                left = null,
                right = null,
                drive = null,
                progress = 0f,
                condition = condition,
                rejectReason = null,
                note = "no_pose"
            )
            return SitupCounterResult(
                reps = reps,
                progress = 0f,
                conditionCode = condition,
                isCycleActive = phase != Phase.IDLE_TOP,
                lastRepAtMs = lastRepTs
            )
        }

        if (trackingGapFrames > 0) {
            Log.i(
                "AirFloatTune",
                "situp trackingRecovered gapFrames=$trackingGapFrames phase=$phase reps=$reps"
            )
        }
        trackingGapFrames = 0
        hasSeenValidPose = true

        val leftRaw = angles.leftHip
        val rightRaw = angles.rightHip
        val left = ema(emaLeft, leftRaw, emaAlpha)
        val right = ema(emaRight, rightRaw, emaAlpha)
        emaLeft = left
        emaRight = right
        val effectiveLeft = left ?: right
        val effectiveRight = right ?: left
        if (effectiveLeft == null || effectiveRight == null) {
            trackingGapFrames += 1
            val condition =
                if (trackingGapFrames >= trackingLostFramesForCondition) {
                    ConditionCode.TRACKING_LOST
                } else {
                    ConditionCode.BAD_START
                }
            logDiagSnapshot(
                nowTs = nowTs,
                left = left,
                right = right,
                drive = null,
                progress = 0f,
                condition = condition,
                rejectReason = null,
                note = "angles_unstable"
            )
            return SitupCounterResult(
                reps = reps,
                progress = 0f,
                conditionCode = condition,
                isCycleActive = phase != Phase.IDLE_TOP,
                lastRepAtMs = lastRepTs
            )
        }
        val drive = min(effectiveLeft, effectiveRight)
        val progress = angleToProgress(drive)

        var rejectReason: RepRejectReason? = null

        when (phase) {
            Phase.IDLE_TOP -> {
                updateObservedTop(effectiveLeft, effectiveRight)
                val topReference = currentTopReference()
                topReady = observedTopAngle >= minTopReadyAngleDeg
                val nearTop = topReady && drive >= topReference - topReadySlackDeg
                if (nearTop) {
                    if (!topReadyLogged) {
                        Log.i(
                            "AirFloatTune",
                            "situp topReady drive=${fmt1(drive)} left=${fmt1(effectiveLeft)} right=${fmt1(effectiveRight)} topRef=${fmt1(topReference)} minTopReady=${fmt1(minTopReadyAngleDeg)}"
                        )
                        topReadyLogged = true
                    }
                    armStartFromTop(
                        left = currentTopLeftReference() ?: effectiveLeft,
                        right = currentTopRightReference() ?: effectiveRight,
                        nowTs = nowTs
                    )
                }
                val gapOk = (nowTs - lastCycleEndTs) >= minInterRepGapMs
                val descending = prevDrive?.let { (it - drive) >= minStartDropPerFrameDeg } ?: false
                if (nearTop) {
                    startBelowThresholdFrames = 0
                }
                val armExpired = armedTopTs > 0L && (nowTs - armedTopTs) > maxStartArmMs
                if (armExpired || (!nearTop && drive <= bottomThresholdDeg)) {
                    resetStartArm()
                }
                val armedTopDrive = armedTopDriveOrNull()
                val startDrop = armedTopDrive?.let { (it - drive).coerceAtLeast(0f) }
                val canStart =
                    armedTopDrive != null &&
                        !nearTop &&
                        topReady &&
                        gapOk &&
                        descending &&
                        startDrop != null &&
                        startDrop >= startCycleDeltaDeg
                if (!nearTop && canStart) {
                    startBelowThresholdFrames += 1
                } else if (!nearTop) {
                    startBelowThresholdFrames = 0
                }
                if (startBelowThresholdFrames >= startDebounceFrames && armedTopDrive != null) {
                    phase = Phase.DESCENDING
                    cycleStartTs = nowTs
                    cycleTopLeft = armedTopLeft
                    cycleTopRight = armedTopRight
                    cycleMinLeft = effectiveLeft
                    cycleMinRight = effectiveRight
                    cycleFrameCount = 1
                    startBelowThresholdFrames = 0
                    Log.i(
                        "AirFloatTune",
                        "situp cycleStart drive=${fmt1(drive)} left=${fmt1(effectiveLeft)} right=${fmt1(effectiveRight)} topRef=${fmt1(topReference)} topL=${fmt1(cycleTopLeft)} topR=${fmt1(cycleTopRight)} bottom=${fmt1(bottomThresholdDeg)}"
                    )
                    resetStartArm()
                }
            }

            Phase.DESCENDING -> {
                cycleFrameCount += 1
                if (effectiveLeft < cycleMinLeft) cycleMinLeft = effectiveLeft
                if (effectiveRight < cycleMinRight) cycleMinRight = effectiveRight
                updateStartAsymmetryLatch(effectiveLeft, effectiveRight)

                val reachedBottomByAbsolute = drive <= bottomThresholdDeg
                val reachedBottomByTravel = (cycleTopDrive() - drive) >= minBottomTravelDeg
                if (reachedBottomByAbsolute || reachedBottomByTravel) {
                    cycleReachedBottomByAbsolute = reachedBottomByAbsolute
                    phase = Phase.ASCENDING
                    Log.i(
                        "AirFloatTune",
                        "situp bottomReached drive=${fmt1(drive)} travel=${fmt1(cycleTopDrive() - drive)} abs=$reachedBottomByAbsolute travelGate=$reachedBottomByTravel"
                    )
                } else if (drive >= cycleTopDrive() - completionReturnSlackDeg) {
                    val attemptTravel = attemptMaxTravel()
                    if (attemptTravel >= 4f) {
                        Log.i(
                            "AirFloatTune",
                            "situp cycleCancelled travel=${fmt1(attemptTravel)} drive=${fmt1(drive)} frames=$cycleFrameCount"
                        )
                    }
                    if (cycleStartedAsymmetric) {
                        Log.i(
                            "AirFloatTune",
                            "situp cycleIgnored reason=EARLY_ASYMMETRY travelL=${fmt1((cycleTopLeft - cycleMinLeft).coerceAtLeast(0f))} travelR=${fmt1((cycleTopRight - cycleMinRight).coerceAtLeast(0f))}"
                        )
                    } else if (attemptTravel >= 8f) {
                        rejectReason = validateAttempt(nowTs, reachedBottom = false)
                    }
                    phase = Phase.IDLE_TOP
                    lastCycleEndTs = nowTs
                    topReady = drive >= minTopReadyAngleDeg
                    topReadyLogged = topReady
                    setObservedTopFromCurrent(effectiveLeft, effectiveRight)
                    resetStartArm()
                    resetCycle()
                }
            }

            Phase.ASCENDING -> {
                cycleFrameCount += 1
                if (effectiveLeft < cycleMinLeft) cycleMinLeft = effectiveLeft
                if (effectiveRight < cycleMinRight) cycleMinRight = effectiveRight
                updateStartAsymmetryLatch(effectiveLeft, effectiveRight)

                val returnedNearStart = drive >= (cycleTopDrive() - completionReturnSlackDeg)
                if (returnedNearStart) {
                    val attemptTravel = attemptMaxTravel()
                    if (cycleStartedAsymmetric) {
                        Log.i(
                            "AirFloatTune",
                            "situp cycleIgnored reason=EARLY_ASYMMETRY travelL=${fmt1((cycleTopLeft - cycleMinLeft).coerceAtLeast(0f))} travelR=${fmt1((cycleTopRight - cycleMinRight).coerceAtLeast(0f))}"
                        )
                    } else if (attemptTravel >= 8f) {
                        val reject = validateAttempt(nowTs, reachedBottom = true)
                        if (reject == null) {
                            reps += 1
                            lastRepTs = nowTs
                            logRep()
                        } else {
                            rejectReason = reject
                        }
                    } else if (attemptTravel >= 4f) {
                        Log.i(
                            "AirFloatTune",
                            "situp cycleIgnored travel=${fmt1(attemptTravel)} frames=$cycleFrameCount"
                        )
                    }
                    phase = Phase.IDLE_TOP
                    lastCycleEndTs = nowTs
                    topReady = drive >= minTopReadyAngleDeg
                    topReadyLogged = topReady
                    setObservedTopFromCurrent(effectiveLeft, effectiveRight)
                    resetStartArm()
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

        return SitupCounterResult(
            reps = reps,
            progress = progress,
            conditionCode = condition,
            repRejectReason = rejectReason,
            isCycleActive = phase != Phase.IDLE_TOP,
            lastRepAtMs = lastRepTs
        ).also {
            logDiagSnapshot(
                nowTs = nowTs,
                left = left,
                right = right,
                drive = drive,
                progress = progress,
                condition = condition,
                rejectReason = rejectReason
            )
        }
    }

    private fun onTrackingGapHard() {
        Log.i(
            "AirFloatTune",
            "situp trackingReset gapFrames=$trackingGapFrames phase=$phase reps=$reps"
        )
        phase = Phase.IDLE_TOP
        topReady = false
        topReadyLogged = false
        observedTopLeft = Float.NEGATIVE_INFINITY
        observedTopRight = Float.NEGATIVE_INFINITY
        prevDrive = null
        resetStartArm()
        resetCycle()
    }

    private fun resetCycle() {
        cycleStartTs = 0L
        cycleTopLeft = 0f
        cycleTopRight = 0f
        cycleMinLeft = Float.POSITIVE_INFINITY
        cycleMinRight = Float.POSITIVE_INFINITY
        cycleFrameCount = 0
        cycleStartedAsymmetric = false
        cycleReachedBottomByAbsolute = false
    }

    private fun armStartFromTop(left: Float, right: Float, nowTs: Long) {
        armedTopLeft = left
        armedTopRight = right
        armedTopTs = nowTs
    }

    private fun resetStartArm() {
        startBelowThresholdFrames = 0
        armedTopLeft = Float.NaN
        armedTopRight = Float.NaN
        armedTopTs = 0L
    }

    private fun armedTopDriveOrNull(): Float? {
        if (!armedTopLeft.isFinite() || !armedTopRight.isFinite()) return null
        return min(armedTopLeft, armedTopRight)
    }

    private fun logDiagSnapshot(
        nowTs: Long,
        left: Float?,
        right: Float?,
        drive: Float?,
        progress: Float,
        condition: ConditionCode,
        rejectReason: RepRejectReason?,
        note: String? = null
    ) {
        val shouldLog =
            nowTs - lastDiagLogTs >= diagLogIntervalMs ||
                phase != lastDiagPhase ||
                condition != lastDiagCondition ||
                topReady != lastDiagTopReady ||
                rejectReason != null
        if (!shouldLog) return

        lastDiagLogTs = nowTs
        lastDiagPhase = phase
        lastDiagCondition = condition
        lastDiagTopReady = topReady

        val extras =
            buildString {
                append("phase=").append(phase)
                append(" reps=").append(reps)
                append(" cond=").append(condition)
                append(" reject=").append(rejectReason ?: "-")
                append(" topReady=").append(topReady)
                append(" topRef=").append(fmt1OrDash(currentTopReferenceOrNull()))
                append(" gap=").append(trackingGapFrames)
                append(" progress=").append(fmt1(progress * 100f)).append("%")
                append(" drive=").append(fmt1OrDash(drive))
                append(" left=").append(fmt1OrDash(left))
                append(" right=").append(fmt1OrDash(right))
                append(" cycleFrames=").append(cycleFrameCount)
                append(" travelL=").append(fmt1(currentTravel(cycleTopLeft, cycleMinLeft)))
                append(" travelR=").append(fmt1(currentTravel(cycleTopRight, cycleMinRight)))
                if (note != null) {
                    append(" note=").append(note)
                }
            }
        Log.i("AirFloatSitupDiag", extras)
    }

    private fun validateAttempt(nowTs: Long, reachedBottom: Boolean): RepRejectReason? {
        if (!cycleMinLeft.isFinite() || !cycleMinRight.isFinite()) {
            return RepRejectReason.TRACKING_LOST
        }

        if (cycleFrameCount < minCycleFrames) {
            Log.i(
                "AirFloatTune",
                "situp repRejected reason=TOO_FAST frames=$cycleFrameCount minFrames=$minCycleFrames"
            )
            return RepRejectReason.TOO_FAST
        }

        val durationMs = if (cycleStartTs > 0L) nowTs - cycleStartTs else Long.MAX_VALUE
        if (durationMs in 1 until minRepDurationMs) {
            Log.i(
                "AirFloatTune",
                "situp repRejected reason=TOO_FAST durationMs=$durationMs minMs=$minRepDurationMs"
            )
            return RepRejectReason.TOO_FAST
        }

        val travelLeft = (cycleTopLeft - cycleMinLeft).coerceAtLeast(0f)
        val travelRight = (cycleTopRight - cycleMinRight).coerceAtLeast(0f)
        val hi = max(travelLeft, travelRight)
        val lo = min(travelLeft, travelRight)

        if (!reachedBottom || hi < minDepthTravelDeg) {
            Log.i(
                "AirFloatTune",
                "situp repRejected reason=INSUFFICIENT_DEPTH travelL=${fmt1(travelLeft)} travelR=${fmt1(travelRight)} minDepth=${fmt1(minDepthTravelDeg)} reachedBottom=$reachedBottom"
            )
            return RepRejectReason.INSUFFICIENT_TOP
        }

        if (!cycleReachedBottomByAbsolute && hi < minBottomTravelDeg) {
            Log.i(
                "AirFloatTune",
                "situp repRejected reason=INSUFFICIENT_DEPTH travelL=${fmt1(travelLeft)} travelR=${fmt1(travelRight)} minTravelBottom=${fmt1(minBottomTravelDeg)} reachedBottomByAbs=false"
            )
            return RepRejectReason.INSUFFICIENT_TOP
        }

        if (lo < minBothSidesTravelDeg) {
            Log.i(
                "AirFloatTune",
                "situp repRejected reason=ASYMMETRIC_RANGE travelL=${fmt1(travelLeft)} travelR=${fmt1(travelRight)} minBoth=${fmt1(minBothSidesTravelDeg)}"
            )
            return RepRejectReason.ASYMMETRIC_RANGE
        }

        val ratio = lo / hi.coerceAtLeast(1f)
        if (hi >= minAsymmetryCheckTravelDeg && lo >= minDepthTravelDeg && ratio < minSymmetryRatio) {
            Log.i(
                "AirFloatTune",
                "situp repRejected reason=ASYMMETRIC_RANGE travelL=${fmt1(travelLeft)} travelR=${fmt1(travelRight)} ratio=${fmt2(ratio)} minRatio=${fmt2(minSymmetryRatio)}"
            )
            return RepRejectReason.ASYMMETRIC_RANGE
        }

        return null
    }

    private fun attemptMaxTravel(): Float {
        val travelLeft = (cycleTopLeft - cycleMinLeft).coerceAtLeast(0f)
        val travelRight = (cycleTopRight - cycleMinRight).coerceAtLeast(0f)
        return max(travelLeft, travelRight)
    }

    private fun updateStartAsymmetryLatch(currentLeft: Float, currentRight: Float) {
        if (cycleStartedAsymmetric) return
        if (cycleFrameCount <= 0 || cycleFrameCount > startAsymmetryWindowFrames) return

        val travelLeft = (cycleTopLeft - currentLeft).coerceAtLeast(0f)
        val travelRight = (cycleTopRight - currentRight).coerceAtLeast(0f)
        val hi = max(travelLeft, travelRight)
        if (hi < startAsymmetryTravelDeg) return

        val lo = min(travelLeft, travelRight)
        val ratio = lo / hi.coerceAtLeast(1f)
        if (lo < minBothSidesTravelDeg || ratio < minSymmetryRatio) {
            cycleStartedAsymmetric = true
            Log.i(
                "AirFloatTune",
                "situp earlyAsymmetry latched travelL=${fmt1(travelLeft)} travelR=${fmt1(travelRight)} ratio=${fmt2(ratio)} frames=$cycleFrameCount"
            )
        }
    }

    private fun cycleTopDrive(): Float {
        return min(cycleTopLeft, cycleTopRight)
    }

    private fun updateObservedTop(left: Float, right: Float) {
        if (left > observedTopLeft) observedTopLeft = left
        if (right > observedTopRight) observedTopRight = right
    }

    private fun setObservedTopFromCurrent(left: Float, right: Float) {
        observedTopLeft = left
        observedTopRight = right
    }

    private fun currentTopReference(): Float {
        return currentTopReferenceOrNull() ?: topThresholdDeg
    }

    private fun currentTopReferenceOrNull(): Float? {
        val left = currentTopLeftReference()
        val right = currentTopRightReference()
        return when {
            left != null && right != null -> min(left, right)
            left != null -> left
            right != null -> right
            else -> null
        }
    }

    private fun currentTopLeftReference(): Float? {
        if (!observedTopLeft.isFinite()) return null
        return observedTopLeft
    }

    private fun currentTopRightReference(): Float? {
        if (!observedTopRight.isFinite()) return null
        return observedTopRight
    }

    private fun logRep() {
        val ampL = cycleTopLeft - cycleMinLeft
        val ampR = cycleTopRight - cycleMinRight
        Log.i(
            "AirFloatTune",
            "situp rep=$reps minL=${fmt1(cycleMinLeft)} maxL=${fmt1(cycleTopLeft)} ampL=${fmt1(ampL)} minR=${fmt1(cycleMinRight)} maxR=${fmt1(cycleTopRight)} ampR=${fmt1(ampR)} top=${fmt1(topThresholdDeg)} bottom=${fmt1(bottomThresholdDeg)}"
        )
    }

    private fun angleToProgress(driveAngle: Float): Float {
        val span = (topThresholdDeg - bottomThresholdDeg).coerceAtLeast(1f)
        return ((topThresholdDeg - driveAngle) / span).coerceIn(0f, 1f)
    }

    private fun extractHipAngles(points: List<PointF>): SitupAngles? {
        if (points.size <= rKnee) return null
        val left = angleDeg(points[lShoulder], points[lHip], points[lKnee])
        val right = angleDeg(points[rShoulder], points[rHip], points[rKnee])
        if (left == null && right == null) return null
        return SitupAngles(left, right)
    }

    private data class SitupAngles(
        val leftHip: Float?,
        val rightHip: Float?
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
        if (newValue == null) return prev
        if (prev == null) return newValue
        return alpha * newValue + (1f - alpha) * prev
    }

    private fun currentTravel(start: Float, minValue: Float): Float {
        if (!start.isFinite() || !minValue.isFinite()) return 0f
        return (start - minValue).coerceAtLeast(0f)
    }

    private val observedTopAngle: Float
        get() = currentTopReferenceOrNull() ?: Float.NEGATIVE_INFINITY

    private fun fmt1(v: Float): String = String.format(java.util.Locale.US, "%.1f", v)
    private fun fmt2(v: Float): String = String.format(java.util.Locale.US, "%.2f", v)
    private fun fmt1OrDash(v: Float?): String = v?.let(::fmt1) ?: "--"
}
