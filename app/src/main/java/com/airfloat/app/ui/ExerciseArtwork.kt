package com.airfloat.app.ui

import androidx.annotation.DrawableRes
import com.airfloat.app.R

object ExerciseArtwork {
    @DrawableRes
    fun forPreset(presetKey: String): Int? =
        when (presetKey) {
            WorkoutFragment.SOURCE_PRESS_BARBELL,
            HomeFragment.LAUNCH_PRESS_BARBELL -> R.drawable.barbell_press_image
            WorkoutFragment.SOURCE_PRESS_DUMBBELL,
            HomeFragment.LAUNCH_PRESS_DUMBBELL -> R.drawable.dumbbell_press_image
            WorkoutFragment.SOURCE_SQUAT_BETA,
            HomeFragment.LAUNCH_SQUAT_BETA -> R.drawable.squats_image
            WorkoutFragment.SOURCE_PUSHUP,
            HomeFragment.LAUNCH_PUSHUP -> R.drawable.pushups_image
            WorkoutFragment.SOURCE_SITUP,
            HomeFragment.LAUNCH_SITUP -> R.drawable.sit_up_image
            else -> null
        }

    @DrawableRes
    fun forZone(zone: MuscleZone): Int? =
        when (zone) {
            MuscleZone.CHEST -> R.drawable.pushups_image_today
            MuscleZone.CORE -> R.drawable.sit_up_image_today
            MuscleZone.ARMS -> R.drawable.barbell_press_image_today
            MuscleZone.LEGS -> R.drawable.squat_image_today
            else -> null
        }
}
