package com.airfloat.app.stats

enum class AdaptiveCoachingKind {
    LOAD,
    EXECUTION,
    BODY
}

enum class AdaptiveCoachingTone {
    ATTACK,
    SHARPEN,
    RECOVER,
    HOLD
}

data class AdaptiveCoachingInsight(
    val kind: AdaptiveCoachingKind,
    val title: String,
    val detail: String,
    val action: String,
    val tone: AdaptiveCoachingTone,
    val priority: Int
)

data class AdaptiveCoachingSnapshot(
    val headline: String,
    val detail: String,
    val insights: List<AdaptiveCoachingInsight>,
    val headlineTone: AdaptiveCoachingTone = AdaptiveCoachingTone.HOLD
)

object AdaptiveCoachingCalculator {
    fun buildSnapshot(
        selectedExerciseKey: String,
        overall: ProgressSnapshot,
        exercise: ProgressSnapshot,
        body: BodyMetricsSnapshot,
        phaseMap: BodyPhaseMapSnapshot,
        recentExerciseSessions: List<WorkoutSessionRecord>
    ): AdaptiveCoachingSnapshot {
        val insights =
            listOf(
                buildLoadInsight(overall),
                buildExecutionInsight(selectedExerciseKey, exercise, recentExerciseSessions),
                buildBodyInsight(selectedExerciseKey, body, phaseMap)
            ).sortedByDescending { it.priority }

        val lead = insights.firstOrNull()
        val headline =
            when (lead?.tone) {
                AdaptiveCoachingTone.ATTACK -> "SYSTEM GREENLIT A PUSH"
                AdaptiveCoachingTone.SHARPEN -> "SHARPEN BEFORE THE NEXT PUSH"
                AdaptiveCoachingTone.RECOVER -> "RECOVERY IS THE NEXT PR"
                AdaptiveCoachingTone.HOLD -> "CURRENT BLOCK IS STABLE"
                null -> "COACHING IS STANDING BY"
            }
        val detail = lead?.action ?: "Log more sessions and body check-ins to unlock adaptive coaching."

        return AdaptiveCoachingSnapshot(
            headline = headline,
            detail = detail,
            insights = insights,
            headlineTone = lead?.tone ?: AdaptiveCoachingTone.HOLD
        )
    }

    fun buildSnapshot(snapshot: StateSnapshot): AdaptiveCoachingSnapshot {
        val headlineTone =
            when {
                snapshot.streakRisk -> AdaptiveCoachingTone.SHARPEN
                snapshot.hasNewPR -> AdaptiveCoachingTone.ATTACK
                snapshot.lastSessionQuality == LastSessionQuality.POOR -> AdaptiveCoachingTone.RECOVER
                snapshot.liveContext.timeOfDay == TimeOfDaySignal.NIGHT -> AdaptiveCoachingTone.RECOVER
                snapshot.liveContext.timeOfDay == TimeOfDaySignal.PRE_WORKOUT -> AdaptiveCoachingTone.ATTACK
                snapshot.loadTrend == LoadTrend.PLATEAU -> AdaptiveCoachingTone.SHARPEN
                else -> AdaptiveCoachingTone.HOLD
            }
        val headline =
            when {
                snapshot.streakRisk -> "⚠ STREAK AT RISK"
                snapshot.hasNewPR -> "✦ NEW MILESTONE"
                snapshot.lastSessionQuality == LastSessionQuality.POOR -> "RECOVERY SIGNAL"
                snapshot.liveContext.timeOfDay == TimeOfDaySignal.MORNING -> "RECOVERY WINDOW"
                snapshot.liveContext.timeOfDay == TimeOfDaySignal.PRE_WORKOUT -> "PRIME TRAINING WINDOW"
                snapshot.liveContext.timeOfDay == TimeOfDaySignal.NIGHT -> "CNS LOAD INDEX"
                else -> "ADAPTIVE COACH"
            }
        val detail =
            when {
                snapshot.streakRisk ->
                    "You have a ${snapshot.currentStreak}-day run alive. Miss today and the consistency signal breaks."
                snapshot.hasNewPR ->
                    "${SessionStatsCalculator.exerciseLabel(snapshot.bestExercise)} just broke through. The next session should consolidate, not scatter."
                snapshot.lastSessionQuality == LastSessionQuality.POOR ->
                    "Your last ${SessionStatsCalculator.exerciseLabel(snapshot.lastSessionExercise).lowercase()} session degraded. The next move is recovery-led, not ego-led."
                snapshot.liveContext.timeOfDay == TimeOfDaySignal.MORNING ->
                    "Recovery is the story right now: ${snapshot.hoursSinceLastSession}H since the last session, so readiness matters more than a fresh load jump."
                snapshot.liveContext.timeOfDay == TimeOfDaySignal.PRE_WORKOUT ->
                    "You're in a prime training window. ${SessionStatsCalculator.exerciseLabel(snapshot.bestExercise)} is the strongest call if quality stays intact."
                snapshot.liveContext.timeOfDay == TimeOfDaySignal.NIGHT ->
                    "CNS load index is reading late-day. ${snapshot.hoursSinceLastSession}H since the last session usually means tomorrow decides whether this block absorbs or leaks."
                snapshot.loadTrend == LoadTrend.PLATEAU ->
                    "Load has flattened. This is where the screen should steer attention, not dump more charts on you."
                else ->
                    "Current signals are stable enough to guide the next session without overcorrecting."
            }

        val insights = buildSnapshotInsights(snapshot)

        return AdaptiveCoachingSnapshot(
            headline = headline,
            detail = detail,
            insights = insights,
            headlineTone = headlineTone
        )
    }

