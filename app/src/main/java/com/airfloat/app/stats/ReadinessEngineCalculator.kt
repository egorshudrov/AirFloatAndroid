package com.airfloat.app.stats

import kotlin.math.roundToInt

enum class ReadinessState {
    CHARGED,
    READY,
    CAUTION,
    RECOVER
}

enum class ReadinessFactorKind {
    LOAD,
    EXECUTION,
    RECOVERY,
    BODY
}

data class ReadinessFactor(
    val kind: ReadinessFactorKind,
    val label: String,
    val score: Int,
    val detail: String
)

data class ReadinessSnapshot(
    val score: Int,
    val state: ReadinessState,
    val headline: String,
    val detail: String,
    val nextMove: String,
    val factors: List<ReadinessFactor>
)

object ReadinessEngineCalculator {
    fun buildSnapshot(
        exerciseKey: String,
        overall: ProgressSnapshot,
        exercise: ProgressSnapshot,
        body: BodyMetricsSnapshot,
        phaseMap: BodyPhaseMapSnapshot,
        recentExerciseSessions: List<WorkoutSessionRecord>
    ): ReadinessSnapshot {
        val factors =
            listOf(
                buildLoadFactor(overall),
                buildExecutionFactor(exerciseKey, exercise, recentExerciseSessions),
                buildRecoveryFactor(overall, recentExerciseSessions),
                buildBodyFactor(body, phaseMap)
            )
        val weightedScore =
            (
                factors[0].score * 0.28f +
                    factors[1].score * 0.30f +
                    factors[2].score * 0.22f +
                    factors[3].score * 0.20f
                ).roundToInt().coerceIn(0, 100)
        val state =
            when {
                weightedScore >= 86 -> ReadinessState.CHARGED
                weightedScore >= 72 -> ReadinessState.READY
                weightedScore >= 56 -> ReadinessState.CAUTION
                else -> ReadinessState.RECOVER
            }
        val weakest = factors.minByOrNull { it.score }
        val movement = SessionStatsCalculator.exerciseLabel(exerciseKey).lowercase()
        val headline =
            when (state) {
                ReadinessState.CHARGED -> "SYSTEM READY"
                ReadinessState.READY -> "READY WITH CONTROL"
                ReadinessState.CAUTION -> "READINESS IS FRAGILE"
                ReadinessState.RECOVER -> "RECOVERY WINDOW"
            }
        val detail = weakest?.detail ?: "Keep building clean signal."
        val nextMove =
            when (state) {
                ReadinessState.CHARGED ->
                    "Best window to push $movement is the next heavy session."
                ReadinessState.READY ->
                    "Push if execution stays clean, but do not force sloppy extra volume."
                ReadinessState.CAUTION ->
                    "Use a controlled top set instead of an all-out jump until the weakest factor improves."
                ReadinessState.RECOVER ->
                    "Make the next $movement session technical or lighter before you chase another hard push."
            }

        return ReadinessSnapshot(
            score = weightedScore,
            state = state,
            headline = headline,
            detail = detail,
            nextMove = nextMove,
            factors = factors
        )
    }

    private fun buildLoadFactor(overall: ProgressSnapshot): ReadinessFactor {
        val score =
            when {
                overall.activeDays >= overall.activeDaysTarget && overall.precisionSignal < 74 -> 42
                overall.loadDeltaPercent >= 30 && overall.completionDelta < 0 -> 48
                overall.loadDeltaPercent >= 20 -> 61
                overall.loadDeltaPercent <= -20 && overall.precisionSignal >= 86 -> 84
                else -> 78
            }
        val detail =
            when {
                overall.activeDays >= overall.activeDaysTarget && overall.precisionSignal < 74 ->
                    "Cadence is already maxed while form dipped. More load now will likely come in dirty."
                overall.loadDeltaPercent >= 30 && overall.completionDelta < 0 ->
                    "Load jumped hard and technical quality slipped with it."
                overall.loadDeltaPercent <= -20 && overall.precisionSignal >= 86 ->
                    "Load cooled off enough to leave room for another push."
                else ->
                    "Load is sitting in a controlled band."
            }
        return ReadinessFactor(
            kind = ReadinessFactorKind.LOAD,
            label = "LOAD",
            score = score,
            detail = detail
        )
    }

