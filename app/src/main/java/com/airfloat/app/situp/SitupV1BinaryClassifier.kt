package com.airfloat.app.situp

import android.content.Context
import android.graphics.PointF
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class SitupQualityLabel {
    GOOD,
    BAD,
    UNKNOWN
}

data class SitupBinaryPrediction(
    val quality: SitupQualityLabel,
    val qualityName: String,
    val badScoreRaw: Float,
    val badScoreSmoothed: Float,
    val confidence: Float
)

/**
 * Sit-up quality model (GOOD/BAD with UNKNOWN band near threshold).
 * Model payload: app/src/main/assets/situp_v1_binary_model.json
 */
class SitupV1BinaryClassifier private constructor(
    private val featureNames: List<String>,
    private val mean: FloatArray,
    private val std: FloatArray,
    private val weights: FloatArray,
    private val badThreshold: Float,
    private val unknownMargin: Float,
    private val emaAlpha: Float
) {

    companion object {
        private const val TAG = "AirFloatSitup"
        private const val MODEL_ASSET = "situp_v1_binary_model.json"

        // BlazePose landmarks.
        private const val NOSE = 0
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
            "hip_flex_avg",
            "hip_flex_diff",
            "knee_avg",
            "knee_diff",
            "torso_vertical_deg",
            "wrist_to_knee_norm",
            "shoulder_to_knee_norm",
            "nose_to_knee_norm",
            "score_mean",
            "visible_points"
        )
        private val STAT_SUFFIXES = listOf("mean", "std", "min", "max", "p10", "p90", "range", "vel_mean")

        fun fromAsset(context: Context): SitupV1BinaryClassifier? {
            return try {
                val raw = context.assets.open(MODEL_ASSET).bufferedReader().use { it.readText() }
                val json = JSONObject(raw)

                val featureNames = jsonArrayToStringList(json.getJSONArray("feature_names"))
                val stdBlock = json.getJSONObject("standardization")
                val mean = jsonArrayToFloatArray(stdBlock.getJSONArray("mean"))
                val std = jsonArrayToFloatArray(stdBlock.getJSONArray("std"))
                val weights = jsonArrayToFloatArray(json.getJSONArray("weights"))
                val badThreshold = json.optDouble("bad_threshold", 0.50).toFloat().coerceIn(0.10f, 0.90f)
                val unknownMargin = json.optDouble("unknown_confidence_margin", 0.10).toFloat().coerceIn(0.04f, 0.30f)
                val emaAlpha = json.optDouble("ema_alpha", 0.30).toFloat().coerceIn(0.05f, 1f)

                if (featureNames.isEmpty() ||
                    mean.size != featureNames.size ||
                    std.size != featureNames.size ||
                    weights.size != featureNames.size + 1
                ) {
                    Log.e(
                        TAG,
                        "Situp model payload invalid: features=${featureNames.size} mean=${mean.size} std=${std.size} weights=${weights.size}"
                    )
                    null
                } else {
                    Log.i(
                        TAG,
                        "Loaded situp v1 model: features=${featureNames.size} badThreshold=$badThreshold margin=$unknownMargin ema=$emaAlpha"
                    )
                    SitupV1BinaryClassifier(
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
                Log.e(TAG, "Failed to load situp v1 model", t)
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
        val hipFlexAvg: Float,
        val hipFlexDiff: Float,
        val kneeAvg: Float,
        val kneeDiff: Float,
        val torsoVerticalDeg: Float,
        val wristToKneeNorm: Float,
        val shoulderToKneeNorm: Float,
        val noseToKneeNorm: Float,
        val scoreMean: Float,
        val visiblePoints: Float,
        val valid: Boolean,
        val zeroFrame: Boolean
    )

    private val history = ArrayDeque<FrameMetrics>()
    private val maxFrames = 160
    private val minFramesForPrediction = 24

    private var emaBad: Float? = null
    private var stableQuality: SitupQualityLabel = SitupQualityLabel.UNKNOWN
    private var lastPrediction: SitupBinaryPrediction? = null
    private var lastFeatureMap: Map<String, Float>? = null

    fun reset() {
        history.clear()
        emaBad = null
        stableQuality = SitupQualityLabel.UNKNOWN
        lastPrediction = null
        lastFeatureMap = null
    }

    fun lastPrediction(): SitupBinaryPrediction? = lastPrediction

    fun lastFeatureMap(): Map<String, Float>? = lastFeatureMap

    fun historySize(): Int = history.size

    fun minFramesForPrediction(): Int = minFramesForPrediction

    fun predict(normPoints: List<PointF>): SitupBinaryPrediction? {
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
            val standardized = (x - mean[i]) / denom
            z[i] = standardized.coerceIn(-8f, 8f)
        }

        var logit = weights[0]
        for (i in z.indices) logit += weights[i + 1] * z[i]
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
                SitupQualityLabel.BAD -> {
                    when {
                        smoothedBad >= holdBad -> SitupQualityLabel.BAD
                        smoothedBad <= enterGood -> SitupQualityLabel.GOOD
                        else -> SitupQualityLabel.UNKNOWN
                    }
                }
                SitupQualityLabel.GOOD -> {
                    when {
                        smoothedBad <= holdGood -> SitupQualityLabel.GOOD
                        smoothedBad >= enterBad -> SitupQualityLabel.BAD
                        else -> SitupQualityLabel.UNKNOWN
                    }
                }
                SitupQualityLabel.UNKNOWN -> {
                    when {
                        smoothedBad >= enterBad -> SitupQualityLabel.BAD
                        smoothedBad <= enterGood -> SitupQualityLabel.GOOD
                        else -> SitupQualityLabel.UNKNOWN
                    }
                }
            }
        stableQuality = quality

        val maxDistance = max(badThreshold, 1f - badThreshold).coerceAtLeast(1e-3f)
        val confidence = (distance / maxDistance).coerceIn(0f, 1f)
        val qualityName =
            when (quality) {
                SitupQualityLabel.GOOD -> "good"
                SitupQualityLabel.BAD -> "bad"
                SitupQualityLabel.UNKNOWN -> "unknown"
            }

        val prediction = SitupBinaryPrediction(
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
        if (n == 0) return map

        val series = LinkedHashMap<String, FloatArray>(SERIES_METRICS.size)
        series["hip_flex_avg"] = h.map { it.hipFlexAvg }.toFloatArray()
        series["hip_flex_diff"] = h.map { it.hipFlexDiff }.toFloatArray()
        series["knee_avg"] = h.map { it.kneeAvg }.toFloatArray()
        series["knee_diff"] = h.map { it.kneeDiff }.toFloatArray()
        series["torso_vertical_deg"] = h.map { it.torsoVerticalDeg }.toFloatArray()
        series["wrist_to_knee_norm"] = h.map { it.wristToKneeNorm }.toFloatArray()
        series["shoulder_to_knee_norm"] = h.map { it.shoulderToKneeNorm }.toFloatArray()
        series["nose_to_knee_norm"] = h.map { it.noseToKneeNorm }.toFloatArray()
        series["score_mean"] = h.map { it.scoreMean }.toFloatArray()
        series["visible_points"] = h.map { it.visiblePoints }.toFloatArray()

        for (metric in SERIES_METRICS) {
            val s = series[metric] ?: continue
            val stats = statsFeatures(s)
            for (i in STAT_SUFFIXES.indices) {
                map["${metric}_${STAT_SUFFIXES[i]}"] = stats[i]
            }
        }

        val validFrameRatio = h.count { it.valid }.toFloat() / max(1, n).toFloat()
        val zeroFrameRatio = h.count { it.zeroFrame }.toFloat() / max(1, n).toFloat()
        val hipSeries = series["hip_flex_avg"] ?: FloatArray(0)
        val cycle = estimateCycles(hipSeries)
        val hipFilled = interpolateNans(hipSeries)
        val hipCycleDepth = percentile(hipFilled, 90f) - percentile(hipFilled, 10f)

        map["valid_frame_ratio"] = validFrameRatio
        map["zero_frame_ratio"] = zeroFrameRatio
        map["estimated_cycles"] = cycle.first
        map["tempo_per_frame"] = if (n > 0) cycle.first / n.toFloat() else 0f
        map["hip_cycle_depth"] = hipCycleDepth
        map["hip_motion_energy"] = cycle.second
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
            for (i in 1 until filled.size) {
                acc += abs(filled[i] - filled[i - 1])
            }
            vel = acc / (filled.size - 1)
        }

        return floatArrayOf(mean, std, minV, maxV, p10, p90, range, vel)
    }

    private fun estimateCycles(series: FloatArray): Pair<Float, Float> {
        val filled = movingAverage(interpolateNans(series), window = 5)
        if (filled.size < 5) return 0f to 0f
        val range = (filled.maxOrNull() ?: 0f) - (filled.minOrNull() ?: 0f)
        if (range < 1e-3f) return 0f to 0f
        val minProm = max(2f, 0.10f * range)
        var cycles = 0
        var lastIdx = -10
        for (i in 2 until (filled.size - 2)) {
            if (filled[i] <= filled[i - 1] && filled[i] <= filled[i + 1]) {
                var localMax = Float.NEGATIVE_INFINITY
                val from = max(0, i - 4)
                val to = min(filled.size - 1, i + 4)
                for (j in from..to) localMax = max(localMax, filled[j])
                if ((localMax - filled[i]) >= minProm && (i - lastIdx) >= 3) {
                    cycles += 1
                    lastIdx = i
                }
            }
        }
        var motionEnergy = 0f
        if (filled.size > 1) {
            var acc = 0f
            for (i in 1 until filled.size) acc += abs(filled[i] - filled[i - 1])
            motionEnergy = acc / (filled.size - 1)
        }
        return cycles.toFloat() to motionEnergy
    }

    private fun extractFrameMetrics(points: List<PointF>): FrameMetrics? {
        if (points.size <= R_ANKLE) return null

        val nose = safePoint(points, NOSE)
        val lShoulder = safePoint(points, L_SHOULDER)
        val rShoulder = safePoint(points, R_SHOULDER)
        val lElbow = safePoint(points, L_ELBOW)
        val rElbow = safePoint(points, R_ELBOW)
        val lWrist = safePoint(points, L_WRIST)
        val rWrist = safePoint(points, R_WRIST)
        val lHip = safePoint(points, L_HIP)
        val rHip = safePoint(points, R_HIP)
        val lKnee = safePoint(points, L_KNEE)
        val rKnee = safePoint(points, R_KNEE)
        val lAnkle = safePoint(points, L_ANKLE)
        val rAnkle = safePoint(points, R_ANKLE)

        val validCount = listOf(
            nose, lShoulder, rShoulder, lElbow, rElbow, lWrist, rWrist,
            lHip, rHip, lKnee, rKnee, lAnkle, rAnkle
        ).count { it != null }
        val scoreMean = (validCount / 13f).coerceIn(0f, 1f)
        val visiblePoints = validCount.toFloat()
        val zeroFrame = validCount == 0

        val hipL = angleDeg(lShoulder, lHip, lKnee)
        val hipR = angleDeg(rShoulder, rHip, rKnee)
        val kneeL = angleDeg(lHip, lKnee, lAnkle)
        val kneeR = angleDeg(rHip, rKnee, rAnkle)

        val shoulderMid = midpoint(lShoulder, rShoulder)
        val hipMid = midpoint(lHip, rHip)
        val kneeMid = midpoint(lKnee, rKnee)
        val scale = bodyScale(shoulderMid, hipMid, kneeMid)

        val hipFlexAvg = meanFinite(hipL, hipR)
        val hipFlexDiff = if (hipL.isFinite() && hipR.isFinite()) abs(hipL - hipR) else Float.NaN
        val kneeAvg = meanFinite(kneeL, kneeR)
        val kneeDiff = if (kneeL.isFinite() && kneeR.isFinite()) abs(kneeL - kneeR) else Float.NaN
        val torsoVertical = torsoVerticalDeg(shoulderMid, hipMid)

        val wristToKneeNorm =
            if (scale.isFinite()) {
                val left = if (lWrist != null && lKnee != null) dist(lWrist, lKnee) / scale else Float.NaN
                val right = if (rWrist != null && rKnee != null) dist(rWrist, rKnee) / scale else Float.NaN
                meanFinite(left, right)
            } else {
                Float.NaN
            }
        val shoulderToKneeNorm =
            if (scale.isFinite() && shoulderMid != null && kneeMid != null) {
                dist(shoulderMid, kneeMid) / scale
            } else {
                Float.NaN
            }
        val noseToKneeNorm =
            if (scale.isFinite() && nose != null && kneeMid != null) {
                dist(nose, kneeMid) / scale
            } else {
                Float.NaN
            }

        val valid = SERIES_METRICS.any {
            when (it) {
                "hip_flex_avg" -> hipFlexAvg.isFinite()
                "hip_flex_diff" -> hipFlexDiff.isFinite()
                "knee_avg" -> kneeAvg.isFinite()
                "knee_diff" -> kneeDiff.isFinite()
                "torso_vertical_deg" -> torsoVertical.isFinite()
                "wrist_to_knee_norm" -> wristToKneeNorm.isFinite()
                "shoulder_to_knee_norm" -> shoulderToKneeNorm.isFinite()
                "nose_to_knee_norm" -> noseToKneeNorm.isFinite()
                "score_mean" -> scoreMean.isFinite()
                "visible_points" -> visiblePoints.isFinite()
                else -> false
            }
        }

        return FrameMetrics(
            hipFlexAvg = hipFlexAvg,
            hipFlexDiff = hipFlexDiff,
            kneeAvg = kneeAvg,
            kneeDiff = kneeDiff,
            torsoVerticalDeg = torsoVertical,
            wristToKneeNorm = wristToKneeNorm,
            shoulderToKneeNorm = shoulderToKneeNorm,
            noseToKneeNorm = noseToKneeNorm,
            scoreMean = scoreMean,
            visiblePoints = visiblePoints,
            valid = valid,
            zeroFrame = zeroFrame
        )
    }

    private fun safePoint(points: List<PointF>, idx: Int): PointF? {
        if (idx < 0 || idx >= points.size) return null
        val p = points[idx]
        if (!p.x.isFinite() || !p.y.isFinite()) return null
        if (abs(p.x) < 1e-6f && abs(p.y) < 1e-6f) return null
        return p
    }

    private fun midpoint(a: PointF?, b: PointF?): PointF? {
        if (a == null || b == null) return null
        return PointF((a.x + b.x) * 0.5f, (a.y + b.y) * 0.5f)
    }

    private fun dist(a: PointF, b: PointF): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
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
        var cosT = (bax * bcx + bay * bcy) / (normBA * normBC)
        cosT = cosT.coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cosT).toDouble()).toFloat()
    }

    private fun torsoVerticalDeg(shoulderMid: PointF?, hipMid: PointF?): Float {
        if (shoulderMid == null || hipMid == null) return Float.NaN
        val vx = shoulderMid.x - hipMid.x
        val vy = shoulderMid.y - hipMid.y
        if (abs(vx) < 1e-8f && abs(vy) < 1e-8f) return Float.NaN
        return Math.toDegrees(atan2(abs(vx).toDouble(), (abs(vy) + 1e-8f).toDouble())).toFloat()
    }

    private fun bodyScale(shoulderMid: PointF?, hipMid: PointF?, kneeMid: PointF?): Float {
        if (shoulderMid == null || hipMid == null || kneeMid == null) return Float.NaN
        val d1 = dist(shoulderMid, hipMid)
        val d2 = dist(hipMid, kneeMid)
        if (d1 < 1e-6f && d2 < 1e-6f) return Float.NaN
        if (d1 < 1e-6f) return d2
        if (d2 < 1e-6f) return d1
        return (d1 + d2) * 0.5f
    }

    private fun meanFinite(a: Float, b: Float): Float {
        val aOk = a.isFinite()
        val bOk = b.isFinite()
        return when {
            aOk && bOk -> (a + b) * 0.5f
            aOk -> a
            bOk -> b
            else -> Float.NaN
        }
    }

    private fun movingAverage(v: FloatArray, window: Int): FloatArray {
        if (v.isEmpty()) return v
        if (window <= 1) return v.copyOf()
        val pad = window / 2
        val padded = FloatArray(v.size + pad * 2)
        for (i in padded.indices) {
            val src = (i - pad).coerceIn(0, v.lastIndex)
            padded[i] = v[src]
        }
        val out = FloatArray(v.size)
        val inv = 1f / window.toFloat()
        for (i in out.indices) {
            var sum = 0f
            for (k in 0 until window) sum += padded[i + k]
            out[i] = sum * inv
        }
        return out
    }

    private fun interpolateNans(values: FloatArray): FloatArray {
        if (values.isEmpty()) return values
        val out = values.copyOf()
        val validIdx = out.indices.filter { out[it].isFinite() }
        if (validIdx.isEmpty()) {
            for (i in out.indices) out[i] = 0f
            return out
        }
        if (validIdx.size == 1) {
            val v = out[validIdx[0]]
            for (i in out.indices) out[i] = v
            return out
        }
        var prev = validIdx[0]
        for (i in 0 until prev) out[i] = out[prev]
        for (idx in 1 until validIdx.size) {
            val cur = validIdx[idx]
            val prevValue = out[prev]
            val curValue = out[cur]
            val span = (cur - prev).toFloat()
            for (i in (prev + 1) until cur) {
                val t = (i - prev) / span
                out[i] = prevValue + t * (curValue - prevValue)
            }
            prev = cur
        }
        for (i in (prev + 1) until out.size) out[i] = out[prev]
        return out
    }

    private fun percentile(values: FloatArray, q: Float): Float {
        if (values.isEmpty()) return 0f
        val finite = values.filter { it.isFinite() }.sorted()
        if (finite.isEmpty()) return 0f
        if (finite.size == 1) return finite[0]
        val qq = q.coerceIn(0f, 100f)
        val pos = qq / 100f * (finite.size - 1).toFloat()
        val lo = kotlin.math.floor(pos).toInt()
        val hi = kotlin.math.ceil(pos).toInt()
        if (lo == hi) return finite[lo]
        val t = pos - lo.toFloat()
        return finite[lo] + t * (finite[hi] - finite[lo])
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
