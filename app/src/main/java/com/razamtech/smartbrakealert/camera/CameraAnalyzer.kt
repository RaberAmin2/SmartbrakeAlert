package com.razamtech.smartbrakealert.camera

import android.content.Context
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlin.math.roundToInt
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp

class CameraAnalyzer(
    context: Context,
    private val distanceEstimator: DistanceEstimator,
    private val onResult: (DetectionResult?) -> Unit
) : ImageAnalysis.Analyzer {

    private val interpreter: Interpreter? = runCatching {
        Interpreter(FileUtil.loadMappedFile(context, MODEL_FILE))
    }.getOrNull()
    private val labels: List<String> = runCatching {
        FileUtil.loadLabels(context, LABELS_FILE)
    }.getOrDefault(DEFAULT_LABELS)
    private val inputShape: IntArray = interpreter?.getInputTensor(0)?.shape() ?: intArrayOf(1, 640, 640, 3)
    private val inputWidth = inputShape.getOrNull(1) ?: 640
    private val inputHeight = inputShape.getOrNull(2) ?: 640
    private val imageProcessor: ImageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(inputHeight, inputWidth, ResizeOp.ResizeMethod.BILINEAR))
        .add(NormalizeOp(0f, 255f))
        .build()
    private val tensorImage = TensorImage(DataType.FLOAT32)
    private var warmedUp = false

    override fun analyze(image: ImageProxy) {
        val detection = runCatching { detectVehicle(image) }.getOrNull()
        onResult(detection)
        image.close()
    }

    private fun detectVehicle(image: ImageProxy): DetectionResult? {
        val interpreter = interpreter ?: return null

        runInterpreterWarmup()

        tensorImage.load(image.toBitmap())
        val processedImage = imageProcessor.process(tensorImage)

        val outputShape = interpreter.getOutputTensor(0).shape()
        val numDetections = outputShape.getOrNull(1) ?: return null
        val attributes = outputShape.getOrNull(2) ?: return null
        val outputBuffer = Array(1) { Array(numDetections) { FloatArray(attributes) } }

        val inputBuffer = processedImage.buffer
        inputBuffer.rewind()
        interpreter.run(inputBuffer, outputBuffer)

        val scaleX = image.width / inputWidth.toFloat()
        val detections = outputBuffer[0]

        var bestResult: DetectionResult? = null
        var bestConfidence = 0f

        for (detection in detections) {
            if (detection.size < 6) continue
            val objectness = detection[4].coerceIn(0f, 1f)
            val classScores = detection.copyOfRange(5, detection.size)
            val (classIndex, classConfidence) = classScores
                .withIndex()
                .maxByOrNull { it.value } ?: continue

            val label = labels.getOrElse(classIndex) { classIndex.toString() }
            if (label !in VEHICLE_LABELS) continue

            val confidence = (objectness * classConfidence).coerceIn(0f, 1f)
            if (confidence < CONFIDENCE_THRESHOLD || confidence <= bestConfidence) continue

            val bboxWidth = detection[2] * scaleX
            val bboxWidthPx = bboxWidth.roundToInt().coerceAtLeast(1)
            val distanceMeters = distanceEstimator.estimateMeters(bboxWidthPx, image.width)

            bestConfidence = confidence
            bestResult = DetectionResult(
                label = label,
                distanceMeters = distanceMeters,
                confidence = confidence
            )
        }

        return bestResult
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
        private const val MODEL_FILE = "yolov11n.tflite"
        private const val LABELS_FILE = "labels.txt"
        private const val CONFIDENCE_THRESHOLD = 0.3f
        private val VEHICLE_LABELS = setOf("car", "bus", "truck", "motorcycle", "bicycle")
        private val DEFAULT_LABELS = listOf("person", "bicycle", "car", "motorcycle", "airplane", "bus")
    }
}
