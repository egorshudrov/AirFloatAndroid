package com.airfloat.app.pose

import android.content.Context
import android.graphics.PointF
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class ConditionCode {
    OK,
    TRACKING_LOST,
    BAD_START,
    RANGE_TOO_SMALL
}

enum class RepRejectReason {
    INVALID_BOTTOM_ANGLE,
    INSUFFICIENT_TOP,
    TOO_FAST,
    ASYMMETRIC_RANGE,
    ASYNC_ARMS,
    TRACKING_LOST
}

data class FrameResult(
    val normPoints: List<PointF>,   // 0..1 координаты
    val rawNormPoints: List<PointF>,// 0..1 координаты MediaPipe без ротации (для ML)
    val leftAngle: Float?,          // локоть
    val rightAngle: Float?,
    val repProgress: Float,         // 0..1 прогресс повтора (низ->верх)
    val reps: Int,
    val fps: Float,
    val latencyMs: Float,
    val pipeline: String,
    val conditionCode: ConditionCode,
    val reasonEvent: ConditionCode?,
    val repRejectEvent: RepRejectReason?,
    val isFrontCamera: Boolean,
    val srcWidth: Int,              // ширина кадра после учета rotationDegrees
    val srcHeight: Int
)

class PoseDetector(
    context: Context,
    private val isFrontCamera: Boolean,
    private val enablePressRepPipeline: Boolean = true,
    private val emitReasonEvents: Boolean = true,
    private val requireArmsUpForTracking: Boolean = true,
    private val trackingPointIndices: IntArray = intArrayOf(11, 13, 15, 12, 14, 16),
    private val minPoseDetectionConfidence: Float = 0.5f,
    private val minPosePresenceConfidence: Float = 0.5f,
    private val minTrackingConfidence: Float = 0.5f,
    private val downThreshold: Float = 82f,
    private val upThreshold: Float = 128f,
    private val gaugeBottomAngleDeg: Float = downThreshold,
    private val holdFrames: Int = 2,
    private val syncWindowFrames: Int = 10,
    private val emaAlpha: Float = 0.35f,
    private val minRepBottomAngleDeg: Float = 35f,
    private val minUnderTopTravelDeg: Float = 18f,
    private val underTopTopSlackDeg: Float = 1f,
    private val minRepDurationMs: Long = 420L,
    private val minRangeSymmetryRatio: Float = 0.62f,
    private val startAsymmetryWindowFrames: Int = 3,
    private val startAsymmetryTravelDeg: Float = 10f,
    private val onFrame: (FrameResult) -> Unit
) {

    private val landmarker: PoseLandmarker
    private val landmarkerLock = Any()
    @Volatile
    private var isClosed = false

    // EMA
    private var emaL: Float? = null
    private var emaR: Float? = null

    // FSM / репы (простая версия: обе руки + окно синхронизации)
    private val fsmL = AngleFSM(down = downThreshold, up = upThreshold, holdFrames = holdFrames)
    private val fsmR = AngleFSM(down = downThreshold, up = upThreshold, holdFrames = holdFrames)
    private val syncWindow = syncWindowFrames
    private var pendingL = 0
    private var pendingR = 0
    private var reps = 0
    private var underTopAttemptActive = false
    private var underTopPeakL = Float.NEGATIVE_INFINITY
    private var underTopPeakR = Float.NEGATIVE_INFINITY
    private val armsDownWristOffset = 0.14f
    private var trackingGapFrames = 0
    private val resetStateAfterGapFrames = 4
    private val trackingLostFramesForCondition = 3
    private var rangeEvalFrames = 0
    private var rangeMinL = Float.POSITIVE_INFINITY
    private var rangeMaxL = Float.NEGATIVE_INFINITY
    private var rangeMinR = Float.POSITIVE_INFINITY
    private var rangeMaxR = Float.NEGATIVE_INFINITY
    private val rangeEvalWindowFrames = 16
    private val minRangeAmplitudeDeg = 20f
    private val rangeTooSmallWindowsRequired = 2
    private var rangeTooSmallWindowStreak = 0
    private var lastConditionCode = ConditionCode.BAD_START
    private var repMinL = Float.POSITIVE_INFINITY
    private var repMaxL = Float.NEGATIVE_INFINITY
    private var repMinR = Float.POSITIVE_INFINITY
    private var repMaxR = Float.NEGATIVE_INFINITY
    private var repRangeStartTs = 0L
    private var repRangeFrames = 0
    private var repStartedAsymmetric = false
    private var lastTrackingLostEventTs = 0L
    private var lastBadStartEventTs = 0L
    private var lastRangeTooSmallEventTs = 0L
    private val trackingLostEventCooldownMs = 3500L
    private val badStartEventCooldownMs = 1400L
    private val rangeTooSmallEventCooldownMs = 3000L

    // FPS
    private var lastTs: Long = 0L
    private var fps: Float = 0f
    private var lastInputTs: Long = 0L
    private var lastProcessTs: Long = 0L
    // Keep uncapped by default; camera backpressure already prevents queue growth.
    // A hard interval cap can silently drop FPS on 30 FPS camera streams.
    private val minIntervalMs = 0L

    // размеры текущего кадра (с учётом rotation)
    private var frameW: Int = 0
    private var frameH: Int = 0

    init {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("pose_landmarker_lite.task")
            .build()

        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(minPoseDetectionConfidence)
            .setMinPosePresenceConfidence(minPosePresenceConfidence)
            .setMinTrackingConfidence(minTrackingConfidence)
            .setResultListener { result: PoseLandmarkerResult, input ->
                handleResult(result)
            }
            .setErrorListener { e ->
                Log.e("AirFloat", "PoseLandmarker error", e)
            }
            .build()

        landmarker = PoseLandmarker.createFromOptions(context, options)
        Log.i(
            "AirFloat",
            "Pose detector initialized: backend=MediaPipe model=pose_landmarker_lite.task mode=LIVE_STREAM down=$downThreshold up=$upThreshold gaugeBottom=$gaugeBottomAngleDeg hold=$holdFrames sync=$syncWindow ema=$emaAlpha minBottom=$minRepBottomAngleDeg minUnderTopTravel=$minUnderTopTravelDeg topSlack=$underTopTopSlackDeg minRepMs=$minRepDurationMs minSymRatio=$minRangeSymmetryRatio detConf=$minPoseDetectionConfidence poseConf=$minPosePresenceConfidence trkConf=$minTrackingConfidence requireArmsUp=$requireArmsUpForTracking pressPipeline=$enablePressRepPipeline"
        )
    }

    fun close() {
        synchronized(landmarkerLock) {
            if (isClosed) return
            isClosed = true
            landmarker.close()
        }
    }

    fun process(imageProxy: ImageProxy) {
        try {
            if (isClosed) return

            val now = SystemClock.uptimeMillis()
            if (minIntervalMs > 0L && lastProcessTs != 0L && now - lastProcessTs < minIntervalMs) {
                imageProxy.close()
                return
            }
            lastProcessTs = now

            val startTs = SystemClock.uptimeMillis()
            lastInputTs = startTs

            val bitmap = imageProxy.toBitmap()   // твой ImageProxyExt.kt
            val mpImage: MPImage = BitmapImageBuilder(bitmap).build()

            val rotationDegrees =
                imageProxy.imageInfo.rotationDegrees

            val crop = imageProxy.cropRect
            val cropW = crop.width()
            val cropH = crop.height()

            // учтём поворот: для 90/270 ширина/высота меняются местами
            if (rotationDegrees % 180 == 0) {
                frameW = cropW
                frameH = cropH
            } else {
                frameW = cropH
                frameH = cropW
            }

            val opts = ImageProcessingOptions.builder()
                .setRotationDegrees(rotationDegrees)
                .build()

            synchronized(landmarkerLock) {
                if (!isClosed) {
                    landmarker.detectAsync(mpImage, opts, startTs)
                }
            }

        } catch (t: Throwable) {
            if (!isClosed) {
                Log.e("AirFloat", "process() failed", t)
            }
        } finally {
            imageProxy.close()
        }
    }

    private fun handleResult(result: PoseLandmarkerResult) {
        val ts = SystemClock.uptimeMillis()
        val latency = if (lastInputTs != 0L) (ts - lastInputTs).toFloat() else 0f

        if (lastTs != 0L) {
            val dt = max(1L, ts - lastTs)
            val instFps = 1000f / dt.toFloat()
            // сгладим FPS, чтобы не прыгал
            fps = if (fps == 0f) instFps else (0.2f * instFps + 0.8f * fps)
        }
        lastTs = ts

        val poses = result.landmarks()
        if (poses.isEmpty()) {
            val hadPendingRep = pendingL > 0 || pendingR > 0
            onTrackingGap()
            emitFrame(
                normPoints = emptyList(),
                rawNormPoints = emptyList(),
                leftAngle = null,
                rightAngle = null,
                latencyMs = latency,
                conditionCode = conditionForGap(),
                repRejectEvent = if (hadPendingRep) RepRejectReason.TRACKING_LOST else null
            )
            return
        }

        val lm = poses[0] // 33 точки

        // Отдадим ВСЕ точки 0..1 (сырые MediaPipe, без поворота)
        val rawNormPoints = lm.map {
            PointF(it.x(), it.y())
        }

        // И отдельный rotated набор под текущий UI/overlay.
        // Поворот координат в портретный режим (только это меняем)
        val normPoints = lm.map {

            val x = it.x()
            val y = it.y()

            // swap осей + поворот
            val nx = y
            val ny = 1f - x

            PointF(nx, ny)
        }

        if (!hasReliablePoints(normPoints)) {
            val hadPendingRep = pendingL > 0 || pendingR > 0
            onTrackingGap()
            emitFrame(
                normPoints = normPoints,
                rawNormPoints = rawNormPoints,
                leftAngle = null,
                rightAngle = null,
                latencyMs = latency,
                conditionCode = conditionForGap(),
                repRejectEvent = if (hadPendingRep) RepRejectReason.TRACKING_LOST else null
            )
            return
        }

        // BlazePose индексы
        val L_SHOULDER = 11
        val L_ELBOW = 13
        val L_WRIST = 15
        val R_SHOULDER = 12
        val R_ELBOW = 14
        val R_WRIST = 16

        if (requireArmsUpForTracking && areArmsDown(normPoints)) {
            val hadPendingRep = pendingL > 0 || pendingR > 0
            onTrackingGap(forceTrackingLost = true)
            emitFrame(
                normPoints = normPoints,
                rawNormPoints = rawNormPoints,
                leftAngle = null,
                rightAngle = null,
                latencyMs = latency,
                conditionCode = ConditionCode.TRACKING_LOST,
                repRejectEvent = if (hadPendingRep) RepRejectReason.TRACKING_LOST else null
            )
            return
        }

        if (!enablePressRepPipeline) {
            onTrackingRecovered()
            emitFrame(
                normPoints = normPoints,
                rawNormPoints = rawNormPoints,
                leftAngle = null,
                rightAngle = null,
                latencyMs = latency,
                conditionCode = ConditionCode.OK,
                repRejectEvent = null
            )
            return
        }

        val leftAngleRaw = angleDeg(
            a = normPoints[L_SHOULDER],
            b = normPoints[L_ELBOW],
            c = normPoints[L_WRIST]
        )

        val rightAngleRaw = angleDeg(
            a = normPoints[R_SHOULDER],
            b = normPoints[R_ELBOW],
            c = normPoints[R_WRIST]
        )

        val leftAngle = sanitizeAngle(leftAngleRaw)
        val rightAngle = sanitizeAngle(rightAngleRaw)
        if (leftAngle == null || rightAngle == null) {
            val hadPendingRep = pendingL > 0 || pendingR > 0
            onTrackingGap()
            emitFrame(
                normPoints = normPoints,
                rawNormPoints = rawNormPoints,
                leftAngle = null,
                rightAngle = null,
                latencyMs = latency,
                conditionCode = conditionForGap(),
                repRejectEvent = if (hadPendingRep) RepRejectReason.TRACKING_LOST else null
            )
            return
        }
        onTrackingRecovered()

        emaL = ema(emaL, leftAngle, emaAlpha)
        emaR = ema(emaR, rightAngle, emaAlpha)
        val smoothL = emaL ?: leftAngle
        val smoothR = emaR ?: rightAngle

        // репы: обе руки должны закрыть rep в окне
        val leftRep = fsmL.update(emaL)
        val rightRep = fsmR.update(emaR)
        val bothPrimed = fsmL.isPrimed() && fsmR.isPrimed()
        updateRepRange(smoothL, smoothR, ts)

        if (leftRep) pendingL = syncWindow
        if (rightRep) pendingR = syncWindow

        var repCompleted = false
        var repRejectEvent: RepRejectReason? = null
        if (bothPrimed && pendingL > 0 && pendingR > 0) {
            if (repStartedAsymmetric) {
                Log.i(
                    "AirFloatTune",
                    "press cycleIgnored reason=EARLY_ASYMMETRY ampL=${fmt1((repMaxL - repMinL).coerceAtLeast(0f))} ampR=${fmt1((repMaxR - repMinR).coerceAtLeast(0f))}"
                )
            } else {
                val rejectReason = validateCurrentRep(ts)
                if (rejectReason == null) {
                    reps += 1
                    repCompleted = true
                    logRepAmplitude()
                } else {
                    repRejectEvent = rejectReason
                    logRepRejected()
                }
            }
            pendingL = 0
            pendingR = 0
            resetRepRange()
            resetRangeEval()
            resetUnderTopAttempt()
        } else {
            if ((pendingL == 1 && pendingR == 0) || (pendingR == 1 && pendingL == 0)) {
                repRejectEvent = RepRejectReason.ASYNC_ARMS
            }
            if (pendingL > 0) pendingL--
            if (pendingR > 0) pendingR--
        }

        if (repRejectEvent == null) {
            repRejectEvent = updateUnderTopFailedAttempt(smoothL, smoothR, bothPrimed, repCompleted)
        }

        if (!bothPrimed) {
            // Do not allow stale one-arm triggers to survive while one side is unprimed.
            pendingL = 0
            pendingR = 0
            resetRepRange()
            resetRangeEval()
            resetUnderTopAttempt()
        }

        val conditionCode = when {
            !bothPrimed -> ConditionCode.BAD_START
            repCompleted -> ConditionCode.OK
            isRangeTooSmall(smoothL, smoothR) -> ConditionCode.RANGE_TOO_SMALL
            else -> ConditionCode.OK
        }

        emitFrame(
            normPoints = normPoints,
            rawNormPoints = rawNormPoints,
            leftAngle = emaL,
            rightAngle = emaR,
            latencyMs = latency,
            conditionCode = conditionCode,
            repRejectEvent = repRejectEvent
        )
    }

    private fun emitFrame(
        normPoints: List<PointF>,
        rawNormPoints: List<PointF>,
        leftAngle: Float?,
        rightAngle: Float?,
        latencyMs: Float,
        conditionCode: ConditionCode,
        repRejectEvent: RepRejectReason? = null
    ) {
        val repProgress = calculateRepProgress(leftAngle, rightAngle)
        onFrame(
            FrameResult(
                normPoints = normPoints,
                rawNormPoints = rawNormPoints,
                leftAngle = leftAngle,
                rightAngle = rightAngle,
                repProgress = repProgress,
                reps = reps,
                fps = fps,
                latencyMs = latencyMs,
                pipeline = "MediaPipe",
                conditionCode = conditionCode,
                reasonEvent = if (emitReasonEvents) buildReasonEvent(conditionCode) else null,
                repRejectEvent = repRejectEvent,
                isFrontCamera = isFrontCamera,
                srcWidth = frameW,
                srcHeight = frameH
            )
        )
    }

    private fun onTrackingGap(forceTrackingLost: Boolean = false) {
        // If tracking breaks, never keep pending sync window alive.
        pendingL = 0
        pendingR = 0
        resetRepRange()
        resetRangeEval()
        resetUnderTopAttempt()
        trackingGapFrames += 1
        if (forceTrackingLost && trackingGapFrames < trackingLostFramesForCondition) {
            trackingGapFrames = trackingLostFramesForCondition
        }
        if (trackingGapFrames == resetStateAfterGapFrames) {
            fsmL.reset()
            fsmR.reset()
            emaL = null
            emaR = null
            Log.d("AirFloat", "Tracking gap: rep FSM state reset")
        }
    }

    private fun onTrackingRecovered() {
        trackingGapFrames = 0
    }

    private fun buildReasonEvent(conditionCode: ConditionCode): ConditionCode? {
        val transitioned = conditionCode != lastConditionCode
        val now = SystemClock.uptimeMillis()
        val event =
            if (conditionCode != ConditionCode.OK && transitioned && canEmitReasonEvent(conditionCode, now)) {
                markReasonEventTime(conditionCode, now)
                conditionCode
            } else {
                null
            }
        lastConditionCode = conditionCode
        if (event != null) {
            Log.i("AirFloatCond", "reasonEvent=$event reps=$reps")
        }
        return event
    }

    private fun conditionForGap(): ConditionCode {
        // Ignore micro-dropouts: they should not instantly flip UI to tracking-lost.
        return if (trackingGapFrames >= trackingLostFramesForCondition) {
            ConditionCode.TRACKING_LOST
        } else {
            lastConditionCode
        }
    }

    private fun isRangeTooSmall(left: Float, right: Float): Boolean {
        updateRangeEval(left, right)
        if (rangeEvalFrames < rangeEvalWindowFrames) return false

        val ampL = rangeMaxL - rangeMinL
        val ampR = rangeMaxR - rangeMinR
        val tooSmallWindow = ampL < minRangeAmplitudeDeg || ampR < minRangeAmplitudeDeg
        if (tooSmallWindow) {
            rangeTooSmallWindowStreak += 1
        } else {
            rangeTooSmallWindowStreak = 0
        }

        val rangeTooSmall = rangeTooSmallWindowStreak >= rangeTooSmallWindowsRequired
        Log.d(
            "AirFloatCond",
            "rangeWindow ampL=${fmt1(ampL)} ampR=${fmt1(ampR)} tooSmallWindow=$tooSmallWindow streak=$rangeTooSmallWindowStreak result=$rangeTooSmall"
        )

        // Start a fresh window from current sample.
        rangeEvalFrames = 1
        rangeMinL = left
        rangeMaxL = left
        rangeMinR = right
        rangeMaxR = right
        return rangeTooSmall
    }

    private fun updateRangeEval(left: Float, right: Float) {
        if (rangeEvalFrames == 0) {
            rangeEvalFrames = 1
            rangeMinL = left
            rangeMaxL = left
            rangeMinR = right
            rangeMaxR = right
            return
        }
        rangeEvalFrames += 1
        if (left < rangeMinL) rangeMinL = left
        if (left > rangeMaxL) rangeMaxL = left
        if (right < rangeMinR) rangeMinR = right
        if (right > rangeMaxR) rangeMaxR = right
    }

    private fun resetRangeEval() {
        rangeEvalFrames = 0
        rangeMinL = Float.POSITIVE_INFINITY
        rangeMaxL = Float.NEGATIVE_INFINITY
        rangeMinR = Float.POSITIVE_INFINITY
        rangeMaxR = Float.NEGATIVE_INFINITY
        rangeTooSmallWindowStreak = 0
    }

    private fun updateRepRange(left: Float, right: Float, nowTs: Long) {
        if (repRangeStartTs == 0L) {
            repRangeStartTs = nowTs
            repRangeFrames = 0
        }
        repRangeFrames += 1
        if (left < repMinL) repMinL = left
        if (left > repMaxL) repMaxL = left
        if (right < repMinR) repMinR = right
        if (right > repMaxR) repMaxR = right
        updateStartAsymmetryLatch()
    }

    private fun resetRepRange() {
        repMinL = Float.POSITIVE_INFINITY
        repMaxL = Float.NEGATIVE_INFINITY
        repMinR = Float.POSITIVE_INFINITY
        repMaxR = Float.NEGATIVE_INFINITY
        repRangeStartTs = 0L
        repRangeFrames = 0
        repStartedAsymmetric = false
    }

    private fun logRepAmplitude() {
        if (!repMinL.isFinite() || !repMaxL.isFinite() || !repMinR.isFinite() || !repMaxR.isFinite()) {
            return
        }
        val ampL = repMaxL - repMinL
        val ampR = repMaxR - repMinR
        Log.i(
            "AirFloatTune",
            "rep=$reps minL=${fmt1(repMinL)} maxL=${fmt1(repMaxL)} ampL=${fmt1(ampL)} minR=${fmt1(repMinR)} maxR=${fmt1(repMaxR)} ampR=${fmt1(ampR)} down=$downThreshold up=$upThreshold"
        )
    }

    private fun validateCurrentRep(nowTs: Long): RepRejectReason? {
        if (!repMinL.isFinite() || !repMaxL.isFinite() || !repMinR.isFinite() || !repMaxR.isFinite()) {
            return RepRejectReason.INVALID_BOTTOM_ANGLE
        }

        // Reject implausibly deep elbow "folds" caused by perspective glitches (e.g. leaning into camera).
        if (repMinL < minRepBottomAngleDeg || repMinR < minRepBottomAngleDeg) {
            return RepRejectReason.INVALID_BOTTOM_ANGLE
        }

        val durationMs = if (repRangeStartTs > 0L) nowTs - repRangeStartTs else Long.MAX_VALUE
        if (durationMs in 1 until minRepDurationMs) {
            Log.i(
                "AirFloatTune",
                "repRejected reason=TOO_FAST durationMs=$durationMs minMs=$minRepDurationMs"
            )
            return RepRejectReason.TOO_FAST
        }

        val ampL = (repMaxL - repMinL).coerceAtLeast(0f)
        val ampR = (repMaxR - repMinR).coerceAtLeast(0f)
        val hi = max(ampL, ampR)
        val lo = min(ampL, ampR)
        if (hi < minUnderTopTravelDeg) {
            Log.i(
                "AirFloatTune",
                "repRejected reason=INSUFFICIENT_TOP_TRAVEL ampL=${fmt1(ampL)} ampR=${fmt1(ampR)} minTravel=${fmt1(minUnderTopTravelDeg)}"
            )
            return RepRejectReason.INSUFFICIENT_TOP
        }
        val ratio = lo / hi.coerceAtLeast(1f)
        if (ratio < minRangeSymmetryRatio) {
            Log.i(
                "AirFloatTune",
                "repRejected reason=ASYMMETRIC_RANGE ampL=${fmt1(ampL)} ampR=${fmt1(ampR)} ratio=${fmt1(ratio)} minRatio=$minRangeSymmetryRatio"
            )
            return RepRejectReason.ASYMMETRIC_RANGE
        }

        return null
    }

    private fun logRepRejected() {
        Log.i(
            "AirFloatTune",
            "repRejected minL=${fmt1(repMinL)} maxL=${fmt1(repMaxL)} minR=${fmt1(repMinR)} maxR=${fmt1(repMaxR)} down=$downThreshold up=$upThreshold"
        )
    }

    private fun updateUnderTopFailedAttempt(
        left: Float,
        right: Float,
        bothPrimed: Boolean,
        repCompleted: Boolean
    ): RepRejectReason? {
        if (!bothPrimed || repCompleted) {
            resetUnderTopAttempt()
            return null
        }

        if (!underTopAttemptActive) {
            if (left < downThreshold && right < downThreshold) {
                underTopAttemptActive = true
                underTopPeakL = left
                underTopPeakR = right
            }
            return null
        }

        if (left > underTopPeakL) underTopPeakL = left
        if (right > underTopPeakR) underTopPeakR = right

        val returnedToBottom = left < downThreshold && right < downThreshold
        if (!returnedToBottom) return null

        if (repStartedAsymmetric) {
            Log.i(
                "AirFloatTune",
                "press cycleIgnored reason=EARLY_ASYMMETRY_RETURN peakL=${fmt1(underTopPeakL)} peakR=${fmt1(underTopPeakR)}"
            )
            resetUnderTopAttempt()
            return null
        }

        val travelL = underTopPeakL - downThreshold
        val travelR = underTopPeakR - downThreshold
        val meaningfulAttempt = travelL >= minUnderTopTravelDeg || travelR >= minUnderTopTravelDeg
        val topGate = upThreshold - underTopTopSlackDeg
        val reachedTopOnBoth = underTopPeakL >= topGate && underTopPeakR >= topGate
        val rejected = meaningfulAttempt && !reachedTopOnBoth
        if (!rejected) {
            resetUnderTopAttempt()
            return null
        }

        val hi = max(travelL.coerceAtLeast(0f), travelR.coerceAtLeast(0f)).coerceAtLeast(1f)
        val lo = min(travelL.coerceAtLeast(0f), travelR.coerceAtLeast(0f))
        val symmetryRatio = lo / hi
        val reason = if (symmetryRatio < minRangeSymmetryRatio) {
            RepRejectReason.ASYMMETRIC_RANGE
        } else {
            RepRejectReason.INSUFFICIENT_TOP
        }

        if (reason == RepRejectReason.ASYMMETRIC_RANGE) {
            Log.i(
                "AirFloatTune",
                "repRejected reason=ASYMMETRIC_RANGE peakL=${fmt1(underTopPeakL)} peakR=${fmt1(underTopPeakR)} ratio=${fmt1(symmetryRatio)} minRatio=$minRangeSymmetryRatio down=$downThreshold up=$upThreshold"
            )
        } else {
            Log.i(
                "AirFloatTune",
                "repRejected reason=INSUFFICIENT_TOP peakL=${fmt1(underTopPeakL)} peakR=${fmt1(underTopPeakR)} down=$downThreshold up=$upThreshold"
            )
        }
        resetUnderTopAttempt()
        return reason
    }

    private fun resetUnderTopAttempt() {
        underTopAttemptActive = false
        underTopPeakL = Float.NEGATIVE_INFINITY
        underTopPeakR = Float.NEGATIVE_INFINITY
    }

    private fun updateStartAsymmetryLatch() {
        if (repStartedAsymmetric) return
        if (repRangeFrames <= 0 || repRangeFrames > startAsymmetryWindowFrames) return
        if (!repMinL.isFinite() || !repMaxL.isFinite() || !repMinR.isFinite() || !repMaxR.isFinite()) return

        val ampL = (repMaxL - repMinL).coerceAtLeast(0f)
        val ampR = (repMaxR - repMinR).coerceAtLeast(0f)
        val hi = max(ampL, ampR)
        if (hi < startAsymmetryTravelDeg) return

        val lo = min(ampL, ampR)
        val ratio = lo / hi.coerceAtLeast(1f)
        if (lo < startAsymmetryTravelDeg * 0.5f || ratio < minRangeSymmetryRatio) {
            repStartedAsymmetric = true
            Log.i(
                "AirFloatTune",
                "press earlyAsymmetry latched ampL=${fmt1(ampL)} ampR=${fmt1(ampR)} ratio=${fmt1(ratio)} frames=$repRangeFrames"
            )
        }
    }

    private fun fmt1(value: Float): String {
        return String.format(java.util.Locale.US, "%.1f", value)
    }

    private fun calculateRepProgress(left: Float?, right: Float?): Float {
        val leftP = angleProgress(left)
        val rightP = angleProgress(right)
        // Progress should reflect weaker arm to avoid optimistic gauge.
        return min(leftP, rightP)
    }

    private fun angleProgress(angle: Float?): Float {
        if (angle == null) return 0f
        val lower = min(gaugeBottomAngleDeg, upThreshold - 1f)
        val span = (upThreshold - lower).coerceAtLeast(1f)
        return ((angle - lower) / span).coerceIn(0f, 1f)
    }

    private fun canEmitReasonEvent(code: ConditionCode, now: Long): Boolean {
        return when (code) {
            ConditionCode.TRACKING_LOST -> now - lastTrackingLostEventTs >= trackingLostEventCooldownMs
            ConditionCode.BAD_START -> now - lastBadStartEventTs >= badStartEventCooldownMs
            ConditionCode.RANGE_TOO_SMALL -> now - lastRangeTooSmallEventTs >= rangeTooSmallEventCooldownMs
            ConditionCode.OK -> false
        }
    }

    private fun markReasonEventTime(code: ConditionCode, now: Long) {
        when (code) {
            ConditionCode.TRACKING_LOST -> lastTrackingLostEventTs = now
            ConditionCode.BAD_START -> lastBadStartEventTs = now
            ConditionCode.RANGE_TOO_SMALL -> lastRangeTooSmallEventTs = now
            ConditionCode.OK -> Unit
        }
    }

    private fun hasReliablePoints(points: List<PointF>): Boolean {
        for (idx in trackingPointIndices) {
            if (idx < 0 || idx >= points.size) return false
            val p = points[idx]
            if (!p.x.isFinite() || !p.y.isFinite()) return false
            if (p.x < -0.1f || p.x > 1.1f) return false
            if (p.y < -0.1f || p.y > 1.1f) return false
        }
        return true
    }

    private fun areArmsDown(points: List<PointF>): Boolean {
        if (points.size <= 16) return false
        val leftDown = (points[15].y - points[11].y) > armsDownWristOffset
        val rightDown = (points[16].y - points[12].y) > armsDownWristOffset
        return leftDown && rightDown
    }

    private fun sanitizeAngle(a: Float?): Float? {
        if (a == null) return null
        if (a < 5f || a > 179f) return null
        return a
    }

    private fun ema(prev: Float?, newV: Float?, alpha: Float): Float? {
        if (newV == null) return prev
        if (prev == null) return newV
        return alpha * newV + (1f - alpha) * prev
    }

    private fun angleDeg(a: PointF, b: PointF, c: PointF): Float? {
        val bax = a.x - b.x
        val bay = a.y - b.y
        val bcx = c.x - b.x
        val bcy = c.y - b.y

        val normBA = sqrt(bax * bax + bay * bay)
        val normBC = sqrt(bcx * bcx + bcy * bcy)
        if (normBA < 1e-6f || normBC < 1e-6f) return null

        var cosT = (bax * bcx + bay * bcy) / (normBA * normBC)
        cosT = min(1f, max(-1f, cosT))

        val ang = acos(cosT) * (180f / Math.PI.toFloat())
        return ang
    }
}

private class AngleFSM(
    private var down: Float,
    private var up: Float,
    private val holdFrames: Int
) {
    // state: 0 wait for Up (arms raised), 1 wait for Down, 2 wait for Up to finish rep
    private var state = 0
    private var hold = 0

    fun reset() {
        state = 0
        hold = 0
    }

    fun isPrimed(): Boolean {
        return state != 0
    }

    fun update(angle: Float?): Boolean {
        if (angle == null) return false

        when (state) {
            0 -> { // need initial Up
                if (angle > up) {
                    state = 1
                    hold = 0
                }
                return false
            }
            1 -> { // looking for Down
                if (angle < down) {
                    state = 2
                    hold = 0
                }
                return false
            }
            2 -> { // looking for Up to complete rep
                if (angle > up) {
                    hold += 1
                    val req = if (holdFrames > 0) holdFrames else 1
                    if (hold >= req) {
                        state = 1 // stay primed for next Down->Up
                        hold = 0
                        return true
                    }
                } else if (angle < down) {
                    // dropped back down, stay in state 2 but reset hold
                    hold = 0
                }
            }
        }
        return false
    }
}
