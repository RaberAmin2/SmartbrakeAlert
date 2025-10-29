package com.razamtech.smartbrakealert.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class SoundAlert(context: Context) {

    private val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 80)
    private val deviceVibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(VibratorManager::class.java)
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    @Synchronized
    fun playWarning() {
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 250)
        vibrate(150)
    }

    @Synchronized
    fun playAlarm() {
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
        vibrate(300)
    }

    @Synchronized
    fun clear() {
        toneGenerator.stopTone()
        deviceVibrator?.cancel()
    }

    fun release() {
        toneGenerator.release()
    }

    private fun vibrate(duration: Long) {
        val vibrator = deviceVibrator ?: return
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }
}
