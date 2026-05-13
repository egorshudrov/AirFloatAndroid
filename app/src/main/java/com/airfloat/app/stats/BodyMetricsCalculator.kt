package com.airfloat.app.stats

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

data class BodyWeightTrendPoint(
    val label: String,
    val weightKg: Float
)

enum class BodyRecompositionTone {
    POSITIVE,
    NEUTRAL,
    WARNING
}

data class BodyMetricsSnapshot(
    val latestWeightKg: Float?,
    val rangeDeltaKg: Float,
    val latestWaistCm: Float?,
    val waistDeltaCm: Float,
    val latestBodyFatPercent: Float?,
    val bodyFatDeltaPercent: Float,
    val latestNote: String?,
    val checkInCount: Int,
    val outputPerKg: Float,
    val recompositionLabel: String,
    val recompositionDetail: String,
    val recompositionTone: BodyRecompositionTone,
    val trendPoints: List<BodyWeightTrendPoint>
)

data class BodyPerformanceCorrelationPoint(
    val sessionId: String,
    val title: String,
    val performanceValue: String,
    val sessionLabel: String,
    val bodySummary: String,
    val signalLabel: String,
    val signalDetail: String,
    val note: String?,
    val tone: BodyRecompositionTone
)

data class BodyPerformanceCorrelationSnapshot(
    val headline: String,
    val detail: String,
    val points: List<BodyPerformanceCorrelationPoint>
)

enum class BodyPhaseState {
    LEANER,
    CLEAN_GAIN,
    STABLE,
    SOFTER,
    NO_DATA
}

data class BodyPhasePeriodPoint(
    val label: String,
    val phaseLabel: String,
    val phaseState: BodyPhaseState,
    val bodySummary: String,
    val loadLabel: String,
    val intensity: Int
)

data class BodyPhaseMapSnapshot(
    val headline: String,
    val detail: String,
    val points: List<BodyPhasePeriodPoint>
)

object BodyMetricsCalculator {
    fun buildSnapshot(
        records: List<BodyWeightRecord>,
        totalReps: Int,
        range: ProgressRange,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): BodyMetricsSnapshot {
        if (records.isEmpty()) {
            return BodyMetricsSnapshot(
                latestWeightKg = null,
                rangeDeltaKg = 0f,
                latestWaistCm = null,
                waistDeltaCm = 0f,
                latestBodyFatPercent = null,
                bodyFatDeltaPercent = 0f,
                latestNote = null,
                checkInCount = 0,
                outputPerKg = 0f,
                recompositionLabel = "NO SIGNAL",
                recompositionDetail = "Start logging weight, waist, and body-fat to unlock recomposition analytics.",
                recompositionTone = BodyRecompositionTone.NEUTRAL,
                trendPoints = emptyList()
            )
        }

        val filtered = filterByRange(records, range, zoneId)
        val latest = filtered.maxByOrNull { it.timestampMs } ?: records.maxByOrNull { it.timestampMs }
        val earliest = filtered.minByOrNull { it.timestampMs } ?: latest
        val latestWeight = latest?.weightKg
        val latestWaist = filtered.latestMetricOrFallback(records) { it.waistCm }
        val earliestWaist = filtered.earliestMetricOrFallback(records) { it.waistCm }
        val latestBodyFat = filtered.latestMetricOrFallback(records) { it.bodyFatPercent }
        val earliestBodyFat = filtered.earliestMetricOrFallback(records) { it.bodyFatPercent }
        val outputPerKg =
            if (latestWeight != null && latestWeight > 0f) {
                totalReps / latestWeight
            } else {
                0f
            }
        val rangeDeltaKg = if (latest != null && earliest != null) latest.weightKg - earliest.weightKg else 0f
        val waistDeltaCm = if (latestWaist != null && earliestWaist != null) latestWaist - earliestWaist else 0f
        val bodyFatDeltaPercent =
            if (latestBodyFat != null && earliestBodyFat != null) latestBodyFat - earliestBodyFat else 0f
        val recompositionSignal = buildRecompositionSignal(
            latestWeightKg = latestWeight,
            rangeDeltaKg = rangeDeltaKg,
            latestWaistCm = latestWaist,
            waistDeltaCm = waistDeltaCm,
            latestBodyFatPercent = latestBodyFat,
            bodyFatDeltaPercent = bodyFatDeltaPercent,
            outputPerKg = outputPerKg,
            checkInCount = filtered.size
        )

        return BodyMetricsSnapshot(
            latestWeightKg = latestWeight,
            rangeDeltaKg = rangeDeltaKg,
            latestWaistCm = latestWaist,
            waistDeltaCm = waistDeltaCm,
            latestBodyFatPercent = latestBodyFat,
            bodyFatDeltaPercent = bodyFatDeltaPercent,
            latestNote = filtered.latestNoteOrFallback(records),
            checkInCount = filtered.size,
            outputPerKg = outputPerKg,
            recompositionLabel = recompositionSignal.label,
            recompositionDetail = recompositionSignal.detail,
            recompositionTone = recompositionSignal.tone,
            trendPoints = buildTrendPoints(filtered.ifEmpty { records }, range, zoneId)
        )
    }

