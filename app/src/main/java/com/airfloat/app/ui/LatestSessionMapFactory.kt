package com.airfloat.app.ui

import com.airfloat.app.stats.SessionStatsCalculator
import com.airfloat.app.stats.WorkoutSessionAttemptRecord
import com.airfloat.app.stats.WorkoutSessionRecord
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class LatestAttemptTone {
    CLEAN,
    MISS,
    NEUTRAL
}

data class LatestAttemptDetailModel(
    val title: String,
    val badge: String,
    val tone: LatestAttemptTone,
    val meta: String,
    val detail: String
)

data class LatestSessionMapModel(
    val sessionTitle: String,
    val sessionBadge: String,
    val sessionMeta: String,
    val attempts: List<WorkoutSessionAttemptRecord>,
    val selectedIndex: Int,
    val selectedAttempt: LatestAttemptDetailModel,
    val isLegacy: Boolean
)

object LatestSessionMapFactory {
    fun build(
        session: WorkoutSessionRecord,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): LatestSessionMapModel {
        val (attempts, isLegacy) = attemptsForDisplay(session)
        val selectedIndex = defaultSelectedIndex(attempts)
        val selectedAttempt =
            attempts.getOrNull(selectedIndex)?.let { detailFor(it, isLegacy) }
                ?: LatestAttemptDetailModel(
                    title = "ATTEMPT --",
                    badge = "NO DATA",
                    tone = LatestAttemptTone.NEUTRAL,
                    meta = "No per-attempt telemetry was stored for this session.",
                    detail = "Start a new workout and every rep will land on this map."
                )

        val date =
            Instant.ofEpochMilli(session.timestampMs)
                .atZone(zoneId)
                .format(DateTimeFormatter.ofPattern("dd MMM", Locale.US))
                .uppercase(Locale.US)
        val attemptSummary =
            if (attempts.isEmpty()) {
                "NO ATTEMPTS"
            } else {
                "${attempts.size} ${if (attempts.size == 1) "ATTEMPT" else "ATTEMPTS"}"
            }

        return LatestSessionMapModel(
            sessionTitle = SessionStatsCalculator.exerciseLabel(SessionStatsCalculator.analyticsExerciseKey(session)).uppercase(Locale.US),
            sessionBadge = "${session.completionRate}%",
            sessionMeta =
                buildString {
                    append(date)
                    append(" · ")
                    append(attemptSummary)
                    if (isLegacy) {
                        append(" · LEGACY")
                    }
                },
            attempts = attempts,
            selectedIndex = selectedIndex,
            selectedAttempt = selectedAttempt,
            isLegacy = isLegacy
        )
    }

    fun attemptsForDisplay(session: WorkoutSessionRecord): Pair<List<WorkoutSessionAttemptRecord>, Boolean> {
        if (session.attempts.isNotEmpty()) {
            return session.attempts.sortedBy { it.index } to false
        }

        val totalAttempts = session.successfulAttempts + session.failedAttempts
        if (totalAttempts <= 0) return emptyList<WorkoutSessionAttemptRecord>() to true

        val durationStep = (session.durationMs / totalAttempts.toLong()).coerceAtLeast(0L)
        val kcalStep = if (totalAttempts > 0) session.estimatedKcal / totalAttempts else 0f
        val fallback =
            buildList {
                for (index in 0 until totalAttempts) {
                    val success = index < session.successfulAttempts
                    add(
                        WorkoutSessionAttemptRecord(
                            index = index + 1,
                            repSnapshot =
                                if (success) {
                                    (index + 1).coerceAtMost(session.reps.coerceAtLeast(1))
                                } else {
                                    session.reps.coerceAtLeast(0)
                                },
                            success = success,
                            elapsedMs = durationStep * (index + 1L),
                            estimatedKcal = kcalStep * (index + 1),
                            detail =
                                if (success) {
                                    "Legacy clean rep reconstructed from session totals."
                                } else {
                                    "Legacy missed attempt reconstructed from session totals."
                                }
                        )
                    )
                }
            }
        return fallback to true
    }

    fun defaultSelectedIndex(attempts: List<WorkoutSessionAttemptRecord>): Int {
        if (attempts.isEmpty()) return -1
        val failedIndex = attempts.indexOfLast { !it.success }
        return if (failedIndex >= 0) failedIndex else attempts.lastIndex
    }

    fun detailFor(
        attempt: WorkoutSessionAttemptRecord,
        isLegacy: Boolean
    ): LatestAttemptDetailModel {
        val tone = if (attempt.success) LatestAttemptTone.CLEAN else LatestAttemptTone.MISS
        val repMeta =
            if (attempt.success) {
                "Rep ${attempt.repSnapshot.coerceAtLeast(1)} locked"
            } else {
                "Rep count held at ${attempt.repSnapshot.coerceAtLeast(0)}"
            }
        return LatestAttemptDetailModel(
            title = "ATTEMPT ${attempt.index.toString().padStart(2, '0')}",
            badge = if (attempt.success) "CLEAN" else "MISS",
            tone = tone,
            meta = "$repMeta • ${formatDuration(attempt.elapsedMs)} • ${String.format(Locale.US, "%.2f KCAL", attempt.estimatedKcal)}",
            detail =
                if (isLegacy) {
                    "${attempt.detail} Exact order was reconstructed from old session totals."
                } else {
                    attempt.detail
                }
        )
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000L).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }
}
