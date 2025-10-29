package com.razamtech.smartbrakealert.camera

class DistanceEstimator(
    private val knownWidthMeters: Double = 1.8,
    private val focalLengthPixels: Double = 1200.0,
    private val smoothingFactor: Double = 0.25
) {

    private var lastEstimate: Double? = null

    fun estimateMeters(boundingBoxWidthPx: Int, imageWidthPx: Int): Double {
        val effectiveWidth = boundingBoxWidthPx.coerceAtLeast(1)
        val focal = if (focalLengthPixels > 0) focalLengthPixels else imageWidthPx * 1.2
        val rawDistance = (knownWidthMeters * focal) / effectiveWidth
        val normalized = rawDistance * (imageWidthPx / (focal * 1.0))
        val clamped = normalized.coerceIn(0.5, 200.0)
        val smoothed = lastEstimate?.let { previous ->
            previous + smoothingFactor * (clamped - previous)
        } ?: clamped
        lastEstimate = smoothed
        return smoothed
    }

    fun estimateFromConfidence(confidence: Float): Double {
        val normalized = confidence.coerceIn(0f, 1f)
        val distance = 30.0 * (1 - normalized) + 5.0
        val smoothed = lastEstimate?.let { prev ->
            prev + smoothingFactor * (distance - prev)
        } ?: distance
        lastEstimate = smoothed
        return smoothed
    }

    fun reset() {
        lastEstimate = null
    }
}
