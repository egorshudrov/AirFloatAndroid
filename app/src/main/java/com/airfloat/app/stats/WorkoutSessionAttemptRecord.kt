package com.airfloat.app.stats

import org.json.JSONObject

data class WorkoutSessionAttemptRecord(
    val index: Int,
    val repSnapshot: Int,
    val success: Boolean,
    val elapsedMs: Long,
    val estimatedKcal: Float,
    val detail: String
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("index", index)
            .put("repSnapshot", repSnapshot)
            .put("success", success)
            .put("elapsedMs", elapsedMs)
            .put("estimatedKcal", estimatedKcal.toDouble())
            .put("detail", detail)

    companion object {
        fun fromJson(json: JSONObject): WorkoutSessionAttemptRecord =
            WorkoutSessionAttemptRecord(
                index = json.optInt("index"),
                repSnapshot = json.optInt("repSnapshot"),
                success = json.optBoolean("success"),
                elapsedMs = json.optLong("elapsedMs"),
                estimatedKcal = json.optDouble("estimatedKcal").toFloat(),
                detail = json.optString("detail")
            )
    }
}
