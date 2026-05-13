package com.airfloat.app.ui.charts

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.DashPathEffect
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.withSave
import com.airfloat.app.stats.DailyProgressPoint
import kotlin.math.max

class ProgressTrendChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val gridPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#24F0EDE8")
            strokeWidth = dp(1f)
            style = Paint.Style.STROKE
        }
    private val glowPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6AE8FF47")
            strokeWidth = dp(10f)
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    private val linePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E8FF47")
            strokeWidth = dp(3f)
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    private val fillPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
    private val pointPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E8FF47")
            style = Paint.Style.FILL
        }
    private val pointInnerPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0A0A0B")
            style = Paint.Style.FILL
        }
    private val labelPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#73F0EDE8")
            textSize = sp(11f)
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.MONOSPACE
        }
    private val averagePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#34E8FF47")
            strokeWidth = dp(1f)
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(dp(6f), dp(5f)), 0f)
        }
    private val highlightPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#55E8FF47")
            style = Paint.Style.FILL
        }
    private val peakLabelPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B5E8FF47")
            textSize = sp(10f)
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.MONOSPACE
        }

    private val linePath = Path()
    private val fillPath = Path()
    private var points: List<DailyProgressPoint> = emptyList()

    fun setPoints(value: List<DailyProgressPoint>) {
        points = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val content = RectF(
            paddingLeft + dp(8f),
            paddingTop + dp(12f),
            width - paddingRight - dp(8f),
            height - paddingBottom - dp(28f)
        )
        if (content.width() <= 0f || content.height() <= 0f) return

        drawGrid(canvas, content)
        if (points.isEmpty()) {
            drawEmptyState(canvas, content)
            return
        }

        val maxValue = max(1f, points.maxOf { it.score }.toFloat())
        val averageValue = points.map { it.score }.average().toFloat()
        val stepX = if (points.size == 1) 0f else content.width() / (points.size - 1)
        val coords = points.mapIndexed { index, point ->
            val x = content.left + (stepX * index)
            val normalized = point.score / maxValue
            val y = content.bottom - (content.height() * normalized)
            x to y
        }
        val averageY = content.bottom - (content.height() * (averageValue / maxValue))

        linePath.reset()
        fillPath.reset()
        coords.forEachIndexed { index, (x, y) ->
            if (index == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, content.bottom)
                fillPath.lineTo(x, y)
            } else {
                val previous = coords[index - 1]
                val controlX = (previous.first + x) / 2f
                linePath.cubicTo(controlX, previous.second, controlX, y, x, y)
                fillPath.cubicTo(controlX, previous.second, controlX, y, x, y)
            }
        }
        fillPath.lineTo(coords.last().first, content.bottom)
        fillPath.close()

        fillPaint.shader =
            LinearGradient(
                0f,
                content.top,
                0f,
                content.bottom,
                intArrayOf(Color.parseColor("#44E8FF47"), Color.parseColor("#05E8FF47")),
                null,
                Shader.TileMode.CLAMP
            )

        canvas.drawLine(content.left, averageY, content.right, averageY, averagePaint)
        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(linePath, glowPaint)
        canvas.drawPath(linePath, linePaint)

        val peakIndex = points.indices.maxByOrNull { points[it].score } ?: 0
        coords.forEachIndexed { index, (x, y) ->
            if (index == coords.lastIndex) {
                canvas.drawCircle(x, y, dp(10f), highlightPaint)
            }
            canvas.drawCircle(x, y, dp(5f), pointPaint)
            canvas.drawCircle(x, y, dp(2f), pointInnerPaint)
        }

        val peakPoint = coords[peakIndex]
        canvas.drawText("PEAK", peakPoint.first, peakPoint.second - dp(12f), peakLabelPaint)

        drawLabels(canvas, content, coords)
    }

    private fun drawGrid(canvas: Canvas, content: RectF) {
        val rows = 4
        for (index in 0..rows) {
            val y = content.top + (content.height() / rows) * index
            canvas.drawLine(content.left, y, content.right, y, gridPaint)
        }
    }

    private fun drawLabels(canvas: Canvas, content: RectF, coords: List<Pair<Float, Float>>) {
        val step = if (points.size <= 6) 1 else 2
        coords.forEachIndexed { index, (x, _) ->
            if (index % step != 0 && index != coords.lastIndex) return@forEachIndexed
            canvas.drawText(points[index].label, x, content.bottom + dp(18f), labelPaint)
        }
    }

    private fun drawEmptyState(canvas: Canvas, content: RectF) {
        canvas.withSave {
            drawText(
                "NO TRAINING HISTORY",
                content.centerX(),
                content.centerY(),
                labelPaint
            )
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity
}
