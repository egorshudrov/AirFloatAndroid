package com.airfloat.app.ui

import com.airfloat.app.stats.AppState
import com.airfloat.app.stats.WorkoutSessionRecord
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class TodayUiState(
    val appState: AppState,
    val dateMonthLabel: String,
    val dateDayLabel: String
)

object TodaySurfaceFactory {
    @Suppress("UNUSED_PARAMETER")
    fun build(
        appState: AppState,
        sessions: List<WorkoutSessionRecord>,
        zoneId: ZoneId = ZoneId.systemDefault(),
        nowEpochMs: Long = System.currentTimeMillis()
    ): TodayUiState {
        val zonedDateTime = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId)
        val dateMonthLabel =
            zonedDateTime
                .format(DateTimeFormatter.ofPattern("MMM", Locale.US))
                .uppercase(Locale.US)
        val dateDayLabel =
            zonedDateTime
                .format(DateTimeFormatter.ofPattern("d", Locale.US))
                .uppercase(Locale.US)

        return TodayUiState(
            appState = appState,
            dateMonthLabel = dateMonthLabel,
            dateDayLabel = dateDayLabel
        )
    }
}