    private fun buildExecutionFactor(
        exerciseKey: String,
        exercise: ProgressSnapshot,
        recentExerciseSessions: List<WorkoutSessionRecord>
    ): ReadinessFactor {
        val recent = recentExerciseSessions.sortedByDescending { it.timestampMs }.take(4)
        val failedAttempts = recent.sumOf { it.failedAttempts }
        val score =
            (exercise.precisionSignal - failedAttempts * 6)
                .coerceIn(0, 100)
                .let { if (recent.isNotEmpty() && recent.all { it.failedAttempts == 0 }) (it + 4).coerceAtMost(100) else it }
        val movement = SessionStatsCalculator.exerciseLabel(exerciseKey).lowercase()
        val detail =
            when {
                recent.isEmpty() ->
                    "Not enough recent $movement data to trust execution readiness yet."
                failedAttempts >= 3 || exercise.precisionSignal < 75 ->
                    "Recent $movement sessions still leak too many failed or loose reps."
                exercise.precisionSignal >= 90 ->
                    "Execution is sharp enough to trust a harder effort."
                else ->
                    "Technique is decent, but not yet laser-stable."
            }
        return ReadinessFactor(
            kind = ReadinessFactorKind.EXECUTION,
            label = "EXECUTION",
            score = score,
            detail = detail
        )
    }

    private fun buildRecoveryFactor(
        overall: ProgressSnapshot,
        recentExerciseSessions: List<WorkoutSessionRecord>
    ): ReadinessFactor {
        val recentDurationLoad = recentExerciseSessions.sortedByDescending { it.timestampMs }.take(3).sumOf { it.durationMs }
        val score =
            when {
                overall.currentStreakDays >= 5 && overall.activeDays >= overall.activeDaysTarget -> 54
                overall.activeDays >= overall.activeDaysTarget && overall.precisionSignal < 80 -> 58
                recentDurationLoad > 18L * 60L * 1000L -> 64
                overall.activeDays in 1..overall.activeDaysTarget -> 82
                else -> 72
            }
        val detail =
            when {
                overall.currentStreakDays >= 5 && overall.activeDays >= overall.activeDaysTarget ->
                    "Streak pressure is high enough that the next win may come from recovery, not more grind."
                overall.activeDays >= overall.activeDaysTarget && overall.precisionSignal < 80 ->
                    "Cadence is solid, but recovery is not fully protecting quality."
                recentDurationLoad > 18L * 60L * 1000L ->
                    "Recent sessions accumulated enough grind time to justify caution."
                else ->
                    "Recovery pressure looks manageable."
            }
        return ReadinessFactor(
            kind = ReadinessFactorKind.RECOVERY,
            label = "RECOVERY",
            score = score,
            detail = detail
        )
    }

    private fun buildBodyFactor(
        body: BodyMetricsSnapshot,
        phaseMap: BodyPhaseMapSnapshot
    ): ReadinessFactor {
        val positiveWeeks =
            phaseMap.points.count {
                it.phaseState == BodyPhaseState.LEANER || it.phaseState == BodyPhaseState.CLEAN_GAIN
            }
        val warningWeeks = phaseMap.points.count { it.phaseState == BodyPhaseState.SOFTER }
        val score =
            when {
                body.checkInCount == 0 -> 60
                body.recompositionTone == BodyRecompositionTone.WARNING || warningWeeks >= 2 -> 46
                body.recompositionTone == BodyRecompositionTone.POSITIVE && positiveWeeks >= 2 -> 86
                else -> 71
            }
        val detail =
            when {
                body.checkInCount == 0 ->
                    "Body signal is too thin. Readiness cannot fully trust the recomposition layer yet."
                body.recompositionTone == BodyRecompositionTone.WARNING || warningWeeks >= 2 ->
                    "Body phase is not helping right now. Push carefully until softness pressure cools off."
                body.recompositionTone == BodyRecompositionTone.POSITIVE && positiveWeeks >= 2 ->
                    "Body phase is supportive. Good time to convert clean work into progression."
                else ->
                    "Body phase is neutral and not blocking performance."
            }
        return ReadinessFactor(
            kind = ReadinessFactorKind.BODY,
            label = "BODY",
            score = score,
            detail = detail
        )
    }
}
