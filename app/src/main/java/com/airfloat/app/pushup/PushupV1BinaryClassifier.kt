package com.airfloat.app.pushup

import android.content.Context
import android.graphics.PointF
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class PushupQualityLabel {
    GOOD,
    BAD,
    UNKNOWN
}

data class PushupBinaryPrediction(
    val quality: PushupQualityLabel,
    val qualityName: String,
    val badScoreRaw: Float,
    val badScoreSmoothed: Float,
    val confidence: Float
)

/**
 * Push-up quality model (GOOD/BAD with UNKNOWN band near decision threshold).
 * Model payload: app/src/main/assets/pushup_v1_binary_model.json
 */
class PushupV1BinaryClassifier private constructor(
    private val featureNames: List<String>,
    private val mean: FloatArray,
    private val std: FloatArray,
    private val weights: FloatArray,
    private val badThreshold: Float,
    private val unknownMargin: Float,
    private val emaAlpha: Float
) {

    companion object {
        private const val TAG = "AirFloatPushup"
        private const val MODEL_ASSET = "pushup_v1_binary_model.json"

        // BlazePose landmarks.
        private const val L_SHOULDER = 11
        private const val R_SHOULDER = 12
        private const val L_ELBOW = 13
        private const val R_ELBOW = 14
        private const val L_WRIST = 15
        private const val R_WRIST = 16
        private const val L_HIP = 23
        private const val R_HIP = 24
        private const val L_KNEE = 25
        private const val R_KNEE = 26
        private const val L_ANKLE = 27
        private const val R_ANKLE = 28

        private val SERIES_METRICS = listOf(
            "elbow_avg",
            "elbow_diff",
            "body_line_avg",
            "body_line_diff",
            "hip_sag_norm",
            "wrist_drop_norm",
            "arm_extension_norm",
            "torso_to_ankle_norm"
        )
        private val STAT_SUFFIXES = listOf("mean", "std", "min", "max", "p10", "p90", "range", "vel_mean")

        fun fromAsset(context: Context): PushupV1BinaryClassifier? {
            return try {
                val raw = context.assets.open(MODEL_ASSET).bufferedReader().use { it.readText() }
                val json = JSONObject(raw)

                val featureNames = jsonArrayToStringList(json.getJSONArray("feature_names"))
                val stdBlock = json.getJSONObject("standardization")
                val mean = jsonArrayToFloatArray(stdBlock.getJSONArray("mean"))
                val std = jsonArrayToFloatArray(stdBlock.getJSONArray("std"))
                val weights = jsonArrayToFloatArray(json.getJSONArray("weights"))
                val badThreshold = json.optDouble("bad_threshold", 0.50).toFloat().coerceIn(0.1f, 0.9f)
                val unknownMargin = json.optDouble("unknown_confidence_margin", 0.10).toFloat().coerceIn(0.04f, 0.3f)
                val emaAlpha = json.optDouble("ema_alpha", 0.30).toFloat().coerceIn(0.05f, 1f)

                if (featureNames.isEmpty() ||
                    mean.size != featureNames.size ||
                    std.size != featureNames.size ||
                    weights.size != featureNames.size + 1
                ) {
                    Log.e(
                        TAG,
                        "Pushup model payload invalid: features=${featureNames.size} mean=${mean.size} std=${std.size} weights=${weights.size}"
                    )
                    null
                } else {
                    Log.i(
                        TAG,
                        "Loaded pushup v1 model: features=${featureNames.size} badThreshold=$badThreshold margin=$unknownMargin ema=$emaAlpha"
                    )
                    PushupV1BinaryClassifier(
                        featureNames = featureNames,
                        mean = mean,
                        std = std,
                        weights = weights,
                        badThreshold = badThreshold,
                        unknownMargin = unknownMargin,
                        emaAlpha = emaAlpha
                    )
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to load pushup v1 model", t)
                null
            }
        }

        private fun jsonArrayToFloatArray(arr: JSONArray): FloatArray {
            val out = FloatArray(arr.length())
            for (i in 0 until arr.length()) out[i] = arr.optDouble(i, 0.0).toFloat()
            return out
        }

        private fun jsonArrayToStringList(arr: JSONArray): List<String> {
            val out = ArrayList<String>(arr.length())
            for (i in 0 until arr.length()) out += arr.optString(i, "")
            return out
        }
    }

    private data class FrameMetrics(
        val elbowAvg: Float,
        val elbowDiff: Float,
        val bodyLineAvg: Float,
        val bodyLineDiff: Float,
        val hipSagNorm: Float,
        val wristDropNorm: Float,
        val armExtensionNorm: Float,
        val torsoToAnkleNorm: Float,
        val valid: Boolean,
        val zeroFrame: Boolean
    )

    private val history = ArrayDeque<FrameMetrics>()
    private val maxFrames = 150
    private val minFramesForPrediction = 36

    private var emaBad: Float? = null
    private var stableQuality: PushupQualityLabel = PushupQualityLabel.UNKNOWN
    private var lastPrediction: PushupBinaryPrediction? = null
    private var lastFeatureMap: Map<String, Float>? = null

    fun reset() {
        history.clear()
        emaBad = null
        stableQuality = PushupQualityLabel.UNKNOWN
        lastPrediction = null
        lastFeatureMap = null
    }

    fun lastPrediction(): PushupBinaryPrediction? = lastPrediction

    fun lastFeatureMap(): Map<String, Float>? = lastFeatureMap

    fun historySize(): Int = history.size

    fun minFramesForPrediction(): Int = minFramesForPrediction

    fun predict(normPoints: List<PointF>): PushupBinaryPrediction? {
        val frameMetrics = extractFrameMetrics(normPoints) ?: return null
        history.addLast(frameMetrics)
        while (history.size > maxFrames) history.removeFirst()

        if (history.size < minFramesForPrediction) return null
        val featureMap = buildFeatureMap()
        if (featureMap.isEmpty()) return null
        lastFeatureMap = featureMap

        val z = FloatArray(featureNames.size)
        for (i in featureNames.indices) {
            val x = featureMap[featureNames[i]] ?: 0f
            val denom = if (std[i] > 1e-6f) std[i] else 1f
            z[i] = (x - mean[i]) / denom
        }

        var logit = weights[0]
        for (i in z.indices) {
            logit += weights[i + 1] * z[i]
        }
        val rawBad = sigmoid(logit)
        val smoothedBad = ema(emaBad, rawBad, emaAlpha)
        emaBad = smoothedBad

        val distance = abs(smoothedBad - badThreshold)
        val enterBad = (badThreshold + unknownMargin).coerceAtMost(0.98f)
        val enterGood = (badThreshold - unknownMargin).coerceAtLeast(0.02f)
        val holdBand = (unknownMargin * 0.5f).coerceAtLeast(0.03f)
        val holdBad = (badThreshold - holdBand).coerceAtLeast(0.02f)
        val holdGood = (badThreshold + holdBand).coerceAtMost(0.98f)

        val quality =
            when (stableQuality) {
                PushupQualityLabel.BAD -> {
                    when {
                        smoothedBad >= holdBad -> PushupQualityLabel.BAD
                        smoothedBad <= enterGood -> PushupQualityLabel.GOOD
                        else -> PushupQualityLabel.UNKNOWN
                    }
                }
                PushupQualityLabel.GOOD -> {
                    when {
                        smoothedBad <= holdGood -> PushupQualityLabel.GOOD
                        smoothedBad >= enterBad -> PushupQualityLabel.BAD
                        else -> PushupQualityLabel.UNKNOWN
                    }
                }
                PushupQualityLabel.UNKNOWN -> {
                    when {
                        smoothedBad >= enterBad -> PushupQualityLabel.BAD
                        smoothedBad <= enterGood -> PushupQualityLabel.GOOD
                        else -> PushupQualityLabel.UNKNOWN
                    }
                }
            }
        stableQuality = quality

        val maxDistance = max(badThreshold, 1f - badThreshold).coerceAtLeast(1e-3f)
        val confidence = (distance / maxDistance).coerceIn(0f, 1f)
        val qualityName =
            when (quality) {
                PushupQualityLabel.GOOD -> "good"
                PushupQualityLabel.BAD -> "bad"
                PushupQualityLabel.UNKNOWN -> "unknown"
            }

        val prediction = PushupBinaryPrediction(
            quality = quality,
            qualityName = qualityName,
            badScoreRaw = rawBad,
            badScoreSmoothed = smoothedBad,
            confidence = confidence
        )
        lastPrediction = prediction
        return prediction
    }

    private fun buildFeatureMap(): MutableMap<String, Float> {
        val map = LinkedHashMap<String, Float>(featureNames.size)
        val h = history.toList()
        val n = h.size

        val series = LinkedHashMap<String, FloatArray>(SERIES_METRICS.size)
        series["elbow_avg"] = h.map { it.elbowAvg }.toFloatArray()
        series["elbow_diff"] = h.map { it.elbowDiff }.toFloatArray()
        series["body_line_avg"] = h.map { it.bodyLineAvg }.toFloatArray()
        series["body_line_diff"] = h.map { it.bodyLineDiff }.toFloatArray()
        series["hip_sag_norm"] = h.map { it.hipSagNorm }.toFloatArray()
        series["wrist_drop_norm"] = h.map { it.wristDropNorm }.toFloatArray()
        series["arm_extension_norm"] = h.map { it.armExtensionNorm }.toFloatArray()
        series["torso_to_ankle_norm"] = h.map { it.torsoToAnkleNorm }.toFloatArray()

        for (metric in SERIES_METRICS) {
            val s = series[metric] ?: continue
            val stats = statsFeatures(s)
            for (i in STAT_SUFFIXES.indices) {
                map["${metric}_${STAT_SUFFIXES[i]}"] = stats[i]
            }
        }

        val validFrameRatio = h.count { it.valid }.toFloat() / max(1, n).toFloat()
        val zeroFrameRatio = h.count { it.zeroFrame }.toFloat() / max(1, n).toFloat()
        val elbowSeries = series["elbow_avg"] ?: FloatArray(0)
        val cycle = estimateCycles(elbowSeries)
        val elbowFilled = interpolateNans(elbowSeries)
        val cycleDepth = percentile(elbowFilled, 90f) - percentile(elbowFilled, 10f)

        map["valid_frame_ratio"] = validFrameRatio
        map["zero_frame_ratio"] = zeroFrameRatio
        map["estimated_cycles"] = cycle.first
        map["tempo_per_frame"] = if (n > 0) cycle.first / n.toFloat() else 0f
        map["elbow_cycle_depth"] = cycleDepth
        map["elbow_motion_energy"] = cycle.second
        return map
    }

    private fun statsFeatures(series: FloatArray): FloatArray {
        val finite = series.filter { it.isFinite() }
        if (finite.size < 3) return FloatArray(STAT_SUFFIXES.size) { 0f }

        val sorted = finite.sorted()
        val mean = finite.sum() / finite.size
        val variance = finite.fold(0f) { acc, v -> acc + (v - mean) * (v - mean) } / finite.size
        val std = sqrt(max(variance, 0f))
        val minV = sorted.first()
        val maxV = sorted.last()
        val p10 = percentile(sorted.toFloatArray(), 10f)
        val p90 = percentile(sorted.toFloatArray(), 90f)
        val range = maxV - minV

        val filled = interpolateNans(series)
        var vel = 0f
        if (filled.size > 1) {
            var acc = 0f
            for (i in 1 until filled.size) acc += abs(filled[i] - filled[i - 1])
            vel = acc / (filled.size - 1)
        }

        return floatArrayOf(mean, std, minV, maxV, p10, p90, range, vel)
    }

    private fun estimateCycles(series: FloatArray): Pair<Float, Float> {
        val filled = interpolateNans(series)
        val smooth = movingAverage(filled, 5)
        if (smooth.size < 5) return 0f to 0f

        val minV = smooth.minOrNull() ?: return 0f to 0f
        val maxV = smooth.maxOrNull() ?: return 0f to 0f
        val range = maxV - minV
        if (range < 1e-3f) return 0f to 0f

        val minProm = max(3f, 0.12f * range)
        var cycles = 0
        var lastIdx = -10
        for (i in 2 until smooth.size - 2) {
            if (smooth[i] <= smooth[i - 1] && smooth[i] <= smooth[i + 1]) {
                val lo = max(0, i - 4)
                val hi = min(smooth.size, i + 5)
                val localMax = smooth.copyOfRange(lo, hi).maxOrNull() ?: smooth[i]
                if (localMax - smooth[i] >= minProm && (i - lastIdx) >= 4) {
                    cycles += 1
                    lastIdx = i
                }
            }
        }

        var motion = 0f
        if (smooth.size > 1) {
            for (i in 1 until smooth.size) motion += abs(smooth[i] - smooth[i - 1])
            motion /= (smooth.size - 1)
        }
        return cycles.toFloat() to motion
    }

    private fun movingAverage(series: FloatArray, window: Int): FloatArray {
        if (series.isEmpty()) return series
        if (window <= 1) return series.copyOf()
        val pad = window / 2
        val out = FloatArray(series.size)
        for (i in series.indices) {
            val from = max(0, i - pad)
            val to = min(series.lastIndex, i + pad)
            var sum = 0f
            var c = 0
            for (j in from..to) {
                sum += series[j]
                c += 1
            }
            out[i] = if (c > 0) sum / c else series[i]
        }
        return out
    }

    private fun interpolateNans(series: FloatArray): FloatArray {
        if (series.isEmpty()) return series
        val out = series.copyOf()
        val validIdx = ArrayList<Int>()
        for (i in out.indices) if (out[i].isFinite()) validIdx += i
        if (validIdx.isEmpty()) return FloatArray(out.size) { 0f }
        if (validIdx.size == 1) {
            val v = out[validIdx[0]]
            for (i in out.indices) out[i] = v
            return out
        }

        var first = validIdx.first()
        for (i in 0 until first) out[i] = out[first]
        for (k in 0 until validIdx.size - 1) {
            val i0 = validIdx[k]
            val i1 = validIdx[k + 1]
            val v0 = out[i0]
            val v1 = out[i1]
            val span = (i1 - i0).coerceAtLeast(1)
            for (i in i0..i1) {
                val t = (i - i0).toFloat() / span.toFloat()
                out[i] = v0 + (v1 - v0) * t
            }
        }
        val last = validIdx.last()
        for (i in last + 1 until out.size) out[i] = out[last]
        return out
    }

    private fun percentile(values: FloatArray, p: Float): Float {
        if (values.isEmpty()) return 0f
        val clean = values.filter { it.isFinite() }.sorted()
        if (clean.isEmpty()) return 0f
        if (clean.size == 1) return clean[0]
        val rank = (p.coerceIn(0f, 100f) / 100f) * (clean.size - 1)
        val lo = floor(rank).toInt()
        val hi = ceil(rank).toInt()
        if (lo == hi) return clean[lo]
        val t = rank - lo.toFloat()
        return clean[lo] + (clean[hi] - clean[lo]) * t
    }

    private fun extractFrameMetrics(points: List<PointF>): FrameMetrics? {
        if (points.size <= R_ANKLE) return null
        val zeroFrame = points.all { !it.x.isFinite() || !it.y.isFinite() || (abs(it.x) < 1e-9f && abs(it.y) < 1e-9f) }

        val ls = safePoint(points, L_SHOULDER)
        val rs = safePoint(points, R_SHOULDER)
        val le = safePoint(points, L_ELBOW)
        val re = safePoint(points, R_ELBOW)
        val lw = safePoint(points, L_WRIST)
        val rw = safePoint(points, R_WRIST)
        val lh = safePoint(points, L_HIP)
        val rh = safePoint(points, R_HIP)
        val la = safePoint(points, L_ANKLE)
        val ra = safePoint(points, R_ANKLE)

        val leftElbow = angleDeg(ls, le, lw)
        val rightElbow = angleDeg(rs, re, rw)
        val bodyLineL = angleDeg(ls, lh, la)
        val bodyLineR = angleDeg(rs, rh, ra)

        val shoulderMid = midpoint(ls, rs)
        val hipMid = midpoint(lh, rh)
        val ankleMid = midpoint(la, ra)
        val torsoLen = dist(shoulderMid, hipMid)

        val elbowAvg = meanFinite(leftElbow, rightElbow)
        val elbowDiff = if (leftElbow.isFinite() && rightElbow.isFinite()) abs(leftElbow - rightElbow) else Float.NaN
        val bodyAvg = meanFinite(bodyLineL, bodyLineR)
        val bodyDiff = if (bodyLineL.isFinite() && bodyLineR.isFinite()) abs(bodyLineL - bodyLineR) else Float.NaN

        val hipSagNorm =
            if (shoulderMid != null && hipMid != null && ankleMid != null && torsoLen.isFinite() && torsoLen > 1e-6f) {
                val yLine = yOnLine(hipMid.x, shoulderMid, ankleMid)
                if (yLine.isFinite()) (hipMid.y - yLine) / torsoLen else Float.NaN
            } else {
                Float.NaN
            }

        val wristDropNorm =
            if (torsoLen.isFinite() && torsoLen > 1e-6f) {
                meanFinite(
                    if (ls != null && lw != null) (lw.y - ls.y) / torsoLen else Float.NaN,
                    if (rs != null && rw != null) (rw.y - rs.y) / torsoLen else Float.NaN
                )
            } else {
                Float.NaN
            }

        val armExtensionNorm =
            if (torsoLen.isFinite() && torsoLen > 1e-6f) {
                meanFinite(
                    if (ls != null && lw != null) dist(ls, lw) / torsoLen else Float.NaN,
                    if (rs != null && rw != null) dist(rs, rw) / torsoLen else Float.NaN
                )
            } else {
                Float.NaN
            }

        val torsoToAnkleNorm =
            if (torsoLen.isFinite() && torsoLen > 1e-6f) {
                meanFinite(
                    if (lh != null && la != null) dist(lh, la) / torsoLen else Float.NaN,
                    if (rh != null && ra != null) dist(rh, ra) / torsoLen else Float.NaN
                )
            } else {
                Float.NaN
            }

        val valid = listOf(
            elbowAvg,
            elbowDiff,
            bodyAvg,
            bodyDiff,
            hipSagNorm,
            wristDropNorm,
            armExtensionNorm,
            torsoToAnkleNorm
        ).any { it.isFinite() }

        return FrameMetrics(
            elbowAvg = elbowAvg,
            elbowDiff = elbowDiff,
            bodyLineAvg = bodyAvg,
            bodyLineDiff = bodyDiff,
            hipSagNorm = hipSagNorm,
            wristDropNorm = wristDropNorm,
            armExtensionNorm = armExtensionNorm,
            torsoToAnkleNorm = torsoToAnkleNorm,
            valid = valid,
            zeroFrame = zeroFrame
        )
    }

    private fun safePoint(points: List<PointF>, idx: Int): PointF? {
        if (idx < 0 || idx >= points.size) return null
        val p = points[idx]
        if (!p.x.isFinite() || !p.y.isFinite()) return null
        return p
    }

    private fun meanFinite(a: Float, b: Float): Float {
        return when {
            a.isFinite() && b.isFinite() -> (a + b) * 0.5f
            a.isFinite() -> a
            b.isFinite() -> b
            else -> Float.NaN
        }
    }

    private fun dist(a: PointF?, b: PointF?): Float {
        if (a == null || b == null) return Float.NaN
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun midpoint(a: PointF?, b: PointF?): PointF? {
        if (a == null || b == null) return null
        return PointF((a.x + b.x) * 0.5f, (a.y + b.y) * 0.5f)
    }

    private fun yOnLine(xTarget: Float, p1: PointF, p2: PointF): Float {
        val dx = p2.x - p1.x
        if (abs(dx) < 1e-8f) return Float.NaN
        val t = (xTarget - p1.x) / dx
        return p1.y + (p2.y - p1.y) * t
    }

    private fun angleDeg(a: PointF?, b: PointF?, c: PointF?): Float {
        if (a == null || b == null || c == null) return Float.NaN
        val bax = a.x - b.x
        val bay = a.y - b.y
        val bcx = c.x - b.x
        val bcy = c.y - b.y
        val normBA = sqrt(bax * bax + bay * bay)
        val normBC = sqrt(bcx * bcx + bcy * bcy)
        if (normBA < 1e-6f || normBC < 1e-6f) return Float.NaN
        var cosTheta = (bax * bcx + bay * bcy) / (normBA * normBC)
        cosTheta = min(1f, max(-1f, cosTheta))
        return Math.toDegrees(acos(cosTheta).toDouble()).toFloat()
    }

    private fun orientationDeg(from: PointF, to: PointF): Float {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val n = sqrt(dx * dx + dy * dy)
        if (n < 1e-6f) return 90f

        val raw = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
        var a = raw
        if (a < 0f) a += 360f
        if (a > 180f) a -= 180f
        return a.coerceIn(0f, 180f)
    }

    private fun sigmoid(x: Float): Float {
        return if (x >= 0f) {
            val e = exp(-x)
            1f / (1f + e)
        } else {
            val e = exp(x)
            e / (1f + e)
        }
    }

    private fun ema(prev: Float?, newValue: Float, alpha: Float): Float {
        if (prev == null) return newValue
        return alpha * newValue + (1f - alpha) * prev
    }
}
