package com.airfloat.app.stats

import org.json.JSONArray
import org.json.JSONObject

data class WorkoutSessionRecord(
    val id: String,
    val timestampMs: Long,
    val exerciseKey: String,
    val presetKey: String,
    val goalReps: Int,
    val completed: Boolean,
    val reps: Int,
    val successfulAttempts: Int,
    val failedAttempts: Int,
    val durationMs: Long,
    val estimatedKcal: Float,
    val completionRate: Int,
    val attempts: List<WorkoutSessionAttemptRecord> = emptyList()
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("timestampMs", timestampMs)
            .put("exerciseKey", exerciseKey)
            .put("presetKey", presetKey)
            .put("goalReps", goalReps)
            .put("completed", completed)
            .put("reps", reps)
            .put("successfulAttempts", successfulAttempts)
            .put("failedAttempts", failedAttempts)
            .put("durationMs", durationMs)
            .put("estimatedKcal", estimatedKcal.toDouble())
            .put("completionRate", completionRate)
            .put(
                "attempts",
                JSONArray().apply {
                    attempts.forEach { put(it.toJson()) }
                }
            )

    companion object {
        fun fromJson(json: JSONObject): WorkoutSessionRecord =
            WorkoutSessionRecord(
                id = json.optString("id"),
                timestampMs = json.optLong("timestampMs"),
                exerciseKey = json.optString("exerciseKey"),
                presetKey = json.optString("presetKey"),
                goalReps = json.optInt("goalReps"),
                completed = json.optBoolean("completed"),
                reps = json.optInt("reps"),
                successfulAttempts = json.optInt("successfulAttempts"),
                failedAttempts = json.optInt("failedAttempts"),
                durationMs = json.optLong("durationMs"),
                estimatedKcal = json.optDouble("estimatedKcal").toFloat(),
                completionRate = json.optInt("completionRate"),
                attempts =
                    buildList {
                        val attemptsArray = json.optJSONArray("attempts") ?: JSONArray()
                        for (index in 0 until attemptsArray.length()) {
                            val attempt = attemptsArray.optJSONObject(index) ?: continue
                            add(WorkoutSessionAttemptRecord.fromJson(attempt))
                        }
                    }
            )
    }
}
