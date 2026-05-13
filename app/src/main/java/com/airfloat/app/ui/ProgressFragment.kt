package com.airfloat.app.ui

import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.airfloat.app.R
import com.airfloat.app.stats.AppStateCalculator
import com.airfloat.app.stats.AppTimeContext
import com.airfloat.app.stats.ProgramScheduleRepository
import com.airfloat.app.stats.ProgressRange
import com.airfloat.app.stats.ProgressTodayWriteBack
import com.airfloat.app.stats.RecommendedIntensity
import com.airfloat.app.stats.SessionStatsCalculator
import com.airfloat.app.stats.SessionStatsRepository
import com.airfloat.app.stats.WorkoutSessionRecord
import com.airfloat.app.ui.charts.LatestSessionAttemptsChartView
import com.airfloat.app.ui.charts.MlScoreRingView
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

class ProgressFragment : Fragment(R.layout.fragment_progress) {
    private data class PostSessionArrivalState(
        val sessionId: String,
        val fallbackSession: WorkoutSessionRecord? = null
    )

    private val surfaceEase = DecelerateInterpolator(1.7f)
    private lateinit var repository: SessionStatsRepository
    private lateinit var scheduleRepository: ProgramScheduleRepository
    private lateinit var progressScrollView: ScrollView
    private lateinit var headerMicroLabel: TextView
    private lateinit var headerTitle: TextView
    private lateinit var exerciseScroll: HorizontalScrollView
    private lateinit var exercisePressTab: TextView
    private lateinit var exerciseDumbbellTab: TextView
    private lateinit var exercisePushupTab: TextView
    private lateinit var exerciseSitupTab: TextView
    private lateinit var exerciseSquatTab: TextView
    private lateinit var latestSessionCard: LinearLayout
    private lateinit var latestSessionTitle: TextView
    private lateinit var latestSessionMeta: TextView
    private lateinit var latestSessionRing: MlScoreRingView
    private lateinit var latestSessionChart: LatestSessionAttemptsChartView
    private lateinit var latestAttemptCard: LinearLayout
    private lateinit var latestAttemptTitle: TextView
    private lateinit var latestAttemptBadge: TextView
    private lateinit var latestAttemptMeta: TextView
    private lateinit var latestAttemptDetail: TextView
    private lateinit var consistencyCalendar: ConsistencyCalendarView

    private var currentExerciseKey = WorkoutFragment.SOURCE_PRESS_BARBELL
    private var selectedSessionId: String? = null
    private var cachedSessions: List<WorkoutSessionRecord> = emptyList()
    private var currentLatestModel: LatestSessionMapModel? = null
    private var postSessionArrivalState: PostSessionArrivalState? = null
    private var shouldAutoScrollPostSession = false
    private val zoneId: ZoneId = ZoneId.systemDefault()
    private var currentCalendarMonth: YearMonth = YearMonth.now(zoneId)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = SessionStatsRepository(requireContext())
        scheduleRepository = ProgramScheduleRepository(requireContext())

        progressScrollView = view.findViewById(R.id.progressScrollView)
        headerMicroLabel = view.findViewById(R.id.progressHeaderMicroLabel)
        headerTitle = view.findViewById(R.id.progressHeaderTitle)
        exerciseScroll = view.findViewById(R.id.progressExerciseScroll)
        exercisePressTab = view.findViewById(R.id.progressExercisePressTab)
        exerciseDumbbellTab = view.findViewById(R.id.progressExerciseDumbbellTab)
        exercisePushupTab = view.findViewById(R.id.progressExercisePushupTab)
        exerciseSitupTab = view.findViewById(R.id.progressExerciseSitupTab)
        exerciseSquatTab = view.findViewById(R.id.progressExerciseSquatTab)
        latestSessionCard = view.findViewById(R.id.progressLatestSessionCard)
        latestSessionTitle = view.findViewById(R.id.progressLatestSessionTitle)
        latestSessionMeta = view.findViewById(R.id.progressLatestSessionMeta)
        latestSessionRing = view.findViewById(R.id.progressLatestSessionRing)
        latestSessionChart = view.findViewById(R.id.progressLatestSessionChart)
        latestAttemptCard = view.findViewById(R.id.progressLatestAttemptCard)
        latestAttemptTitle = view.findViewById(R.id.progressLatestAttemptTitle)
        latestAttemptBadge = view.findViewById(R.id.progressLatestAttemptBadge)
        latestAttemptMeta = view.findViewById(R.id.progressLatestAttemptMeta)
        latestAttemptDetail = view.findViewById(R.id.progressLatestAttemptDetail)
        consistencyCalendar = view.findViewById(R.id.progressConsistencyCalendar)