    private fun filterByRange(
        records: List<BodyWeightRecord>,
        range: ProgressRange,
        zoneId: ZoneId
    ): List<BodyWeightRecord> {
        if (range == ProgressRange.ALL) return records.sortedBy { it.timestampMs }
        val today = LocalDate.now(zoneId)
        val days = if (range == ProgressRange.DAYS_7) 6L else 29L
        val earliest = today.minusDays(days)
        return records.filter {
            Instant.ofEpochMilli(it.timestampMs).atZone(zoneId).toLocalDate() >= earliest
        }.sortedBy { it.timestampMs }
    }

    private fun buildTrendPoints(
        records: List<BodyWeightRecord>,
        range: ProgressRange,
        zoneId: ZoneId
    ): List<BodyWeightTrendPoint> {
        if (records.isEmpty()) return emptyList()
        return records
            .sortedBy { it.timestampMs }
            .takeLast(if (range == ProgressRange.ALL) 8 else 6)
            .map { record ->
                val date = Instant.ofEpochMilli(record.timestampMs).atZone(zoneId).toLocalDate()
                BodyWeightTrendPoint(
                    label =
                        when (range) {
                            ProgressRange.DAYS_7 -> date.dayOfWeek.name.take(3)
                            ProgressRange.DAYS_30 -> date.dayOfMonth.toString()
                            ProgressRange.ALL -> "${date.dayOfMonth}/${date.monthValue}"
                        },
                    weightKg = record.weightKg
                )
            }
    }

