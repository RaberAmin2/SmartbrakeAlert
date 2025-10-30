package com.razamtech.smartbrakealert.logic

import android.os.SystemClock
import com.razamtech.smartbrakealert.camera.DetectionResult
import com.razamtech.smartbrakealert.ui.OverlayView
import com.razamtech.smartbrakealert.ui.SoundAlert

class WarningController(
    private val overlayView: OverlayView,
    private val soundAlert: SoundAlert
) {

    private var lastLevel: Int = 0
    private var lastAlertTimestamp: Long = 0L

    fun onDetection(result: DetectionResult, ttc: Double?) {
        val level = determineLevel(result.distanceMeters, ttc)
        overlayView.updateDanger(
            level = level,
            distance = result.distanceMeters,
            ttc = ttc,
            confidence = result.confidence,
            boundingBoxes = listOf(result.boundingBox),
            label = result.label
        )
        triggerSound(level)
    }

    fun onNoDetection() {
        overlayView.updateDanger(0, null, null, null)
        if (lastLevel != 0) {
            soundAlert.clear()
        }
        lastLevel = 0
    }

    fun release() {
        soundAlert.clear()
    }

    private fun determineLevel(distance: Double, ttc: Double?): Int {
        if (ttc != null && ttc < 2.0 && distance < 10.0) {
            return 2
        }
        if (ttc != null && ttc < 3.5) {
            return 1
        }
        return 0
    }

    private fun triggerSound(level: Int) {
        if (level == lastLevel && SystemClock.elapsedRealtime() - lastAlertTimestamp < ALERT_COOLDOWN_MS) {
            overlayView.invalidate()
            return
        }
        when (level) {
            0 -> soundAlert.clear()
            1 -> soundAlert.playWarning()
            2 -> soundAlert.playAlarm()
        }
        lastLevel = level
        lastAlertTimestamp = SystemClock.elapsedRealtime()
    }

    companion object {
        private const val ALERT_COOLDOWN_MS = 1500L
    }
}
