package com.airfloat.app.ui.charts

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.airfloat.app.stats.ExerciseMilestonePoint

class ExerciseMilestoneTimelineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val trackPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#22FFFFFF")
            strokeWidth = dp(2f)
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
    private val nodePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
    private val glowPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
    private val titlePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B5F0EDE8")
            textSize = sp(9f)
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.MONOSPACE
        }
    private val valuePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F0EDE8")
            textSize = sp(11f)
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.MONOSPACE
        }
    private val detailPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#73F0EDE8")
            textSize = sp(9f)
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.MONOSPACE
        }
    private val highlightLinePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CCE8FF47")
            strokeWidth = dp(1f)
            style = Paint.Style.STROKE
        }
    private val highlightLabelPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E8FF47")
            textSize = sp(10f)
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.MONOSPACE
            isFakeBoldText = true
        }

    private var milestones: List<ExerciseMilestonePoint> = emptyList()
    private var highlightedSessionId: String? = null
    private var highlightRevealProgress = 0f

    fun setMilestones(value: List<ExerciseMilestonePoint>) {
        milestones = value.takeLast(6)
        invalidate()
    }

    fun highlightMilestone(
        sessionId: String?,
        animate: Boolean = true
    ) {
        highlightedSessionId = sessionId
        if (sessionId == null) {
            highlightRevealProgress = 0f
            invalidate()
            return
        }
        if (!animate) {
            highlightRevealProgress = 1f
            invalidate()
            return
        }
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 420L
            addUpdateListener { animator ->
                highlightRevealProgress = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (milestones.isEmpty()) {
            canvas.drawText("NO PR TIMELINE", width / 2f, height / 2f, detailPaint)
            return
        }

        val left = paddingLeft + dp(10f)
        val right = width - paddingRight - dp(10f)
        val centerY = height * 0.53f
        val count = milestones.size
        val step = if (count == 1) 0f else (right - left) / (count - 1)

        canvas.drawLine(left, centerY, right, centerY, trackPaint)

        milestones.forEachIndexed { index, point ->
            val x = left + step * index
            glowPaint.color = accentGlow(point.accentKey)
            nodePaint.color = accentSolid(point.accentKey)
            if (point.sessionId == highlightedSessionId) {
                val reveal = highlightRevealProgress.coerceIn(0f, 1f)
                val top = centerY - dp(42f)
                val revealTop = centerY - ((centerY - top) * reveal)
                highlightLinePaint.alpha = (255 * reveal).toInt()
                highlightLabelPaint.alpha = (255 * reveal).toInt()
                canvas.drawLine(x, revealTop, x, centerY - dp(8f), highlightLinePaint)
                canvas.drawText("PR ↑", x, top - dp(4f), highlightLabelPaint)
            }
            canvas.drawCircle(x, centerY, dp(10f), glowPaint)
            canvas.drawCircle(x, centerY, dp(6f), nodePaint)

            canvas.drawText(shortTitle(point.title), x, centerY - dp(22f), titlePaint)
            canvas.drawText(point.value, x, centerY + dp(24f), valuePaint)
            canvas.drawText(point.detail, x, centerY + dp(40f), detailPaint)
        }
    }

    private fun shortTitle(title: String): String =
        when {
            title.startsWith("VOLUME") -> "VOL"
            title.startsWith("PRECISION") -> "FORM"
            title.startsWith("ENDURANCE") -> "GRIND"
            else -> "PR"
        }

    private fun accentSolid(key: String): Int =
        when (key) {
            "precision" -> Color.parseColor("#FF47FFB2")
            "endurance" -> Color.parseColor("#FFEF9F27")
            else -> Color.parseColor("#FFE8FF47")
        }

    private fun accentGlow(key: String): Int =
        when (key) {
            "precision" -> Color.parseColor("#6647FFB2")
            "endurance" -> Color.parseColor("#66EF9F27")
            else -> Color.parseColor("#66E8FF47")
        }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity
}