    fun buildPerformanceCorrelationSnapshot(
        exerciseKey: String,
        exerciseSessions: List<WorkoutSessionRecord>,
        milestones: List<ExerciseMilestonePoint>,
        records: List<BodyWeightRecord>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): BodyPerformanceCorrelationSnapshot {
        if (exerciseSessions.isEmpty() || milestones.isEmpty()) {
            return BodyPerformanceCorrelationSnapshot(
                headline = "NO CORRELATION YET",
                detail = "Record body check-ins around hard sessions and this layer will connect body phase to breakthrough timing.",
                points = emptyList()
            )
        }

        val orderedSessions = exerciseSessions.sortedBy { it.timestampMs }
        val points =
            milestones.takeLast(4).mapNotNull { milestone ->
                val session = orderedSessions.firstOrNull { it.id == milestone.sessionId } ?: return@mapNotNull null
                val matchedRecord = findNearestCheckIn(session.timestampMs, records)
                val totalRepsToDate = orderedSessions.filter { it.timestampMs <= session.timestampMs }.sumOf { it.reps }
                val bodySnapshot =
                    matchedRecord?.let { record ->
                        buildSnapshot(
                            records = records.filter { it.timestampMs <= record.timestampMs },
                            totalReps = totalRepsToDate,
                            range = ProgressRange.ALL,
                            zoneId = zoneId
                        )
                    }
                val sessionDate = Instant.ofEpochMilli(session.timestampMs).atZone(zoneId).toLocalDate()
                val bodySummary =
                    if (matchedRecord != null) {
                        buildBodySummary(matchedRecord)
                    } else {
                        "No nearby body check-in"
                    }
                BodyPerformanceCorrelationPoint(
                    sessionId = session.id,
                    title = milestone.title,
                    performanceValue = milestone.value,
                    sessionLabel = "${sessionDate.dayOfMonth}/${sessionDate.monthValue} · ${SessionStatsCalculator.exerciseLabel(exerciseKey)}",
                    bodySummary = bodySummary,
                    signalLabel = bodySnapshot?.recompositionLabel ?: "NO PHASE DATA",
                    signalDetail = bodySnapshot?.recompositionDetail ?: "Add a body check-in closer to this session to lock the body-phase read.",
                    note = matchedRecord?.note?.takeIf { it.isNotBlank() },
                    tone = bodySnapshot?.recompositionTone ?: BodyRecompositionTone.NEUTRAL
                )
            }

        val positiveCount = points.count { it.tone == BodyRecompositionTone.POSITIVE }
        val warningCount = points.count { it.tone == BodyRecompositionTone.WARNING }
        val missingCount = points.count { it.bodySummary == "No nearby body check-in" }

        val headline =
            when {
                points.isEmpty() -> "NO CORRELATION YET"
                missingCount >= 2 -> "LOCK THE BODY SIGNAL"
                positiveCount > warningCount && positiveCount > 0 -> "BREAKTHROUGHS HIT IN CLEANER PHASES"
                warningCount > positiveCount -> "PRS ARE FIGHTING A SOFTER TREND"
                else -> "PERFORMANCE IS RISING THROUGH A STABLE PHASE"
            }
        val detail =
            when {
                points.isEmpty() ->
                    "No milestone-to-body links are available for this movement yet."
                missingCount >= 2 ->
                    "Your ${SessionStatsCalculator.exerciseLabel(exerciseKey).lowercase()} breakthroughs need tighter body check-ins around the session date."
                positiveCount > warningCount && positiveCount > 0 ->
                    "Most recent ${SessionStatsCalculator.exerciseLabel(exerciseKey).lowercase()} breakthroughs arrived while recomposition markers stayed favorable."
                warningCount > positiveCount ->
                    "Recent ${SessionStatsCalculator.exerciseLabel(exerciseKey).lowercase()} PRs landed while composition drifted softer. Good place to tighten recovery and intake."
                else ->
                    "Breakthroughs are landing in a mostly stable body phase, which is a strong base for the next push."
            }

        return BodyPerformanceCorrelationSnapshot(
            headline = headline,
            detail = detail,
            points = points
        )
    }

