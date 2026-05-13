package com.airfloat.app.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.airfloat.app.R
import java.util.Locale

class WorkoutFragment : Fragment(R.layout.fragment_workout) {
    interface Host {
        fun onStartWorkoutRequested(goalText: String)
        fun onWorkoutPresetRequested(presetKey: String)
        fun onToggleQualityRequested()
        fun onToggleRecordingRequested()
        fun onOpenSpotifyRequested()
    }

    data class UiState(
        val subtitleText: String,
        val startButtonText: String,
        val selectedPresetKey: String,
        val selectedExerciseLabel: String,
        val qualityButtonText: String,
        val qualityButtonVisible: Boolean,
        val qualityButtonEnabled: Boolean,
        val recordButtonText: String,
        val repsEnabled: Boolean,
        val repsHint: String,
        val recentSessions: List<WorkoutRecentSessionModel>
    )

    private data class ExerciseOptionUi(
        val presetKey: String,
        val title: String,
        val symbol: String,
        val imageRes: Int?
    )

    private data class ExerciseOptionBinding(
        val container: View,
        val image: ImageView,
        val symbol: TextView,
        val title: TextView,
        val action: TextView
    )

    private data class RecentSessionBinding(
        val container: View,
        val title: TextView,
        val meta: TextView
    )

    private var host: Host? = null
    private var uiState =
        UiState(
            subtitleText = "MANUAL SESSION",
            startButtonText = "Start Session",
            selectedPresetKey = SOURCE_PRESS_BARBELL,
            selectedExerciseLabel = "BARBELL PRESS",
            qualityButtonText = "Exercise AI: N/A",
            qualityButtonVisible = false,
            qualityButtonEnabled = false,
            recordButtonText = "Record: Off",
            repsEnabled = true,
            repsHint = "Reps",
            recentSessions = emptyList()
        )
    private var logoMotionAnimator: AnimatorSet? = null
    private var recentExpanded = false
    private var recentAnimator: ValueAnimator? = null
    private var chooserVisible = false

    private lateinit var trainContentScroll: ScrollView
    private lateinit var titleBlock: View
    private lateinit var logoScene: View
    private lateinit var exerciseHeroVisualFrame: View
    private lateinit var exerciseHeroImageView: ImageView
    private lateinit var titleGlowText: TextView
    private lateinit var titleText: TextView
    private lateinit var subtitleText: TextView
    private lateinit var chooseExerciseButton: View
    private lateinit var chooseExerciseValueText: TextView
    private lateinit var goalShell: View
    private lateinit var repsMinusButton: TextView
    private lateinit var repsValueInput: EditText
    private lateinit var repsPlusButton: TextView
    private lateinit var goButton: View
    private lateinit var goButtonLabelText: TextView
    private lateinit var spotifyPlaylistButton: View
    private lateinit var squatShadowButton: View
    private lateinit var recordButton: View
    private lateinit var workoutSetupQualityLabel: TextView
    private lateinit var workoutSetupQualityValue: TextView
    private lateinit var workoutSetupPlaylistValue: TextView
    private lateinit var workoutSetupRecordValue: TextView
    private lateinit var prestartHero: View
    private lateinit var prestartControlsColumn: View
    private lateinit var recentSessionsToggle: View
    private lateinit var recentSessionsValueText: TextView
    private lateinit var recentSessionsChevronText: TextView
    private lateinit var recentSessionsContainer: LinearLayout
    private lateinit var exerciseChooserOverlay: View
    private lateinit var exerciseChooserSheet: View
    private lateinit var exerciseChooserCloseButton: View
    private lateinit var exerciseChooserList: LinearLayout
    private val surfaceEase = DecelerateInterpolator(1.7f)
    private var goalRepsValue = 0
    private val exerciseOptions =
        listOf(
            ExerciseOptionUi(
                presetKey = SOURCE_PRESS_BARBELL,
                title = "BARBELL PRESS",
                symbol = "BB",
                imageRes = ExerciseArtwork.forPreset(SOURCE_PRESS_BARBELL)
            ),
            ExerciseOptionUi(
                presetKey = SOURCE_PRESS_DUMBBELL,
                title = "DUMBBELL PRESS",
                symbol = "DB",
                imageRes = ExerciseArtwork.forPreset(SOURCE_PRESS_DUMBBELL)
            ),
            ExerciseOptionUi(
                presetKey = SOURCE_SQUAT_BETA,
                title = "SQUATS",
                symbol = "SQ",
                imageRes = ExerciseArtwork.forPreset(SOURCE_SQUAT_BETA)
            ),
            ExerciseOptionUi(
                presetKey = SOURCE_PUSHUP,
                title = "PUSH-UP",
                symbol = "PU",
                imageRes = ExerciseArtwork.forPreset(SOURCE_PUSHUP)
            ),
            ExerciseOptionUi(
                presetKey = SOURCE_SITUP,
                title = "SIT-UP",
                symbol = "SU",
                imageRes = ExerciseArtwork.forPreset(SOURCE_SITUP)
            )
        )

