package com.halo.app.data.toolkit

import kotlin.math.hypot

internal data class MeasurementPoint(val x: Double, val y: Double)
internal data class RoomWall(val metres: Double, val left: Boolean)

internal object HaloMeasurement {
    fun imageEstimate(referenceCentimetres: Double, referencePixels: Double, measuredPixels: Double): Double {
        require(referenceCentimetres.isFinite() && referenceCentimetres > 0 && referenceCentimetres <= 100_000)
        require(referencePixels.isFinite() && measuredPixels.isFinite() && referencePixels >= 8 && measuredPixels >= 2)
        require(referencePixels <= 200_000 && measuredPixels <= 200_000)
        return (referenceCentimetres * measuredPixels / referencePixels).also {
            require(it.isFinite() && it > 0 && it <= 1_000_000)
        }
    }

    fun imageEstimate(width: Int, height: Int, points: List<MeasurementPoint>, referenceCentimetres: Double): Double {
        require(width > 0 && height > 0 && points.size == 4 && points.all { it.x in 0.0..1.0 && it.y in 0.0..1.0 })
        fun separation(first: Int, second: Int) = hypot(
            (points[first].x - points[second].x) * width,
            (points[first].y - points[second].y) * height,
        )
        return imageEstimate(referenceCentimetres, separation(0, 1), separation(2, 3))
    }

    fun roomPoints(walls: List<RoomWall>): List<MeasurementPoint> {
        require(walls.size <= 120)
        var direction = 0
        var point = MeasurementPoint(0.0, 0.0)
        return buildList {
            add(point)
            for (wall in walls) {
                require(validWall(wall.metres))
                point = when (direction) {
                    0 -> point.copy(x = point.x + wall.metres)
                    1 -> point.copy(y = point.y + wall.metres)
                    2 -> point.copy(x = point.x - wall.metres)
                    else -> point.copy(y = point.y - wall.metres)
                }
                add(point)
                direction = (direction + if (wall.left) 3 else 1) % 4
            }
        }
    }

    fun validWall(length: Double): Boolean = length.isFinite() && length in .01..100.0
}