    fun buildPhaseMapSnapshot(
        exerciseKey: String,
        exerciseSessions: List<WorkoutSessionRecord>,
        records: List<BodyWeightRecord>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): BodyPhaseMapSnapshot {
        val weekStart = LocalDate.now(zoneId).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekStarts = (0L..5L).map { weekStart.minusWeeks(5L - it) }
        var previousAverages: WeekAverages? = null

        val points =
            weekStarts.map { start ->
                val end = start.plusDays(6L)
                val weekRecords =
                    records.filter {
                        val date = Instant.ofEpochMilli(it.timestampMs).atZone(zoneId).toLocalDate()
                        date in start..end
                    }
                val weekSessions =
                    exerciseSessions.filter {
                        val date = Instant.ofEpochMilli(it.timestampMs).atZone(zoneId).toLocalDate()
                        date in start..end
                    }
                val averages = buildWeekAverages(weekRecords)
                val phaseState = classifyPhase(averages, previousAverages)
                val point =
                    BodyPhasePeriodPoint(
                        label = "${start.dayOfMonth}/${start.monthValue}",
                        phaseLabel = phaseLabel(phaseState),
                        phaseState = phaseState,
                        bodySummary = averages?.summary ?: "No body check-in",
                        loadLabel = "${weekSessions.sumOf { it.reps }} reps · ${weekSessions.size} sess",
                        intensity = computePhaseIntensity(weekSessions)
                    )
                if (averages != null) previousAverages = averages
                point
            }

        val positiveCount = points.count { it.phaseState == BodyPhaseState.LEANER || it.phaseState == BodyPhaseState.CLEAN_GAIN }
        val warningCount = points.count { it.phaseState == BodyPhaseState.SOFTER }
        val noDataCount = points.count { it.phaseState == BodyPhaseState.NO_DATA }
        val headline =
            when {
                noDataCount >= 3 -> "PHASE MAP NEEDS MORE CHECK-INS"
                warningCount >= 3 -> "RECENT WEEKS DRIFTED SOFTER"
                positiveCount >= 3 -> "RECENT WEEKS STAYED PRODUCTIVE"
                else -> "BODY PHASES ARE MOSTLY STABLE"
            }
        val detail =
            when {
                noDataCount >= 3 ->
                    "Log at least one body check-in each week to turn this map into a reliable phase tracker for ${SessionStatsCalculator.exerciseLabel(exerciseKey).lowercase()}."
                warningCount >= 3 ->
                    "Several recent ${SessionStatsCalculator.exerciseLabel(exerciseKey).lowercase()} weeks show softness creep against load. Good place to tighten inputs."
                positiveCount >= 3 ->
                    "Most recent ${SessionStatsCalculator.exerciseLabel(exerciseKey).lowercase()} weeks stacked productive body phases on top of training load."
                else ->
                    "Phase drift is controlled. This is a strong baseline for the next push in ${SessionStatsCalculator.exerciseLabel(exerciseKey).lowercase()}."
            }

        return BodyPhaseMapSnapshot(
            headline = headline,
            detail = detail,
            points = points
        )
    }

    private data class RecompositionSignal(
        val label: String,
        val detail: String,
        val tone: BodyRecompositionTone
    )

    private data class WeekAverages(
        val weightKg: Float,
        val waistCm: Float?,
        val bodyFatPercent: Float?,
        val summary: String
    )

