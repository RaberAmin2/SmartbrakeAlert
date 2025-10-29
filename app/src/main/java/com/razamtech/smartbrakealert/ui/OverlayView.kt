package com.razamtech.smartbrakealert.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import java.util.Locale

class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.TRANSPARENT
    }
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.TRANSPARENT
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 18f * resources.displayMetrics.scaledDensity
    }

    private var dangerLevel: Int = 0
    private var distanceMeters: Double? = null
    private var ttcSeconds: Double? = null
    private var confidence: Float? = null
    private val bbox = RectF()

    fun updateDanger(level: Int, distance: Double?, ttc: Double?, confidence: Float?) {
        dangerLevel = level
        distanceMeters = distance
        ttcSeconds = ttc
        this.confidence = confidence
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val color = when (dangerLevel) {
            2 -> Color.argb(180, 220, 0, 0)
            1 -> Color.argb(160, 255, 200, 0)
            else -> Color.argb(120, 0, 0, 0)
        }
        backgroundPaint.color = color
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        if (dangerLevel == 0) {
            drawStatusText(canvas, "Bereit", Color.WHITE)
            return
        }

        val boxPadding = width.coerceAtMost(height) * 0.1f
        bbox.set(
            boxPadding,
            boxPadding,
            width - boxPadding,
            height - boxPadding
        )
        framePaint.color = if (dangerLevel == 2) Color.RED else Color.YELLOW
        canvas.drawRoundRect(bbox, 32f, 32f, framePaint)

        val distanceText = distanceMeters?.let { "Distanz: ${String.format(Locale.getDefault(), "%.1f", it)} m" }
        val ttcText = ttcSeconds?.let { "TTC: ${String.format(Locale.getDefault(), "%.1f", it)} s" }
        val confidenceText = confidence?.let { "Konfidenz: ${String.format(Locale.getDefault(), "%.0f", it * 100)}%" }
        val lines = listOfNotNull(distanceText, ttcText, confidenceText)
        if (lines.isEmpty()) return

        textPaint.textSize = (width * 0.04f).coerceAtLeast(32f)
        var offsetY = height / 2f - (lines.size - 1) * textPaint.textSize / 2
        for (line in lines) {
            val textWidth = textPaint.measureText(line)
            canvas.drawText(line, width / 2f - textWidth / 2f, offsetY, textPaint)
            offsetY += textPaint.textSize * 1.2f
        }
    }

    private fun drawStatusText(canvas: Canvas, text: String, color: Int) {
        textPaint.color = color
        textPaint.textSize = (width * 0.045f).coerceAtLeast(32f)
        val textWidth = textPaint.measureText(text)
        val baseLine = height / 2f + textPaint.textSize / 2f
        canvas.drawText(text, width / 2f - textWidth / 2f, baseLine, textPaint)
        textPaint.color = Color.WHITE
    }
}
