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

data class SquatPrediction(
    val labelId: Int,
    val labelName: String,
    val score: Float,
    val confidence: Float,
    val scores: FloatArray
)

/**
 * Lightweight on-device classifier for squat quality (shadow mode).
 * Model was trained offline and exported to app/src/main/assets/squat_v0_model.json.
 */
class SquatV0Classifier private constructor(
    private val classNames: List<String>,
    private val mean: FloatArray,
    private val std: FloatArray,
    private val weights: Array<FloatArray>
) {

    companion object {
        private const val TAG = "AirFloatSquat"
        private const val MODEL_ASSET = "squat_v0_model.json"

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

        fun fromAsset(context: Context): SquatV0Classifier? {
            return try {
                val raw = context.assets.open(MODEL_ASSET).bufferedReader().use { it.readText() }
                val json = JSONObject(raw)

                val classNames = jsonArrayToStringList(json.getJSONArray("class_names"))
                val stdBlock = json.getJSONObject("standardization")
                val mean = jsonArrayToFloatArray(stdBlock.getJSONArray("mean"))
                val std = jsonArrayToFloatArray(stdBlock.getJSONArray("std"))
                val weights = jsonArray2DToFloatArray(json.getJSONArray("ovr_weights"))

                if (mean.isEmpty() || std.isEmpty() || weights.isEmpty()) {
                    Log.e(TAG, "Squat model payload is empty")
                    null
                } else {
                    Log.i(
                        TAG,
                        "Loaded squat shadow model: classes=${classNames.size} features=${mean.size}"
                    )
                    SquatV0Classifier(classNames, mean, std, weights)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to load squat shadow model", t)
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

        private fun jsonArray2DToFloatArray(arr: JSONArray): Array<FloatArray> {
            val out = Array(arr.length()) { FloatArray(0) }
            for (i in 0 until arr.length()) {
                out[i] = jsonArrayToFloatArray(arr.getJSONArray(i))
            }
            return out
        }

        private fun jsonArrayToStringList(arr: JSONArray): List<String> {
            val out = ArrayList<String>(arr.length())
            for (i in 0 until arr.length()) {
                out.add(arr.optString(i, "class_$i"))
            }
            return out
        }
    }

    fun predict(normPoints: List<PointF>): SquatPrediction? {
        val base = extractBaseFeatures(normPoints) ?: return null
        val all = buildAllFeatures(base)
        if (all.size != mean.size) return null
        if (weights.any { it.size != all.size + 1 }) return null

        val z = FloatArray(all.size)
        for (i in all.indices) {
            val denom = if (std[i] > 1e-6f) std[i] else 1f
            z[i] = (all[i] - mean[i]) / denom
        }

        val scores = FloatArray(weights.size)
        var bestIdx = 0
        var bestVal = Float.NEGATIVE_INFINITY
        var secondVal = Float.NEGATIVE_INFINITY

        for (c in weights.indices) {
            val w = weights[c]
            var logit = w[0] // bias
            for (i in z.indices) {
                logit += w[i + 1] * z[i]
            }
            val s = sigmoid(logit)
            scores[c] = s
            if (s > bestVal) {
                secondVal = bestVal
                bestVal = s
                bestIdx = c
            } else if (s > secondVal) {
                secondVal = s
            }
        }

        val name = classNames.getOrElse(bestIdx) { "class_$bestIdx" }
        val confidence = (bestVal - secondVal).coerceIn(0f, 1f)
        return SquatPrediction(
            labelId = bestIdx,
            labelName = name,
            score = bestVal,
            confidence = confidence,
            scores = scores
        )
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
            // 12 raw
            base[0], base[1], base[2], base[3], base[4], base[5],
            base[6], base[7], base[8], base[9], base[10], base[11],
            // 8 engineered
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

        // orientation in [0..180], ~90 when torso is vertical on portrait camera feed.
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
}

