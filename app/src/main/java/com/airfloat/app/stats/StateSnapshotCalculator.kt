package com.airfloat.app.stats

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

enum class LastSessionQuality(private val rawValue: String) {
    GREAT("great"),
    OK("ok"),
    POOR("poor");

    override fun toString(): String = rawValue
}

enum class LoadTrend(private val rawValue: String) {
    RISING("rising"),
    FALLING("falling"),
    PLATEAU("plateau");

    override fun toString(): String = rawValue
}

enum class BodyRecompSignal(private val rawValue: String) {
    ACTIVE("active"),
    NONE("none");

    override fun toString(): String = rawValue
}

data class StateSnapshot(
    val lastSessionExercise: String,
    val lastSessionQuality: LastSessionQuality,
    val currentStreak: Int,
    val streakRisk: Boolean,
    val bestExercise: String,
    val weakestExercise: String,
    val loadTrend: LoadTrend,
    val bodyRecompSignal: BodyRecompSignal,
    val hasNewPR: Boolean,
    val daysSinceLastCheckIn: Int,
    val daysSinceLastSession: Int = Int.MAX_VALUE,
    val hoursSinceLastSession: Int = Int.MAX_VALUE,
    val liveContext: LiveContextSnapshot = LiveContextSnapshot()
)

object StateSnapshotCalculator {
    fun getStateSnapshot(
        sessions: List<WorkoutSessionRecord>,
        range: ProgressRange,
        exercise: String?,
        bodyRecords: List<BodyWeightRecord> = emptyList(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        nowEpochMs: Long = System.currentTimeMillis(),
        today: LocalDate = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId).toLocalDate()
    ): StateSnapshot {
        val liveContext = LiveContextCalculator.getLiveContext(nowEpochMs = nowEpochMs, zoneId = zoneId)
        if (sessions.isEmpty()) {
            return StateSnapshot(
                lastSessionExercise = "none",
                lastSessionQuality = LastSessionQuality.OK,
                currentStreak = 0,
                streakRisk = false,
                bestExercise = exercise ?: "none",
                weakestExercise = exercise ?: "none",
                loadTrend = LoadTrend.PLATEAU,
                bodyRecompSignal = BodyRecompSignal.NONE,
                hasNewPR = false,
                daysSinceLastCheckIn = Int.MAX_VALUE,
                daysSinceLastSession = Int.MAX_VALUE,
                hoursSinceLastSession = Int.MAX_VALUE,
                liveContext = liveContext
            )
        }

        val latestSession = sessions.maxByOrNull { it.timestampMs }!!
        val latestSessionDate = sessionDate(latestSession, zoneId)
        val hoursSinceLastSession =
            ChronoUnit.HOURS.between(
                Instant.ofEpochMilli(latestSession.timestampMs),
                Instant.ofEpochMilli(nowEpochMs)
            ).toInt().coerceAtLeast(0)
        val daysSinceLastSession =
            ChronoUnit.DAYS.between(
                latestSessionDate,
                today
            ).toInt().coerceAtLeast(0)
        val rangeSessions = filterSessionsByRange(sessions, range, zoneId, today)
        val rankedScope = rangeSessions.ifEmpty { sessions }
        val focusExercise = exercise ?: SessionStatsCalculator.analyticsExerciseKey(latestSession)
        val focusSessions =
            sessions
                .filter { SessionStatsCalculator.analyticsExerciseKey(it) == focusExercise }
                .sortedByDescending { it.timestampMs }
        val (currentWindow, previousWindow) = splitComparisonWindows(sessions, range, focusExercise, zoneId, today)
        val currentLoad = currentWindow.sumOf { it.reps }
        val previousLoad = previousWindow.sumOf { it.reps }
        val daysSinceLastCheckIn =
            bodyRecords
                .maxByOrNull { it.timestampMs }
                ?.let { latest ->
                    ChronoUnit.DAYS.between(
                        sessionDate(latest.timestampMs, zoneId),
                        today
                    ).toInt().coerceAtLeast(0)
                }
                ?: Int.MAX_VALUE
        val bodySnapshot =
            BodyMetricsCalculator.buildSnapshot(
                records = bodyRecords,
                totalReps = currentLoad,
                range = range,
                zoneId = zoneId
            )

        val scoredExercises = buildExerciseScores(rankedScope)
        val bestExercise = scoredExercises.maxByOrNull { it.score }?.exerciseKey ?: focusExercise
        val weakestExercise = scoredExercises.minByOrNull { it.score }?.exerciseKey ?: focusExercise

        return StateSnapshot(
            lastSessionExercise = SessionStatsCalculator.analyticsExerciseKey(latestSession),
            lastSessionQuality = qualityFor(latestSession.completionRate),
            currentStreak = computeActiveStreakDays(sessions, zoneId),
            streakRisk = latestSessionDate == today.minusDays(1L),
            bestExercise = bestExercise,
            weakestExercise = weakestExercise,
            loadTrend = loadTrendFor(currentLoad, previousLoad),
            bodyRecompSignal = bodySignalFor(bodySnapshot),
            hasNewPR = hasNewPr(focusSessions),
            daysSinceLastCheckIn = daysSinceLastCheckIn,
            daysSinceLastSession = daysSinceLastSession,
            hoursSinceLastSession = hoursSinceLastSession,
            liveContext = liveContext
        )
    }

