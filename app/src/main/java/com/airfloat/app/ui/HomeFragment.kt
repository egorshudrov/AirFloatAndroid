package com.airfloat.app.ui

import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.airfloat.app.R
import com.airfloat.app.stats.AppState
import com.airfloat.app.stats.LiveContextSnapshot
import com.airfloat.app.stats.RecommendedIntensity
import kotlin.math.roundToInt

class HomeFragment : Fragment(R.layout.fragment_home) {
    interface Host {
        fun onQuickLaunchPresetRequested(presetKey: String)
    }

    private val surfaceEase = DecelerateInterpolator(1.7f)
    private var host: Host? = null
    private var uiState: TodayUiState = TodaySurfaceFactory.build(
        appState = AppState(
            lastSession = null,
            currentStreak = 0,
            streakRisk = false,
            recommendedExercise = LAUNCH_PRESS_BARBELL,
            recommendedIntensity = RecommendedIntensity.NORMAL,
            timeContext = LiveContextSnapshot(),
            hasNewPR = false,
            lastProgressRead = 0L
        ),
        sessions = emptyList()
    )
    private lateinit var dateMonthText: TextView
    private lateinit var dateDayText: TextView
    private lateinit var titleText: TextView
    private lateinit var bodyMapCardView: BodyMapCardView

    override fun onAttach(context: android.content.Context) {
        super.onAttach(context)
        host = context as? Host
    }

    override fun onDetach() {
        host = null
        super.onDetach()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val headerRow: View = view.findViewById(R.id.todayHeaderRow)
        dateMonthText = view.findViewById(R.id.todayDateMonthText)
        dateDayText = view.findViewById(R.id.todayDateDayText)
        titleText = view.findViewById(R.id.todayTitleText)
        bodyMapCardView = view.findViewById(R.id.todayBodyMapCard)

        bodyMapCardView.setBodyMapCardListener(object : BodyMapCardView.BodyMapCardListener {
            override fun onExerciseSelected(presetKey: String) {
                host?.onQuickLaunchPresetRequested(presetKey)
            }
        })

        animateViewIn(headerRow, 0L, fromYDp = 10f, fromScale = 0.99f, durationMs = 420L)
        animateViewIn(bodyMapCardView, 60L, fromYDp = 18f, fromScale = 0.97f, durationMs = 560L)
        render(uiState)
    }

    fun render(state: TodayUiState) {
        uiState = state
        if (view == null) return
        if (!::bodyMapCardView.isInitialized) return

        dateMonthText.text = state.dateMonthLabel
        dateDayText.text = state.dateDayLabel
        titleText.text = "TODAY"
        bodyMapCardView.setZones(buildMuscleZones(state.appState))
    }

    private fun animateViewIn(
        view: View,
        delayMs: Long,
        fromYDp: Float = 0f,
        fromScale: Float = 0.97f,
        durationMs: Long = 560L
    ) {
        view.animate().cancel()
        view.alpha = 0f
        view.translationY = fromYDp.dpPx()
        view.scaleX = fromScale
        view.scaleY = fromScale
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(delayMs)
            .setDuration(durationMs)
            .setInterpolator(surfaceEase)
            .start()
    }

    private fun Float.dpPx(): Float = this * resources.displayMetrics.density

    private fun buildMuscleZones(appState: AppState): List<MuscleZoneModel> =
        listOf(
            MuscleZoneModel(
                zone = MuscleZone.CHEST,
                precision = averagePrecision(appState, CHEST_KEYS),
                rank = rankFor(averagePrecision(appState, CHEST_KEYS)),
                lastSeenLabel = mostRecentLabel(appState, CHEST_KEYS),
                exercises = buildZoneExercises(appState, MuscleZone.CHEST),
                missReason = zoneMissReason(appState, MuscleZone.CHEST, averagePrecision(appState, CHEST_KEYS), CHEST_KEYS)
            ),
            MuscleZoneModel(
                zone = MuscleZone.CORE,
                precision = averagePrecision(appState, CORE_KEYS),
                rank = rankFor(averagePrecision(appState, CORE_KEYS)),
                lastSeenLabel = mostRecentLabel(appState, CORE_KEYS),
                exercises = buildZoneExercises(appState, MuscleZone.CORE),
                missReason = zoneMissReason(appState, MuscleZone.CORE, averagePrecision(appState, CORE_KEYS), CORE_KEYS)
            ),
            MuscleZoneModel(
                zone = MuscleZone.ARMS,
                precision = averagePrecision(appState, ARMS_KEYS),
                rank = rankFor(averagePrecision(appState, ARMS_KEYS)),
                lastSeenLabel = mostRecentLabel(appState, ARMS_KEYS),
                exercises = buildZoneExercises(appState, MuscleZone.ARMS),
                missReason = zoneMissReason(appState, MuscleZone.ARMS, averagePrecision(appState, ARMS_KEYS), ARMS_KEYS)
            ),
            MuscleZoneModel(
                zone = MuscleZone.LEGS,
                precision = averagePrecision(appState, LEGS_KEYS),
                rank = rankFor(averagePrecision(appState, LEGS_KEYS)),
                lastSeenLabel = mostRecentLabel(appState, LEGS_KEYS),
                exercises = buildZoneExercises(appState, MuscleZone.LEGS),
                missReason = zoneMissReason(appState, MuscleZone.LEGS, averagePrecision(appState, LEGS_KEYS), LEGS_KEYS)
            )
        )

