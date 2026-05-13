package com.airfloat.app.stats

import android.content.Context
import org.json.JSONArray

class SessionStatsRepository(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadSessions(): List<WorkoutSessionRecord> {
        val raw = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(WorkoutSessionRecord.fromJson(item))
                }
            }.sortedByDescending { it.timestampMs }
        }.getOrDefault(emptyList())
    }

    fun saveSession(record: WorkoutSessionRecord) {
        val updated =
            buildList {
                add(record)
                addAll(loadSessions().filterNot { it.id == record.id })
            }
                .sortedByDescending { it.timestampMs }
                .take(MAX_SESSIONS)

        val encoded = JSONArray().apply {
            updated.forEach { put(it.toJson()) }
        }
        // Commit synchronously so screens rendered immediately after session finish
        // can read the latest session without waiting for async prefs flush.
        prefs.edit().putString(KEY_SESSIONS, encoded.toString()).commit()
    }

    companion object {
        private const val PREFS_NAME = "airfloat_session_stats"
        private const val KEY_SESSIONS = "sessions"
        private const val MAX_SESSIONS = 240
    }
}
