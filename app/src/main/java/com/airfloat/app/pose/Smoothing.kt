package com.airfloat.app.pose

object Smoothing {

    fun ema(prev: Float?, new: Float?, alpha: Float): Float? {
        require(alpha > 0f && alpha <= 1f) { "alpha must be in (0, 1]" }

        if (new == null) return prev
        if (prev == null) return new

        return alpha * new + (1f - alpha) * prev
    }
}