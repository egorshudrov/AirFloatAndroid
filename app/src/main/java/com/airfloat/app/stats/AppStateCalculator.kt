package com.airfloat.app.stats

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class RecommendedIntensity {
    LIGHT,
    NORMAL,
    HEAVY
}

data class SessionSummary(
    val id: String,
    val exerciseKey: String,
    val completed: Boolean,
    val reps: Int,
    val precision: Int,
    val failedAttempts: Int,
    val successfulAttempts: Int,
    val endTimeMs: Long
)

data class AppTimeContext(
    val nowEpochMs: Long,
    val zoneId: ZoneId = ZoneId.systemDefault(),
    val liveContext: LiveContextSnapshot = LiveContextCalculator.getLiveContext(nowEpochMs, zoneId),
    val today: LocalDate = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId).toLocalDate()
)

data class AppState(
    val lastSession: SessionSummary?,
    val currentStreak: Int,
    val streakRisk: Boolean,
    val recommendedExercise: String,
    val recommendedIntensity: RecommendedIntensity,
    val timeContext: LiveContextSnapshot,
    val hasNewPR: Boolean,
    val lastProgressRead: Long,
    val currentRank: String = "RAW",
    val avgPrecision: Int = 0,
    val precisionDeltaThisWeek: Int = 0,
    val sessionStreak: Int = 0,
    val qualityStreak: Int = 0,
    val hasEliteSession: Boolean = false,
    val eliteSessionCount: Int = 0,
    val exercisePrecision: Map<String, Int> = emptyMap(),
    val exerciseLastSeen: Map<String, String> = emptyMap(),
    val exerciseRole: Map<String, String> = emptyMap()
)

object AppStateCalculator {
    private const val DEFAULT_EXERCISE = "press_barbell"
    private val supportedExercises =
        listOf(
            "press_barbell",
            "press_dumbbell",
            "pushup",
            "situp",
            "squat_beta"
        )
    private val exerciseIntensityRank =
        mapOf(
            "situp" to 0,
            "pushup" to 1,
            "squat_beta" to 2,
            "press_dumbbell" to 3,
            "press_barbell" to 4
        )

    fun getAppState(
        sessions: List<WorkoutSessionRecord>,
        timeContext: AppTimeContext,
        lastProgressRead: Long = 0L
    ): AppState {
        if (sessions.isEmpty()) {
            return AppState(
                lastSession = null,
                currentStreak = 0,
                streakRisk = false,
                recommendedExercise = DEFAULT_EXERCISE,
                recommendedIntensity = RecommendedIntensity.NORMAL,
                timeContext = timeContext.liveContext,
                hasNewPR = false,
                lastProgressRead = lastProgressRead,
                currentRank = "RAW",
                avgPrecision = 0,
                precisionDeltaThisWeek = 0,
                sessionStreak = 0,
                qualityStreak = 0,
                hasEliteSession = false,
                eliteSessionCount = 0,
                exercisePrecision = supportedExercises.associateWith { 0 },
                exerciseLastSeen = supportedExercises.associateWith { "—" },
                exerciseRole = emptyMap()
            )
        }

        val latestSession = sessions.maxByOrNull { it.timestampMs }!!
        val latestSessionEndMs = latestSession.timestampMs + latestSession.durationMs
        val latestExercise = SessionStatsCalculator.analyticsExerciseKey(latestSession)
        val snapshot =
            StateSnapshotCalculator.getStateSnapshot(
                sessions = sessions,
                range = ProgressRange.DAYS_30,
                exercise = latestExercise,
                zoneId = timeContext.zoneId,
                nowEpochMs = timeContext.nowEpochMs,
                today = timeContext.today
            )
        val hoursSinceLastSession = snapshot.hoursSinceLastSession
        val streakRisk = hoursSinceLastSession in 36..72
        val restSignal = shouldRest(snapshot, hoursSinceLastSession, timeContext.liveContext)
        val recommendedIntensity =
            when {
                restSignal -> RecommendedIntensity.LIGHT
                snapshot.hasNewPR -> RecommendedIntensity.HEAVY
                streakRisk -> RecommendedIntensity.LIGHT
                snapshot.loadTrend == LoadTrend.RISING && snapshot.lastSessionQuality == LastSessionQuality.GREAT ->
                    RecommendedIntensity.HEAVY
                snapshot.lastSessionQuality == LastSessionQuality.POOR -> RecommendedIntensity.LIGHT
                else -> RecommendedIntensity.NORMAL
            }
        val avgPrecision = recentAveragePrecision(sessions)
        val currentRank = rankFor(avgPrecision)
        val precisionDeltaThisWeek = weeklyPrecisionDelta(sessions, timeContext.zoneId, timeContext.today)
        val hasUnseenPr = snapshot.hasNewPR && lastProgressRead < latestSessionEndMs
        val sessionStreak = consecutiveSessionStreak(sessions, thresholdPrecision = null)
        val qualityStreak = consecutiveSessionStreak(sessions, thresholdPrecision = 80)
        val eliteSessionCount = sessions.count { it.completionRate >= 89 }
        val exercisePrecision = buildExercisePrecisionMap(sessions)
        val exerciseLastSeen = buildExerciseLastSeenMap(sessions, timeContext.zoneId, timeContext.today)
        val exerciseRole =
            buildExerciseRoleMap(
                sessions = sessions,
                recommendedExercise = recommendedExercise(
                    sessions = sessions,
                    snapshot = snapshot,
                    restSignal = restSignal,
                    streakRisk = streakRisk
                ),
                precisionMap = exercisePrecision,
                zoneId = timeContext.zoneId,
                today = timeContext.today
            )
        val recommendedExercise =
            recommendedExercise(
                sessions = sessions,
                snapshot = snapshot,
                restSignal = restSignal,
                streakRisk = streakRisk
            )

        return AppState(
            lastSession =
                SessionSummary(
                    id = latestSession.id,
                    exerciseKey = latestExercise,
                    completed = latestSession.completed,
                    reps = latestSession.reps,
                    precision = latestSession.completionRate,
                    failedAttempts = latestSession.failedAttempts,
                    successfulAttempts = latestSession.successfulAttempts,
                    endTimeMs = latestSession.timestampMs + latestSession.durationMs
                ),
            currentStreak = snapshot.currentStreak,
            streakRisk = streakRisk,
            recommendedExercise = recommendedExercise,
            recommendedIntensity = recommendedIntensity,
            timeContext = timeContext.liveContext,
            hasNewPR = hasUnseenPr,
            lastProgressRead = lastProgressRead,
            currentRank = currentRank,
            avgPrecision = avgPrecision,
            precisionDeltaThisWeek = precisionDeltaThisWeek,
            sessionStreak = sessionStreak,
            qualityStreak = qualityStreak,
            hasEliteSession = eliteSessionCount > 0,
            eliteSessionCount = eliteSessionCount,
            exercisePrecision = exercisePrecision,
            exerciseLastSeen = exerciseLastSeen,
            exerciseRole = exerciseRole
        )
    }

