package com.airfloat.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.widget.AppCompatImageView
import com.airfloat.app.R

enum class MuscleZone {
    CHEST,
    CORE,
    ARMS,
    LEGS
}

enum class FormRank {
    RAW,
    FORMING,
    SOLID,
    ELITE
}

data class ZoneExercise(
    val name: String,
    val precision: Int,
    val presetKey: String
)

data class MuscleZoneModel(
    val zone: MuscleZone,
    val precision: Int,
    val rank: FormRank,
    val lastSeenLabel: String,
    val exercises: List<ZoneExercise>,
    val missReason: String?
)

class BodyMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    interface BodyMapListener {
        fun onZoneSelected(zone: MuscleZone)
    }

    private val zoneRects = linkedMapOf<MuscleZone, List<RectF>>()
    private val zoneData = linkedMapOf<MuscleZone, MuscleZoneModel>()
    private val contentBounds = RectF()
    private var listener: BodyMapListener? = null
    private var selectedZone: MuscleZone? = null
    private var selectionProgress = 1f
    private var selectionAnimator: ValueAnimator? = null

    private val fillPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
    private val strokePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(1f)
        }

    init {
        adjustViewBounds = true
        scaleType = ScaleType.FIT_CENTER
        setImageResource(R.drawable.ic_body_map)
        isClickable = true
        isFocusable = true
    }

    fun setBodyMapListener(listener: BodyMapListener?) {
        this.listener = listener
    }

    fun setZoneData(zones: List<MuscleZoneModel>) {
        zoneData.clear()
        zones.forEach { zoneData[it.zone] = it }
        if (selectedZone != null && zoneData[selectedZone] == null) {
            selectedZone = null
        }
        invalidate()
    }

    fun setSelectedZone(
        zone: MuscleZone?,
        animate: Boolean = true
    ) {
        if (zone == null) {
            selectionAnimator?.cancel()
            selectedZone = null
            selectionProgress = 0f
            invalidate()
            return
        }
        if (selectedZone == zone) return
        if (animate) {
            selectZone(zone)
        } else {
            selectionAnimator?.cancel()
            selectedZone = zone
            selectionProgress = 1f
            invalidate()
        }
    }

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateZoneRects(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawZoneOverlays(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                val tappedZone = findZoneAt(event.x, event.y) ?: return super.onTouchEvent(event)
                setSelectedZone(tappedZone, animate = true)
                listener?.onZoneSelected(tappedZone)
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun findZoneAt(
        x: Float,
        y: Float
    ): MuscleZone? =
        zoneRects.entries.firstOrNull { (_, rects) ->
            rects.any { it.contains(x, y) }
        }?.key

    private fun updateZoneRects(
        width: Int,
        height: Int
    ) {
        val availableWidth = (width - paddingLeft - paddingRight).toFloat().coerceAtLeast(0f)
        val availableHeight = (height - paddingTop - paddingBottom).toFloat().coerceAtLeast(0f)
        if (availableWidth <= 0f || availableHeight <= 0f) {
            zoneRects.clear()
            return
        }

        val viewportAspect = VIEWPORT_WIDTH / VIEWPORT_HEIGHT
        val availableAspect = availableWidth / availableHeight

        val contentWidth: Float
        val contentHeight: Float
        if (availableAspect > viewportAspect) {
            contentHeight = availableHeight
            contentWidth = contentHeight * viewportAspect
        } else {
            contentWidth = availableWidth
            contentHeight = contentWidth / viewportAspect
        }

        val left = paddingLeft + (availableWidth - contentWidth) / 2f
        val top = paddingTop + (availableHeight - contentHeight) / 2f
        contentBounds.set(left, top, left + contentWidth, top + contentHeight)

        fun rect(
            leftFraction: Float,
            topFraction: Float,
            rightFraction: Float,
            bottomFraction: Float
        ) = RectF(
            left + (contentWidth * leftFraction),
            top + (contentHeight * topFraction),
            left + (contentWidth * rightFraction),
            top + (contentHeight * bottomFraction)
        )

        zoneRects.clear()
        zoneRects[MuscleZone.CHEST] = listOf(rect(0.32f, 0.18f, 0.68f, 0.35f))
        zoneRects[MuscleZone.CORE] = listOf(rect(0.36f, 0.37f, 0.64f, 0.56f))
        zoneRects[MuscleZone.ARMS] =
            listOf(
                rect(0.14f, 0.22f, 0.29f, 0.55f),
                rect(0.71f, 0.22f, 0.86f, 0.55f)
            )
        zoneRects[MuscleZone.LEGS] =
            listOf(
                rect(0.32f, 0.58f, 0.48f, 0.98f),
                rect(0.52f, 0.58f, 0.68f, 0.98f)
            )
    }

    private fun selectZone(zone: MuscleZone) {
        if (selectedZone == zone) return
        selectedZone = zone
        selectionAnimator?.cancel()
        selectionProgress = 0f
        selectionAnimator =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 250L
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    selectionProgress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
    }

    private fun drawZoneOverlays(canvas: Canvas) {
        val zone = selectedZone ?: return
        val model = zoneData[zone] ?: return
        val color = if (hasZoneData(model)) zoneColor(model.precision) else 0xFF505050.toInt()
        val animatedFill = withAlpha(color, ((0x22 * selectionProgress).toInt()).coerceIn(0, 0x22))
        val animatedStroke = withAlpha(color, (255 * selectionProgress).toInt().coerceIn(0, 255))

        fillPaint.color = animatedFill
        strokePaint.color = animatedStroke

        zoneRects[zone].orEmpty().forEach { rect ->
            canvas.drawRoundRect(rect, dp(12f), dp(12f), fillPaint)
            canvas.drawRoundRect(rect, dp(12f), dp(12f), strokePaint)
        }
    }

    private fun hasZoneData(model: MuscleZoneModel): Boolean =
        model.lastSeenLabel != "—" || model.exercises.any { it.precision > 0 }

    private fun zoneColor(precision: Int): Int =
        when (precision) {
            in 89..100 -> 0xFFE8FF47.toInt()
            in 76..88 -> 0xFF1D9E75.toInt()
            in 61..75 -> 0xFFEF9F27.toInt()
            else -> 0xFFE24B4A.toInt()
        }

    private fun withAlpha(
        color: Int,
        alpha: Int
    ): Int = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private companion object {
        const val VIEWPORT_WIDTH = 160f
        const val VIEWPORT_HEIGHT = 320f
    }
}
