package com.airfloat.app.stats

import android.content.Context
import org.json.JSONArray

class BodyMetricsRepository(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadCheckIns(): List<BodyWeightRecord> {
        val raw = prefs.getString(KEY_WEIGHTS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(BodyWeightRecord.fromJson(item))
                }
            }.sortedByDescending { it.timestampMs }
        }.getOrDefault(emptyList())
    }

    fun saveCheckIn(record: BodyWeightRecord) {
        val updated =
            buildList {
                add(record)
                addAll(loadCheckIns().filterNot { it.id == record.id })
            }
                .sortedByDescending { it.timestampMs }
                .take(MAX_RECORDS)

        val encoded = JSONArray().apply {
            updated.forEach { put(it.toJson()) }
        }
        prefs.edit().putString(KEY_WEIGHTS, encoded.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "airfloat_body_metrics"
        private const val KEY_WEIGHTS = "weights"
        private const val MAX_RECORDS = 180
    }
}
