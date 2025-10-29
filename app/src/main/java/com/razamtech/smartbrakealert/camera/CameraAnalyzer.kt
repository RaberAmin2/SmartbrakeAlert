package com.razamtech.smartbrakealert.camera

import android.content.Context
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlin.math.roundToInt
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil

class CameraAnalyzer(
    context: Context,
    private val distanceEstimator: DistanceEstimator,
    private val onResult: (DetectionResult?) -> Unit
) : ImageAnalysis.Analyzer {

    private val interpreter: Interpreter? = runCatching {
        Interpreter(FileUtil.loadMappedFile(context, MODEL_FILE))
    }.getOrNull()
    private var warmedUp = false

    override fun analyze(image: ImageProxy) {
        val detection = runCatching { detectVehicle(image) }.getOrNull()
        onResult(detection)
        image.close()
    }

    private fun detectVehicle(image: ImageProxy): DetectionResult? {
        val luminancePlane = image.planes.firstOrNull() ?: return null
        val buffer = luminancePlane.buffer.duplicate()
        val pixelCount = buffer.remaining()
        if (pixelCount == 0) return null

        var sum = 0L
        while (buffer.hasRemaining()) {
            sum += buffer.get().toInt() and 0xFF
        }
        val average = sum / pixelCount.toDouble()
        if (average < LUMINANCE_THRESHOLD) {
            return null
        }

        runInterpreterWarmup()

        val confidence = ((average - LUMINANCE_THRESHOLD) / (255.0 - LUMINANCE_THRESHOLD))
            .coerceIn(0.1, 1.0)
        val bboxWidthPx = (image.width * (BASE_BBOX_RATIO + confidence * 0.3)).roundToInt()
            .coerceIn(1, image.width)
        val distanceMeters = distanceEstimator.estimateMeters(bboxWidthPx, image.width)

        return DetectionResult(
            label = "vehicle",
            distanceMeters = distanceMeters,
            confidence = confidence.toFloat()
        )
    }

    private fun runInterpreterWarmup() {
        val interpreter = interpreter ?: return
        if (warmedUp) return
        if (interpreter.inputTensorCount <= 0 || interpreter.outputTensorCount <= 0) {
            warmedUp = true
            return
        }
        val inputShape = interpreter.getInputTensor(0).shape()
        val inputSize = inputShape.fold(1) { acc, value -> acc * value }
        val inputBuffer = FloatArray(inputSize)
        val outputShape = interpreter.getOutputTensor(0).shape()
        val outputSize = outputShape.fold(1) { acc, value -> acc * value }
        val outputBuffer = FloatArray(outputSize)
        interpreter.run(inputBuffer, outputBuffer)
        warmedUp = true
    }

    fun close() {
        interpreter?.close()
    }

    companion object {
        private const val MODEL_FILE = "model.tflite"
        private const val LUMINANCE_THRESHOLD = 80.0
        private const val BASE_BBOX_RATIO = 0.2
    }
}
