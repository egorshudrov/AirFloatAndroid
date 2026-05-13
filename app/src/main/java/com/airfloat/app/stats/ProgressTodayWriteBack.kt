package com.airfloat.app.stats

data class ProgressTodayWriteBack(
    val recommendedExercise: String,
    val recommendedIntensity: RecommendedIntensity,
    val streakRisk: Boolean,
    val readAtMs: Long
)
