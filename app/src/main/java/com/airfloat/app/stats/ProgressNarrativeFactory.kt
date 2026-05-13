package com.airfloat.app.stats

import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

data class ProgressNarrativeSnapshot(
    val chapterHeader: String,
    val progressArc: String
)

object ProgressNarrativeFactory {
    fun build(
        snapshot: ProgressSnapshot,
        state: StateSnapshot,
        sessions: List<WorkoutSessionRecord>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ProgressNarrativeSnapshot {
        return ProgressNarrativeSnapshot(
            chapterHeader = buildChapterHeader(snapshot, state, sessions, zoneId),
            progressArc = buildProgressArc(snapshot, state)
        )
    }

    private fun buildChapterHeader(
        snapshot: ProgressSnapshot,
        state: StateSnapshot,
        sessions: List<WorkoutSessionRecord>,
        zoneId: ZoneId
    ): String {
        val bestExercise = SessionStatsCalculator.exerciseLabel(state.bestExercise).uppercase(Locale.US)
        val weakestExercise = SessionStatsCalculator.exerciseLabel(state.weakestExercise).uppercase(Locale.US)
        val comeback = buildComebackLabel(sessions, zoneId)
        return when {
            state.hasNewPR -> "✦ NEW PERSONAL RECORD — $bestExercise BROKE THROUGH"
            comeback != null -> comeback
            state.liveContext.dayOfWeek == DayOfWeekSignal.MONDAY ->
                "WEEK RESET — CARRY THE ${state.currentStreak}D STREAK"
            state.liveContext.dayOfWeek == DayOfWeekSignal.FRIDAY ->
                "END OF WEEK — SESSION OR REST DECISION"
            state.liveContext.dayOfWeek == DayOfWeekSignal.SUNDAY ->
                "DEEP REVIEW MODE — ${snapshot.totalSessions} SESSIONS IN ${windowLabel(snapshot)}"
            state.loadTrend == LoadTrend.PLATEAU ->
                "PLATEAU DETECTED — $weakestExercise IS PACING THE BLOCK"
            state.loadTrend == LoadTrend.RISING && state.currentStreak >= 4 ->
                "RISING BLOCK — ${state.currentStreak}D STREAK WITH $bestExercise LEADING"
            state.daysSinceLastSession >= 3 ->
                "URGENCY WINDOW — ${state.daysSinceLastSession}D WITHOUT A SESSION"
            else ->
                "LIVE PROGRESS MODEL — $bestExercise IS YOUR STRONGEST SIGNAL"
        }
    }

    private fun buildProgressArc(
        snapshot: ProgressSnapshot,
        state: StateSnapshot
    ): String {
        return when {
            state.daysSinceLastSession >= 3 ->
                "You have been away for ${state.daysSinceLastSession} days. The next session matters more than the last one did."
            snapshot.repsDelta > 0 && snapshot.completionDelta >= 0 ->
                "You've added ${snapshot.repsDelta} reps in this ${windowLabel(snapshot)} and precision still climbed ${signedPoints(snapshot.completionDelta)}."
            snapshot.loadDeltaPercent > 0 && snapshot.completionDelta < 0 ->
                "Volume is ${signedPercent(snapshot.loadDeltaPercent)}, but precision slipped ${abs(snapshot.completionDelta)} pts. The next win is cleaner work, not more work."
            snapshot.currentStreakDays >= 4 && snapshot.totalReps >= 800 ->
                "This is a ${snapshot.currentStreakDays}D live streak with ${snapshot.totalReps} reps inside the current ${windowLabel(snapshot)}."
            state.bodyRecompSignal == BodyRecompSignal.ACTIVE ->
                "Body signal is live now, so output and recomposition are finally telling the same story."
            else ->
                "Current output is holding across the ${windowLabel(snapshot)}. The next lift is to sharpen ${SessionStatsCalculator.exerciseLabel(state.weakestExercise).lowercase(Locale.US)}."
        }
    }

    private fun buildComebackLabel(
        sessions: List<WorkoutSessionRecord>,
        zoneId: ZoneId
    ): String? {
        if (sessions.size < 2) return null
        val ordered = sessions.sortedBy { it.timestampMs }
        for (index in ordered.lastIndex downTo 1) {
            val current = ordered[index]
            val previous = ordered[index - 1]
            val currentDate = Instant.ofEpochMilli(current.timestampMs).atZone(zoneId).toLocalDate()
            val previousDate = Instant.ofEpochMilli(previous.timestampMs).atZone(zoneId).toLocalDate()
            val gapDays = java.time.temporal.ChronoUnit.DAYS.between(previousDate, currentDate).toInt()
            if (gapDays >= 8) {
                val sessionsSinceGap = ordered.drop(index).size + 1
                return "COMEBACK BLOCK — $sessionsSinceGap SESSIONS AFTER ${gapDays}D GAP"
            }
        }
        return null
    }

    private fun windowLabel(snapshot: ProgressSnapshot): String =
        when {
            snapshot.rangeLabel.contains("7D") -> "7D WINDOW"
            snapshot.rangeLabel.contains("30D") -> "30D WINDOW"
            else -> "FULL HISTORY"
        }

    private fun signedPercent(value: Int): String =
        if (value > 0) "+$value%" else "$value%"

    private fun signedPoints(value: Int): String =
        when {
            value > 0 -> "+$value pts"
            value < 0 -> "$value pts"
            else -> "0 pts"
        }
}
