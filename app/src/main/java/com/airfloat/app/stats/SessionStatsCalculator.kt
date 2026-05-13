package com.airfloat.app.stats

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

enum class ProgressRange {
    DAYS_7,
    DAYS_30,
    ALL
}

data class DailyProgressPoint(
    val label: String,
    val reps: Int,
    val sessions: Int,
    val score: Float
)

data class ExerciseDistributionPoint(
    val exerciseKey: String,
    val label: String,
    val sessions: Int,
    val reps: Int,
    val avgCompletionRate: Int
)

data class SessionQualityPoint(
    val label: String,
    val completionRate: Int,
    val reps: Int,
    val exerciseKey: String
)

data class PersonalBestEntry(
    val title: String,
    val value: String,
    val detail: String,
    val exerciseKey: String,
    val sessionId: String
)

data class ConsistencyHeatmapCell(
    val label: String,
    val intensity: Int,
    val sessions: Int,
    val reps: Int,
    val isToday: Boolean
)

data class ExerciseMilestonePoint(
    val title: String,
    val value: String,
    val detail: String,
    val accentKey: String,
    val sessionId: String
)

data class ProgressSnapshot(
    val rangeLabel: String,
    val comparisonLabel: String,
    val totalSessions: Int,
    val totalReps: Int,
    val averageRepsPerSession: Int,
    val averageCompletionRate: Int,
    val totalKcal: Int,
    val currentStreakDays: Int,
    val activeDays: Int,
    val activeDaysTarget: Int,
    val precisionSignal: Int,
    val sessionDelta: Int,
    val repsDelta: Int,
    val completionDelta: Int,
    val loadDeltaPercent: Int,
    val trendPoints: List<DailyProgressPoint>,
    val qualityPoints: List<SessionQualityPoint>,
    val heatmapCells: List<ConsistencyHeatmapCell>,
    val distributionPoints: List<ExerciseDistributionPoint>,
    val topVolumeBest: PersonalBestEntry?,
    val topQualityBest: PersonalBestEntry?,
    val topEnduranceBest: PersonalBestEntry?,
    val recentSessions: List<WorkoutSessionRecord>
)

object SessionStatsCalculator {
    fun analyticsExerciseKey(session: WorkoutSessionRecord): String =
        when {
            session.exerciseKey == "press" && session.presetKey == "press_dumbbell" -> "press_dumbbell"
            session.exerciseKey == "press" && session.presetKey == "press_barbell" -> "press_barbell"
            else -> session.exerciseKey
        }