    private fun recentAveragePrecision(sessions: List<WorkoutSessionRecord>): Int {
        val recent = sessions.sortedByDescending { it.timestampMs }.take(3)
        if (recent.isEmpty()) return 0
        return recent.map { it.completionRate }.average().toInt()
    }

    private fun rankFor(avgPrecision: Int): String =
        when {
            avgPrecision >= 89 -> "ELITE"
            avgPrecision >= 76 -> "SOLID"
            avgPrecision >= 61 -> "FORMING"
            else -> "RAW"
        }

    private fun weeklyPrecisionDelta(
        sessions: List<WorkoutSessionRecord>,
        zoneId: ZoneId,
        today: LocalDate
    ): Int {
        val currentStart = today.minusDays(6)
        val previousStart = currentStart.minusDays(7)
        val previousEnd = currentStart.minusDays(1)
        val currentWindow =
            sessions.filter { sessionDate(it.timestampMs, zoneId) in currentStart..today }
        val previousWindow =
            sessions.filter { sessionDate(it.timestampMs, zoneId) in previousStart..previousEnd }
        if (currentWindow.isEmpty() || previousWindow.isEmpty()) return 0
        val currentAverage = currentWindow.map { it.completionRate }.average()
        val previousAverage = previousWindow.map { it.completionRate }.average()
        return (currentAverage - previousAverage).toInt()
    }

    private fun consecutiveSessionStreak(
        sessions: List<WorkoutSessionRecord>,
        thresholdPrecision: Int?
    ): Int {
        if (sessions.isEmpty()) return 0
        val sorted = sessions.sortedByDescending { it.timestampMs }
        var streak = 0
        var previousTimestamp = 0L

        sorted.forEachIndexed { index, session ->
            if (thresholdPrecision != null && session.completionRate < thresholdPrecision) {
                return streak
            }
            if (index > 0) {
                val gapHours = ((previousTimestamp - session.timestampMs) / (60L * 60L * 1000L)).toInt()
                if (gapHours > 72) {
                    return streak
                }
            }
            streak += 1
            previousTimestamp = session.timestampMs
        }

        return streak
    }

    private fun buildExercisePrecisionMap(sessions: List<WorkoutSessionRecord>): Map<String, Int> =
        supportedExercises.associateWith { exercise ->
            sessions
                .filter { SessionStatsCalculator.analyticsExerciseKey(it) == exercise }
                .maxByOrNull { it.timestampMs }
                ?.completionRate ?: 0
        }

