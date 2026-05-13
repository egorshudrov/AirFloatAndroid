package com.airfloat.app.stats

import android.content.Context
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate

class ProgramScheduleRepository(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadSchedule(): ProgramSchedule =
        ProgramSchedule(
            restDaysOfWeek = loadRestDays(),
            dateOverrides = loadDateOverrides()
        )

    fun loadRestDays(): Set<DayOfWeek> {
        if (!prefs.contains(KEY_REST_DAYS_OF_WEEK)) return DEFAULT_REST_DAYS
        val raw = prefs.getStringSet(KEY_REST_DAYS_OF_WEEK, emptySet()).orEmpty()
        return raw.mapNotNullTo(linkedSetOf()) { value ->
            runCatching { DayOfWeek.valueOf(value) }.getOrNull()
        }
    }

    fun saveRestDays(restDays: Set<DayOfWeek>) {
        prefs.edit()
            .putStringSet(KEY_REST_DAYS_OF_WEEK, restDays.mapTo(linkedSetOf()) { it.name })
            .apply()
    }

    fun loadDateOverrides(): Map<LocalDate, PlannedDayType> {
        val raw = prefs.getString(KEY_DATE_OVERRIDES_JSON, null).orEmpty()
        if (raw.isBlank()) return emptyMap()

        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        val result = linkedMapOf<LocalDate, PlannedDayType>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val date = runCatching { LocalDate.parse(key) }.getOrNull() ?: continue
            val value =
                runCatching { PlannedDayType.valueOf(json.optString(key)) }
                    .getOrNull() ?: continue
            result[date] = value
        }
        return result
    }

    fun saveDateOverrides(overrides: Map<LocalDate, PlannedDayType>) {
        val json =
            JSONObject().apply {
                overrides
                    .toSortedMap()
                    .forEach { (date, type) ->
                        put(date.toString(), type.name)
                    }
            }
        prefs.edit()
            .putString(KEY_DATE_OVERRIDES_JSON, json.toString())
            .apply()
    }

    fun saveSchedule(schedule: ProgramSchedule) {
        prefs.edit()
            .putStringSet(
                KEY_REST_DAYS_OF_WEEK,
                schedule.restDaysOfWeek.mapTo(linkedSetOf()) { it.name }
            )
            .putString(KEY_DATE_OVERRIDES_JSON, encodeDateOverrides(schedule.dateOverrides))
            .apply()
    }

    fun setDateOverride(
        date: LocalDate,
        type: PlannedDayType
    ) {
        val overrides = loadDateOverrides().toMutableMap()
        overrides[date] = type
        saveDateOverrides(overrides)
    }

    fun clearDateOverride(date: LocalDate) {
        val overrides = loadDateOverrides().toMutableMap()
        if (overrides.remove(date) == null) return
        saveDateOverrides(overrides)
    }

    private fun encodeDateOverrides(overrides: Map<LocalDate, PlannedDayType>): String =
        JSONObject().apply {
            overrides
                .toSortedMap()
                .forEach { (date, type) ->
                    put(date.toString(), type.name)
                }
        }.toString()

    companion object {
        const val KEY_REST_DAYS_OF_WEEK = "rest_days_of_week"
        const val KEY_DATE_OVERRIDES_JSON = "date_plan_overrides_json"
        private const val PREFS_NAME = "airfloat_program_settings"
        private val DEFAULT_REST_DAYS = linkedSetOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    }
}