    private fun buildZoneExercises(
        appState: AppState,
        zone: MuscleZone
    ): List<ZoneExercise> =
        when (zone) {
            MuscleZone.CHEST ->
                listOf(
                    ZoneExercise(
                        name = "Barbell Press",
                        precision = appState.exercisePrecision["press_barbell"] ?: 0,
                        presetKey = LAUNCH_PRESS_BARBELL
                    ),
                    ZoneExercise(
                        name = "Dumbbell Press",
                        precision = appState.exercisePrecision["press_dumbbell"] ?: 0,
                        presetKey = LAUNCH_PRESS_DUMBBELL
                    ),
                    ZoneExercise(
                        name = "Push-up",
                        precision = appState.exercisePrecision["pushup"] ?: 0,
                        presetKey = LAUNCH_PUSHUP
                    )
                )
            MuscleZone.CORE ->
                listOf(
                    ZoneExercise(
                        name = "Sit-up",
                        precision = appState.exercisePrecision["situp"] ?: 0,
                        presetKey = LAUNCH_SITUP
                    )
                )
            MuscleZone.ARMS ->
                listOf(
                    ZoneExercise(
                        name = "Dumbbell Press",
                        precision = appState.exercisePrecision["press_dumbbell"] ?: 0,
                        presetKey = LAUNCH_PRESS_DUMBBELL
                    )
                )
            MuscleZone.LEGS ->
                listOf(
                    ZoneExercise(
                        name = "Squats",
                        precision = appState.exercisePrecision["squat_beta"] ?: 0,
                        presetKey = LAUNCH_SQUAT_BETA
                    )
                )
        }

    private fun averagePrecision(
        appState: AppState,
        exerciseKeys: List<String>
    ): Int {
        val values = exerciseKeys.mapNotNull { key ->
            appState.exercisePrecision[key]?.takeIf { it > 0 }
        }
        return if (values.isEmpty()) 0 else values.average().roundToInt()
    }

    private fun mostRecentLabel(
        appState: AppState,
        exerciseKeys: List<String>
    ): String {
        val labels = exerciseKeys.mapNotNull { key -> appState.exerciseLastSeen[key] }.filter { it != "—" }
        return labels.minByOrNull { parseDaysAgo(it) } ?: "—"
    }

    private fun parseDaysAgo(label: String): Int =
        label.substringBefore('D').toIntOrNull() ?: Int.MAX_VALUE

    private fun rankFor(precision: Int): FormRank =
        when (precision) {
            in 89..100 -> FormRank.ELITE
            in 76..88 -> FormRank.SOLID
            in 61..75 -> FormRank.FORMING
            else -> FormRank.RAW
        }

    private fun zoneMissReason(
        appState: AppState,
        zone: MuscleZone,
        precision: Int,
        exerciseKeys: List<String>
    ): String? {
        if (!hasZoneData(appState, exerciseKeys)) return null
        if (precision > 75) return null
        return when (zone) {
            MuscleZone.CHEST -> "FORM BREAK — press path opened on the final reps"
            MuscleZone.CORE -> "FORM BREAK — trunk control opened too early"
            MuscleZone.ARMS -> "FORM BREAK — shoulder drive drifted unevenly"
            MuscleZone.LEGS -> "FORM BREAK — left knee tracking inward"
        }
    }

    private fun hasZoneData(
        appState: AppState,
        exerciseKeys: List<String>
    ): Boolean = exerciseKeys.any { key -> (appState.exerciseLastSeen[key] ?: "—") != "—" }

    companion object {
        const val TAG = "root_home"
        const val LAUNCH_PRESS_BARBELL = "press_barbell"
        const val LAUNCH_PRESS_DUMBBELL = "press_dumbbell"
        const val LAUNCH_PUSHUP = "pushup"
        const val LAUNCH_SITUP = "situp"
        const val LAUNCH_SQUAT_BETA = "squat_beta"
        private val CHEST_KEYS = listOf("press_barbell", "press_dumbbell", "pushup")
        private val CORE_KEYS = listOf("situp")
        private val ARMS_KEYS = listOf("press_dumbbell")
        private val LEGS_KEYS = listOf("squat_beta")
    }
}
