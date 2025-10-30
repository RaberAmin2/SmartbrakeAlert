package com.razamtech.smartbrakealert.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.razamtech.smartbrakealert.camera.BoundingBox
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
    private var boundingBoxes: List<BoundingBox> = emptyList()
    private var detectionLabel: String? = null
    private val tempRect = RectF()

    fun updateDanger(
        level: Int,
        distance: Double?,
        ttc: Double?,
        confidence: Float?,
        boundingBoxes: List<BoundingBox> = emptyList(),
        label: String? = null
    ) {
        dangerLevel = level
        distanceMeters = distance
        ttcSeconds = ttc
        this.confidence = confidence
        this.boundingBoxes = boundingBoxes
        detectionLabel = label
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

        val boxes = boundingBoxes
        if (boxes.isEmpty()) {
            drawStatusText(canvas, "Bereit", Color.WHITE)
            return
        }

        framePaint.color = when (dangerLevel) {
            2 -> Color.RED
            1 -> Color.YELLOW
            else -> Color.WHITE
        }

        textPaint.color = Color.WHITE
        textPaint.textSize = (width * 0.035f).coerceAtLeast(28f)

        boxes.forEach { box ->
            val clampedLeft = box.left.coerceIn(0f, 1f)
            val clampedTop = box.top.coerceIn(0f, 1f)
            val clampedRight = box.right.coerceIn(0f, 1f)
            val clampedBottom = box.bottom.coerceIn(0f, 1f)

            tempRect.set(
                clampedLeft * width,
                clampedTop * height,
                clampedRight * width,
                clampedBottom * height
            )
            canvas.drawRoundRect(tempRect, 28f, 28f, framePaint)
        }

        val infoLines = buildInfoLines()
        if (infoLines.isEmpty()) {
            return
        }

        val primaryRect = RectF().apply {
            val first = boxes.first()
            set(
                first.left.coerceIn(0f, 1f) * width,
                first.top.coerceIn(0f, 1f) * height,
                first.right.coerceIn(0f, 1f) * width,
                first.bottom.coerceIn(0f, 1f) * height
            )
        }

        val margin = 24f
        val lineHeight = textPaint.textSize * 1.1f
        val totalTextHeight = lineHeight * infoLines.size
        var startY = primaryRect.bottom + margin + textPaint.textSize
        if (startY + totalTextHeight > height - margin) {
            startY = (primaryRect.top - margin - totalTextHeight)
                .coerceAtLeast(margin + textPaint.textSize)
        }

        var offsetY = startY
        val baseX = (primaryRect.left + margin).coerceAtLeast(margin)
        for (line in infoLines) {
            val textWidth = textPaint.measureText(line)
            val x = baseX.coerceIn(margin, width - margin - textWidth)
            canvas.drawText(line, x, offsetY, textPaint)
            offsetY += lineHeight
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

    private fun buildInfoLines(): List<String> {
        val locale = Locale.getDefault()
        val distanceText = distanceMeters?.let {
            "Distanz: ${String.format(locale, "%.1f", it)} m"
        }
        val ttcText = ttcSeconds?.let {
            "TTC: ${String.format(locale, "%.1f", it)} s"
        }
        val confidenceText = confidence?.let {
            "Konfidenz: ${String.format(locale, "%.0f", it * 100)}%"
        }
        val labelText = detectionLabel?.takeIf { it.isNotBlank() }?.let {
            it.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(locale) else char.toString()
            }
        }
        return listOfNotNull(labelText, distanceText, ttcText, confidenceText)
    }
}
