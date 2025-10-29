package com.razamtech.smartbrakealert.logic

class CollisionPredictor {

    fun calculateTtc(distanceMeters: Double, speedKmh: Double): Double? {
        if (speedKmh <= SPEED_THRESHOLD_KMH || distanceMeters <= 0.0) {
            return null
        }
        val speedMs = speedKmh / MS_TO_KMH
        if (speedMs <= 0.0) return null
        val ttc = distanceMeters / speedMs
        if (!ttc.isFinite() || ttc < 0) {
            return null
        }
        val rounded = kotlin.math.round(ttc * 100.0) / 100.0
        return if (rounded.isFinite()) rounded else null
    }

    companion object {
        private const val SPEED_THRESHOLD_KMH = 1.0
        private const val MS_TO_KMH = 3.6
    }
}
