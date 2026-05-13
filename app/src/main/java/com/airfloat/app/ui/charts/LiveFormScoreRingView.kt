package com.airfloat.app.ui.charts

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.PathInterpolator
import androidx.core.content.ContextCompat
import com.airfloat.app.R
import kotlin.math.min

class LiveFormScoreRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val trackPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 10f * resources.displayMetrics.density
            color = 0x24FFFFFF
        }

    private val arcPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 10f * resources.displayMetrics.density
            color = ContextCompat.getColor(context, R.color.amber_score)
        }

    private val textPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.hud_text_primary)
            textAlign = Paint.Align.CENTER
            textSize = 28f * resources.displayMetrics.scaledDensity
            typeface = android.graphics.Typeface.MONOSPACE
            isFakeBoldText = true
        }

    private val pulsePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f * resources.displayMetrics.density
            color = ContextCompat.getColor(context, R.color.acid_lime_400)
            alpha = 0
        }

    private val arcBounds = RectF()
    private var displayedScore = 0f
    private var pulseProgress = 0f
    private var scoreAnimator: ValueAnimator? = null
    private var pulseAnimator: ValueAnimator? = null
    private val scoreInterpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
    private val pulseInterpolator = PathInterpolator(0.22f, 1f, 0.36f, 1f)

    fun setScore(score: Int, animate: Boolean = true, pulse: Boolean = score > 85) {
        val clamped = score.coerceIn(0, 100).toFloat()
        scoreAnimator?.cancel()
        if (!animate) {
            displayedScore = clamped
            updateArcColor(clamped.toInt())
            invalidate()
        } else {
            val start = displayedScore
            scoreAnimator =
                ValueAnimator.ofFloat(start, clamped).apply {
                    duration = 600L
                    interpolator = scoreInterpolator
                    addUpdateListener { animator ->
                        displayedScore = animator.animatedValue as Float
                        updateArcColor(displayedScore.toInt())
                        invalidate()
                    }
                    start()
                }
        }

        if (pulse) {
            pulseAnimator?.cancel()
            pulseAnimator =
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 400L
                    interpolator = pulseInterpolator
                    addUpdateListener { animator ->
                        pulseProgress = animator.animatedValue as Float
                        invalidate()
                    }
                    start()
                }
        } else {
            pulseProgress = 0f
            invalidate()
        }
    }

    override fun onDetachedFromWindow() {
        scoreAnimator?.cancel()
        pulseAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        if (size <= 0f) return

        val cx = width * 0.5f
        val cy = height * 0.5f
        val inset = 10f * resources.displayMetrics.density
        arcBounds.set(inset, inset, width - inset, height - inset)

        canvas.drawArc(arcBounds, -90f, 360f, false, trackPaint)
        canvas.drawArc(arcBounds, -90f, 360f * (displayedScore / 100f), false, arcPaint)

        if (pulseProgress > 0f) {
            val radius = arcBounds.width() * 0.34f + (40f * resources.displayMetrics.density * pulseProgress)
            pulsePaint.alpha = ((1f - pulseProgress) * 76f).toInt().coerceIn(0, 76)
            canvas.drawCircle(cx, cy, radius, pulsePaint)
        }

        val value = displayedScore.toInt().toString()
        val metrics = textPaint.fontMetrics
        val baseline = cy - (metrics.ascent + metrics.descent) * 0.5f
        canvas.drawText(value, cx, baseline, textPaint)
    }

    private fun updateArcColor(score: Int) {
        arcPaint.color =
            when {
                score <= 60 -> ContextCompat.getColor(context, R.color.danger_400)
                score <= 85 -> ContextCompat.getColor(context, R.color.amber_score)
                else -> ContextCompat.getColor(context, R.color.acid_lime_400)
            }
    }
}
