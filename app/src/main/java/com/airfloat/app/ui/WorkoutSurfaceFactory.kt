package com.airfloat.app.ui

import com.airfloat.app.stats.AppState
import com.airfloat.app.stats.WorkoutSessionRecord
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class WorkoutRecentSessionModel(
    val id: String,
    val title: String,
    val meta: String,
    val presetKey: String
)

object WorkoutSurfaceFactory {
    private val dateFormatter = DateTimeFormatter.ofPattern("dd MMM", Locale.US)

    fun build(
        subtitleText: String,
        qualityButtonText: String,
        qualityButtonVisible: Boolean,
        qualityButtonEnabled: Boolean,
        recordButtonText: String,
        repsEnabled: Boolean,
        repsHint: String,
        sessions: List<WorkoutSessionRecord>,
        appState: AppState,
        todayEntryPresetKey: String?,
        selectedPresetKey: String,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): WorkoutFragment.UiState =
        WorkoutFragment.UiState(
            subtitleText = subtitleText,
            startButtonText = buildStartButtonText(todayEntryPresetKey, appState),
            selectedPresetKey = selectedPresetKey,
            selectedExerciseLabel = exerciseLabel(selectedPresetKey),
            qualityButtonText = qualityButtonText,
            qualityButtonVisible = qualityButtonVisible,
            qualityButtonEnabled = qualityButtonEnabled,
            recordButtonText = recordButtonText,
            repsEnabled = repsEnabled,
            repsHint = repsHint,
            recentSessions = buildRecentSessions(sessions, zoneId)
        )

    fun exerciseLabel(presetKey: String): String =
        when (presetKey) {
            WorkoutFragment.SOURCE_PRESS_BARBELL,
            HomeFragment.LAUNCH_PRESS_BARBELL -> "BARBELL PRESS"
            WorkoutFragment.SOURCE_PRESS_DUMBBELL -> "DUMBBELL PRESS"
            WorkoutFragment.SOURCE_PUSHUP,
            HomeFragment.LAUNCH_PUSHUP -> "PUSH-UP"
            WorkoutFragment.SOURCE_SITUP,
            HomeFragment.LAUNCH_SITUP -> "SIT-UP"
            WorkoutFragment.SOURCE_SQUAT_BETA,
            HomeFragment.LAUNCH_SQUAT_BETA -> "SQUATS"
            else -> "SESSION"
        }

    private fun buildStartButtonText(
        todayEntryPresetKey: String?,
        appState: AppState
    ): String {
        return "Start Session"
    }

    private fun buildRecentSessions(
        sessions: List<WorkoutSessionRecord>,
        zoneId: ZoneId
    ): List<WorkoutRecentSessionModel> =
        sessions
            .sortedByDescending { it.timestampMs }
            .take(4)
            .map { session ->
                WorkoutRecentSessionModel(
                    id = session.id,
                    title = exerciseLabel(session.presetKey),
                    meta = "${formatDate(session.timestampMs, zoneId)} · ${session.reps} REPS",
                    presetKey = session.presetKey
                )
            }

    private fun formatDate(
        timestampMs: Long,
        zoneId: ZoneId
    ): String = Instant.ofEpochMilli(timestampMs).atZone(zoneId).format(dateFormatter).uppercase(Locale.US)
}
