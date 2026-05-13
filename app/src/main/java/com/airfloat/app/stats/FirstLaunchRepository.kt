package com.airfloat.app.stats

import android.content.Context
import java.time.DayOfWeek

data class FirstLaunchState(
    val shouldShowFirstLaunching: Boolean,
    val completedAtMs: Long?,
    val completedVersion: Int
)

class FirstLaunchRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val programScheduleRepository = ProgramScheduleRepository(appContext)

    fun loadState(): FirstLaunchState {
        val completed = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        val completedVersion = prefs.getInt(KEY_ONBOARDING_COMPLETED_VERSION, 0)
        val completedAtMs =
            prefs
                .takeIf { it.contains(KEY_ONBOARDING_COMPLETED_AT_MS) }
                ?.getLong(KEY_ONBOARDING_COMPLETED_AT_MS, 0L)
                ?.takeIf { it > 0L }

        return FirstLaunchState(
            shouldShowFirstLaunching = !completed || completedVersion < CURRENT_ONBOARDING_VERSION,
            completedAtMs = completedAtMs,
            completedVersion = completedVersion
        )
    }

    fun shouldShowFirstLaunching(): Boolean = loadState().shouldShowFirstLaunching

    fun markCompleted() {
        prefs.edit()
            .putBoolean(KEY_ONBOARDING_COMPLETED, true)
            .putLong(KEY_ONBOARDING_COMPLETED_AT_MS, System.currentTimeMillis())
            .putInt(KEY_ONBOARDING_COMPLETED_VERSION, CURRENT_ONBOARDING_VERSION)
            .apply()
    }

    fun completeWithWeeklyProgram(restDaysOfWeek: Set<DayOfWeek>) {
        programScheduleRepository.saveRestDays(restDaysOfWeek)
        markCompleted()
    }

    fun resetForDebug() {
        prefs.edit()
            .remove(KEY_ONBOARDING_COMPLETED)
            .remove(KEY_ONBOARDING_COMPLETED_AT_MS)
            .remove(KEY_ONBOARDING_COMPLETED_VERSION)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "airfloat_first_launch"
        const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        const val KEY_ONBOARDING_COMPLETED_AT_MS = "onboarding_completed_at_ms"
        const val KEY_ONBOARDING_COMPLETED_VERSION = "onboarding_completed_version"
        private const val CURRENT_ONBOARDING_VERSION = 2
    }
}
