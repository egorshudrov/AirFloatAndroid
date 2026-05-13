package com.airfloat.app.stats

import org.json.JSONObject

data class BodyWeightRecord(
    val id: String,
    val timestampMs: Long,
    val weightKg: Float,
    val waistCm: Float? = null,
    val bodyFatPercent: Float? = null,
    val note: String? = null
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("timestampMs", timestampMs)
            .put("weightKg", weightKg.toDouble())
            .apply {
                waistCm?.let { put("waistCm", it.toDouble()) }
                bodyFatPercent?.let { put("bodyFatPercent", it.toDouble()) }
                note?.takeIf { it.isNotBlank() }?.let { put("note", it) }
            }

    companion object {
        fun fromJson(json: JSONObject): BodyWeightRecord =
            BodyWeightRecord(
                id = json.optString("id"),
                timestampMs = json.optLong("timestampMs"),
                weightKg = json.optDouble("weightKg").toFloat(),
                waistCm = json.optNullableFloat("waistCm"),
                bodyFatPercent = json.optNullableFloat("bodyFatPercent"),
                note = json.optString("note").takeIf { it.isNotBlank() }
            )

        private fun JSONObject.optNullableFloat(key: String): Float? =
            if (has(key) && !isNull(key)) optDouble(key).toFloat() else null
    }
}
