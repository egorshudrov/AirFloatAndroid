package com.airfloat.app.ui.charts

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import com.airfloat.app.stats.BodyWeightTrendPoint
import kotlin.math.max
import kotlin.math.min

class BodyWeightTrendChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val linePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E8FF47")
            strokeWidth = dp(3f)
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    private val glowPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#55E8FF47")
            strokeWidth = dp(9f)
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    private val fillPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
    private val labelPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#73F0EDE8")
            textSize = sp(10f)
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.MONOSPACE
        }
    private val valuePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B5F0EDE8")
            textSize = sp(10f)
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.MONOSPACE
        }
    private val pointPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E8FF47")
            style = Paint.Style.FILL
        }

    private val linePath = Path()
    private val fillPath = Path()
    private var points: List<BodyWeightTrendPoint> = emptyList()

    fun setPoints(value: List<BodyWeightTrendPoint>) {
        points = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.isEmpty()) {
            canvas.drawText("NO BODY METRICS", width / 2f, height / 2f, labelPaint)
            return
        }

        val content = RectF(
            paddingLeft + dp(8f),
            paddingTop + dp(12f),
            width - paddingRight - dp(8f),
            height - paddingBottom - dp(24f)
        )
        val minWeight = points.minOf { it.weightKg }
        val maxWeight = points.maxOf { it.weightKg }
        val range = max(0.4f, maxWeight - minWeight)
        val stepX = if (points.size == 1) 0f else content.width() / (points.size - 1)

        val coords = points.mapIndexed { index, point ->
            val x = content.left + stepX * index
            val normalized = (point.weightKg - minWeight) / range
            val y = content.bottom - content.height() * normalized
            x to y
        }

        linePath.reset()
        fillPath.reset()
        coords.forEachIndexed { index, (x, y) ->
            if (index == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, content.bottom)
                fillPath.lineTo(x, y)
            } else {
                val prev = coords[index - 1]
                val controlX = (prev.first + x) / 2f
                linePath.cubicTo(controlX, prev.second, controlX, y, x, y)
                fillPath.cubicTo(controlX, prev.second, controlX, y, x, y)
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
                intArrayOf(Color.parseColor("#36E8FF47"), Color.parseColor("#06E8FF47")),
                null,
                Shader.TileMode.CLAMP
            )

        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(linePath, glowPaint)
        canvas.drawPath(linePath, linePaint)

        coords.forEachIndexed { index, (x, y) ->
            canvas.drawCircle(x, y, dp(4f), pointPaint)
            canvas.drawText(points[index].label, x, content.bottom + dp(16f), labelPaint)
        }

        canvas.drawText(String.format("%.1f", maxWeight), content.right, content.top - dp(2f), valuePaint)
        canvas.drawText(String.format("%.1f", minWeight), content.right, content.bottom - dp(4f), valuePaint)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity
}
