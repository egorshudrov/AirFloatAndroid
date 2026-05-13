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
import kotlin.math.roundToInt

data class SessionBreakdownMetric(
    val label: String,
    val value: Int,
    val valueLabel: String
)

class SessionBreakdownBarsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val labelPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#73F0EDE8")
            textSize = sp(10f)
            typeface = android.graphics.Typeface.MONOSPACE
        }
    private val valuePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F0EDE8")
            textSize = sp(10f)
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

    private var metrics: List<SessionBreakdownMetric> = emptyList()

    fun setMetrics(value: List<SessionBreakdownMetric>) {
        metrics = value.take(4)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (metrics.isEmpty()) {
            canvas.drawText("NO SESSION DETAIL", paddingLeft.toFloat(), height / 2f, labelPaint)
            return
        }

        val left = paddingLeft + dp(2f)
        val right = width - paddingRight - dp(2f)
        val labelWidth = dp(74f)
        val trackLeft = left + labelWidth
        val trackWidth = right - trackLeft
        val rowHeight = dp(42f)
        val barHeight = dp(10f)

        metrics.forEachIndexed { index, metric ->
            val top = paddingTop + rowHeight * index
            val labelBaseline = top + dp(12f)
            val barTop = top + dp(22f)
            val trackRect = RectF(trackLeft, barTop, right, barTop + barHeight)
            val fillRight = trackLeft + trackWidth * (metric.value.coerceIn(0, 100) / 100f)
            val fillRect = RectF(trackLeft, barTop, fillRight, barTop + barHeight)

            canvas.drawText(metric.label.uppercase(), left, labelBaseline, labelPaint)
            canvas.drawText(metric.valueLabel, right, labelBaseline, valuePaint)
            canvas.drawRoundRect(trackRect, dp(6f), dp(6f), trackPaint)

            fillPaint.shader =
                LinearGradient(
                    fillRect.left,
                    fillRect.top,
                    fillRect.right,
                    fillRect.bottom,
                    intArrayOf(accentTopColor(metric.value), accentBottomColor(metric.value)),
                    null,
                    Shader.TileMode.CLAMP
                )
            canvas.drawRoundRect(fillRect, dp(6f), dp(6f), fillPaint)
        }
    }

    private fun accentTopColor(value: Int): Int =
        when {
            value >= 86 -> Color.parseColor("#77FFF2BE")
            value >= 61 -> Color.parseColor("#70FFD96D")
            else -> Color.parseColor("#78FF8A8A")
        }

    private fun accentBottomColor(value: Int): Int =
        when {
            value >= 86 -> Color.parseColor("#FF47FFB2")
            value >= 61 -> Color.parseColor("#FFE8FF47")
            else -> Color.parseColor("#FFFF4D4D")
        }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity
}