    private fun buildLoadInsight(overall: ProgressSnapshot): AdaptiveCoachingInsight {
        return when {
            overall.activeDays >= overall.activeDaysTarget && overall.precisionSignal < 74 ->
                AdaptiveCoachingInsight(
                    kind = AdaptiveCoachingKind.LOAD,
                    title = "RECOVER BEFORE PUSHING LOAD",
                    detail = "Cadence is maxed for the current range while precision dropped to ${overall.precisionSignal}%.",
                    action = "Take one lighter session, then rebuild intensity when precision climbs back above 80%.",
                    tone = AdaptiveCoachingTone.RECOVER,
                    priority = 96
                )
            overall.loadDeltaPercent >= 25 && overall.completionDelta <= -4 ->
                AdaptiveCoachingInsight(
                    kind = AdaptiveCoachingKind.LOAD,
                    title = "STABILIZE CURRENT LOAD",
                    detail = "Load surged ${signedPercent(overall.loadDeltaPercent)} while completion slipped ${signedPoints(overall.completionDelta)}.",
                    action = "Hold progression for 2 sessions and chase cleaner volume before adding more work.",
                    tone = AdaptiveCoachingTone.SHARPEN,
                    priority = 90
                )
            overall.loadDeltaPercent <= -15 && overall.precisionSignal >= 86 ->
                AdaptiveCoachingInsight(
                    kind = AdaptiveCoachingKind.LOAD,
                    title = "PUSH CAPACITY AGAIN",
                    detail = "Load cooled ${signedPercent(overall.loadDeltaPercent)} but overall precision stayed sharp at ${overall.precisionSignal}%.",
                    action = "Raise the next target slightly or add one hard set to the main movement this week.",
                    tone = AdaptiveCoachingTone.ATTACK,
                    priority = 82
                )
            else ->
                AdaptiveCoachingInsight(
                    kind = AdaptiveCoachingKind.LOAD,
                    title = "STACK THIS BLOCK",
                    detail = "Load and precision are balanced enough to keep accumulating without changing the plan.",
                    action = "Stay in the current progression and build more clean volume before forcing a new jump.",
                    tone = AdaptiveCoachingTone.HOLD,
                    priority = 58
                )
        }
    }