    private fun buildRecompositionSignal(
        latestWeightKg: Float?,
        rangeDeltaKg: Float,
        latestWaistCm: Float?,
        waistDeltaCm: Float,
        latestBodyFatPercent: Float?,
        bodyFatDeltaPercent: Float,
        outputPerKg: Float,
        checkInCount: Int
    ): RecompositionSignal {
        if (checkInCount == 0) {
            return RecompositionSignal(
                label = "NO SIGNAL",
                detail = "Start logging weight, waist, and body-fat to build a body-response model.",
                tone = BodyRecompositionTone.NEUTRAL
            )
        }

        if ((latestWaistCm == null && latestBodyFatPercent == null) || checkInCount < 2) {
            return when {
                rangeDeltaKg >= 0.8f ->
                    RecompositionSignal(
                        label = "WEIGHT UP",
                        detail = "Mass is climbing. Add waist or body-fat to verify whether this is clean gain.",
                        tone = BodyRecompositionTone.NEUTRAL
                    )
                rangeDeltaKg <= -0.8f ->
                    RecompositionSignal(
                        label = "WEIGHT CUT",
                        detail = "Body mass is trending down. Add waist or body-fat to separate a clean cut from simple scale loss.",
                        tone = BodyRecompositionTone.WARNING
                    )
                else ->
                    RecompositionSignal(
                        label = "WEIGHT STABLE",
                        detail = "Scale is steady. Add waist or body-fat to unlock a real recomposition read.",
                        tone = BodyRecompositionTone.NEUTRAL
                    )
            }
        }

        return when {
            (waistDeltaCm <= -1.0f || bodyFatDeltaPercent <= -0.5f) && rangeDeltaKg >= -0.6f ->
                RecompositionSignal(
                    label = "LEANER PROFILE",
                    detail = "Waist/body-fat is dropping while body mass is holding. Output is at ${formatOutputPerKg(outputPerKg)}.",
                    tone = BodyRecompositionTone.POSITIVE
                )
            rangeDeltaKg >= 0.7f && waistDeltaCm <= 0.7f && bodyFatDeltaPercent <= 0.3f ->
                RecompositionSignal(
                    label = "CLEAN GAIN",
                    detail = "Mass is rising without a matching softness spike. Good phase to push performance.",
                    tone = BodyRecompositionTone.POSITIVE
                )
            rangeDeltaKg <= -0.8f && waistDeltaCm >= -0.2f && bodyFatDeltaPercent >= -0.1f ->
                RecompositionSignal(
                    label = "CHECK CUT QUALITY",
                    detail = "Scale is dropping faster than composition markers. Recheck recovery, fueling, and weigh-in consistency.",
                    tone = BodyRecompositionTone.WARNING
                )
            waistDeltaCm >= 1.2f || bodyFatDeltaPercent >= 0.6f ->
                RecompositionSignal(
                    label = "SOFTER TREND",
                    detail = "Waist/body-fat is drifting up. Keep training load, but tighten recovery and nutrition inputs.",
                    tone = BodyRecompositionTone.WARNING
                )
            else ->
                RecompositionSignal(
                    label = "STABLE COMPOSITION",
                    detail = "Body metrics are mostly flat. This is a good baseline for the next performance push.",
                    tone = BodyRecompositionTone.NEUTRAL
                )
        }
    }

    private fun List<BodyWeightRecord>.latestMetricOrFallback(
        fallback: List<BodyWeightRecord>,
        extractor: (BodyWeightRecord) -> Float?
    ): Float? = asReversed().firstNotNullOfOrNull(extractor) ?: fallback.sortedBy { it.timestampMs }.asReversed().firstNotNullOfOrNull(extractor)

    private fun List<BodyWeightRecord>.earliestMetricOrFallback(
        fallback: List<BodyWeightRecord>,
        extractor: (BodyWeightRecord) -> Float?
    ): Float? = firstNotNullOfOrNull(extractor) ?: fallback.sortedBy { it.timestampMs }.firstNotNullOfOrNull(extractor)

    private fun List<BodyWeightRecord>.latestNoteOrFallback(fallback: List<BodyWeightRecord>): String? =
        asReversed().firstNotNullOfOrNull { it.note?.takeIf(String::isNotBlank) }
            ?: fallback.sortedBy { it.timestampMs }.asReversed().firstNotNullOfOrNull { it.note?.takeIf(String::isNotBlank) }

    private fun buildWeekAverages(records: List<BodyWeightRecord>): WeekAverages? {
        if (records.isEmpty()) return null
        val avgWeight = records.map { it.weightKg }.average().toFloat()
        val waistValues = records.mapNotNull { it.waistCm }
        val bodyFatValues = records.mapNotNull { it.bodyFatPercent }
        val avgWaist = waistValues.averageOrNull()
        val avgBodyFat = bodyFatValues.averageOrNull()
        val summary =
            buildList {
                add(formatWeight(avgWeight))
                avgWaist?.let { add(formatWaist(it)) }
                avgBodyFat?.let { add(formatBodyFat(it)) }
            }.joinToString(" · ")
        return WeekAverages(
            weightKg = avgWeight,
            waistCm = avgWaist,
            bodyFatPercent = avgBodyFat,
            summary = summary
        )
    }

