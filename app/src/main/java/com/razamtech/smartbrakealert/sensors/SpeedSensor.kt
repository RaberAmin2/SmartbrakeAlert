package com.razamtech.smartbrakealert.sensors

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SpeedSensor(
    context: Context,
    private val kalmanFilter: KalmanFilter
) {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private val _speedFlow = MutableStateFlow(0.0)
    val speedFlow: StateFlow<Double> = _speedFlow
    private var isRunning = false

    private val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 500L)
        .setMinUpdateIntervalMillis(250L)
        .setWaitForAccurateLocation(false)
        .build()

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            handleLocation(location)
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRunning) return
        fusedLocationClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())
        isRunning = true
    }

    fun stop() {
        if (!isRunning) return
        fusedLocationClient.removeLocationUpdates(callback)
        isRunning = false
    }

    private fun handleLocation(location: Location) {
        val speedMetersPerSecond = location.speed.coerceAtLeast(0f).toDouble()
        val filteredSpeed = kalmanFilter.filter(speedMetersPerSecond)
        val speedKmh = filteredSpeed * MS_TO_KMH
        _speedFlow.value = speedKmh
    }

    fun reset() {
        kalmanFilter.reset()
        _speedFlow.value = 0.0
    }

    companion object {
        private const val MS_TO_KMH = 3.6
    }
}
