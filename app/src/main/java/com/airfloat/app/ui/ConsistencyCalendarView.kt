package com.airfloat.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.airfloat.app.R
import com.airfloat.app.stats.PlannedDayType
import com.airfloat.app.stats.ProgramScheduleRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import android.graphics.drawable.GradientDrawable
import android.view.animation.DecelerateInterpolator

class ConsistencyCalendarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val monthLocale = Locale.US
    private val detailDateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", monthLocale)
    private val eyebrowText: TextView
    private val monthTitleText: TextView
    private val summaryText: TextView
    private val streakValueText: TextView
    private val streakLabelText: TextView
    private val adherenceValueText: TextView
    private val adherenceFill: View
    private val navMonthText: TextView
    private val prevMonthButton: TextView
    private val nextMonthButton: TextView
    private val gridView: ConsistencyCalendarGridView
    private val detailCardFrame: FrameLayout
    private val detailCardContent: LinearLayout
    private val detailDateText: TextView
    private val detailBadgeText: TextView
    private val detailSessionsContainer: LinearLayout
    private val detailEmptyText: TextView
    private val detailActionsRow: LinearLayout
    private val detailTrainButton: TextView
    private val detailRestButton: TextView
    private val detailClearButton: TextView
    private val detailActionHintText: TextView
    private val statTotalValueText: TextView
    private val statProgramValueText: TextView
    private val statMissedValueText: TextView
    private val dayPills: List<TextView>
    private val detailEase = DecelerateInterpolator(1.7f)
    private val scheduleRepository = ProgramScheduleRepository(context)

    var onDaySelected: ((CalendarDayData) -> Unit)? = null
    var onMonthChangeRequested: ((YearMonth) -> List<CalendarDayData>?)? = null

    private var days: List<CalendarDayData> = emptyList()
    private var selectedDay: CalendarDayData? = null
    private var detailAnimator: ValueAnimator? = null
    private var displayedMonth: YearMonth? = null
    private var restDays: Set<DayOfWeek> = scheduleRepository.loadRestDays()

    init {
        LayoutInflater.from(context).inflate(R.layout.view_consistency_calendar, this, true)
        eyebrowText = findViewById(R.id.consistencyEyebrowText)
        monthTitleText = findViewById(R.id.consistencyMonthTitleText)
        summaryText = findViewById(R.id.consistencySummaryText)
        streakValueText = findViewById(R.id.consistencyStreakValueText)
        streakLabelText = findViewById(R.id.consistencyStreakLabelText)
        adherenceValueText = findViewById(R.id.consistencyAdherenceValueText)
        adherenceFill = findViewById(R.id.consistencyAdherenceFill)
        navMonthText = findViewById(R.id.consistencyNavMonthText)
        prevMonthButton = findViewById(R.id.consistencyPrevMonthButton)
        nextMonthButton = findViewById(R.id.consistencyNextMonthButton)
        gridView = findViewById(R.id.consistencyCalendarGrid)
        detailCardFrame = findViewById(R.id.consistencyDetailCardFrame)
        detailCardContent = findViewById(R.id.consistencyDetailCardContent)
        detailDateText = findViewById(R.id.consistencyDetailDateText)
        detailBadgeText = findViewById(R.id.consistencyDetailBadgeText)
        detailSessionsContainer = findViewById(R.id.consistencyDetailSessionsContainer)
        detailEmptyText = findViewById(R.id.consistencyDetailEmptyText)
        detailActionsRow = findViewById(R.id.consistencyDetailActionsRow)
        detailTrainButton = findViewById(R.id.consistencyDetailTrainButton)
        detailRestButton = findViewById(R.id.consistencyDetailRestButton)
        detailClearButton = findViewById(R.id.consistencyDetailClearButton)
        detailActionHintText = findViewById(R.id.consistencyDetailActionHintText)
        statTotalValueText = findViewById(R.id.consistencyStatTotalValueText)
        statProgramValueText = findViewById(R.id.consistencyStatProgramValueText)
        statMissedValueText = findViewById(R.id.consistencyStatMissedValueText)
        dayPills =
            listOf(
                findViewById(R.id.consistencyPillMon),
                findViewById(R.id.consistencyPillTue),
                findViewById(R.id.consistencyPillWed),
                findViewById(R.id.consistencyPillThu),
                findViewById(R.id.consistencyPillFri),
                findViewById(R.id.consistencyPillSat),
                findViewById(R.id.consistencyPillSun)
            )
        gridView.onDayTapped = { day ->
            selectedDay = day
            showDetail(day, animate = true)
            onDaySelected?.invoke(day)
        }
        prevMonthButton.setOnClickListener { shiftMonth(-1) }
        nextMonthButton.setOnClickListener { shiftMonth(1) }
        detailTrainButton.setOnClickListener {
            selectedDay?.let { day ->
                setDateOverride(day.date, PlannedDayType.TRAIN)
            }
        }
        detailRestButton.setOnClickListener {
            selectedDay?.let { day ->
                setDateOverride(day.date, PlannedDayType.REST)
            }
        }
        detailClearButton.setOnClickListener {
            selectedDay?.let { day ->
                clearDateOverride(day.date)
            }
        }
        updateHeader(emptyList())
        collapseDetail(animate = false)
    }

    fun setData(days: List<CalendarDayData>) {
        this.days = days.sortedBy { it.date }
        displayedMonth = this.days.firstOrNull()?.let { YearMonth.from(it.date) } ?: displayedMonth
        restDays = scheduleRepository.loadRestDays()
        updateHeader(this.days)
        updateAdherence(this.days)
        updateStats(this.days)
        gridView.setDays(this.days)
        selectedDay =
            selectedDay?.let { selected ->
                this.days.firstOrNull { it.date == selected.date }
            }
        if (selectedDay != null) {
            showDetail(selectedDay!!, animate = false)
        } else {
            collapseDetail(animate = false)
        }
    }

    fun setSelectedDate(date: LocalDate?) {
        gridView.setSelectedDate(date)
        selectedDay = days.firstOrNull { it.date == date }
        if (selectedDay != null) {
            showDetail(selectedDay!!, animate = false)
        } else {
            collapseDetail(animate = false)
        }
    }

    private fun updateHeader(days: List<CalendarDayData>) {
        if (days.isEmpty()) {
            eyebrowText.text = "CONSISTENCY"
            monthTitleText.text = ""
            summaryText.text = "0 workouts · 0 misses"
            streakValueText.text = "0"
            streakLabelText.text = "DAY STREAK"
            navMonthText.text = ""
            return
        }

        val month = displayedMonth ?: YearMonth.from(days.first().date)
        val trainedDays = days.count { it.sessions.isNotEmpty() }
        val missedDays = days.count { it.isMissed }
        val streak = adherenceStreak(days)

        eyebrowText.text = "CONSISTENCY"
        monthTitleText.text =
            month.month.getDisplayName(TextStyle.FULL, monthLocale).uppercase(monthLocale)
        summaryText.text = "$trainedDays workouts · $missedDays misses"
        streakValueText.text = streak.toString()
        streakLabelText.text = "DAY STREAK"
        navMonthText.text =
            "${month.month.getDisplayName(TextStyle.FULL, Locale.US).uppercase(Locale.US)} ${month.year}"
    }

    private fun updateAdherence(days: List<CalendarDayData>) {
        val score = adherenceScore(days)
        adherenceValueText.text = "$score%"
        adherenceFill.post {
            val trackWidth = (adherenceFill.parent as? View)?.width ?: return@post
            val params = adherenceFill.layoutParams
            params.width = (trackWidth * (score / 100f)).toInt().coerceAtLeast(dpInt(10f))
            adherenceFill.layoutParams = params
        }

        dayPills.forEachIndexed { index, pill ->
            val dayOfWeek = DayOfWeek.of(index + 1)
            val isRest = dayOfWeek in restDays
            pill.background = pillBackground(isRest)
            pill.setTextColor(
                if (isRest) {
                    Color.parseColor("#555555")
                } else {
                    Color.parseColor("#1D9E75")
                }
            )
        }
    }

    private fun updateStats(days: List<CalendarDayData>) {
        val totalSessions = days.sumOf { it.sessions.size }
        val missed = days.count { it.isMissed }
        val adherence = adherenceScore(days)
        statTotalValueText.text = totalSessions.toString()
        statProgramValueText.text = "$adherence%"
        statMissedValueText.text = missed.toString()
    }

    private fun showDetail(
        day: CalendarDayData,
        animate: Boolean
    ) {
        bindDetail(day)
        gridView.setSelectedDate(day.date)
        animateDetailHeight(expanded = true, animate = animate)
    }

    private fun collapseDetail(animate: Boolean) {
        animateDetailHeight(expanded = false, animate = animate)
    }

    private fun shiftMonth(offset: Int) {
        val current = displayedMonth ?: return
        val target = current.plusMonths(offset.toLong())
        val newDays = onMonthChangeRequested?.invoke(target) ?: return
        displayedMonth = target
        selectedDay = null
        setData(newDays)
        gridView.setSelectedDate(null)
    }

    private fun refreshCurrentMonth() {
        val month = displayedMonth
        if (month != null) {
            val refreshed = onMonthChangeRequested?.invoke(month)
            if (refreshed != null) {
                setData(refreshed)
                return
            }
        }
        reapplyScheduleLocally()
    }

    private fun reapplyScheduleLocally() {
        if (days.isEmpty()) return
        val today = LocalDate.now()
        val dateOverrides = scheduleRepository.loadDateOverrides()
        val rebuilt =
            days.map { day ->
                val hasDateOverride = day.date in dateOverrides
                val plannedDayType =
                    ConsistencyCalendarDataBuilder.resolvePlannedDayType(
                        date = day.date,
                        restDays = restDays,
                        dateOverrides = dateOverrides
                    )
                val plannedRest = plannedDayType == PlannedDayType.REST
                val hasTraining = day.sessions.isNotEmpty()
                val avgScore =
                    if (hasTraining) {
                        day.sessions.map { it.completionRate }.average().toInt()
                    } else {
                        0
                    }
                val isMissed = day.date.isBefore(today) && !plannedRest && !hasTraining
                day.copy(
                    state =
                        when {
                            hasTraining && avgScore >= 90 -> DayState.TRAINED_PERFECT
                            hasTraining && avgScore >= 80 -> DayState.TRAINED_HIGH
                            hasTraining && avgScore >= 70 -> DayState.TRAINED_MID
                            hasTraining -> DayState.TRAINED_LOW
                            day.date == today && !plannedRest -> DayState.TODAY_EMPTY
                            isMissed -> DayState.MISSED
                            plannedRest -> DayState.PLANNED_REST
                            day.date.isAfter(today) -> DayState.FUTURE
                            else -> DayState.REST
                        },
                    avgScore = avgScore,
                    isPlannedRest = plannedRest,
                    isMissed = isMissed,
                    plannedDayType = plannedDayType,
                    hasDateOverride = hasDateOverride
                )
            }
        setData(rebuilt)
    }

    private fun bindDetail(day: CalendarDayData) {
        val accent = accentColor(day)
        val badge = badgeLabel(day)

        detailDateText.text = detailDateFormatter.format(day.date).uppercase(monthLocale)
        detailBadgeText.text = badge
        detailBadgeText.background = badgeBackground(accent)
        detailBadgeText.setTextColor(accent)
        detailCardContent.background = detailBackground(accent)

        detailSessionsContainer.removeAllViews()
        val trained = isTrained(day)
        detailSessionsContainer.visibility = if (trained) View.VISIBLE else View.GONE

        if (trained) {
            detailEmptyText.visibility = View.GONE
            day.sessions.forEach { session ->
                detailSessionsContainer.addView(createSessionRow(session, accent))
            }
        } else {
            detailEmptyText.visibility = View.VISIBLE
            detailEmptyText.text = emptyStateText(day)
            detailEmptyText.setTextColor(emptyStateColor(day))
        }

        bindDetailActions(day, trained)
    }

    private fun bindDetailActions(
        day: CalendarDayData,
        trained: Boolean
    ) {
        if (trained && !day.hasDateOverride) {
            detailActionsRow.visibility = View.GONE
            detailActionHintText.visibility = View.GONE
            return
        }

        detailActionsRow.visibility = View.VISIBLE

        if (trained) {
            detailTrainButton.visibility = View.GONE
            detailRestButton.visibility = View.GONE
            detailClearButton.visibility = if (day.hasDateOverride) View.VISIBLE else View.GONE
            detailActionHintText.visibility = View.VISIBLE
            detailActionHintText.text = "Saved workouts define this day. You can only clear the manual override."
            detailActionHintText.setTextColor(Color.parseColor("#444444"))
            styleActionButton(detailClearButton, active = false, accent = Color.parseColor("#F0EDE8"))
            return
        }

        detailTrainButton.visibility = View.VISIBLE
        detailRestButton.visibility = View.VISIBLE
        detailClearButton.visibility = if (day.hasDateOverride) View.VISIBLE else View.GONE

        styleActionButton(
            detailTrainButton,
            active = day.plannedDayType == PlannedDayType.TRAIN,
            accent = Color.parseColor("#1D9E75")
        )
        styleActionButton(
            detailRestButton,
            active = day.plannedDayType == PlannedDayType.REST,
            accent = Color.parseColor("#7F77DD")
        )
        styleActionButton(detailClearButton, active = false, accent = Color.parseColor("#F0EDE8"))

        detailActionHintText.visibility = View.VISIBLE
        if (day.hasDateOverride) {
            detailActionHintText.text = "Manual override is active for this date."
            detailActionHintText.setTextColor(Color.parseColor("#C9C0B3"))
        } else {
            detailActionHintText.text = "This date currently follows your weekly template."
            detailActionHintText.setTextColor(Color.parseColor("#444444"))
        }
    }

    private fun styleActionButton(
        button: TextView,
        active: Boolean,
        accent: Int
    ) {
        button.background =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(999f)
                if (active) {
                    setColor(accent)
                    setStroke(dpInt(1f), accent)
                } else {
                    setColor(Color.parseColor("#0A0A0B"))
                    setStroke(dpInt(1f), withAlpha(accent, 0.35f))
                }
            }
        button.setTextColor(
            if (active) {
                Color.parseColor("#0A0A0B")
            } else {
                accent
            }
        )
    }

    private fun setDateOverride(
        date: LocalDate,
        type: PlannedDayType
    ) {
        scheduleRepository.setDateOverride(date, type)
        refreshCurrentMonth()
    }

    private fun clearDateOverride(date: LocalDate) {
        scheduleRepository.clearDateOverride(date)
        refreshCurrentMonth()
    }

    private fun createSessionRow(
        session: DaySession,
        accent: Int
    ): View {
        val row =
            LinearLayout(context).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = dpInt(8f)
                    }
                background = context.getDrawable(R.drawable.consistency_calendar_detail_row)
                gravity = android.view.Gravity.CENTER_VERTICAL
                orientation = LinearLayout.HORIZONTAL
                setPadding(dpInt(10f), dpInt(10f), dpInt(10f), dpInt(10f))
            }

        val dot =
            View(context).apply {
                layoutParams =
                    LinearLayout.LayoutParams(dpInt(6f), dpInt(6f)).apply {
                        marginEnd = dpInt(10f)
                    }
                background =
                    GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(accent)
                    }
            }

        val name =
            TextView(context).apply {
                layoutParams =
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = session.exerciseName
                setTextColor(Color.parseColor("#F0EDE8"))
                textSize = 12f
            }

        val meta =
            TextView(context).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                text = "${session.reps} REPS · ${session.completionRate}%"
                setTextColor(accent)
                textSize = 10f
                typeface = Typeface.MONOSPACE
            }

        row.addView(dot)
        row.addView(name)
        row.addView(meta)
        return row
    }

    private fun animateDetailHeight(
        expanded: Boolean,
        animate: Boolean
    ) {
        detailAnimator?.cancel()
        val targetHeight =
            if (expanded) {
                measuredDetailHeight()
            } else {
                0
            }
        val startHeight = detailCardFrame.layoutParams.height.coerceAtLeast(0)

        if (!animate) {
            detailCardFrame.layoutParams =
                detailCardFrame.layoutParams.apply {
                    height = targetHeight
                }
            detailCardFrame.alpha = if (expanded) 1f else 0f
            return
        }

        detailAnimator =
            ValueAnimator.ofInt(startHeight, targetHeight).apply {
                duration = 220L
                interpolator = detailEase
                addUpdateListener { animator ->
                    val height = animator.animatedValue as Int
                    detailCardFrame.layoutParams =
                        detailCardFrame.layoutParams.apply {
                            this.height = height
                        }
                    detailCardFrame.alpha =
                        when {
                            targetHeight == 0 -> {
                                if (startHeight == 0) {
                                    0f
                                } else {
                                    height / startHeight.toFloat()
                                }
                            }
                            else -> height / targetHeight.toFloat()
                        }
                }
                start()
            }
    }

    private fun measuredDetailHeight(): Int {
        val width = (measuredWidth - paddingLeft - paddingRight).coerceAtLeast(0)
        if (width == 0) return 0
        detailCardContent.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        return detailCardContent.measuredHeight
    }

    private fun isTrained(day: CalendarDayData): Boolean = day.sessions.isNotEmpty()

    private fun isManualTrainDay(day: CalendarDayData): Boolean =
        !isTrained(day) &&
            !day.isMissed &&
            day.hasDateOverride &&
            day.plannedDayType == PlannedDayType.TRAIN

    private fun accentColor(day: CalendarDayData): Int =
        when {
            isTrained(day) -> Color.parseColor("#1D9E75")
            day.isMissed -> Color.parseColor("#E24B4A")
            isManualTrainDay(day) -> Color.parseColor("#1D9E75")
            day.isPlannedRest || day.state == DayState.PLANNED_REST -> Color.parseColor("#7F77DD")
            day.state == DayState.TODAY_EMPTY -> Color.parseColor("#E8FF47")
            else -> Color.parseColor("#444444")
        }

    private fun badgeLabel(day: CalendarDayData): String =
        when {
            isTrained(day) -> "WORKOUT"
            day.isMissed -> "MISSED"
            isManualTrainDay(day) -> "TRAIN DAY"
            day.state == DayState.TODAY_EMPTY -> "TODAY"
            day.state == DayState.FUTURE -> "FUTURE"
            else -> "REST"
        }

    private fun emptyStateText(day: CalendarDayData): String =
        when {
            day.isMissed -> "A workout was expected here, but no session was saved."
            isManualTrainDay(day) -> "This date is marked as a planned training day."
            day.isPlannedRest || day.state == DayState.PLANNED_REST -> "This day is marked as planned rest."
            day.state == DayState.TODAY_EMPTY -> "No completed workout yet today."
            day.state == DayState.FUTURE -> "This day is still ahead."
            else -> "No workout data for this day."
        }

    private fun emptyStateColor(day: CalendarDayData): Int =
        when {
            day.isMissed -> Color.parseColor("#E24B4A")
            isManualTrainDay(day) -> Color.parseColor("#1D9E75")
            day.isPlannedRest || day.state == DayState.PLANNED_REST -> Color.parseColor("#7F77DD")
            day.state == DayState.TODAY_EMPTY -> Color.parseColor("#E8FF47")
            day.state == DayState.FUTURE -> Color.parseColor("#333333")
            else -> Color.parseColor("#444444")
        }

    private fun detailBackground(accent: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(14f)
            setColor(Color.parseColor("#111111"))
            setStroke(dpInt(1f), accent)
        }

    private fun badgeBackground(accent: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(999f)
            setColor(withAlpha(accent, 0.15f))
            setStroke(dpInt(1f), withAlpha(accent, 0.3f))
        }

    private fun withAlpha(
        color: Int,
        alphaFraction: Float
    ): Int {
        val alpha = (255 * alphaFraction).toInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun adherenceScore(days: List<CalendarDayData>): Int {
        val targetDays =
            days.filter {
                it.state != DayState.FUTURE &&
                    it.state != DayState.TODAY_EMPTY &&
                    !it.isPlannedRest
            }
        if (targetDays.isEmpty()) return 100
        val trained = targetDays.count { it.sessions.isNotEmpty() }
        return ((trained / targetDays.size.toFloat()) * 100f).toInt()
    }

    private fun adherenceStreak(days: List<CalendarDayData>): Int {
        val today = LocalDate.now()
        val ordered =
            days
                .filter { !it.date.isAfter(today) }
                .sortedByDescending { it.date }
        var streak = 0
        for (day in ordered) {
            if (day.isMissed) break
            if (day.sessions.isNotEmpty() || day.isPlannedRest || day.state == DayState.TODAY_EMPTY) {
                streak += 1
            } else {
                break
            }
        }
        return streak
    }

    private fun pillBackground(isRest: Boolean): Drawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(999f)
            if (isRest) {
                setColor(Color.parseColor("#1A7F77DD"))
                setStroke(dpInt(1f), Color.parseColor("#267F77DD"))
            } else {
                setColor(Color.parseColor("#331D9E75"))
                setStroke(dpInt(1f), Color.parseColor("#551D9E75"))
            }
        }

    private fun dpInt(value: Float): Int = dp(value).toInt()

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}

class ConsistencyCalendarGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val cellRadius = dp(8f)
    private val selectedScale = 1.1f
    private val gap = dp(3f)
    private val horizontalPadding = dp(2f)
    private val dayNumberPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = sp(10f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
    private val cellFillPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
    private val cellStrokePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(1f)
        }
    private val todayGlowPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#33E8FF47")
        }
    private val todayStrokePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(2f)
            color = Color.parseColor("#E8FF47")
        }
    private val selectedStrokePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(2f)
            color = Color.parseColor("#F0EDE8")
        }
    private val sessionDotPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#1D9E75")
        }

    private var days: List<CalendarDayData> = emptyList()
    private var visibleDays: List<CalendarDayData?> = emptyList()
    private var cellRects: List<RectF> = emptyList()
    private var cellSize = 0f
    private var selectedDate: LocalDate? = null

    var onDayTapped: ((CalendarDayData) -> Unit)? = null

    fun setDays(days: List<CalendarDayData>) {
        this.days = days.sortedBy { it.date }
        this.visibleDays = buildVisibleDays(this.days)
        if (selectedDate != null && this.days.none { it.date == selectedDate }) {
            selectedDate = null
        }
        requestLayout()
        invalidate()
    }

    fun setSelectedDate(date: LocalDate?) {
        selectedDate = date
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        if (width == 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        val contentWidth = width - paddingLeft - paddingRight - horizontalPadding * 2
        cellSize = ((contentWidth - gap * 6f) / 7f).coerceAtLeast(0f)
        val rows = ceil(visibleDays.size / 7f).toInt().coerceAtLeast(1)
        val contentHeight = rows * cellSize + (rows - 1) * gap
        val measuredHeight =
            (paddingTop + paddingBottom + contentHeight + dp(2f)).toInt()
        setMeasuredDimension(width, resolveSize(measuredHeight, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (visibleDays.isEmpty()) return

        val startX = paddingLeft + horizontalPadding
        val startY = paddingTop + dp(2f)
        val dayBaselineOffset = cellSize * 0.44f
        val dotY = cellSize * 0.76f
        cellRects = ArrayList(visibleDays.size)
        val today = LocalDate.now()

        visibleDays.forEachIndexed { index, day ->
            val row = index / 7
            val column = index % 7
            val left = startX + column * (cellSize + gap)
            val top = startY + row * (cellSize + gap)
            val rect = RectF(left, top, left + cellSize, top + cellSize)
            (cellRects as ArrayList<RectF>).add(rect)

            if (day == null) return@forEachIndexed

            val palette = paletteFor(day)
            val isSelected = day.date == selectedDate
            val isToday = day.date == today
            val drawRect =
                if (isSelected) {
                    scaledRect(rect, selectedScale)
                } else {
                    rect
                }

            if (isToday) {
                val glowRect = expandRect(drawRect, dp(4f))
                canvas.drawRoundRect(glowRect, cellRadius + dp(3f), cellRadius + dp(3f), todayGlowPaint)
            }

            cellFillPaint.color = palette.fillColor
            cellStrokePaint.color = palette.strokeColor

            canvas.drawRoundRect(drawRect, cellRadius, cellRadius, cellFillPaint)
            if (palette.strokeColor != Color.TRANSPARENT) {
                canvas.drawRoundRect(drawRect, cellRadius, cellRadius, cellStrokePaint)
            }
            if (isToday) {
                canvas.drawRoundRect(drawRect, cellRadius, cellRadius, todayStrokePaint)
            }
            if (isSelected) {
                canvas.drawRoundRect(drawRect, cellRadius, cellRadius, selectedStrokePaint)
            }

            dayNumberPaint.color = palette.textColor
            canvas.drawText(
                day.date.dayOfMonth.toString(),
                drawRect.centerX(),
                drawRect.top + dayBaselineOffset,
                dayNumberPaint
            )

            if (day.sessions.isNotEmpty()) {
                sessionDotPaint.color = Color.parseColor("#1D9E75")
                canvas.drawCircle(drawRect.centerX(), drawRect.top + dotY, max(dp(2f), cellSize * 0.02f), sessionDotPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                val index =
                    cellRects.indexOfFirst { rect ->
                        rect.contains(event.x, event.y)
                    }
                val day = visibleDays.getOrNull(index) ?: return super.onTouchEvent(event)
                selectedDate = day.date
                invalidate()
                onDayTapped?.invoke(day)
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun buildVisibleDays(days: List<CalendarDayData>): List<CalendarDayData?> {
        if (days.isEmpty()) return emptyList()
        val leading = days.first().date.dayOfWeek.value - 1
        return buildList {
            repeat(leading) { add(null) }
            addAll(days)
        }
    }

    private fun paletteFor(day: CalendarDayData): CellPalette =
        when {
            day.hasDateOverride &&
                day.plannedDayType == PlannedDayType.TRAIN &&
                day.sessions.isEmpty() &&
                !day.isMissed -> {
                CellPalette(
                    fillColor = Color.parseColor("#261D9E75"),
                    strokeColor = Color.parseColor("#551D9E75"),
                    textColor = Color.parseColor("#1D9E75")
                )
            }

            else ->
                when (day.state) {
                    DayState.TRAINED_PERFECT ->
                        CellPalette(
                            fillColor = Color.parseColor("#1D9E75"),
                            strokeColor = Color.TRANSPARENT,
                            textColor = Color.parseColor("#F0EDE8")
                        )
                    DayState.TRAINED_HIGH ->
                        CellPalette(
                            fillColor = Color.parseColor("#8C1D9E75"),
                            strokeColor = Color.TRANSPARENT,
                            textColor = Color.parseColor("#F0EDE8")
                        )
                    DayState.TRAINED_MID ->
                        CellPalette(
                            fillColor = Color.parseColor("#4D1D9E75"),
                            strokeColor = Color.TRANSPARENT,
                            textColor = Color.parseColor("#1D9E75")
                        )
                    DayState.TRAINED_LOW ->
                        CellPalette(
                            fillColor = Color.parseColor("#261D9E75"),
                            strokeColor = Color.parseColor("#331D9E75"),
                            textColor = Color.parseColor("#1D9E75")
                        )
                    DayState.PLANNED_REST ->
                        CellPalette(
                            fillColor = Color.parseColor("#111111"),
                            strokeColor = Color.parseColor("#267F77DD"),
                            textColor = Color.parseColor("#333333")
                        )
                    DayState.MISSED ->
                        CellPalette(
                            fillColor = Color.parseColor("#1FE24B4A"),
                            strokeColor = Color.parseColor("#33E24B4A"),
                            textColor = Color.parseColor("#E24B4A")
                        )
                    DayState.FUTURE ->
                        CellPalette(
                            fillColor = Color.parseColor("#4D0D0D0F"),
                            strokeColor = Color.parseColor("#1A1A1A"),
                            textColor = Color.parseColor("#222222")
                        )
                    DayState.TODAY_EMPTY ->
                        CellPalette(
                            fillColor = Color.parseColor("#131313"),
                            strokeColor = Color.parseColor("#1A1A1A"),
                            textColor = Color.parseColor("#F0EDE8")
                        )
                    DayState.REST ->
                        CellPalette(
                            fillColor = Color.parseColor("#131313"),
                            strokeColor = Color.parseColor("#1A1A1A"),
                            textColor = Color.parseColor("#333333")
                        )
                }
        }

    private data class CellPalette(
        val fillColor: Int,
        val strokeColor: Int,
        val textColor: Int
    )

    private fun scaledRect(
        rect: RectF,
        scale: Float
    ): RectF {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val halfWidth = rect.width() * scale / 2f
        val halfHeight = rect.height() * scale / 2f
        return RectF(cx - halfWidth, cy - halfHeight, cx + halfWidth, cy + halfHeight)
    }

    private fun expandRect(
        rect: RectF,
        amount: Float
    ): RectF = RectF(rect.left - amount, rect.top - amount, rect.right + amount, rect.bottom + amount)

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity
}
