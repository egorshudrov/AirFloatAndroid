package com.airfloat.app.pose

import kotlin.math.acos
import kotlin.math.sqrt

data class Point2(val x: Float, val y: Float)

object AngleUtils {

    fun jointAngleDeg(a: Point2?, b: Point2?, c: Point2?): Float? {
        if (a == null || b == null || c == null) return null

        val bax = a.x - b.x
        val bay = a.y - b.y
        val bcx = c.x - b.x
        val bcy = c.y - b.y

        val normBa = sqrt(bax * bax + bay * bay)
        val normBc = sqrt(bcx * bcx + bcy * bcy)

        val eps = 1e-6f
        if (normBa < eps || normBc < eps) return null

        var cos = (bax * bcx + bay * bcy) / (normBa * normBc)
        if (cos > 1f) cos = 1f
        if (cos < -1f) cos = -1f

        val rad = acos(cos)
        val deg = rad * (180f / Math.PI.toFloat())

        if (!deg.isFinite()) return null
        return deg.coerceIn(0f, 180f)
    }

    fun elbowAngleDeg(shoulder: Point2?, elbow: Point2?, wrist: Point2?): Float? {
        return jointAngleDeg(shoulder, elbow, wrist)
    }
}