        exercisePressTab.setOnClickListener { setExercise(WorkoutFragment.SOURCE_PRESS_BARBELL) }
        exerciseDumbbellTab.setOnClickListener { setExercise(WorkoutFragment.SOURCE_PRESS_DUMBBELL) }
        exercisePushupTab.setOnClickListener { setExercise(WorkoutFragment.SOURCE_PUSHUP) }
        exerciseSitupTab.setOnClickListener { setExercise(WorkoutFragment.SOURCE_SITUP) }
        exerciseSquatTab.setOnClickListener { setExercise(WorkoutFragment.SOURCE_SQUAT_BETA) }

        latestSessionChart.onAttemptSelected = { attempt ->
            currentLatestModel?.let { model ->
                bindLatestAttempt(LatestSessionMapFactory.detailFor(attempt, model.isLegacy))
            }
        }
        consistencyCalendar.onMonthChangeRequested = { month ->
            buildCalendarMonth(month, cachedSessions)
        }

        animateViewIn(headerMicroLabel, 0L, fromYDp = 8f, fromScale = 0.98f)
        animateViewIn(headerTitle, 40L, fromYDp = 10f, fromScale = 0.98f)
        animateViewIn(exerciseScroll, 100L, fromYDp = 16f, fromScale = 0.98f)
        animateViewIn(latestSessionCard, 180L, fromYDp = 18f, fromScale = 0.98f)
        animateViewIn(consistencyCalendar, 260L, fromYDp = 20f, fromScale = 0.98f)

