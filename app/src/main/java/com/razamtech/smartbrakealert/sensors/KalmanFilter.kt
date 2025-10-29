package com.razamtech.smartbrakealert.sensors

class KalmanFilter(
    private val processNoise: Double = 1e-3,
    private val measurementNoise: Double = 0.05
) {

    private var estimate = 0.0
    private var errorCovariance = 1.0
    private var initialized = false

    fun filter(measurement: Double): Double {
        if (!initialized) {
            estimate = measurement
            initialized = true
            return measurement
        }

        val predictionError = errorCovariance + processNoise
        val kalmanGain = predictionError / (predictionError + measurementNoise)
        estimate += kalmanGain * (measurement - estimate)
        errorCovariance = (1 - kalmanGain) * predictionError
        return estimate
    }

    fun reset() {
        estimate = 0.0
        errorCovariance = 1.0
        initialized = false
    }
}
