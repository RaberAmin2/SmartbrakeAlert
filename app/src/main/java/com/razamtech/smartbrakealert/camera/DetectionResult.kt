package com.razamtech.smartbrakealert.camera

data class DetectionResult(
    val label: String,
    val distanceMeters: Double,
    val confidence: Float
)