    private fun classifyPhase(
        current: WeekAverages?,
        previous: WeekAverages?
    ): BodyPhaseState {
        if (current == null) return BodyPhaseState.NO_DATA
        if (previous == null) return BodyPhaseState.STABLE

        val weightDelta = current.weightKg - previous.weightKg
        val waistDelta = current.waistCm?.let { waist -> previous.waistCm?.let { waist - it } }
        val bodyFatDelta = current.bodyFatPercent?.let { bf -> previous.bodyFatPercent?.let { bf - it } }

        return when {
            ((waistDelta != null && waistDelta <= -0.8f) || (bodyFatDelta != null && bodyFatDelta <= -0.4f)) &&
                weightDelta >= -0.8f -> BodyPhaseState.LEANER
            weightDelta >= 0.6f &&
                (waistDelta == null || waistDelta <= 0.5f) &&
                (bodyFatDelta == null || bodyFatDelta <= 0.2f) -> BodyPhaseState.CLEAN_GAIN
            (waistDelta != null && waistDelta >= 0.8f) ||
                (bodyFatDelta != null && bodyFatDelta >= 0.4f) -> BodyPhaseState.SOFTER
            else -> BodyPhaseState.STABLE
        }
    }

    private fun phaseLabel(state: BodyPhaseState): String =
        when (state) {
            BodyPhaseState.LEANER -> "LEANER"
            BodyPhaseState.CLEAN_GAIN -> "GAIN"
            BodyPhaseState.STABLE -> "STABLE"
            BodyPhaseState.SOFTER -> "SOFTER"
            BodyPhaseState.NO_DATA -> "NO DATA"
        }

    private fun computePhaseIntensity(sessions: List<WorkoutSessionRecord>): Int {
        if (sessions.isEmpty()) return 16
        val reps = sessions.sumOf { it.reps }.coerceAtMost(180)
        val score = sessions.sumOf { it.completionRate } / sessions.size.toFloat()
        return (reps * 0.55f + score * 0.45f).roundToInt().coerceIn(18, 100)
    }

    private fun findNearestCheckIn(
        sessionTimestampMs: Long,
        records: List<BodyWeightRecord>
    ): BodyWeightRecord? {
        if (records.isEmpty()) return null
        val windowMs = 5L * 24L * 60L * 60L * 1000L
        val candidate =
            records.minByOrNull { record ->
                val diff = kotlin.math.abs(record.timestampMs - sessionTimestampMs)
                if (diff <= windowMs) diff else Long.MAX_VALUE
            }
        return candidate?.takeIf { kotlin.math.abs(it.timestampMs - sessionTimestampMs) <= windowMs }
    }

    private fun buildBodySummary(record: BodyWeightRecord): String =
        buildList {
            add(formatWeight(record.weightKg))
            record.waistCm?.let { add(formatWaist(it)) }
            record.bodyFatPercent?.let { add(formatBodyFat(it)) }
        }.joinToString(" · ")

    private fun List<Float>.averageOrNull(): Float? = if (isEmpty()) null else average().toFloat()

    fun formatWeight(weightKg: Float): String = String.format("%.1f kg", weightKg)

    fun formatDelta(deltaKg: Float): String =
        when {
            deltaKg > 0f -> String.format("+%.1f kg", deltaKg)
            deltaKg < 0f -> String.format("%.1f kg", deltaKg)
            else -> "0.0 kg"
        }

    fun formatWaist(waistCm: Float): String = String.format("%.1f cm", waistCm)

    fun formatWaistDelta(deltaCm: Float): String =
        when {
            deltaCm > 0f -> String.format("+%.1f cm", deltaCm)
            deltaCm < 0f -> String.format("%.1f cm", deltaCm)
            else -> "0.0 cm"
        }

    fun formatBodyFat(bodyFatPercent: Float): String = String.format("%.1f%%", bodyFatPercent)

    fun formatBodyFatDelta(deltaPercent: Float): String =
        when {
            deltaPercent > 0f -> String.format("+%.1f pts", deltaPercent)
            deltaPercent < 0f -> String.format("%.1f pts", deltaPercent)
            else -> "0.0 pts"
        }

    fun formatOutputPerKg(value: Float): String = String.format("%.1f reps/kg", value)
}
