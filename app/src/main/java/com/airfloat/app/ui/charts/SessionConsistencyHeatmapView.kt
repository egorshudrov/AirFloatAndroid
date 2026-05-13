package com.airfloat.app.ui.charts

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.airfloat.app.stats.ConsistencyHeatmapCell
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

class SessionConsistencyHeatmapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val slotPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#101417")
            style = Paint.Style.FILL
        }
    private val slotStrokePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#16D9C8B5")
            style = Paint.Style.STROKE
            strokeWidth = dp(1f)
        }
    private val cellPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
    private val accentPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
    private val strokePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8CEAE2D6")
            style = Paint.Style.STROKE
            strokeWidth = dp(1.2f)
        }
    private val weekdayPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8CC9C0B3")
            textSize = sp(10f)
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.MONOSPACE
        }
    private val monthPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B5F0EDE8")
            textSize = sp(11f)
            textAlign = Paint.Align.LEFT
            typeface = android.graphics.Typeface.MONOSPACE
        }
    private val dayPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E8F0EDE8")
            textSize = sp(9f)
            textAlign = Paint.Align.LEFT
            typeface = android.graphics.Typeface.MONOSPACE
        }
    private val quietDayPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7AC9C0B3")
            textSize = sp(9f)
            textAlign = Paint.Align.LEFT
            typeface = android.graphics.Typeface.MONOSPACE
        }
    private val emptyPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6AF0EDE8")
            textSize = sp(11f)
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.MONOSPACE
        }

    private var cells: List<ConsistencyHeatmapCell> = emptyList()

    fun setCells(value: List<ConsistencyHeatmapCell>) {
        cells = value.takeLast(35)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (cells.isEmpty()) {
            canvas.drawText("NO CALENDAR DATA", width / 2f, height / 2f, emptyPaint)
            return
        }

        val today = LocalDate.now()
        val startDate = today.minusDays((cells.size - 1).toLong())
        val columns = 7
        val leadingSlots = startDate.dayOfWeek.value - 1
        val totalSlots = leadingSlots + cells.size
        val rows = ((totalSlots + columns - 1) / columns).coerceAtLeast(1)
        val left = paddingLeft + dp(2f)
        val right = width - paddingRight - dp(2f)
        val top = paddingTop + dp(6f)
        val monthBaseline = top + dp(10f)
        val weekdayTop = top + dp(24f)
        val gridTop = top + dp(42f)
        val bottom = height - paddingBottom - dp(6f)
        val gap = dp(7f)
        val cellSize =
            minOf(
                (right - left - gap * (columns - 1)) / columns,
                (bottom - gridTop - gap * (rows - 1)) / rows
            )
        if (cellSize <= 0f) return

        val weekdayLabels = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
        weekdayLabels.forEachIndexed { index, label ->
            val x = left + index * (cellSize + gap) + cellSize / 2f
            canvas.drawText(label, x, weekdayTop, weekdayPaint)
        }

        val monthMarkerDates = buildMonthMarkers(startDate)
        monthMarkerDates.forEach { date ->
            val slotIndex = leadingSlots + startDate.until(date).days
            if (slotIndex !in 0 until totalSlots) return@forEach
            val column = slotIndex % columns
            val row = slotIndex / columns
            val markerX = left + column * (cellSize + gap)
            val markerY = monthBaseline + row * (cellSize + gap)
            canvas.drawText(
                date.month.getDisplayName(TextStyle.SHORT, Locale.US).uppercase(Locale.US),
                markerX,
                markerY,
                monthPaint
            )
        }

        for (slotIndex in 0 until (rows * columns)) {
            val row = slotIndex / columns
            val column = slotIndex % columns
            val x = left + column * (cellSize + gap)
            val y = gridTop + row * (cellSize + gap)
            val rect = RectF(x, y, x + cellSize, y + cellSize)
            canvas.drawRoundRect(rect, dp(12f), dp(12f), slotPaint)
            canvas.drawRoundRect(rect, dp(12f), dp(12f), slotStrokePaint)

            val dataIndex = slotIndex - leadingSlots
            if (dataIndex !in cells.indices) continue
            val cell = cells[dataIndex]

            val inset = dp(2f)
            val inner = RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset)
            cellPaint.color = intensityColor(cell.intensity)
            canvas.drawRoundRect(inner, dp(10f), dp(10f), cellPaint)

            val dayNumberPaint = if (cell.sessions > 0 || cell.isToday) dayPaint else quietDayPaint
            canvas.drawText(
                cell.label,
                inner.left + dp(8f),
                inner.top + dp(15f),
                dayNumberPaint
            )

            if (cell.sessions > 0) {
                accentPaint.color = accentColor(cell.intensity)
                val dotRadius = if (cell.sessions >= 2) dp(4f) else dp(3f)
                canvas.drawText(
                    "",
                    0f,
                    0f,
                    dayPaint
                )
                canvas.drawCircle(inner.right - dp(10f), inner.bottom - dp(10f), dotRadius, accentPaint)
            }

            if (cell.isToday) {
                canvas.drawRoundRect(inner, dp(10f), dp(10f), strokePaint)
            }
        }
    }

    private fun buildMonthMarkers(startDate: LocalDate): List<LocalDate> {
        val markers = mutableListOf(startDate)
        for (offset in 1 until cells.size) {
            val date = startDate.plusDays(offset.toLong())
            if (date.dayOfMonth == 1) {
                markers += date
            }
        }
        return markers
    }

    private fun intensityColor(intensity: Int): Int =
        when {
            intensity >= 85 -> Color.parseColor("#1E3C3029")
            intensity >= 65 -> Color.parseColor("#16302823")
            intensity >= 35 -> Color.parseColor("#12221D19")
            intensity > 0 -> Color.parseColor("#101A1D1B")
            else -> Color.parseColor("#00000000")
        }

    private fun accentColor(intensity: Int): Int =
        when {
            intensity >= 85 -> Color.parseColor("#FF1D9E75")
            intensity >= 65 -> Color.parseColor("#FF3BAE89")
            intensity >= 35 -> Color.parseColor("#FFEF9F27")
            intensity > 0 -> Color.parseColor("#FF7A6A57")
            else -> Color.parseColor("#00000000")
        }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity
}
