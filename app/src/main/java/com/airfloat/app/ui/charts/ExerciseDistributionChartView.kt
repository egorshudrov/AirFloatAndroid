package com.airfloat.app.ui.charts

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import com.airfloat.app.stats.ExerciseDistributionPoint
import kotlin.math.max

class ExerciseDistributionChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val labelPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#C8F0EDE8")
            textSize = sp(12f)
            typeface = android.graphics.Typeface.MONOSPACE
        }
    private val valuePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F0EDE8")
            textSize = sp(12f)
            textAlign = Paint.Align.RIGHT
            typeface = android.graphics.Typeface.MONOSPACE
        }
    private val trackPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#18FFFFFF")
            style = Paint.Style.FILL
        }
    private val fillPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

    private var points: List<ExerciseDistributionPoint> = emptyList()

    fun setPoints(value: List<ExerciseDistributionPoint>) {
        points = value.take(4)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.isEmpty()) {
            canvas.drawText("NO EXERCISE SPLIT", paddingLeft.toFloat(), height / 2f, labelPaint)
            return
        }

        val left = paddingLeft + dp(2f)
        val right = width - paddingRight - dp(2f)
        val labelWidth = dp(90f)
        val trackLeft = left + labelWidth
        val trackWidth = right - trackLeft
        val rowHeight = dp(48f)
        val barHeight = dp(12f)
        val maxValue = max(1, points.maxOf { it.reps })

        points.forEachIndexed { index, point ->
            val top = paddingTop + (index * rowHeight)
            val labelBaseline = top + dp(14f)
            val barTop = top + dp(24f)
            val rect = RectF(trackLeft, barTop, right, barTop + barHeight)
            val fillRight = trackLeft + (trackWidth * (point.reps / maxValue.toFloat()))
            val fillRect = RectF(trackLeft, barTop, fillRight, barTop + barHeight)

            canvas.drawText(point.label.uppercase(), left, labelBaseline, labelPaint)
            canvas.drawText("${point.reps} reps \u2022 ${point.avgCompletionRate}%", right, labelBaseline, valuePaint)
            canvas.drawRoundRect(rect, dp(6f), dp(6f), trackPaint)

            fillPaint.shader =
                LinearGradient(
                    fillRect.left,
                    fillRect.top,
                    fillRect.right,
                    fillRect.bottom,
                    intArrayOf(
                        Color.parseColor("#99E8FF47"),
                        accentColor(point.exerciseKey)
                    ),
                    null,
                    Shader.TileMode.CLAMP
                )
            canvas.drawRoundRect(fillRect, dp(6f), dp(6f), fillPaint)
        }
    }

    private fun accentColor(exerciseKey: String): Int =
        when (exerciseKey) {
            "pushup" -> Color.parseColor("#47FFB2")
            "situp" -> Color.parseColor("#EF9F27")
            "squat_beta" -> Color.parseColor("#69C7FF")
            else -> Color.parseColor("#E8FF47")
        }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity
}
