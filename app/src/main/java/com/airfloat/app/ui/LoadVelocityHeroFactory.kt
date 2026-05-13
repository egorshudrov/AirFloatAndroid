package com.airfloat.app.ui

import com.airfloat.app.stats.DayOfWeekSignal
import com.airfloat.app.stats.ProgressSnapshot
import com.airfloat.app.stats.SessionStatsCalculator
import com.airfloat.app.stats.StateSnapshot
import com.airfloat.app.stats.TimeOfDaySignal
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class LoadVelocityHeroTone {
    RISING,
    FALLING,
    PLATEAU
}

data class LoadVelocityMetricModel(
    val label: String,
    val value: String,
    val delta: String,
    val tone: LoadVelocityHeroTone
)

data class LoadVelocityHeroModel(
    val rangeLabel: String,
    val stateLabel: String,
    val deltaValue: String,
    val title: String,
    val subtitle: String,
    val tone: LoadVelocityHeroTone,
    val precisionMetric: LoadVelocityMetricModel,
    val cadenceMetric: LoadVelocityMetricModel,
    val avgRepsMetric: LoadVelocityMetricModel
)

object LoadVelocityHeroFactory {
    fun fromSnapshot(
        snapshot: ProgressSnapshot,
        stateSnapshot: StateSnapshot? = null
    ): LoadVelocityHeroModel {
        val tone = toneFor(snapshot.loadDeltaPercent)
        val windowLabel = windowLabel(snapshot.rangeLabel)
        val averageRepsDelta = averageRepsDelta(snapshot)
        val stateLabel = stateLabelFor(stateSnapshot, tone)
        val subtitle = subtitleFor(snapshot, stateSnapshot, tone, windowLabel)

        return LoadVelocityHeroModel(
            rangeLabel = snapshot.rangeLabel,
            stateLabel = stateLabel,
            deltaValue = formatSignedPercent(snapshot.loadDeltaPercent),
            title = heroTitleFor(stateSnapshot),
            subtitle = subtitle,
            tone = tone,
            precisionMetric =
                LoadVelocityMetricModel(
                    label = "PRECISION",
                    value = "${snapshot.precisionSignal}%",
                    delta = formatPointDelta(snapshot.completionDelta),
                    tone = toneFor(snapshot.completionDelta)
                ),
            cadenceMetric =
                LoadVelocityMetricModel(
                    label = "CADENCE",
                    value = "${snapshot.activeDays}/${snapshot.activeDaysTarget}",
                    delta = formatSessionDelta(snapshot.sessionDelta),
                    tone = toneFor(snapshot.sessionDelta)
                ),
            avgRepsMetric =
                LoadVelocityMetricModel(
                    label = "AVG REPS",
                    value = snapshot.averageRepsPerSession.toString(),
                    delta = formatAvgRepsDelta(averageRepsDelta),
                    tone = toneFor(averageRepsDelta)
                )
        )
    }

    private fun toneFor(value: Int): LoadVelocityHeroTone =
        when {
            value >= 12 -> LoadVelocityHeroTone.RISING
            value <= -12 -> LoadVelocityHeroTone.FALLING
            else -> LoadVelocityHeroTone.PLATEAU
        }

    private fun averageRepsDelta(snapshot: ProgressSnapshot): Int {
        val previousSessions = snapshot.totalSessions - snapshot.sessionDelta
        val previousReps = snapshot.totalReps - snapshot.repsDelta
        if (previousSessions <= 0) return snapshot.averageRepsPerSession
        val previousAverage = previousReps / previousSessions.toFloat()
        return (snapshot.averageRepsPerSession - previousAverage).roundToInt()
    }

    private fun windowLabel(rangeLabel: String): String =
        when {
            rangeLabel.contains("7D") -> "7d"
            rangeLabel.contains("30D") -> "30d"
            else -> "all time"
        }

    private fun arrowFor(tone: LoadVelocityHeroTone): String =
        when (tone) {
            LoadVelocityHeroTone.RISING -> "↑"
            LoadVelocityHeroTone.FALLING -> "↓"
            LoadVelocityHeroTone.PLATEAU -> "→"
        }

    private fun formatSignedPercent(value: Int): String =
        if (value > 0) "+$value%" else "$value%"

    private fun formatPointDelta(value: Int): String =
        when {
            value > 0 -> "↑$value pts vs prev"
            value < 0 -> "↓${abs(value)} pts vs prev"
            else -> "→ flat vs prev"
        }

    private fun formatSessionDelta(value: Int): String =
        when {
            value > 0 -> "↑$value sessions vs prev"
            value < 0 -> "↓${abs(value)} sessions vs prev"
            else -> "→ same session pace"
        }

