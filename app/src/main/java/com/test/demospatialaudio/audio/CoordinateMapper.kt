package com.test.demospatialaudio.audio

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object CoordinateMapper {

    const val ROOM_RADIUS = 10f
    private const val MIN_NORMALIZED_RADIUS = 0.05

    fun clampMinRadius(x: Double, y: Double): Pair<Double, Double> {
        val radius = sqrt(x * x + y * y)
        if (radius >= MIN_NORMALIZED_RADIUS) {
            return Pair(clampUnit(x), clampUnit(y))
        }
        if (radius <= 0.0) {
            return Pair(0.0, -MIN_NORMALIZED_RADIUS)
        }
        val scale = MIN_NORMALIZED_RADIUS / radius
        return Pair(clampUnit(x * scale), clampUnit(y * scale))
    }

    fun normalize(
        px: Float, py: Float,
        width: Float, height: Float
    ): Pair<Double, Double> {
        if (width <= 0f || height <= 0f) return Pair(0.0, 0.0)
        val half = min(width, height) / 2f
        val cx = width / 2f
        val cy = height / 2f
        val nx = ((px - cx) / half).toDouble()
        val ny = ((py - cy) / half).toDouble()
        return clampMinRadius(nx, ny)
    }

    fun screenPoint(
        normalizedX: Double, normalizedY: Double,
        width: Float, height: Float
    ): Pair<Float, Float> {
        val half = min(width, height) / 2f
        val cx = width / 2f
        val cy = height / 2f
        return Pair(
            cx + normalizedX.toFloat() * half,
            cy + normalizedY.toFloat() * half
        )
    }

    private fun clampUnit(value: Double): Double = max(-1.0, min(1.0, value))
}