    private fun buildExecutionInsight(
        selectedExerciseKey: String,
        exercise: ProgressSnapshot,
        recentExerciseSessions: List<WorkoutSessionRecord>
    ): AdaptiveCoachingInsight {
        val recent = recentExerciseSessions.sortedByDescending { it.timestampMs }.take(3)
        val avgCompletion =
            if (recent.isNotEmpty()) {
                (recent.sumOf { it.completionRate } / recent.size.toFloat()).toInt()
            } else {
                exercise.averageCompletionRate
            }
        val failedAttempts = recent.sumOf { it.failedAttempts }
        val movement = SessionStatsCalculator.exerciseLabel(selectedExerciseKey).lowercase()

        return when {
            recent.isEmpty() ->
                AdaptiveCoachingInsight(
                    kind = AdaptiveCoachingKind.EXECUTION,
                    title = "BUILD A MOVEMENT BASELINE",
                    detail = "There is not enough recent $movement data to adapt technique guidance yet.",
                    action = "Finish 2-3 clean $movement sessions so the model can start prescribing technical moves.",
                    tone = AdaptiveCoachingTone.HOLD,
                    priority = 56
                )
            failedAttempts >= 3 || avgCompletion < 75 ->
                AdaptiveCoachingInsight(
                    kind = AdaptiveCoachingKind.EXECUTION,
                    title = "REBUILD CLEAN REPS",
                    detail = "Recent $movement sessions show $failedAttempts failed attempts with average completion around $avgCompletion%.",
                    action = "Run the same goal again and do not progress until you stack 3 cleaner sessions above 80%.",
                    tone = AdaptiveCoachingTone.SHARPEN,
                    priority = 92
                )
            avgCompletion >= 90 && recent.size >= 2 ->
                AdaptiveCoachingInsight(
                    kind = AdaptiveCoachingKind.EXECUTION,
                    title = "PR WINDOW IS OPEN",
                    detail = "Recent $movement execution is stable at $avgCompletion% with low technical leakage.",
                    action = "Use this movement for your hardest work next session while form is in a green zone.",
                    tone = AdaptiveCoachingTone.ATTACK,
                    priority = 84
                )
            else ->
                AdaptiveCoachingInsight(
                    kind = AdaptiveCoachingKind.EXECUTION,
                    title = "STACK TECHNICAL VOLUME",
                    detail = "$movement quality is serviceable but not yet sharp enough to force a bigger jump.",
                    action = "Keep the current target and bias the next sessions toward controlled, repeatable reps.",
                    tone = AdaptiveCoachingTone.HOLD,
                    priority = 60
                )
        }
    }

    private fun buildBodyInsight(
        selectedExerciseKey: String,
        body: BodyMetricsSnapshot,
        phaseMap: BodyPhaseMapSnapshot
    ): AdaptiveCoachingInsight {
        val movement = SessionStatsCalculator.exerciseLabel(selectedExerciseKey).lowercase()
        val positiveWeeks =
            phaseMap.points.count {
                it.phaseState == BodyPhaseState.LEANER || it.phaseState == BodyPhaseState.CLEAN_GAIN
            }
        val warningWeeks = phaseMap.points.count { it.phaseState == BodyPhaseState.SOFTER }
        val noDataWeeks = phaseMap.points.count { it.phaseState == BodyPhaseState.NO_DATA }

        return when {
            body.checkInCount == 0 || noDataWeeks >= 3 ->
                AdaptiveCoachingInsight(
                    kind = AdaptiveCoachingKind.BODY,
                    title = "LOCK THE BODY SIGNAL",
                    detail = "Performance is moving, but body-phase resolution is still too thin to guide $movement aggressively.",
                    action = "Log one body check-in every week so coaching can separate productive gain from softness drift.",
                    tone = AdaptiveCoachingTone.HOLD,
                    priority = 74
                )
            body.recompositionTone == BodyRecompositionTone.WARNING || warningWeeks >= 2 ->
                AdaptiveCoachingInsight(
                    kind = AdaptiveCoachingKind.BODY,
                    title = "TIGHTEN RECOVERY INPUTS",
                    detail = body.recompositionDetail,
                    action = "Keep load steady for now and tighten sleep, protein, and meal regularity before pushing harder.",
                    tone = AdaptiveCoachingTone.RECOVER,
                    priority = 94
                )
            body.recompositionTone == BodyRecompositionTone.POSITIVE && positiveWeeks >= 2 ->
                AdaptiveCoachingInsight(
                    kind = AdaptiveCoachingKind.BODY,
                    title = "BODY PHASE SUPPORTS PROGRESSION",
                    detail = "Recent body phases stayed favorable while $movement work kept moving.",
                    action = "Lean into harder work now while the phase map is still supporting progression.",
                    tone = AdaptiveCoachingTone.ATTACK,
                    priority = 83
                )
            else ->
                AdaptiveCoachingInsight(
                    kind = AdaptiveCoachingKind.BODY,
                    title = "HOLD BODY PHASE STEADY",
                    detail = body.recompositionDetail,
                    action = "Keep logging weekly check-ins and maintain current nutrition/recovery instead of changing multiple variables at once.",
                    tone = AdaptiveCoachingTone.HOLD,
                    priority = 61
                )
        }
    }

