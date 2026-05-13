package com.airfloat.app.ui.charts

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.airfloat.app.R
import kotlin.math.min

class MlScoreRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val trackPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
    private val glowPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
    private val progressPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
    private val numberPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.MONOSPACE
        }
    private val labelPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.MONOSPACE
        }

    private val arcBounds = RectF()
    private var score = 0
    private var animatedScore = 0f
    private var animator: ValueAnimator? = null

    fun setScore(value: Int, animate: Boolean = true) {
        val target = value.coerceIn(0, 100)
        score = target
        animator?.cancel()
        if (!animate) {
            animatedScore = target.toFloat()
            invalidate()
            return
        }
        animator =
            ValueAnimator.ofFloat(animatedScore, target.toFloat()).apply {
                duration = 520L
                interpolator = DecelerateInterpolator(1.6f)
                addUpdateListener {
                    animatedScore = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width.toFloat(), height.toFloat())
        if (size <= 0f) return

        val stroke = size * 0.11f
        val glowStroke = size * 0.18f
        val inset = stroke * 0.9f + glowStroke * 0.2f
        val cx = width / 2f
        val cy = height / 2f

        arcBounds.set(cx - size / 2f + inset, cy - size / 2f + inset, cx + size / 2f - inset, cy + size / 2f - inset)

        val tone = toneFor(animatedScore.toInt())
        trackPaint.color = ContextCompat.getColor(context, R.color.surface_border_12)
        trackPaint.strokeWidth = stroke
        glowPaint.color = ContextCompat.getColor(context, tone.glowRes)
        glowPaint.alpha = 72
        glowPaint.strokeWidth = glowStroke
        progressPaint.color = ContextCompat.getColor(context, tone.colorRes)
        progressPaint.strokeWidth = stroke
        numberPaint.color = ContextCompat.getColor(context, tone.colorRes)
        numberPaint.textSize = size * 0.28f
        labelPaint.color = ContextCompat.getColor(context, R.color.hud_text_secondary)
        labelPaint.textSize = size * 0.10f

        val sweep = 360f * (animatedScore / 100f)
        canvas.drawArc(arcBounds, -90f, 360f, false, trackPaint)
        canvas.drawArc(arcBounds, -90f, sweep, false, glowPaint)
        canvas.drawArc(arcBounds, -90f, sweep, false, progressPaint)

        val numberBaseline = cy - (numberPaint.descent() + numberPaint.ascent()) / 2f - size * 0.04f
        canvas.drawText(animatedScore.toInt().toString(), cx, numberBaseline, numberPaint)
        val labelBaseline = cy + size * 0.25f
        canvas.drawText("CLEAN RATE", cx, labelBaseline, labelPaint)
    }

    private fun toneFor(value: Int): RingTone {
        return when {
            value <= 60 -> RingTone(R.color.danger_400, R.color.danger_400)
            value <= 85 -> RingTone(R.color.amber_score, R.color.amber_score)
            else -> RingTone(R.color.acid_lime_400, R.color.acid_lime_400)
        }
    }

    private data class RingTone(
        val colorRes: Int,
        val glowRes: Int
    )
}