    private data class ExerciseScore(
        val exerciseKey: String,
        val score: Float
    )

    private fun filterSessionsByRange(
        sessions: List<WorkoutSessionRecord>,
        range: ProgressRange,
        zoneId: ZoneId,
        today: LocalDate
    ): List<WorkoutSessionRecord> {
        if (range == ProgressRange.ALL) return sessions.sortedByDescending { it.timestampMs }
        val days = if (range == ProgressRange.DAYS_7) 6L else 29L
        val earliest = today.minusDays(days)
        return sessions
            .filter { sessionDate(it, zoneId) >= earliest }
            .sortedByDescending { it.timestampMs }
    }

    private fun splitComparisonWindows(
        sessions: List<WorkoutSessionRecord>,
        range: ProgressRange,
        exercise: String?,
        zoneId: ZoneId,
        today: LocalDate
    ): Pair<List<WorkoutSessionRecord>, List<WorkoutSessionRecord>> {
        val days = when (range) {
            ProgressRange.DAYS_7 -> 7L
            ProgressRange.DAYS_30 -> 30L
            ProgressRange.ALL -> 30L
        }
        val currentStart = today.minusDays(days - 1L)
        val previousStart = currentStart.minusDays(days)
        val previousEnd = currentStart.minusDays(1L)

        fun inExercise(session: WorkoutSessionRecord): Boolean =
            exercise == null || SessionStatsCalculator.analyticsExerciseKey(session) == exercise

        val current =
            sessions.filter { session ->
                val date = sessionDate(session, zoneId)
                inExercise(session) && date in currentStart..today
            }
        val previous =
            sessions.filter { session ->
                val date = sessionDate(session, zoneId)
                inExercise(session) && date in previousStart..previousEnd
            }
        return current to previous
    }

    private fun buildExerciseScores(sessions: List<WorkoutSessionRecord>): List<ExerciseScore> {
        return sessions
            .groupBy { SessionStatsCalculator.analyticsExerciseKey(it) }
            .map { (exerciseKey, records) ->
                val averageCompletion = records.map { it.completionRate }.average().toFloat()
                val averageReps = records.map { it.reps }.average().toFloat()
                val completionRatio = records.count { it.completed } / records.size.toFloat()
                val score =
                    (averageCompletion * 0.58f) +
                        (averageReps.coerceAtMost(150f) * 0.24f) +
                        (completionRatio * 100f * 0.18f)
                ExerciseScore(exerciseKey = exerciseKey, score = score)
            }
    }

    private fun qualityFor(completionRate: Int): LastSessionQuality =
        when {
            completionRate >= 90 -> LastSessionQuality.GREAT
            completionRate >= 75 -> LastSessionQuality.OK
            else -> LastSessionQuality.POOR
        }

    private fun computeActiveStreakDays(
        sessions: List<WorkoutSessionRecord>,
        zoneId: ZoneId
    ): Int {
        val dates =
            sessions
                .map { sessionDate(it, zoneId) }
                .toSet()
        val latestDate = dates.maxOrNull() ?: return 0
        var streak = 0
        var cursor = latestDate
        while (cursor in dates) {
            streak += 1
            cursor = cursor.minusDays(1L)
        }
        return streak
    }

    private fun loadTrendFor(
        currentLoad: Int,
        previousLoad: Int
    ): LoadTrend {
        if (previousLoad <= 0) {
            return if (currentLoad > 0) LoadTrend.RISING else LoadTrend.PLATEAU
        }
        val deltaPercent = ((currentLoad - previousLoad) / previousLoad.toFloat()) * 100f
        return when {
            deltaPercent >= 12f -> LoadTrend.RISING
            deltaPercent <= -12f -> LoadTrend.FALLING
            else -> LoadTrend.PLATEAU
        }
    }

    private fun bodySignalFor(snapshot: BodyMetricsSnapshot): BodyRecompSignal {
        val isActionableSignal =
            snapshot.checkInCount >= 2 &&
                snapshot.recompositionLabel !in setOf("NO SIGNAL", "WEIGHT UP", "WEIGHT CUT", "WEIGHT STABLE")
        return if (isActionableSignal) BodyRecompSignal.ACTIVE else BodyRecompSignal.NONE
    }

    private fun hasNewPr(sessions: List<WorkoutSessionRecord>): Boolean {
        val latest = sessions.firstOrNull() ?: return false
        val previous = sessions.drop(1)
        if (previous.isEmpty()) return false

        val maxPreviousReps = previous.maxOf { it.reps }
        val maxPreviousCompletion = previous.maxOf { it.completionRate }
        val maxPreviousSuccessfulAttempts = previous.maxOf { it.successfulAttempts }
        return latest.reps > maxPreviousReps ||
            latest.completionRate > maxPreviousCompletion ||
            latest.successfulAttempts > maxPreviousSuccessfulAttempts
    }

    private fun sessionDate(
        session: WorkoutSessionRecord,
        zoneId: ZoneId
    ): LocalDate = sessionDate(session.timestampMs, zoneId)

    private fun sessionDate(
        timestampMs: Long,
        zoneId: ZoneId
    ): LocalDate = Instant.ofEpochMilli(timestampMs).atZone(zoneId).toLocalDate()
}