    private fun formatAvgRepsDelta(value: Int): String =
        when {
            value > 0 -> "↑$value avg reps vs prev"
            value < 0 -> "↓${abs(value)} avg reps vs prev"
            else -> "→ same avg output"
        }

    private fun stateLabelFor(
        stateSnapshot: StateSnapshot?,
        tone: LoadVelocityHeroTone
    ): String {
        if (stateSnapshot == null) {
            return defaultStateLabel(tone)
        }
        return when (stateSnapshot.liveContext.timeOfDay) {
            TimeOfDaySignal.MORNING -> "RECOVERY WINDOW"
            TimeOfDaySignal.PRE_WORKOUT -> "PRIME TRAINING WINDOW"
            TimeOfDaySignal.NIGHT -> "CNS LOAD WINDOW"
            TimeOfDaySignal.DAYTIME ->
                when (stateSnapshot.liveContext.dayOfWeek) {
                    DayOfWeekSignal.MONDAY -> "WEEK RESET"
                    DayOfWeekSignal.FRIDAY -> "END OF WEEK"
                    DayOfWeekSignal.SUNDAY -> "DEEP REVIEW MODE"
                    DayOfWeekSignal.STANDARD -> defaultStateLabel(tone)
                }
        }
    }

    private fun subtitleFor(
        snapshot: ProgressSnapshot,
        stateSnapshot: StateSnapshot?,
        tone: LoadVelocityHeroTone,
        windowLabel: String
    ): String {
        if (stateSnapshot == null) {
            return "${formatSignedPercent(snapshot.loadDeltaPercent)} · ${snapshot.totalSessions} SESSIONS · ${snapshot.totalReps} REPS"
        }

        return when (stateSnapshot.liveContext.timeOfDay) {
            TimeOfDaySignal.MORNING ->
                "RECOVERY WINDOW · ${stateSnapshot.hoursSinceLastSession}H SINCE LAST SESSION"
            TimeOfDaySignal.PRE_WORKOUT ->
                "${formatSignedPercent(snapshot.loadDeltaPercent)} · ${snapshot.totalSessions} SESSIONS · ${snapshot.totalReps} REPS"
            TimeOfDaySignal.NIGHT ->
                "LATE-DAY LOAD · ${stateSnapshot.hoursSinceLastSession}H SINCE LAST SESSION"
            TimeOfDaySignal.DAYTIME ->
                when (stateSnapshot.liveContext.dayOfWeek) {
                    DayOfWeekSignal.MONDAY ->
                        "WEEK RESET · ${stateSnapshot.currentStreak}D STREAK ACTIVE"
                    DayOfWeekSignal.FRIDAY ->
                        "END OF WEEK · ${snapshot.totalSessions} SESSIONS · ${snapshot.totalReps} REPS"
                    DayOfWeekSignal.SUNDAY ->
                        "DEEP REVIEW · ${snapshot.totalSessions} SESSIONS · ${snapshot.totalReps} REPS"
                    DayOfWeekSignal.STANDARD ->
                        "${formatSignedPercent(snapshot.loadDeltaPercent)} · ${snapshot.totalSessions} SESSIONS · ${snapshot.totalReps} REPS"
                }
        }
    }

    private fun heroTitleFor(stateSnapshot: StateSnapshot?): String {
        if (stateSnapshot == null) return "LOAD IS THE CLEAREST SIGNAL"
        val bestExercise = SessionStatsCalculator.exerciseLabel(stateSnapshot.bestExercise).uppercase(Locale.US)
        val weakestExercise = SessionStatsCalculator.exerciseLabel(stateSnapshot.weakestExercise).uppercase(Locale.US)
        return when {
            stateSnapshot.hasNewPR -> "$bestExercise JUST BROKE THROUGH"
            stateSnapshot.daysSinceLastCheckIn > 7 -> "BODY CHECK-IN IS LATE"
            stateSnapshot.bodyRecompSignal == com.airfloat.app.stats.BodyRecompSignal.ACTIVE -> "BODY RESPONSE IS NOW IN THE MODEL"
            stateSnapshot.loadTrend == com.airfloat.app.stats.LoadTrend.PLATEAU -> "$weakestExercise IS PACING THE BLOCK"
            else -> "$bestExercise IS YOUR STRONGEST SIGNAL"
        }
    }

    private fun defaultStateLabel(tone: LoadVelocityHeroTone): String =
        when (tone) {
            LoadVelocityHeroTone.RISING -> "RISING LOAD"
            LoadVelocityHeroTone.FALLING -> "LOAD DRAWDOWN"
            LoadVelocityHeroTone.PLATEAU -> "PLATEAU SIGNAL"
        }
}
