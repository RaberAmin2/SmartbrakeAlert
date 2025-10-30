package com.razamtech.smartbrakealert.camera

data class DetectionResult(
    val label: String,
    val distanceMeters: Double,
    val confidence: Float,
    val boundingBox: BoundingBox
)

data class BoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)