        render()
    }

    override fun onResume() {
        super.onResume()
        if (::repository.isInitialized) render()
    }

    fun refresh() {
        if (view != null) render()
    }

    fun openPostSessionArrival(sessionId: String) {
        postSessionArrivalState = PostSessionArrivalState(sessionId = sessionId)
        shouldAutoScrollPostSession = true
        if (::progressScrollView.isInitialized) {
            progressScrollView.scrollTo(0, 0)
        }
        if (view != null) render()
    }

    fun openPostSessionArrival(session: WorkoutSessionRecord) {
        postSessionArrivalState = PostSessionArrivalState(sessionId = session.id, fallbackSession = session)
        shouldAutoScrollPostSession = true
        if (::progressScrollView.isInitialized) {
            progressScrollView.scrollTo(0, 0)
        }
        if (view != null) render()
    }

    fun consumeTodayWriteBack(nowEpochMs: Long = System.currentTimeMillis()): ProgressTodayWriteBack? {
        if (!::repository.isInitialized) return null
        val allSessions = repository.loadSessions()
        if (allSessions.isEmpty()) return null

        val focusSession = resolveFocusSessionForWriteBack(allSessions)
        val baseAppState =
            AppStateCalculator.getAppState(
                sessions = allSessions,
                timeContext = AppTimeContext(nowEpochMs = nowEpochMs)
            )

        val recommendedExercise =
            when {
                focusSession == null -> baseAppState.recommendedExercise
                shouldKeepFocusExercise(focusSession) -> SessionStatsCalculator.analyticsExerciseKey(focusSession)
                else -> baseAppState.recommendedExercise
            }

        val recommendedIntensity =
            when {
                focusSession == null -> baseAppState.recommendedIntensity
                !focusSession.completed -> RecommendedIntensity.LIGHT
                focusSession.failedAttempts >= 2 -> RecommendedIntensity.LIGHT
                focusSession.completionRate < 80 -> RecommendedIntensity.LIGHT
                focusSession.completionRate >= 90 && focusSession.failedAttempts == 0 ->
                    when (baseAppState.recommendedIntensity) {
                        RecommendedIntensity.LIGHT -> RecommendedIntensity.NORMAL
                        else -> RecommendedIntensity.HEAVY
                    }
                focusSession.completionRate >= 84 && focusSession.failedAttempts == 0 -> RecommendedIntensity.NORMAL
                else -> RecommendedIntensity.LIGHT
            }

        postSessionArrivalState = null
        shouldAutoScrollPostSession = false

        return ProgressTodayWriteBack(
            recommendedExercise = recommendedExercise,
            recommendedIntensity = recommendedIntensity,
            streakRisk = baseAppState.streakRisk,
            readAtMs = nowEpochMs
        )
    }

    private fun render() {
        val allSessions = repository.loadSessions().sortedByDescending { it.timestampMs }
        cachedSessions = allSessions

        val arrivalState = postSessionArrivalState
        val arrivalSession =
            arrivalState?.let { state ->
                state.fallbackSession ?: allSessions.firstOrNull { it.id == state.sessionId }
            }
        if (arrivalSession != null) {
            currentExerciseKey = SessionStatsCalculator.analyticsExerciseKey(arrivalSession)
            selectedSessionId = arrivalSession.id
            currentCalendarMonth = YearMonth.from(Instant.ofEpochMilli(arrivalSession.timestampMs).atZone(zoneId))
        } else if (postSessionArrivalState != null) {
            postSessionArrivalState = null
            shouldAutoScrollPostSession = false
        }

        val exerciseSessions = currentExerciseSessions()

        val selectedSessionFromList =
            selectedSessionId?.let { id ->
                exerciseSessions.firstOrNull { it.id == id }
            } ?: exerciseSessions.firstOrNull()
        val selectedSession =
            selectedSessionFromList
                ?: arrivalSession?.takeIf {
                    SessionStatsCalculator.analyticsExerciseKey(it) == currentExerciseKey
                }

        selectedSessionId = selectedSession?.id

        bindExerciseTabs()
        bindLatestSessionMap(selectedSession)
        bindCalendar(cachedSessions)

        if (arrivalSession != null && shouldAutoScrollPostSession) {
            shouldAutoScrollPostSession = false
            progressScrollView.post {
                progressScrollView.smoothScrollTo(0, latestSessionCard.top.coerceAtLeast(0))
                pulseSurface(latestSessionCard)
            }
        }
    }

    private fun setExercise(exerciseKey: String) {
        if (currentExerciseKey == exerciseKey) return
        currentExerciseKey = exerciseKey
        selectedSessionId = null
        render()
    }

    private fun bindExerciseTabs() {
        updateExerciseTab(exercisePressTab, currentExerciseKey == WorkoutFragment.SOURCE_PRESS_BARBELL)
        updateExerciseTab(exerciseDumbbellTab, currentExerciseKey == WorkoutFragment.SOURCE_PRESS_DUMBBELL)
        updateExerciseTab(exercisePushupTab, currentExerciseKey == WorkoutFragment.SOURCE_PUSHUP)
        updateExerciseTab(exerciseSitupTab, currentExerciseKey == WorkoutFragment.SOURCE_SITUP)
        updateExerciseTab(exerciseSquatTab, currentExerciseKey == WorkoutFragment.SOURCE_SQUAT_BETA)
    }

    private fun updateExerciseTab(
        tab: TextView,
        active: Boolean
    ) {
        tab.setBackgroundResource(
            if (active) {
                R.drawable.progress_selector_active
            } else {
                R.drawable.progress_selector_idle
            }
        )
        tab.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (active) {
                    R.color.obsidian_950
                } else {
                    R.color.hud_text_primary
                }
            )
        )
    }

    private fun bindLatestSessionMap(session: WorkoutSessionRecord?) {
        latestSessionCard.visibility = View.VISIBLE
        latestSessionCard.alpha = 1f
        latestSessionChart.visibility = View.VISIBLE
        latestSessionChart.alpha = 1f
        latestAttemptCard.visibility = View.VISIBLE
        latestAttemptCard.alpha = 1f
        if (session == null) {
            currentLatestModel = null
            latestSessionTitle.text = WorkoutSurfaceFactory.exerciseLabel(currentExerciseKey).uppercase(Locale.US)
            latestSessionMeta.text = "NO SAVED SESSIONS YET"
            latestSessionRing.setScore(0, animate = false)
            latestSessionChart.setAttempts(emptyList())
            bindLatestAttempt(
                LatestAttemptDetailModel(
                    title = "NO SESSION",
                    badge = "EMPTY",
                    tone = LatestAttemptTone.NEUTRAL,
                    meta = "Complete one workout to unlock the rep map.",
                    detail = "The next saved session for this exercise will appear here."
                )
            )
            return
        }

        val model = LatestSessionMapFactory.build(session)
        currentLatestModel = model
        latestSessionTitle.text = model.sessionTitle
        latestSessionMeta.text = model.sessionMeta
        latestSessionRing.setScore(session.completionRate)
        latestSessionChart.setAttempts(model.attempts)
        if (model.selectedIndex >= 0) {
            latestSessionChart.setSelectedAttempt(model.selectedIndex, notify = false)
        }
        latestSessionChart.post {
            latestSessionChart.requestLayout()
            latestSessionChart.invalidate()
        }
        bindLatestAttempt(model.selectedAttempt)
    }

    private fun bindLatestAttempt(model: LatestAttemptDetailModel) {
        latestAttemptTitle.text = model.title
        latestAttemptBadge.text = model.badge
        latestAttemptMeta.text = model.meta
        latestAttemptDetail.text = model.detail

        val (badgeColor, badgeTextColor, detailTextColor) =
            when (model.tone) {
                LatestAttemptTone.CLEAN -> Triple(0xFF1D9E75.toInt(), 0xFF0A0A0B.toInt(), 0xFFF0EDE8.toInt())
                LatestAttemptTone.MISS -> Triple(0xFFFF4D4D.toInt(), 0xFFFFFFFF.toInt(), 0xFFF0EDE8.toInt())
                LatestAttemptTone.NEUTRAL -> Triple(0xFF2A2D30.toInt(), 0xFFF0EDE8.toInt(), 0xFFC9C0B3.toInt())
            }

        latestAttemptBadge.backgroundTintList = android.content.res.ColorStateList.valueOf(badgeColor)
        latestAttemptBadge.setTextColor(badgeTextColor)
        latestAttemptMeta.setTextColor(ContextCompat.getColor(requireContext(), R.color.bone_200))
        latestAttemptDetail.setTextColor(detailTextColor)
    }

    private fun bindCalendar(allSessions: List<WorkoutSessionRecord>) {
        val days = buildCalendarMonth(currentCalendarMonth, allSessions)
        consistencyCalendar.setData(days)
    }

    private fun buildCalendarMonth(
        month: YearMonth,
        allSessions: List<WorkoutSessionRecord>
    ): List<CalendarDayData> {
        currentCalendarMonth = month
        val schedule = scheduleRepository.loadSchedule()
        return ConsistencyCalendarDataBuilder.buildCalendarMonth(
            year = month.year,
            month = month.monthValue,
            sessions = allSessions,
            restDays = schedule.restDaysOfWeek,
            dateOverrides = schedule.dateOverrides,
            zoneId = zoneId
        )
    }

    private fun currentExerciseSessions(): List<WorkoutSessionRecord> =
        cachedSessions.filter { session ->
            SessionStatsCalculator.analyticsExerciseKey(session) == currentExerciseKey
        }

    private fun resolveFocusSessionForWriteBack(
        allSessions: List<WorkoutSessionRecord>
    ): WorkoutSessionRecord? =
        postSessionArrivalState?.let { arrival ->
            allSessions.firstOrNull { it.id == arrival.sessionId }
        } ?: selectedSessionId?.let { id ->
            allSessions.firstOrNull { it.id == id }
        } ?: allSessions.firstOrNull()

    private fun shouldKeepFocusExercise(session: WorkoutSessionRecord): Boolean =
        !session.completed || session.failedAttempts > 0 || session.completionRate < 84

    private fun pulseSurface(view: View) {
        view.animate().cancel()
        view.scaleX = 0.985f
        view.scaleY = 0.985f
        view.translationY = 10f.dpPx()
        view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(320L)
            .setInterpolator(surfaceEase)
            .start()
    }

    private fun animateViewIn(
        view: View,
        delayMs: Long,
        fromYDp: Float,
        fromScale: Float
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
            .setDuration(520L)
            .setInterpolator(surfaceEase)
            .start()
    }

    private fun Float.dpPx(): Float = this * resources.displayMetrics.density

    companion object {
        const val TAG = "root_progress"
    }
}
