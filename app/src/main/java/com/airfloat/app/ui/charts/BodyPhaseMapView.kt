package com.airfloat.app.ui.charts

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.airfloat.app.stats.BodyPhasePeriodPoint
import com.airfloat.app.stats.BodyPhaseState
import kotlin.math.max

class BodyPhaseMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val loadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val labelPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#73F0EDE8")
            textAlign = Paint.Align.CENTER
            textSize = sp(9f)
            typeface = android.graphics.Typeface.MONOSPACE
        }
    private val phasePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFF0EDE8")
            textAlign = Paint.Align.CENTER
            textSize = sp(10f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
    private val loadTextPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B5F0EDE8")
            textAlign = Paint.Align.CENTER
            textSize = sp(8f)
            typeface = android.graphics.Typeface.MONOSPACE
        }

    private var points: List<BodyPhasePeriodPoint> = emptyList()

    fun setPoints(value: List<BodyPhasePeriodPoint>) {
        points = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.isEmpty()) {
            canvas.drawText("NO PHASE DATA", width / 2f, height / 2f, labelPaint)
            return
        }

        val contentLeft = paddingLeft + dp(4f)
        val contentRight = width - paddingRight - dp(4f)
        val contentTop = paddingTop + dp(18f)
        val contentBottom = height - paddingBottom - dp(18f)
        val columnWidth = ((contentRight - contentLeft) / points.size.toFloat()) - dp(6f)
        val maxBarHeight = contentBottom - contentTop - dp(40f)

        points.forEachIndexed { index, point ->
            val left = contentLeft + index * (columnWidth + dp(6f))
            val rect = RectF(left, contentTop, left + columnWidth, contentBottom)
            val barHeight = max(dp(28f), maxBarHeight * (point.intensity / 100f))
            val barRect = RectF(rect.left, rect.bottom - barHeight, rect.right, rect.bottom)

            glowPaint.color = toneGlow(point.phaseState)
            cardPaint.color = toneCard(point.phaseState)
            loadPaint.color = toneLoad(point.phaseState)

            canvas.drawRoundRect(rect, dp(14f), dp(14f), glowPaint)
            canvas.drawRoundRect(
                RectF(rect.left + dp(1f), rect.top + dp(1f), rect.right - dp(1f), rect.bottom - dp(1f)),
                dp(13f),
                dp(13f),
                cardPaint
            )
            canvas.drawRoundRect(
                RectF(barRect.left + dp(3f), barRect.top, barRect.right - dp(3f), barRect.bottom - dp(3f)),
                dp(10f),
                dp(10f),
                loadPaint
            )

            canvas.drawText(point.label, rect.centerX(), rect.top + dp(14f), labelPaint)
            canvas.drawText(shortPhase(point.phaseLabel), rect.centerX(), rect.top + dp(30f), phasePaint)
            canvas.drawText(point.loadLabel, rect.centerX(), rect.bottom - dp(8f), loadTextPaint)
        }
    }

    private fun shortPhase(value: String): String =
        when (value) {
            "LEANER" -> "LEAN"
            "STABLE" -> "STBL"
            "SOFTER" -> "SOFT"
            "NO DATA" -> "DATA"
            else -> value
        }

    private fun toneCard(state: BodyPhaseState): Int =
        when (state) {
            BodyPhaseState.LEANER -> Color.parseColor("#1F47FFB2")
            BodyPhaseState.CLEAN_GAIN -> Color.parseColor("#1FE8FF47")
            BodyPhaseState.STABLE -> Color.parseColor("#14FFFFFF")
            BodyPhaseState.SOFTER -> Color.parseColor("#22FF4D4D")
            BodyPhaseState.NO_DATA -> Color.parseColor("#0FFFFFFF")
        }

    private fun toneGlow(state: BodyPhaseState): Int =
        when (state) {
            BodyPhaseState.LEANER -> Color.parseColor("#3347FFB2")
            BodyPhaseState.CLEAN_GAIN -> Color.parseColor("#33E8FF47")
            BodyPhaseState.STABLE -> Color.parseColor("#22FFFFFF")
            BodyPhaseState.SOFTER -> Color.parseColor("#33FF4D4D")
            BodyPhaseState.NO_DATA -> Color.parseColor("#12FFFFFF")
        }

    private fun toneLoad(state: BodyPhaseState): Int =
        when (state) {
            BodyPhaseState.LEANER -> Color.parseColor("#FF47FFB2")
            BodyPhaseState.CLEAN_GAIN -> Color.parseColor("#FFE8FF47")
            BodyPhaseState.STABLE -> Color.parseColor("#FFDCE8F0")
            BodyPhaseState.SOFTER -> Color.parseColor("#FFFF4D4D")
            BodyPhaseState.NO_DATA -> Color.parseColor("#55F0EDE8")
        }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity
}
