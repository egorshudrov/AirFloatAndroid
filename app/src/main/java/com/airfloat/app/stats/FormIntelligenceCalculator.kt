package com.airfloat.app.stats

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class FormSignalKind {
    TECHNIQUE,
    STABILITY,
    FAILURE_LEAK,
    CONFIDENCE
}

enum class FormIntelligenceTone {
    ELITE,
    SOLID,
    WATCH,
    FRAGILE
}

data class FormIntelligenceSignal(
    val kind: FormSignalKind,
    val label: String,
    val score: Int,
    val detail: String,
    val action: String
)

data class FormIntelligenceSnapshot(
    val formScore: Int,
    val stabilityScore: Int,
    val confidenceScore: Int,
    val tone: FormIntelligenceTone,
    val headline: String,
    val detail: String,
    val weakestLinkTitle: String,
    val weakestLinkDetail: String,
    val signals: List<FormIntelligenceSignal>
)

object FormIntelligenceCalculator {
    fun buildSnapshot(
        exerciseKey: String,
        recentExerciseSessions: List<WorkoutSessionRecord>
    ): FormIntelligenceSnapshot {
        val recent = recentExerciseSessions.sortedByDescending { it.timestampMs }.take(6)
        if (recent.isEmpty()) {
            return FormIntelligenceSnapshot(
                formScore = 0,
                stabilityScore = 0,
                confidenceScore = 0,
                tone = FormIntelligenceTone.FRAGILE,
                headline = "FORM SIGNAL IS EMPTY",
                detail = "Finish a few tracked sessions and the ML layer will start reading technique patterns instead of noise.",
                weakestLinkTitle = "SIGNAL THIN",
                weakestLinkDetail = "There is not enough recent ${SessionStatsCalculator.exerciseLabel(exerciseKey).lowercase()} data to trust technique analytics yet.",
                signals =
                    listOf(
                        FormIntelligenceSignal(
                            kind = FormSignalKind.CONFIDENCE,
                            label = "CONFIDENCE",
                            score = 0,
                            detail = "Recent tracked volume is still too thin for a trustworthy form model.",
                            action = "Build more recent sessions"
                        )
                    )
            )
        }

        val movement = SessionStatsCalculator.exerciseLabel(exerciseKey).lowercase()
        val averageCompletion = recent.averageOf { it.completionRate }
        val partialSessions = recent.count { !it.completed }
        val missedTargets =
            recent.count { it.goalReps > 0 && it.reps < it.goalReps }
        val totalFailedAttempts = recent.sumOf { it.failedAttempts }
        val totalAttempts = recent.sumOf { (it.successfulAttempts + it.failedAttempts).coerceAtLeast(1) }
        val averageAttempts = totalAttempts / recent.size.toFloat()
        val completionStdDev = standardDeviation(recent.map { it.completionRate.toFloat() })
        val averageCompletionDelta =
            if (recent.size > 1) {
                recent
                    .sortedBy { it.timestampMs }
                    .zipWithNext { prev, next -> abs(next.completionRate - prev.completionRate) }
                    .average()
                    .toFloat()
            } else {
                0f
            }

        val techniqueScore =
            (
                averageCompletion -
                    partialSessions * 6f -
                    missedTargets * 4f
                ).roundToInt().coerceIn(0, 100)
        val stabilityScore =
            (
                100f -
                    completionStdDev * 2.6f -
                    averageCompletionDelta * 1.25f
                ).roundToInt().coerceIn(0, 100)
        val failureLeakScore =
            (
                100f -
                    totalFailedAttempts * 7f -
                    partialSessions * 8f
                ).roundToInt().coerceIn(0, 100)
        val confidenceScore =
            (
                24f +
                    recent.size * 11f +
                    averageAttempts * 6f
                ).roundToInt().coerceIn(0, 100)

        val signals =
            listOf(
                FormIntelligenceSignal(
                    kind = FormSignalKind.TECHNIQUE,
                    label = "TECHNIQUE",
                    score = techniqueScore,
                    detail =
                        when {
                            averageCompletion >= 90f && partialSessions == 0 ->
                                "Recent $movement sessions stay clean even when volume moves."
                            averageCompletion < 75f || missedTargets >= 2 ->
                                "$movement rep shape is leaking before the target is fully secured."
                            else ->
                                "$movement technique is mostly clean, but still softens under target pressure."
                        },
                    action = "Inspect recent rep accuracy"
                ),
                FormIntelligenceSignal(
                    kind = FormSignalKind.STABILITY,
                    label = "STABILITY",
                    score = stabilityScore,
                    detail =
                        when {
                            completionStdDev <= 4f && averageCompletionDelta <= 5f ->
                                "Session-to-session quality is tightly stacked with little drift."
                            completionStdDev >= 10f || averageCompletionDelta >= 12f ->
                                "Quality swings too much between recent sessions to call the pattern stable."
                            else ->
                                "Quality is usable, but still oscillates when stress changes."
                        },
                    action = "Open wider trend window"
                ),
                FormIntelligenceSignal(
                    kind = FormSignalKind.FAILURE_LEAK,
                    label = "FAILURE LEAK",
                    score = failureLeakScore,
                    detail =
                        when {
                            totalFailedAttempts == 0 && partialSessions == 0 ->
                                "The current block is converting attempts into clean completions."
                            totalFailedAttempts >= 4 || partialSessions >= 2 ->
                                "$movement sessions are leaking too many failures for a clean progression block."
                            else ->
                                "Failure leakage is present, but still manageable."
                        },
                    action = "Open latest breakdown"
                ),
                FormIntelligenceSignal(
                    kind = FormSignalKind.CONFIDENCE,
                    label = "CONFIDENCE",
                    score = confidenceScore,
                    detail =
                        when {
                            recent.size < 3 ->
                                "There are not enough recent sessions yet to fully trust the model."
                            averageAttempts < 2.5f ->
                                "Tracked attempt density is still thin, so the ML signal is only moderately reliable."
                            else ->
                                "Recent session density is strong enough for the current form model to speak clearly."
                        },
                    action = "Grow the signal base"
                )
            )

        val weakestSignal = signals.minByOrNull { it.score } ?: signals.first()
        val formScore =
            (
                techniqueScore * 0.42f +
                    stabilityScore * 0.23f +
                    failureLeakScore * 0.20f +
                    confidenceScore * 0.15f
                ).roundToInt().coerceIn(0, 100)
        val tone =
            when {
                formScore >= 88 -> FormIntelligenceTone.ELITE
                formScore >= 74 -> FormIntelligenceTone.SOLID
                formScore >= 58 -> FormIntelligenceTone.WATCH
                else -> FormIntelligenceTone.FRAGILE
            }
        val headline =
            when (tone) {
                FormIntelligenceTone.ELITE -> "FORM SIGNAL IS LOCKED"
                FormIntelligenceTone.SOLID -> "FORM IS HOLDING"
                FormIntelligenceTone.WATCH -> "FORM IS DRIFTING"
                FormIntelligenceTone.FRAGILE -> "FORM NEEDS REBUILD"
            }
        val detail =
            when (tone) {
                FormIntelligenceTone.ELITE ->
                    "Recent $movement execution is precise enough that progression can come from load, not cleanup."
                FormIntelligenceTone.SOLID ->
                    "The current $movement block is strong, but still needs guardrails around the weakest signal."
                FormIntelligenceTone.WATCH ->
                    "There is enough signal to train, but the model still sees instability before the next hard push."
                FormIntelligenceTone.FRAGILE ->
                    "Right now the model is reading more technique leakage than trustworthy progression."
            }

        return FormIntelligenceSnapshot(
            formScore = formScore,
            stabilityScore = stabilityScore,
            confidenceScore = confidenceScore,
            tone = tone,
            headline = headline,
            detail = detail,
            weakestLinkTitle = weakestLinkTitle(weakestSignal.kind),
            weakestLinkDetail = weakestSignal.detail,
            signals = signals
        )
    }

    private fun weakestLinkTitle(kind: FormSignalKind): String =
        when (kind) {
            FormSignalKind.TECHNIQUE -> "REP SHAPE"
            FormSignalKind.STABILITY -> "CONSISTENCY DRIFT"
            FormSignalKind.FAILURE_LEAK -> "FAILURE LEAK"
            FormSignalKind.CONFIDENCE -> "SIGNAL THIN"
        }

    private fun standardDeviation(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val mean = values.average().toFloat()
        val variance = values.sumOf { value -> val delta = value - mean; (delta * delta).toDouble() } / values.size
        return sqrt(variance).toFloat()
    }

    private fun List<WorkoutSessionRecord>.averageOf(selector: (WorkoutSessionRecord) -> Int): Float {
        if (isEmpty()) return 0f
        return sumOf(selector).toFloat() / size.toFloat()
    }
}
