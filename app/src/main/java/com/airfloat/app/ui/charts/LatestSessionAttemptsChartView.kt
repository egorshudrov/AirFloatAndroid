package com.airfloat.app.ui.charts

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.airfloat.app.stats.WorkoutSessionAttemptRecord
import kotlin.math.abs
import kotlin.math.hypot

class LatestSessionAttemptsChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    var onAttemptSelected: ((WorkoutSessionAttemptRecord) -> Unit)? = null

    private val lanePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#22F0EDE8")
            style = Paint.Style.STROKE
            strokeWidth = dp(1f)
            pathEffect = DashPathEffect(floatArrayOf(dp(5f), dp(5f)), 0f)
        }
    private val connectorPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4AF0EDE8")
            style = Paint.Style.STROKE
            strokeWidth = dp(2f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    private val connectorGlowPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#18E8FF47")
            style = Paint.Style.STROKE
            strokeWidth = dp(6f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    private val successPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#47FFB2")
            style = Paint.Style.FILL
        }
    private val failPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF4D4D")
            style = Paint.Style.FILL
        }
    private val selectedGlowPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#45E8FF47")
            style = Paint.Style.FILL
        }
    private val successGlowPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2347FFB2")
            style = Paint.Style.FILL
        }
    private val failGlowPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#26FF4D4D")
            style = Paint.Style.FILL
        }
    private val selectedStrokePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E8FF47")
            style = Paint.Style.STROKE
            strokeWidth = dp(2f)
        }
    private val selectorPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4AE8FF47")
            style = Paint.Style.STROKE
            strokeWidth = dp(1.5f)
        }
    private val labelPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7AF0EDE8")
            textSize = sp(10f)
            typeface = android.graphics.Typeface.MONOSPACE
        }
    private val bottomLabelPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#9DF0EDE8")
            textSize = sp(10f)
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.MONOSPACE
        }
    private val emptyPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6AF0EDE8")
            textSize = sp(11f)
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.MONOSPACE
        }

    private var attempts: List<WorkoutSessionAttemptRecord> = emptyList()
    private var selectedIndex = -1
    private var pointRects: List<RectF> = emptyList()
    private var selectorProgress = 1f
    private var pulseProgress = 1f
    private var pulseIndex = -1
    private var selectorAnimator: ValueAnimator? = null
    private var pulseAnimator: ValueAnimator? = null

    fun setAttempts(value: List<WorkoutSessionAttemptRecord>) {
        attempts = value
        selectedIndex =
            when {
                attempts.isEmpty() -> -1
                selectedIndex in attempts.indices -> selectedIndex
                else -> attempts.lastIndex
            }
        invalidate()
    }

    fun setSelectedAttempt(index: Int, notify: Boolean = false) {
        if (index !in attempts.indices) return
        if (selectedIndex == index) {
            animateSelection(index)
            if (notify) {
                onAttemptSelected?.invoke(attempts[index])
            }
            return
        }
        selectedIndex = index
        animateSelection(index)
        invalidate()
        if (notify) {
            onAttemptSelected?.invoke(attempts[index])
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val content =
            RectF(
                paddingLeft + dp(12f),
                paddingTop + dp(20f),
                width - paddingRight - dp(12f),
                height - paddingBottom - dp(26f)
            )
        if (content.width() <= 0f || content.height() <= 0f) return

        if (attempts.isEmpty()) {
            canvas.drawText("NO REP TELEMETRY FOR THIS SESSION", content.centerX(), content.centerY(), emptyPaint)
            return
        }

        val goodY = content.top + content.height() * 0.28f
        val badY = content.top + content.height() * 0.74f
        val leftInset = dp(42f)
        val plotLeft = content.left + leftInset
        val plotRight = content.right
        val stepX = if (attempts.size == 1) 0f else (plotRight - plotLeft) / (attempts.size - 1)

        canvas.drawLine(plotLeft, goodY, plotRight, goodY, lanePaint)
        canvas.drawLine(plotLeft, badY, plotRight, badY, lanePaint)
        canvas.drawText("CLEAN", content.left, goodY + dp(4f), labelPaint)
        canvas.drawText("MISS", content.left, badY + dp(4f), labelPaint)

        val centers =
            attempts.mapIndexed { index, attempt ->
                val x = plotLeft + stepX * index
                val y = if (attempt.success) goodY else badY
                x to y
            }

        pointRects =
            centers.map { (x, y) ->
                RectF(x - dp(18f), y - dp(18f), x + dp(18f), y + dp(18f))
            }

        centers.zipWithNext().forEach { (start, end) ->
            canvas.drawLine(start.first, start.second, end.first, end.second, connectorGlowPaint)
            canvas.drawLine(start.first, start.second, end.first, end.second, connectorPaint)
        }

        if (selectedIndex in centers.indices) {
            val selected = centers[selectedIndex]
            val selectorEndY = content.top + ((selected.second - content.top) * selectorProgress)
            canvas.drawLine(selected.first, content.top, selected.first, selectorEndY, selectorPaint)
        }

        centers.forEachIndexed { index, (x, y) ->
            val attempt = attempts[index]
            val paint = if (attempt.success) successPaint else failPaint
            val glowPaint = if (attempt.success) successGlowPaint else failGlowPaint
            if (index == selectedIndex) {
                canvas.drawCircle(x, y, dp(13f), selectedGlowPaint)
                canvas.drawCircle(x, y, dp(10f), paint)
                canvas.drawCircle(x, y, dp(13f), selectedStrokePaint)
                if (pulseIndex == index && pulseProgress < 1f) {
                    val ringPaint = if (attempt.success) successPaint else failPaint
                    ringPaint.alpha = ((1f - pulseProgress) * 140).toInt().coerceIn(0, 255)
                    canvas.drawCircle(x, y, dp(10f) + dp(14f) * pulseProgress, ringPaint)
                    ringPaint.alpha = 255
                }
            } else {
                canvas.drawCircle(x, y, dp(11f), glowPaint)
                canvas.drawCircle(x, y, dp(8f), paint)
            }
        }

        drawBottomLabels(canvas, content, centers)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (attempts.isEmpty()) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_UP -> {
                val hitIndex = findNearestIndex(event.x, event.y)
                if (hitIndex >= 0) {
                    setSelectedAttempt(hitIndex, notify = true)
                    performClick()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun animateSelection(index: Int) {
        pulseIndex = index
        selectorAnimator?.cancel()
        pulseAnimator?.cancel()
        selectorProgress = 0f
        pulseProgress = 0f
        selectorAnimator =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 180L
                interpolator = DecelerateInterpolator(1.3f)
                addUpdateListener {
                    selectorProgress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        pulseAnimator =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 250L
                interpolator = DecelerateInterpolator(1.6f)
                addUpdateListener {
                    pulseProgress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
    }

    private fun findNearestIndex(x: Float, y: Float): Int {
        if (pointRects.isEmpty()) return -1
        val directHit = pointRects.indexOfFirst { it.contains(x, y) }
        if (directHit >= 0) return directHit

        var bestIndex = -1
        var bestDistance = Float.MAX_VALUE
        pointRects.forEachIndexed { index, rect ->
            val centerX = rect.centerX()
            val centerY = rect.centerY()
            val distance = hypot(abs(x - centerX), abs(y - centerY))
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = index
            }
        }
        return if (bestDistance <= dp(28f)) bestIndex else -1
    }

    private fun drawBottomLabels(canvas: Canvas, content: RectF, centers: List<Pair<Float, Float>>) {
        if (centers.isEmpty()) return
        val first = centers.first()
        val last = centers.last()
        canvas.drawText("1", first.first, content.bottom + dp(18f), bottomLabelPaint)
        if (centers.size > 2) {
            val selected = selectedIndex.coerceIn(0, centers.lastIndex)
            canvas.drawText(
                attempts[selected].index.toString(),
                centers[selected].first,
                content.bottom + dp(18f),
                bottomLabelPaint
            )
        }
        if (centers.size > 1) {
            canvas.drawText(attempts.last().index.toString(), last.first, content.bottom + dp(18f), bottomLabelPaint)
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity
}