    override fun onAttach(context: Context) {
        super.onAttach(context)
        host = context as? Host
    }

    override fun onDetach() {
        host = null
        super.onDetach()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        trainContentScroll = view.findViewById(R.id.trainContentScroll)
        titleBlock = view.findViewById(R.id.titleBlock)
        logoScene = view.findViewById(R.id.logoScene)
        exerciseHeroVisualFrame = view.findViewById(R.id.exerciseHeroVisualFrame)
        exerciseHeroImageView = view.findViewById(R.id.exerciseHeroImageView)
        titleGlowText = view.findViewById(R.id.titleGlowText)
        titleText = view.findViewById(R.id.titleText)
        subtitleText = view.findViewById(R.id.subtitleText)
        chooseExerciseButton = view.findViewById(R.id.chooseExerciseButton)
        chooseExerciseValueText = view.findViewById(R.id.chooseExerciseValueText)
        goalShell = view.findViewById(R.id.goalShell)
        repsMinusButton = view.findViewById(R.id.repsMinusButton)
        repsValueInput = view.findViewById(R.id.repsValueInput)
        repsPlusButton = view.findViewById(R.id.repsPlusButton)
        goButton = view.findViewById(R.id.goButton)
        goButtonLabelText = view.findViewById(R.id.goButtonLabelText)
        spotifyPlaylistButton = view.findViewById(R.id.spotifyPlaylistButton)
        squatShadowButton = view.findViewById(R.id.squatShadowButton)
        recordButton = view.findViewById(R.id.recordButton)
        workoutSetupQualityLabel = view.findViewById(R.id.workoutSetupQualityLabel)
        workoutSetupQualityValue = view.findViewById(R.id.workoutSetupQualityValue)
        workoutSetupPlaylistValue = view.findViewById(R.id.workoutSetupPlaylistValue)
        workoutSetupRecordValue = view.findViewById(R.id.workoutSetupRecordValue)
        prestartHero = view.findViewById(R.id.prestartHero)
        prestartControlsColumn = view.findViewById(R.id.prestartControlsColumn)
        recentSessionsToggle = view.findViewById(R.id.recentSessionsToggle)
        recentSessionsValueText = view.findViewById(R.id.recentSessionsValueText)
        recentSessionsChevronText = view.findViewById(R.id.recentSessionsChevronText)
        recentSessionsContainer = view.findViewById(R.id.recentSessionsContainer)
        exerciseChooserOverlay = view.findViewById(R.id.exerciseChooserOverlay)
        exerciseChooserSheet = view.findViewById(R.id.exerciseChooserSheet)
        exerciseChooserCloseButton = view.findViewById(R.id.exerciseChooserCloseButton)
        exerciseChooserList = view.findViewById(R.id.exerciseChooserList)

        goButton.setOnClickListener {
            dismissGoalInputFocus()
            host?.onStartWorkoutRequested(goalValueForLaunch())
        }
        chooseExerciseButton.setOnClickListener {
            dismissGoalInputFocus()
            showExerciseChooser()
        }
        spotifyPlaylistButton.setOnClickListener {
            dismissGoalInputFocus()
            host?.onOpenSpotifyRequested()
        }
        squatShadowButton.setOnClickListener {
            dismissGoalInputFocus()
            host?.onToggleQualityRequested()
        }
        recordButton.setOnClickListener {
            dismissGoalInputFocus()
            host?.onToggleRecordingRequested()
        }
        recentSessionsToggle.setOnClickListener {
            dismissGoalInputFocus()
            toggleRecentSessions()
        }
        repsMinusButton.setOnClickListener {
            dismissGoalInputFocus()
            adjustGoalReps(-1)
        }
        repsPlusButton.setOnClickListener {
            dismissGoalInputFocus()
            adjustGoalReps(1)
        }
        repsValueInput.doAfterTextChanged {
            if (!uiState.repsEnabled) return@doAfterTextChanged
            goalRepsValue = it?.toString()?.trim()?.toIntOrNull()?.coerceIn(1, 99) ?: 0
        }
        trainContentScroll.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                maybeDismissGoalInput(event)
            }
            false
        }
        exerciseChooserOverlay.setOnClickListener {
            dismissGoalInputFocus()
            hideExerciseChooser()
        }
        exerciseChooserSheet.setOnClickListener { }
        exerciseChooserCloseButton.setOnClickListener {
            dismissGoalInputFocus()
            hideExerciseChooser()
        }

        listOf(
            goButton,
            chooseExerciseButton,
            spotifyPlaylistButton,
            squatShadowButton,
            recordButton,
            recentSessionsToggle,
            repsMinusButton,
            repsPlusButton,
            exerciseChooserCloseButton
        ).forEach { attachPressScale(it) }

        bindExerciseOptions()
        applyUiState()
    }

    override fun onDestroyView() {
        stopLogoIdleMotion()
        clearContentBlur()
        recentAnimator?.cancel()
        super.onDestroyView()
    }

    fun render(state: UiState) {
        uiState = state
        if (view == null || !::subtitleText.isInitialized) return
        applyUiState()
    }

    fun resetHubState() {
        goalRepsValue = 0
        if (::subtitleText.isInitialized) {
            hideExerciseChooser(animate = false)
            setRecentExpanded(false, animate = false)
            applyUiState()
        }
    }

    fun animateEntrance() {
        if (!::prestartHero.isInitialized) return
        animateViewIn(titleBlock, 0L, fromYDp = 18f, fromXDp = -10f, fromScale = 0.95f)
        animateViewIn(chooseExerciseButton, 90L, fromYDp = 16f, fromScale = 0.97f, durationMs = 520L)
        animateViewIn(goalShell, 150L, fromYDp = 22f, fromScale = 0.96f)
        animateViewIn(goButton, 220L, fromYDp = 26f, fromScale = 0.93f, durationMs = 620L)
        animateViewIn(prestartControlsColumn, 290L, fromYDp = 30f, fromScale = 0.96f, durationMs = 620L)
        animateViewIn(recentSessionsToggle, 360L, fromYDp = 22f, fromScale = 0.97f, durationMs = 520L)
    }

    fun startLogoIdleMotion() {
        if (!::logoScene.isInitialized) return
        stopLogoIdleMotion()
        logoScene.translationY = 0f
        logoScene.scaleX = 1f
        logoScene.scaleY = 1f
        titleGlowText.alpha = 0.72f
    }

    fun stopLogoIdleMotion() {
        logoMotionAnimator?.cancel()
        logoMotionAnimator = null
        if (!::logoScene.isInitialized) return
        logoScene.translationY = 0f
        logoScene.scaleX = 1f
        logoScene.scaleY = 1f
        titleGlowText.alpha = 0.72f
    }

    private fun applyUiState() {
        workoutSetupQualityLabel.text = setupLabel(uiState.qualityButtonText, "Exercise AI")
        workoutSetupQualityValue.text = setupValue(uiState.qualityButtonText)
        workoutSetupQualityValue.setTextColor(ContextCompat.getColor(requireContext(), R.color.obsidian_950))
        squatShadowButton.visibility = if (uiState.qualityButtonVisible) View.VISIBLE else View.GONE
        squatShadowButton.isEnabled = uiState.qualityButtonEnabled
        squatShadowButton.alpha =
            if (!uiState.qualityButtonVisible) {
                1f
            } else if (uiState.qualityButtonEnabled) {
                1f
            } else {
                0.55f
            }
        workoutSetupPlaylistValue.text = "OPEN"
        workoutSetupPlaylistValue.setTextColor(ContextCompat.getColor(requireContext(), R.color.obsidian_950))
        workoutSetupRecordValue.text = setupValue(uiState.recordButtonText)
        workoutSetupRecordValue.setTextColor(ContextCompat.getColor(requireContext(), R.color.obsidian_950))
        val repsEnabled = uiState.repsEnabled
        goalShell.alpha = if (repsEnabled) 1f else 0.62f
        repsMinusButton.isEnabled = repsEnabled
        repsPlusButton.isEnabled = repsEnabled
        repsValueInput.isEnabled = repsEnabled
        repsMinusButton.alpha = if (repsEnabled) 1f else 0.45f
        repsPlusButton.alpha = if (repsEnabled) 1f else 0.45f
        repsValueInput.alpha = if (repsEnabled) 1f else 0.7f
        if (!repsEnabled) {
            goalRepsValue = 0
        }
        syncGoalInput(repsEnabled)

        subtitleText.text = uiState.subtitleText
        chooseExerciseValueText.text = uiState.selectedExerciseLabel
        goButtonLabelText.text = uiState.startButtonText.uppercase(Locale.US)
        renderHeroArtwork()
        bindExerciseOptions()
        bindRecentSessions()
    }

    private fun bindExerciseOptions() {
        exerciseChooserList.removeAllViews()
        exerciseOptions.forEachIndexed { index, option ->
            val binding =
                bindExerciseOption(
                    LayoutInflater.from(requireContext()).inflate(
                        R.layout.view_workout_exercise_option,
                        exerciseChooserList,
                        false
                    )
                )
            val selected = option.presetKey == uiState.selectedPresetKey
            if (option.imageRes != null) {
                binding.image.visibility = View.VISIBLE
                binding.image.setImageResource(option.imageRes)
                binding.symbol.visibility = View.GONE
            } else {
                binding.image.visibility = View.VISIBLE
                binding.image.setImageDrawable(null)
                binding.symbol.visibility = View.VISIBLE
            }
            binding.symbol.text = option.symbol
            binding.title.text = option.title
            binding.action.text = if (selected) "SELECTED" else "CHOOSE"
            binding.symbol.setTextColor(ContextCompat.getColor(requireContext(), R.color.hud_text_primary))
            binding.container.setBackgroundResource(
                if (selected) {
                    R.drawable.workout_exercise_option_card_selected
                } else {
                    R.drawable.workout_exercise_option_card
                }
            )
            val selectExercise = {
                dismissGoalInputFocus()
                host?.onWorkoutPresetRequested(option.presetKey)
                hideExerciseChooser()
            }
            binding.container.setOnClickListener { selectExercise() }
            binding.action.setOnClickListener { selectExercise() }
            attachPressScale(binding.container)
            attachPressScale(binding.action)
            binding.container.alpha = 1f
            exerciseChooserList.addView(binding.container)

            val params = binding.container.layoutParams as ViewGroup.MarginLayoutParams
            params.bottomMargin = if (index == exerciseOptions.lastIndex) 0 else dp(10)
            binding.container.layoutParams = params
        }
    }

    private fun bindRecentSessions() {
        recentSessionsContainer.removeAllViews()
        val recent = uiState.recentSessions
        recentSessionsValueText.text =
            if (recent.isEmpty()) {
                "NO HISTORY"
            } else {
                "${recent.size} READY"
            }
        if (recent.isEmpty()) {
            val empty =
                LayoutInflater.from(requireContext()).inflate(
                    R.layout.view_workout_recent_session_row,
                    recentSessionsContainer,
                    false
                )
            val binding = bindRecentSessionRow(empty)
            binding.title.text = "NO RECENT SESSIONS"
            binding.meta.text = "Finish a workout and it will drop here."
            binding.container.isEnabled = false
            binding.container.alpha = 0.72f
            recentSessionsContainer.addView(binding.container)
        } else {
            recent.forEachIndexed { index, session ->
                val binding =
                    bindRecentSessionRow(
                        LayoutInflater.from(requireContext()).inflate(
                            R.layout.view_workout_recent_session_row,
                            recentSessionsContainer,
                            false
                        )
                    )
                binding.title.text = session.title
                binding.meta.text = session.meta
                binding.container.setOnClickListener {
                    dismissGoalInputFocus()
                    host?.onWorkoutPresetRequested(session.presetKey)
                }
                attachPressScale(binding.container)
                recentSessionsContainer.addView(binding.container)

                val params = binding.container.layoutParams as ViewGroup.MarginLayoutParams
                params.bottomMargin = if (index == recent.lastIndex) 0 else dp(8)
                binding.container.layoutParams = params
            }
        }
        setRecentExpanded(recentExpanded, animate = false)
    }

    private fun showExerciseChooser() {
        if (chooserVisible) return
        chooserVisible = true
        applyContentBlur()
        exerciseChooserOverlay.visibility = View.VISIBLE
        exerciseChooserOverlay.alpha = 0f
        exerciseChooserSheet.alpha = 0f
        exerciseChooserSheet.translationY = dpPx(28f)
        exerciseChooserSheet.scaleX = 0.97f
        exerciseChooserSheet.scaleY = 0.97f
        exerciseChooserOverlay.animate()
            .alpha(1f)
            .setDuration(180L)
            .setInterpolator(surfaceEase)
            .start()
        exerciseChooserSheet.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(220L)
            .setInterpolator(surfaceEase)
            .start()
        animateChooserCardsIn()
    }

    private fun hideExerciseChooser(animate: Boolean = true) {
        if (!chooserVisible && exerciseChooserOverlay.visibility != View.VISIBLE) return
        chooserVisible = false
        if (!animate) {
            exerciseChooserOverlay.visibility = View.GONE
            exerciseChooserOverlay.alpha = 0f
            exerciseChooserSheet.alpha = 1f
            exerciseChooserSheet.translationY = 0f
            exerciseChooserSheet.scaleX = 1f
            exerciseChooserSheet.scaleY = 1f
            clearContentBlur()
            return
        }
        exerciseChooserOverlay.animate().cancel()
        exerciseChooserSheet.animate().cancel()
        exerciseChooserOverlay.animate()
            .alpha(0f)
            .setDuration(150L)
            .setInterpolator(surfaceEase)
            .withEndAction {
                exerciseChooserOverlay.visibility = View.GONE
                clearContentBlur()
            }
            .start()
        exerciseChooserSheet.animate()
            .alpha(0f)
            .translationY(dpPx(20f))
            .scaleX(0.98f)
            .scaleY(0.98f)
            .setDuration(150L)
            .setInterpolator(surfaceEase)
            .start()
    }

    private fun animateChooserCardsIn() {
        for (index in 0 until exerciseChooserList.childCount) {
            val child = exerciseChooserList.getChildAt(index)
            child.alpha = 0f
            child.translationY = dpPx(10f)
            child.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay((index * 24L).coerceAtMost(96L))
                .setDuration(180L)
                .setInterpolator(surfaceEase)
                .start()
        }
    }

    private fun toggleRecentSessions() {
        setRecentExpanded(!recentExpanded, animate = true)
    }

    private fun setRecentExpanded(
        expanded: Boolean,
        animate: Boolean
    ) {
        recentExpanded = expanded
        recentAnimator?.cancel()
        recentSessionsChevronText.text = if (expanded) "HIDE" else "SHOW"

        if (!expanded) {
            if (!animate || recentSessionsContainer.visibility != View.VISIBLE) {
                recentSessionsContainer.visibility = View.GONE
                recentSessionsContainer.alpha = 0f
                recentSessionsContainer.layoutParams =
                    recentSessionsContainer.layoutParams.apply { height = ViewGroup.LayoutParams.WRAP_CONTENT }
                return
            }
            val startHeight = recentSessionsContainer.height
            recentAnimator =
                ValueAnimator.ofInt(startHeight, 0).apply {
                    duration = 180L
                    interpolator = surfaceEase
                    addUpdateListener {
                        val value = it.animatedValue as Int
                        recentSessionsContainer.layoutParams =
                            recentSessionsContainer.layoutParams.apply { height = value }
                        recentSessionsContainer.alpha =
                            if (startHeight == 0) 0f else value / startHeight.toFloat()
                    }
                    doOnEnd {
                        recentSessionsContainer.visibility = View.GONE
                        recentSessionsContainer.layoutParams =
                            recentSessionsContainer.layoutParams.apply { height = ViewGroup.LayoutParams.WRAP_CONTENT }
                    }
                    start()
                }
            return
        }

        if (recentSessionsContainer.childCount == 0) {
            recentSessionsContainer.visibility = View.GONE
            recentSessionsContainer.alpha = 0f
            return
        }

        recentSessionsContainer.visibility = View.VISIBLE
        if (!animate) {
            recentSessionsContainer.alpha = 1f
            recentSessionsContainer.layoutParams =
                recentSessionsContainer.layoutParams.apply { height = ViewGroup.LayoutParams.WRAP_CONTENT }
            return
        }
        val targetHeight = measureExpandedHeight(recentSessionsContainer)
        recentSessionsContainer.layoutParams =
            recentSessionsContainer.layoutParams.apply { height = 0 }
        recentSessionsContainer.alpha = 0f
        recentAnimator =
            ValueAnimator.ofInt(0, targetHeight).apply {
                duration = 220L
                interpolator = surfaceEase
                addUpdateListener {
                    val value = it.animatedValue as Int
                    recentSessionsContainer.layoutParams =
                        recentSessionsContainer.layoutParams.apply { height = value }
                    recentSessionsContainer.alpha =
                        if (targetHeight == 0) 1f else value / targetHeight.toFloat()
                }
                doOnEnd {
                    recentSessionsContainer.layoutParams =
                        recentSessionsContainer.layoutParams.apply { height = ViewGroup.LayoutParams.WRAP_CONTENT }
                }
                start()
            }
    }

    private fun applyContentBlur() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            trainContentScroll.setRenderEffect(
                RenderEffect.createBlurEffect(20f, 20f, Shader.TileMode.CLAMP)
            )
        }
        trainContentScroll.alpha = 0.88f
    }

    private fun clearContentBlur() {
        if (!::trainContentScroll.isInitialized) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            trainContentScroll.setRenderEffect(null)
        }
        trainContentScroll.alpha = 1f
    }

    private fun bindExerciseOption(view: View): ExerciseOptionBinding =
        ExerciseOptionBinding(
            container = view,
            image = view.findViewById(R.id.exerciseOptionImageView),
            symbol = view.findViewById(R.id.exerciseOptionSymbolText),
            title = view.findViewById(R.id.exerciseOptionTitleText),
            action = view.findViewById(R.id.exerciseOptionActionText)
        )

    private fun bindRecentSessionRow(view: View): RecentSessionBinding =
        RecentSessionBinding(
            container = view,
            title = view.findViewById(R.id.recentSessionTitleText),
            meta = view.findViewById(R.id.recentSessionMetaText)
        )

    private fun attachPressScale(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().cancel()
                    v.animate().scaleX(0.98f).scaleY(0.98f).setDuration(120L).setInterpolator(surfaceEase).start()
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    v.animate().cancel()
                    v.animate().scaleX(1f).scaleY(1f).setDuration(180L).setInterpolator(surfaceEase).start()
                }
            }
            false
        }
    }

    private fun animateViewIn(
        view: View,
        delayMs: Long,
        fromYDp: Float = 0f,
        fromXDp: Float = 0f,
        fromScale: Float = 0.96f,
        durationMs: Long = 560L
    ) {
        view.animate().cancel()
        view.alpha = 0f
        view.translationX = dpPx(fromXDp)
        view.translationY = dpPx(fromYDp)
        view.scaleX = fromScale
        view.scaleY = fromScale
        view.animate()
            .alpha(1f)
            .translationX(0f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(delayMs)
            .setDuration(durationMs)
            .setInterpolator(surfaceEase)
            .start()
    }

    private fun setupLabel(
        text: String,
        fallback: String
    ): String = text.substringBefore(':', fallback).trim().ifBlank { fallback }

    private fun setupValue(text: String): String =
        text.substringAfter(':', text)
            .replace("•", " ")
            .trim()
            .replace(Regex("\\s+"), " ")
            .ifBlank { text }
            .uppercase(Locale.US)

    private fun adjustGoalReps(direction: Int) {
        if (!uiState.repsEnabled) return
        goalRepsValue =
            when {
                direction > 0 && goalRepsValue <= 0 -> 10
                else -> (goalRepsValue + (direction * 5)).coerceIn(0, 99)
            }
        syncGoalInput(true)
    }

    private fun goalValueForLaunch(): String =
        if (!uiState.repsEnabled || currentGoalInputValue() <= 0) {
            ""
        } else {
            currentGoalInputValue().toString()
        }

    private fun syncGoalInput(repsEnabled: Boolean) {
        val desiredText =
            when {
                !repsEnabled -> ""
                goalRepsValue <= 0 -> ""
                else -> goalRepsValue.toString()
            }
        if (repsValueInput.text?.toString() != desiredText) {
            repsValueInput.setText(desiredText)
            repsValueInput.setSelection(repsValueInput.text?.length ?: 0)
        }
        repsValueInput.hint =
            when {
                !repsEnabled -> uiState.repsHint.uppercase(Locale.US)
                else -> "FREE"
            }
    }

    private fun currentGoalInputValue(): Int =
        repsValueInput.text?.toString()?.trim()?.toIntOrNull()?.coerceIn(1, 99) ?: 0

    private fun maybeDismissGoalInput(event: MotionEvent) {
        if (!repsValueInput.hasFocus()) return
        val bounds = Rect()
        repsValueInput.getGlobalVisibleRect(bounds)
        if (!bounds.contains(event.rawX.toInt(), event.rawY.toInt())) {
            dismissGoalInputFocus()
        }
    }

    private fun dismissGoalInputFocus() {
        if (!::repsValueInput.isInitialized) return
        repsValueInput.clearFocus()
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(repsValueInput.windowToken, 0)
    }

    private fun measureExpandedHeight(view: View): Int {
        val widthSpec =
            View.MeasureSpec.makeMeasureSpec(
                (view.parent as View).width - (view.paddingLeft + view.paddingRight),
                View.MeasureSpec.AT_MOST
            )
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(widthSpec, heightSpec)
        return view.measuredHeight
    }

    private fun renderHeroArtwork() {
        val imageRes = ExerciseArtwork.forPreset(uiState.selectedPresetKey)
        if (imageRes == null) {
            exerciseHeroVisualFrame.visibility = View.GONE
            titleGlowText.visibility = View.VISIBLE
            titleText.visibility = View.VISIBLE
            exerciseHeroImageView.setImageDrawable(null)
            return
        }
        exerciseHeroVisualFrame.visibility = View.VISIBLE
        exerciseHeroImageView.setImageResource(imageRes)
        titleGlowText.visibility = View.GONE
        titleText.visibility = View.GONE
    }

    private fun ValueAnimator.doOnEnd(block: () -> Unit) {
        addListener(
            object : android.animation.Animator.AnimatorListener {
                override fun onAnimationStart(animation: android.animation.Animator) = Unit

                override fun onAnimationEnd(animation: android.animation.Animator) = block()

                override fun onAnimationCancel(animation: android.animation.Animator) = Unit

                override fun onAnimationRepeat(animation: android.animation.Animator) = Unit
            }
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun dpPx(value: Float): Float = value * resources.displayMetrics.density

    companion object {
        const val TAG = "root_workout"
        const val SOURCE_PRESS_BARBELL = "press_barbell"
        const val SOURCE_PRESS_DUMBBELL = "press_dumbbell"
        const val SOURCE_PUSHUP = "pushup"
        const val SOURCE_SITUP = "situp"
        const val SOURCE_SQUAT_BETA = "squat_beta"
    }
}