    private fun buildExerciseLastSeenMap(
        sessions: List<WorkoutSessionRecord>,
        zoneId: ZoneId,
        today: LocalDate
    ): Map<String, String> =
        supportedExercises.associateWith { exercise ->
            val latest =
                sessions
                    .filter { SessionStatsCalculator.analyticsExerciseKey(it) == exercise }
                    .maxByOrNull { it.timestampMs }
            if (latest == null) {
                "—"
            } else {
                val days = java.time.temporal.ChronoUnit.DAYS.between(sessionDate(latest.timestampMs, zoneId), today).toInt()
                "${days.coerceAtLeast(0)}D AGO"
            }
        }

    private fun buildExerciseRoleMap(
        sessions: List<WorkoutSessionRecord>,
        recommendedExercise: String,
        precisionMap: Map<String, Int>,
        zoneId: ZoneId,
        today: LocalDate
    ): Map<String, String> {
        val roles = linkedMapOf<String, String>()
        val remaining = supportedExercises.toMutableSet()

        if (remaining.remove(recommendedExercise)) {
            roles[recommendedExercise] = "RECOMMENDED"
        }

        val secondRecent =
            remaining.maxByOrNull { exercise ->
                sessions
                    .filter { SessionStatsCalculator.analyticsExerciseKey(it) == exercise }
                    .maxOfOrNull { it.timestampMs } ?: Long.MIN_VALUE
            }
        if (secondRecent != null && remaining.remove(secondRecent)) {
            roles[secondRecent] = "RECENT"
        }

        val underused =
            remaining.minByOrNull { exercise ->
                sessions.count {
                    SessionStatsCalculator.analyticsExerciseKey(it) == exercise &&
                        !sessionDate(it.timestampMs, zoneId).isBefore(today.minusDays(30))
                }
            }
        if (underused != null && remaining.remove(underused)) {
            roles[underused] = "UNDERUSED"
        }

        val improve =
            remaining.minByOrNull { exercise ->
                val precision = precisionMap[exercise] ?: Int.MAX_VALUE
                if (precision == 0) Int.MAX_VALUE else precision
            }
        if (improve != null && remaining.remove(improve)) {
            roles[improve] = "IMPROVE"
        }

        remaining.forEach { roles[it] = "RECENT" }
        return roles
    }

    private fun recommendedExercise(
        sessions: List<WorkoutSessionRecord>,
        snapshot: StateSnapshot,
        restSignal: Boolean,
        streakRisk: Boolean
    ): String =
        when {
            snapshot.hasNewPR -> snapshot.bestExercise
            restSignal -> snapshot.weakestExercise
            streakRisk -> lightestExercise(sessions)
            snapshot.lastSessionQuality == LastSessionQuality.POOR -> snapshot.weakestExercise
            snapshot.loadTrend == LoadTrend.RISING -> snapshot.bestExercise
            else -> snapshot.weakestExercise
        }

    private fun shouldRest(
        snapshot: StateSnapshot,
        hoursSinceLastSession: Int,
        liveContext: LiveContextSnapshot
    ): Boolean {
        val freshHeavySession =
            hoursSinceLastSession in 0..24 &&
                snapshot.currentStreak >= 3 &&
                (snapshot.loadTrend == LoadTrend.RISING || snapshot.hasNewPR) &&
                snapshot.lastSessionQuality != LastSessionQuality.POOR

        return freshHeavySession ||
            (
                liveContext.timeOfDay == TimeOfDaySignal.NIGHT &&
                    hoursSinceLastSession in 0..18 &&
                    snapshot.lastSessionQuality == LastSessionQuality.GREAT
                )
    }

    private fun lightestExercise(sessions: List<WorkoutSessionRecord>): String {
        val byExercise = sessions.groupBy { SessionStatsCalculator.analyticsExerciseKey(it) }
        val trackedExercises = supportedExercises.filter { byExercise[it].orEmpty().isNotEmpty() }
        val rankingScope = if (trackedExercises.isEmpty()) supportedExercises else trackedExercises
        return rankingScope.minByOrNull { exerciseKey ->
            val records = byExercise[exerciseKey].orEmpty()
            val avgReps = if (records.isEmpty()) 0f else records.map { it.reps }.average().toFloat()
            val avgDuration =
                if (records.isEmpty()) {
                    0f
                } else {
                    records.map { it.durationMs / 60_000f }.average().toFloat()
                }
            (avgReps * 0.55f) + (avgDuration * 6f) + ((exerciseIntensityRank[exerciseKey] ?: 5) * 10f)
        } ?: DEFAULT_EXERCISE
    }

    private fun sessionDate(
        timestampMs: Long,
        zoneId: ZoneId
    ): LocalDate = Instant.ofEpochMilli(timestampMs).atZone(zoneId).toLocalDate()
}