    fun buildSnapshot(
        sessions: List<WorkoutSessionRecord>,
        range: ProgressRange,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ProgressSnapshot {
        if (sessions.isEmpty()) {
            return ProgressSnapshot(
                rangeLabel = rangeLabel(range),
                comparisonLabel = comparisonLabel(range),
                totalSessions = 0,
                totalReps = 0,
                averageRepsPerSession = 0,
                averageCompletionRate = 0,
                totalKcal = 0,
                currentStreakDays = 0,
                activeDays = 0,
                activeDaysTarget = activityTargetDays(range),
                precisionSignal = 0,
                sessionDelta = 0,
                repsDelta = 0,
                completionDelta = 0,
                loadDeltaPercent = 0,
                trendPoints = emptyList(),
                qualityPoints = emptyList(),
                heatmapCells = emptyList(),
                distributionPoints = emptyList(),
                topVolumeBest = null,
                topQualityBest = null,
                topEnduranceBest = null,
                recentSessions = emptyList()
            )
        }

        val filtered = filterSessionsByRange(sessions, range, zoneId)
        val comparison = buildComparisonWindow(sessions, range, zoneId)
        val trendPoints = buildTrendPoints(filtered, range, zoneId)
        val qualityPoints = buildQualityPoints(filtered, zoneId)
        val heatmapCells = buildHeatmapCells(filtered, zoneId)
        val distribution = buildDistribution(filtered)

        val totalSessions = filtered.size
        val totalReps = filtered.sumOf { it.reps }
        val avgCompletion =
            if (filtered.isNotEmpty()) {
                (filtered.sumOf { it.completionRate } / filtered.size.toFloat()).roundToInt()
            } else {
                0
            }
        val averageRepsPerSession =
            if (filtered.isNotEmpty()) {
                (totalReps / filtered.size.toFloat()).roundToInt()
            } else {
                0
            }
        val currentComparisonSessions = comparison.first
        val previousComparisonSessions = comparison.second
        val currentComparisonReps = currentComparisonSessions.sumOf { it.reps }
        val previousComparisonReps = previousComparisonSessions.sumOf { it.reps }
        val currentComparisonCompletion = averageCompletion(currentComparisonSessions)
        val previousComparisonCompletion = averageCompletion(previousComparisonSessions)
        val activeDays = distinctActiveDays(currentComparisonSessions, zoneId)
        val precisionSignal = buildPrecisionSignal(filtered)

        return ProgressSnapshot(
            rangeLabel = rangeLabel(range),
            comparisonLabel = comparisonLabel(range),
            totalSessions = totalSessions,
            totalReps = totalReps,
            averageRepsPerSession = averageRepsPerSession,
            averageCompletionRate = avgCompletion,
            totalKcal = filtered.sumOf { it.estimatedKcal.roundToInt() },
            currentStreakDays = computeStreakDays(sessions, zoneId),
            activeDays = activeDays,
            activeDaysTarget = activityTargetDays(range),
            precisionSignal = precisionSignal,
            sessionDelta = currentComparisonSessions.size - previousComparisonSessions.size,
            repsDelta = currentComparisonReps - previousComparisonReps,
            completionDelta = currentComparisonCompletion - previousComparisonCompletion,
            loadDeltaPercent = computeLoadDeltaPercent(currentComparisonReps, previousComparisonReps),
            trendPoints = trendPoints,
            qualityPoints = qualityPoints,
            heatmapCells = heatmapCells,
            distributionPoints = distribution,
            topVolumeBest = buildTopVolumeBest(sessions, zoneId),
            topQualityBest = buildTopQualityBest(sessions, zoneId),
            topEnduranceBest = buildTopEnduranceBest(sessions, zoneId),
            recentSessions = filtered.sortedByDescending { it.timestampMs }.take(5)
        )
    }

    private fun filterSessionsByRange(
        sessions: List<WorkoutSessionRecord>,
        range: ProgressRange,
        zoneId: ZoneId
    ): List<WorkoutSessionRecord> {
        if (range == ProgressRange.ALL) return sessions.sortedByDescending { it.timestampMs }
        val today = LocalDate.now(zoneId)
        val days = if (range == ProgressRange.DAYS_7) 6L else 29L
        val earliest = today.minusDays(days)
        return sessions.filter {
            Instant.ofEpochMilli(it.timestampMs).atZone(zoneId).toLocalDate() >= earliest
        }
            .sortedByDescending { it.timestampMs }
    }

    private fun buildTrendPoints(
        sessions: List<WorkoutSessionRecord>,
        range: ProgressRange,
        zoneId: ZoneId
    ): List<DailyProgressPoint> {
        if (sessions.isEmpty()) return emptyList()

        val grouped =
            sessions.groupBy { Instant.ofEpochMilli(it.timestampMs).atZone(zoneId).toLocalDate() }

        val dates =
            when (range) {
                ProgressRange.DAYS_7 -> {
                    val today = LocalDate.now(zoneId)
                    (0L..6L).map { today.minusDays(6L - it) }
                }
                ProgressRange.DAYS_30 -> {
                    val today = LocalDate.now(zoneId)
                    (0L..29L).map { today.minusDays(29L - it) }
                }
                ProgressRange.ALL -> grouped.keys.sorted()
            }

        return dates.map { date ->
            val daySessions = grouped[date].orEmpty()
            val reps = daySessions.sumOf { it.reps }
            val avgCompletion =
                if (daySessions.isNotEmpty()) {
                    daySessions.sumOf { it.completionRate } / daySessions.size.toFloat()
                } else {
                    0f
                }
            val score = reps * (0.55f + (avgCompletion / 200f))
            DailyProgressPoint(
                label = chartLabel(date, range),
                reps = reps,
                sessions = daySessions.size,
                score = score
            )
        }
    }

    private fun buildDistribution(sessions: List<WorkoutSessionRecord>): List<ExerciseDistributionPoint> {
        return sessions
            .groupBy { analyticsExerciseKey(it) }
            .map { (exerciseKey, records) ->
                val completion =
                    if (records.isNotEmpty()) {
                        (records.sumOf { it.completionRate } / records.size.toFloat()).roundToInt()
                    } else {
                        0
                    }
                ExerciseDistributionPoint(
                    exerciseKey = exerciseKey,
                    label = exerciseLabel(exerciseKey),
                    sessions = records.size,
                    reps = records.sumOf { it.reps },
                    avgCompletionRate = completion
                )
            }
            .sortedByDescending { it.reps }
    }

    fun buildExerciseMilestones(
        sessions: List<WorkoutSessionRecord>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<ExerciseMilestonePoint> {
        if (sessions.isEmpty()) return emptyList()

        val milestones = mutableListOf<ExerciseMilestonePoint>()
        var bestReps = Int.MIN_VALUE
        var bestCompletion = Int.MIN_VALUE
        var bestDuration = Long.MIN_VALUE

        sessions.sortedBy { it.timestampMs }.forEach { session ->
            val date = Instant.ofEpochMilli(session.timestampMs).atZone(zoneId).toLocalDate()
            if (session.reps > bestReps) {
                bestReps = session.reps
                milestones +=
                    ExerciseMilestonePoint(
                        title = "VOLUME PR",
                        value = "${session.reps} reps",
                        detail = "${date.dayOfMonth}/${date.monthValue}",
                        accentKey = "volume",
                        sessionId = session.id
                    )
            }
            if (session.completionRate > bestCompletion) {
                bestCompletion = session.completionRate
                milestones +=
                    ExerciseMilestonePoint(
                        title = "PRECISION PR",
                        value = "${session.completionRate}%",
                        detail = "${date.dayOfMonth}/${date.monthValue}",
                        accentKey = "precision",
                        sessionId = session.id
                    )
            }
            if (session.durationMs > bestDuration) {
                bestDuration = session.durationMs
                milestones +=
                    ExerciseMilestonePoint(
                        title = "ENDURANCE PR",
                        value = formatDuration(session.durationMs),
                        detail = "${date.dayOfMonth}/${date.monthValue}",
                        accentKey = "endurance",
                        sessionId = session.id
                    )
            }
        }

        return milestones.takeLast(6)
    }

    private fun buildQualityPoints(
        sessions: List<WorkoutSessionRecord>,
        zoneId: ZoneId
    ): List<SessionQualityPoint> {
        return sessions
            .sortedByDescending { it.timestampMs }
            .take(8)
            .sortedBy { it.timestampMs }
            .map { session ->
                val date = Instant.ofEpochMilli(session.timestampMs).atZone(zoneId).toLocalDate()
                SessionQualityPoint(
                    label = "${shortExerciseLabel(analyticsExerciseKey(session))} ${date.dayOfMonth}",
                    completionRate = session.completionRate,
                    reps = session.reps,
                    exerciseKey = analyticsExerciseKey(session)
                )
            }
    }

    private fun buildHeatmapCells(
        sessions: List<WorkoutSessionRecord>,
        zoneId: ZoneId
    ): List<ConsistencyHeatmapCell> {
        val today = LocalDate.now(zoneId)
        val start = today.minusDays(34L)
        val grouped =
            sessions.groupBy { Instant.ofEpochMilli(it.timestampMs).atZone(zoneId).toLocalDate() }

        return (0L..34L).map { offset ->
            val date = start.plusDays(offset)
            val daySessions = grouped[date].orEmpty()
            val reps = daySessions.sumOf { it.reps }
            val avgCompletion = averageCompletion(daySessions)
            val intensity =
                if (daySessions.isEmpty()) {
                    0
                } else {
                    (reps.coerceAtMost(120) * 0.55f + avgCompletion * 0.45f)
                        .roundToInt()
                        .coerceIn(8, 100)
                }
            ConsistencyHeatmapCell(
                label = date.dayOfMonth.toString(),
                intensity = intensity,
                sessions = daySessions.size,
                reps = reps,
                isToday = date == today
            )
        }
    }

    private fun buildComparisonWindow(
        sessions: List<WorkoutSessionRecord>,
        range: ProgressRange,
        zoneId: ZoneId
    ): Pair<List<WorkoutSessionRecord>, List<WorkoutSessionRecord>> {
        val windowDays = comparisonWindowDays(range)
        val today = LocalDate.now(zoneId)
        val currentStart = today.minusDays(windowDays - 1L)
        val previousEnd = currentStart.minusDays(1L)
        val previousStart = previousEnd.minusDays(windowDays - 1L)

        val current =
            sessions.filter {
                val date = Instant.ofEpochMilli(it.timestampMs).atZone(zoneId).toLocalDate()
                date >= currentStart
            }
        val previous =
            sessions.filter {
                val date = Instant.ofEpochMilli(it.timestampMs).atZone(zoneId).toLocalDate()
                date in previousStart..previousEnd
            }
        return current to previous
    }

    private fun buildPrecisionSignal(sessions: List<WorkoutSessionRecord>): Int {
        val recent = sessions.sortedByDescending { it.timestampMs }.take(5).reversed()
        if (recent.isEmpty()) return 0

        var weightedScore = 0f
        var totalWeight = 0f
        recent.forEachIndexed { index, session ->
            val weight = (index + 1).toFloat()
            weightedScore += session.completionRate * weight
            totalWeight += weight
        }
        return (weightedScore / totalWeight).roundToInt().coerceIn(0, 100)
    }

    private fun buildTopVolumeBest(
        sessions: List<WorkoutSessionRecord>,
        zoneId: ZoneId
    ): PersonalBestEntry? {
        val best =
            sessions.maxWithOrNull(
                compareBy<WorkoutSessionRecord> { it.reps }
                    .thenBy { it.completionRate }
                    .thenBy { it.durationMs }
            ) ?: return null
        return PersonalBestEntry(
            title = "Top Volume",
            value = "${best.reps} reps",
            detail = sessionDetail(best, zoneId),
            exerciseKey = analyticsExerciseKey(best),
            sessionId = best.id
        )
    }

    private fun buildTopQualityBest(
        sessions: List<WorkoutSessionRecord>,
        zoneId: ZoneId
    ): PersonalBestEntry? {
        val best =
            sessions.maxWithOrNull(
                compareBy<WorkoutSessionRecord> { it.completionRate }
                    .thenBy { it.reps }
                    .thenBy { it.successfulAttempts }
            ) ?: return null
        return PersonalBestEntry(
            title = "Best Precision",
            value = "${best.completionRate}%",
            detail = sessionDetail(best, zoneId),
            exerciseKey = analyticsExerciseKey(best),
            sessionId = best.id
        )
    }

    private fun buildTopEnduranceBest(
        sessions: List<WorkoutSessionRecord>,
        zoneId: ZoneId
    ): PersonalBestEntry? {
        val best =
            sessions.maxWithOrNull(
                compareBy<WorkoutSessionRecord> { it.durationMs }
                    .thenBy { it.reps }
                    .thenBy { it.estimatedKcal }
            ) ?: return null
        return PersonalBestEntry(
            title = "Longest Grind",
            value = formatDuration(best.durationMs),
            detail = sessionDetail(best, zoneId),
            exerciseKey = analyticsExerciseKey(best),
            sessionId = best.id
        )
    }

    private fun sessionDetail(
        session: WorkoutSessionRecord,
        zoneId: ZoneId
    ): String {
        val date = Instant.ofEpochMilli(session.timestampMs).atZone(zoneId).toLocalDate()
        return "${exerciseLabel(analyticsExerciseKey(session))} \u2022 ${date.dayOfMonth}/${date.monthValue}"
    }

    private fun averageCompletion(sessions: List<WorkoutSessionRecord>): Int {
        if (sessions.isEmpty()) return 0
        return (sessions.sumOf { it.completionRate } / sessions.size.toFloat()).roundToInt()
    }

    private fun distinctActiveDays(
        sessions: List<WorkoutSessionRecord>,
        zoneId: ZoneId
    ): Int =
        sessions
            .map { Instant.ofEpochMilli(it.timestampMs).atZone(zoneId).toLocalDate() }
            .toSet()
            .size

    private fun computeLoadDeltaPercent(current: Int, previous: Int): Int {
        if (previous <= 0) return if (current > 0) 100 else 0
        return (((current - previous) / previous.toFloat()) * 100f).roundToInt().coerceIn(-999, 999)
    }

    private fun computeStreakDays(
        sessions: List<WorkoutSessionRecord>,
        zoneId: ZoneId
    ): Int {
        val dates =
            sessions
                .map { Instant.ofEpochMilli(it.timestampMs).atZone(zoneId).toLocalDate() }
                .toSet()
        if (dates.isEmpty()) return 0

        var cursor = LocalDate.now(zoneId)
        var streak = 0
        while (cursor in dates) {
            streak += 1
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    private fun chartLabel(date: LocalDate, range: ProgressRange): String {
        return when (range) {
            ProgressRange.DAYS_7 -> date.dayOfWeek.name.take(3)
            ProgressRange.DAYS_30 -> date.dayOfMonth.toString()
            ProgressRange.ALL -> "${date.monthValue}/${date.dayOfMonth}"
        }
    }

    private fun rangeLabel(range: ProgressRange): String =
        when (range) {
            ProgressRange.DAYS_7 -> "RANGE 7D"
            ProgressRange.DAYS_30 -> "RANGE 30D"
            ProgressRange.ALL -> "RANGE ALL"
        }

    private fun comparisonLabel(range: ProgressRange): String =
        when (range) {
            ProgressRange.DAYS_7 -> "vs previous 7 days"
            ProgressRange.DAYS_30 -> "vs previous 30 days"
            ProgressRange.ALL -> "vs previous 30 days"
        }

    private fun activityTargetDays(range: ProgressRange): Int =
        when (range) {
            ProgressRange.DAYS_7 -> 7
            ProgressRange.DAYS_30 -> 30
            ProgressRange.ALL -> 30
        }

    private fun comparisonWindowDays(range: ProgressRange): Long =
        when (range) {
            ProgressRange.DAYS_7 -> 7L
            ProgressRange.DAYS_30 -> 30L
            ProgressRange.ALL -> 30L
        }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000L).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    private fun shortExerciseLabel(exerciseKey: String): String =
        when (exerciseKey) {
            "press" -> "PR"
            "press_barbell" -> "BB"
            "press_dumbbell" -> "DB"
            "pushup" -> "PU"
            "situp" -> "SU"
            "squat_beta" -> "SQ"
            else -> "SS"
        }

    fun exerciseLabel(exerciseKey: String): String =
        when (exerciseKey) {
            "press" -> "Press"
            "press_barbell" -> "Barbell Press"
            "press_dumbbell" -> "Dumbbell Press"
            "pushup" -> "Push-up"
            "situp" -> "Sit-up"
            "squat_beta" -> "Squats"
            else -> "Session"
        }
}
