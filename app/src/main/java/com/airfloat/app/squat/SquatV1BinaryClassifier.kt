package com.airfloat.app.squat

import android.content.Context
import android.graphics.PointF
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class SquatQualityLabel {
    GOOD,
    BAD,
    UNKNOWN
}

data class SquatBinaryPrediction(
    val quality: SquatQualityLabel,
    val qualityName: String,
    val badScoreRaw: Float,
    val badScoreSmoothed: Float,
    val confidence: Float
)

/**
 * Binary squat quality model (GOOD/BAD with UNKNOWN band near decision threshold).
 * Model payload lives in app/src/main/assets/squat_v1_binary_model.json.
 */
class SquatV1BinaryClassifier private constructor(
    private val mean: FloatArray,
    private val std: FloatArray,
    private val weights: FloatArray,
    private val badThreshold: Float,
    private val unknownMargin: Float,
    private val emaAlpha: Float
) {

    companion object {
        private const val TAG = "AirFloatSquat"
        private const val MODEL_ASSET = "squat_v1_binary_model.json"

        // BlazePose landmarks
        private const val L_SHOULDER = 11
        private const val R_SHOULDER = 12
        private const val L_HIP = 23
        private const val R_HIP = 24
        private const val L_KNEE = 25
        private const val R_KNEE = 26
        private const val L_ANKLE = 27
        private const val R_ANKLE = 28
        private const val L_FOOT = 31
        private const val R_FOOT = 32

        fun fromAsset(context: Context): SquatV1BinaryClassifier? {
            return try {
                val raw = context.assets.open(MODEL_ASSET).bufferedReader().use { it.readText() }
                val json = JSONObject(raw)

                val stdBlock = json.getJSONObject("standardization")
                val mean = jsonArrayToFloatArray(stdBlock.getJSONArray("mean"))
                val std = jsonArrayToFloatArray(stdBlock.getJSONArray("std"))
                val weights = jsonArrayToFloatArray(json.getJSONArray("weights"))
                val badThreshold = json.optDouble("bad_threshold", 0.50).toFloat().coerceIn(0.1f, 0.9f)
                val unknownMargin = json.optDouble("unknown_confidence_margin", 0.08).toFloat().coerceIn(0.01f, 0.3f)
                val emaAlpha = json.optDouble("ema_alpha", 0.35).toFloat().coerceIn(0.05f, 1f)

                if (mean.isEmpty() || std.isEmpty() || weights.size != mean.size + 1) {
                    Log.e(TAG, "Squat v1 payload invalid: features=${mean.size} weights=${weights.size}")
                    null
                } else {
                    Log.i(
                        TAG,
                        "Loaded squat v1 model: features=${mean.size} badThreshold=$badThreshold margin=$unknownMargin ema=$emaAlpha"
                    )
                    // Runtime clamps: we prefer stable feedback over twitchy labels.
                    SquatV1BinaryClassifier(
                        mean = mean,
                        std = std,
                        weights = weights,
                        badThreshold = badThreshold,
                        unknownMargin = max(unknownMargin, 0.12f),
                        emaAlpha = min(emaAlpha, 0.22f)
                    )
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to load squat v1 model", t)
                null
            }
        }

        private fun jsonArrayToFloatArray(arr: JSONArray): FloatArray {
            val out = FloatArray(arr.length())
            for (i in 0 until arr.length()) {
                out[i] = arr.optDouble(i, 0.0).toFloat()
            }
            return out
        }
    }

    private var emaBad: Float? = null
    private var stableQuality: SquatQualityLabel = SquatQualityLabel.UNKNOWN
    private var lastPrediction: SquatBinaryPrediction? = null
    private var lastFeatures: FloatArray? = null

    fun reset() {
        emaBad = null
        stableQuality = SquatQualityLabel.UNKNOWN
        lastPrediction = null
        lastFeatures = null
    }

    fun lastPrediction(): SquatBinaryPrediction? = lastPrediction

    fun lastFeatures(): FloatArray? = lastFeatures

    fun predict(normPoints: List<PointF>): SquatBinaryPrediction? {
        val base = extractBaseFeatures(normPoints) ?: return null
        val all = buildAllFeatures(base)
        if (all.size != mean.size) return null
        lastFeatures = all

        val z = FloatArray(all.size)
        for (i in all.indices) {
            val denom = if (std[i] > 1e-6f) std[i] else 1f
            z[i] = (all[i] - mean[i]) / denom
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
                SquatQualityLabel.BAD -> {
                    when {
                        smoothedBad >= holdBad -> SquatQualityLabel.BAD
                        smoothedBad <= enterGood -> SquatQualityLabel.GOOD
                        else -> SquatQualityLabel.UNKNOWN
                    }
                }
                SquatQualityLabel.GOOD -> {
                    when {
                        smoothedBad <= holdGood -> SquatQualityLabel.GOOD
                        smoothedBad >= enterBad -> SquatQualityLabel.BAD
                        else -> SquatQualityLabel.UNKNOWN
                    }
                }
                SquatQualityLabel.UNKNOWN -> {
                    when {
                        smoothedBad >= enterBad -> SquatQualityLabel.BAD
                        smoothedBad <= enterGood -> SquatQualityLabel.GOOD
                        else -> SquatQualityLabel.UNKNOWN
                    }
                }
            }
        stableQuality = quality

        val maxDistance = max(badThreshold, 1f - badThreshold).coerceAtLeast(1e-3f)
        val confidence = (distance / maxDistance).coerceIn(0f, 1f)
        val qualityName =
            when (quality) {
                SquatQualityLabel.GOOD -> "good"
                SquatQualityLabel.BAD -> "bad"
                SquatQualityLabel.UNKNOWN -> "unknown"
            }

        val prediction = SquatBinaryPrediction(
            quality = quality,
            qualityName = qualityName,
            badScoreRaw = rawBad,
            badScoreSmoothed = smoothedBad,
            confidence = confidence
        )
        lastPrediction = prediction
        return prediction
    }

    private fun buildAllFeatures(base: FloatArray): FloatArray {
        val lk = base[0]
        val rk = base[1]
        val lh = base[2]
        val rh = base[3]
        val la = base[4]
        val ra = base[5]
        val spine = base[6]
        val torso = base[7]
        val lLat = base[8]
        val rLat = base[9]

        val kneeAvg = (lk + rk) * 0.5f
        val hipAvg = (lh + rh) * 0.5f
        val ankleAvg = (la + ra) * 0.5f
        val kneeDiff = abs(lk - rk)
        val hipDiff = abs(lh - rh)
        val ankleDiff = abs(la - ra)
        val torsoMinusSpine = torso - spine
        val kneeLatAvg = (lLat + rLat) * 0.5f

        return floatArrayOf(
            base[0], base[1], base[2], base[3], base[4], base[5],
            base[6], base[7], base[8], base[9], base[10], base[11],
            kneeAvg, hipAvg, ankleAvg,
            kneeDiff, hipDiff, ankleDiff,
            torsoMinusSpine,
            kneeLatAvg
        )
    }

    private fun extractBaseFeatures(points: List<PointF>): FloatArray? {
        if (points.size <= R_FOOT) return null

        val lShoulder = points[L_SHOULDER]
        val rShoulder = points[R_SHOULDER]
        val lHip = points[L_HIP]
        val rHip = points[R_HIP]
        val lKnee = points[L_KNEE]
        val rKnee = points[R_KNEE]
        val lAnkle = points[L_ANKLE]
        val rAnkle = points[R_ANKLE]
        val lFoot = points[L_FOOT]
        val rFoot = points[R_FOOT]

        val leftKneeAngle = angleDeg(lHip, lKnee, lAnkle) ?: return null
        val rightKneeAngle = angleDeg(rHip, rKnee, rAnkle) ?: return null
        val leftHipAngle = angleDeg(lShoulder, lHip, lKnee) ?: return null
        val rightHipAngle = angleDeg(rShoulder, rHip, rKnee) ?: return null
        val leftAnkleAngle = angleDeg(lKnee, lAnkle, lFoot) ?: return null
        val rightAnkleAngle = angleDeg(rKnee, rAnkle, rFoot) ?: return null

        val hipMid = PointF((lHip.x + rHip.x) * 0.5f, (lHip.y + rHip.y) * 0.5f)
        val shoulderMid = PointF((lShoulder.x + rShoulder.x) * 0.5f, (lShoulder.y + rShoulder.y) * 0.5f)

        val torsoLean = orientationDeg(hipMid, shoulderMid)
        val spineAngle = orientationDeg(lHip, lShoulder)

        val leftKneeLateral = abs(lKnee.x - lAnkle.x)
        val rightKneeLateral = abs(rKnee.x - rAnkle.x)

        val symmetryScore =
            abs(leftKneeAngle - rightKneeAngle) +
                abs(leftHipAngle - rightHipAngle) +
                abs(leftAnkleAngle - rightAnkleAngle)

        val ankleMidY = (lAnkle.y + rAnkle.y) * 0.5f
        val hipDepth = (ankleMidY - hipMid.y).coerceAtLeast(0f)

        return floatArrayOf(
            leftKneeAngle,
            rightKneeAngle,
            leftHipAngle,
            rightHipAngle,
            leftAnkleAngle,
            rightAnkleAngle,
            spineAngle,
            torsoLean,
            leftKneeLateral,
            rightKneeLateral,
            symmetryScore,
            hipDepth
        )
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
        return Math.toDegrees(kotlin.math.acos(cosT).toDouble()).toFloat()
    }

    private fun ema(prev: Float?, newValue: Float, alpha: Float): Float {
        if (prev == null) return newValue
        return alpha * newValue + (1f - alpha) * prev
    }
}