    private fun buildSnapshotInsights(snapshot: StateSnapshot): List<AdaptiveCoachingInsight> {
        if (snapshot.currentStreak == 0 &&
            !snapshot.hasNewPR &&
            snapshot.bodyRecompSignal == BodyRecompSignal.NONE &&
            snapshot.daysSinceLastCheckIn == Int.MAX_VALUE
        ) {
            return listOf(
                AdaptiveCoachingInsight(
                    kind = AdaptiveCoachingKind.LOAD,
                    title = "COLLECTING BASELINE",
                    detail = "Need 3 more sessions before the coach can separate true trend from noise.",
                    action = "Finish three clean tracked sessions and the screen will start ranking what matters.",
                    tone = AdaptiveCoachingTone.HOLD,
                    priority = 100
                )
            )
        }

        val rows =
            listOf(
                buildSnapshotLoadInsight(snapshot),
                buildSnapshotExecutionInsight(snapshot),
                buildSnapshotBodyInsight(snapshot)
            )

        return rows.sortedByDescending { it.priority + contextPriorityBoost(snapshot.liveContext, it.kind) }
    }

    private fun contextPriorityBoost(
        liveContext: LiveContextSnapshot,
        kind: AdaptiveCoachingKind
    ): Int =
        when (liveContext.timeOfDay) {
            TimeOfDaySignal.MORNING ->
                when (kind) {
                    AdaptiveCoachingKind.BODY -> 12
                    AdaptiveCoachingKind.EXECUTION -> 8
                    AdaptiveCoachingKind.LOAD -> -4
                }
            TimeOfDaySignal.PRE_WORKOUT ->
                when (kind) {
                    AdaptiveCoachingKind.LOAD -> 12
                    AdaptiveCoachingKind.EXECUTION -> 6
                    AdaptiveCoachingKind.BODY -> 0
                }
            TimeOfDaySignal.NIGHT ->
                when (kind) {
                    AdaptiveCoachingKind.BODY -> 10
                    AdaptiveCoachingKind.EXECUTION -> 8
                    AdaptiveCoachingKind.LOAD -> -6
                }
            TimeOfDaySignal.DAYTIME -> 0
        }

    private fun buildSnapshotLoadInsight(snapshot: StateSnapshot): AdaptiveCoachingInsight {
        val bestMovement = SessionStatsCalculator.exerciseLabel(snapshot.bestExercise).lowercase()
        val weakestMovement = SessionStatsCalculator.exerciseLabel(snapshot.weakestExercise).lowercase()
        return when (snapshot.loadTrend) {
            LoadTrend.RISING ->
                AdaptiveCoachingInsight(
                    kind = AdaptiveCoachingKind.LOAD,
                    title = "LOAD IS CLIMBING",
                    detail = "${snapshot.currentStreak} active days stacked while $bestMovement is carrying your strongest signal.",
                    action = "Push $bestMovement next, but keep $weakestMovement technical instead of chasing more volume everywhere.",
                    tone = AdaptiveCoachingTone.ATTACK,
                    priority = if (snapshot.hasNewPR) 94 else 82
                )
            LoadTrend.FALLING ->
                AdaptiveCoachingInsight(
                    kind = AdaptiveCoachingKind.LOAD,
                    title = "LOAD BACKED OFF",
                    detail = "${snapshot.currentStreak} active days in motion, but the block is no longer carrying forward momentum.",
                    action = "Rebuild controlled volume before trying to force another heavy jump.",
                    tone = AdaptiveCoachingTone.SHARPEN,
                    priority = 80
                )
            LoadTrend.PLATEAU ->
                AdaptiveCoachingInsight(
                    kind = AdaptiveCoachingKind.LOAD,
                    title = "LOAD IS FLAT",
                    detail = "${snapshot.currentStreak} active days have produced a plateau, which usually means the weakest link is now pacing the block.",
                    action = "Bias the next session toward $weakestMovement quality, then retest $bestMovement.",
                    tone = AdaptiveCoachingTone.SHARPEN,
                    priority = 88
                )
        }
    }

