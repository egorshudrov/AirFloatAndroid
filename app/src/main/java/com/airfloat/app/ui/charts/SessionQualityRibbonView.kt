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
import com.airfloat.app.stats.SessionQualityPoint
import kotlin.math.max

class SessionQualityRibbonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val trackPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#16FFFFFF")
            style = Paint.Style.FILL
        }
    private val fillPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
    private val glowPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#44E8FF47")
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
            color = Color.parseColor("#F0EDE8")
            textSize = sp(10f)
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.MONOSPACE
        }

    private var points: List<SessionQualityPoint> = emptyList()

    fun setPoints(value: List<SessionQualityPoint>) {
        points = value.takeLast(8)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.isEmpty()) {
            canvas.drawText(
                "NO QUALITY SIGNAL",
                width / 2f,
                height / 2f,
                labelPaint
            )
            return
        }

        val left = paddingLeft + dp(4f)
        val right = width - paddingRight - dp(4f)
        val top = paddingTop + dp(16f)
        val bottom = height - paddingBottom - dp(24f)
        val chartHeight = bottom - top
        if (chartHeight <= 0f) return

        val gap = dp(10f)
        val barWidth = ((right - left) - (gap * (points.size - 1))) / max(1, points.size)

        points.forEachIndexed { index, point ->
            val barLeft = left + index * (barWidth + gap)
            val barRight = barLeft + barWidth
            val normalized = (point.completionRate.coerceIn(0, 100) / 100f).coerceAtLeast(0.08f)
            val barTop = bottom - (chartHeight * normalized)

            val trackRect = RectF(barLeft, top, barRight, bottom)
            val fillRect = RectF(barLeft, barTop, barRight, bottom)
            val glowRect = RectF(barLeft, fillRect.top - dp(4f), barRight, fillRect.top + dp(6f))

            canvas.drawRoundRect(trackRect, dp(12f), dp(12f), trackPaint)

            fillPaint.shader =
                LinearGradient(
                    fillRect.left,
                    fillRect.top,
                    fillRect.left,
                    fillRect.bottom,
                    intArrayOf(
                        accentTopColor(point.completionRate),
                        accentBottomColor(point.completionRate)
                    ),
                    null,
                    Shader.TileMode.CLAMP
                )
            canvas.drawRoundRect(fillRect, dp(12f), dp(12f), fillPaint)
            canvas.drawRoundRect(glowRect, dp(8f), dp(8f), glowPaint)

            canvas.drawText(
                "${point.completionRate}",
                fillRect.centerX(),
                fillRect.top - dp(8f),
                valuePaint
            )
            canvas.drawText(
                point.label,
                fillRect.centerX(),
                bottom + dp(16f),
                labelPaint
            )
        }
    }

    private fun accentTopColor(score: Int): Int =
        when {
            score >= 86 -> Color.parseColor("#69FFF0B8")
            score >= 61 -> Color.parseColor("#80FFD768")
            else -> Color.parseColor("#73FF7A7A")
        }

    private fun accentBottomColor(score: Int): Int =
        when {
            score >= 86 -> Color.parseColor("#FF47FFB2")
            score >= 61 -> Color.parseColor("#FFE8FF47")
            else -> Color.parseColor("#FFFF4D4D")
        }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity
}
