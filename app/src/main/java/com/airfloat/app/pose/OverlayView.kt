package com.airfloat.app.pose

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View
import java.util.ArrayDeque
import kotlin.math.roundToInt

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var pointsPx: List<PointF> = emptyList()
    private var smoothedPoints: MutableList<PointF>? = null
    private val smoothingAlpha = 0.45f
    private val history: ArrayDeque<List<PointF>> = ArrayDeque()
    private val maxTrail = 4 // сколько предыдущих кадров держим для лёгкого хвоста

    private var leftAngle: Float? = null
    private var rightAngle: Float? = null
    private var reps: Int = 0
    private var fps: Float = 0f
    private var latencyMs: Float = 0f
    private var pipeline: String = ""
    private var debugHudVisible: Boolean = false

    private val pointPaint = Paint().apply {
        isAntiAlias = true
        strokeWidth = 8f
        style = Paint.Style.FILL
    }

    private val neonStroke = Paint().apply {
        isAntiAlias = true
        strokeWidth = 7f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = 0xFFFFC247.toInt()
        setShadowLayer(14f, 0f, 0f, 0x80FFC247.toInt())
    }

    private val neonPoint = Paint().apply {
        isAntiAlias = true
        strokeWidth = 10f
        style = Paint.Style.FILL
        color = 0xFFFFD766.toInt()
        setShadowLayer(12f, 0f, 0f, 0x80FFC247.toInt())
    }

    private val textPaint = Paint().apply {
        isAntiAlias = true
        textSize = 46f
        color = 0xFFFFF3B0.toInt()
        setShadowLayer(8f, 0f, 0f, 0x80FFB300.toInt())
    }

    // Соединения BlazePose (33 точки)
    private val edges = listOf(
        11 to 13, 13 to 15, // левая рука
        12 to 14, 14 to 16, // правая рука
        11 to 12, // плечи
        11 to 23, 12 to 24, // корпус к тазу
        23 to 24, // таз
        23 to 25, 24 to 26, // бёдра
        25 to 27, 26 to 28, // колени
        27 to 29, 28 to 30, // голени
        29 to 31, 30 to 32, // ступни
        15 to 17, 16 to 18, // кисти
        17 to 19, 19 to 21, 18 to 20, 20 to 22, // пальцы (упрощённо)
        11 to 0, 12 to 0 // шея к голове (нос)
    )

    init {
        // Тени для неона требуют software layer
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setPointsPx(newPoints: List<PointF>) {
        if (newPoints.isEmpty()) {
            pointsPx = emptyList()
            smoothedPoints = null
            history.clear()
            invalidate()
            return
        }

        val current =
            if (smoothedPoints == null || smoothedPoints?.size != newPoints.size) {
                newPoints.map { PointF(it.x, it.y) }.toMutableList()
            } else {
                smoothedPoints!!.also { smooth ->
                    for (i in smooth.indices) {
                        val src = newPoints[i]
                        val prev = smooth[i]
                        prev.x = smoothingAlpha * src.x + (1f - smoothingAlpha) * prev.x
                        prev.y = smoothingAlpha * src.y + (1f - smoothingAlpha) * prev.y
                    }
                }
            }
        smoothedPoints = current
        pointsPx = current.map { PointF(it.x, it.y) }

        if (pointsPx.isNotEmpty()) {
            history.addFirst(pointsPx.map { PointF(it.x, it.y) })
            while (history.size > maxTrail) history.removeLast()
        }
        invalidate()
    }

    fun setDebug(left: Float?, right: Float?, reps: Int, fps: Float, latencyMs: Float, pipeline: String) {
        this.leftAngle = left
        this.rightAngle = right
        this.reps = reps
        this.fps = fps
        this.latencyMs = latencyMs
        this.pipeline = pipeline
        invalidate()
    }

    fun setDebugHudVisible(visible: Boolean) {
        if (debugHudVisible == visible) return
        debugHudVisible = visible
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // линии с лёгким хвостом
        history.forEachIndexed { i, pts ->
            if (pts.size < 33) return@forEachIndexed
            val alphaFactor = when (i) {
                0 -> 1f
                1 -> 0.55f
                2 -> 0.35f
                else -> 0.2f
            }
            val paintLine = neonStroke.apply { alpha = (255 * alphaFactor).toInt() }
            edges.forEach { (a, b) ->
                val pa = pts[a]
                val pb = pts[b]
                canvas.drawLine(pa.x, pa.y, pb.x, pb.y, paintLine)
            }
        }

        // точки (текущие)
        for (p in pointsPx) {
            canvas.drawCircle(p.x, p.y, 9f, neonPoint)
        }

        if (debugHudVisible) {
            // текст
            val la = leftAngle?.let { "%.0f".format(it) } ?: "--"
            val ra = rightAngle?.let { "%.0f".format(it) } ?: "--"
            val fpsText = fps.roundToInt()
            val latText = latencyMs.roundToInt()

            canvas.drawText("L:$la  R:$ra", 24f, 70f, textPaint)
            canvas.drawText("Reps: $reps", 24f, 125f, textPaint)
            canvas.drawText("FPS: $fpsText  Lat: ${latText}ms", 24f, 180f, textPaint)
            if (pipeline.isNotBlank()) {
                canvas.drawText("Pipe: $pipeline", 24f, 235f, textPaint)
            }
        }
    }
}