    private fun buildSnapshotExecutionInsight(snapshot: StateSnapshot): AdaptiveCoachingInsight {
        val lastMovement = SessionStatsCalculator.exerciseLabel(snapshot.lastSessionExercise).lowercase()
        val bestMovement = SessionStatsCalculator.exerciseLabel(snapshot.bestExercise).lowercase()
        return when (snapshot.lastSessionQuality) {
            LastSessionQuality.GREAT ->
                AdaptiveCoachingInsight(
                    kind = AdaptiveCoachingKind.EXECUTION,
                    title = "QUALITY WINDOW IS OPEN",
                    detail = "Last $lastMovement session graded great and your best movement is still $bestMovement.",
                    action = "Use the next session to consolidate this pattern before changing multiple variables.",
                    tone = AdaptiveCoachingTone.ATTACK,
                    priority = if (snapshot.hasNewPR) 92 else 78
                )
            LastSessionQuality.OK ->
                AdaptiveCoachingInsight(
                    kind = AdaptiveCoachingKind.EXECUTION,
                    title = "SHARPEN THE REP STANDARD",
                    detail = "Last $lastMovement session was usable, but not sharp enough to trust a bigger jump yet.",
                    action = "Keep the same target and clean the next session before progressing load.",
                    tone = AdaptiveCoachingTone.SHARPEN,
                    priority = 76
                )
            LastSessionQuality.POOR ->
                AdaptiveCoachingInsight(
                    kind = AdaptiveCoachingKind.EXECUTION,
                    title = "LAST SESSION LEAKED QUALITY",
                    detail = "The last $lastMovement session graded poor, which is your clearest recovery signal right now.",
                    action = "Give $lastMovement 48h or strip one layer of intensity before the next hard day.",
                    tone = AdaptiveCoachingTone.RECOVER,
                    priority = if (snapshot.streakRisk) 90 else 96
                )
        }
    }

    private fun buildSnapshotBodyInsight(snapshot: StateSnapshot): AdaptiveCoachingInsight {
        return when {
            snapshot.daysSinceLastCheckIn > 7 ->
                AdaptiveCoachingInsight(
                    kind = AdaptiveCoachingKind.BODY,
                    title = "BODY SIGNAL IS STALE",
                    detail = "${snapshot.daysSinceLastCheckIn} days since the last check-in, so body response is no longer trustworthy.",
                    action = "Log a fresh body check-in before you trust the next progression call.",
                    tone = AdaptiveCoachingTone.SHARPEN,
                    priority = 89
                )
            snapshot.bodyRecompSignal == BodyRecompSignal.ACTIVE ->
                AdaptiveCoachingInsight(
                    kind = AdaptiveCoachingKind.BODY,
                    title = "BODY PHASE IS LIVE",
                    detail = "${snapshot.daysSinceLastCheckIn} days since the last check-in, and recomposition signal is active enough to matter.",
                    action = "Hold nutrition and recovery steady while you push the next high-value session.",
                    tone = AdaptiveCoachingTone.ATTACK,
                    priority = 84
                )
            else ->
                AdaptiveCoachingInsight(
                    kind = AdaptiveCoachingKind.BODY,
                    title = "BODY MODEL IS THIN",
                    detail = "No active body recomposition signal yet, so training should lead and body data should catch up.",
                    action = "Keep logging weekly body check-ins so the next block has real body context.",
                    tone = AdaptiveCoachingTone.HOLD,
                    priority = 62
                )
        }
    }

    private fun signedPercent(value: Int): String =
        when {
            value > 0 -> "+$value%"
            value < 0 -> "$value%"
            else -> "0%"
        }

    private fun signedPoints(value: Int): String =
        when {
            value > 0 -> "+$value pts"
            value < 0 -> "$value pts"
            else -> "0 pts"
        }
}
