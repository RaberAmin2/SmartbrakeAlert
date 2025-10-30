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
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CameraAnalyzer(
    context: Context,
    private val distanceEstimator: DistanceEstimator,
    private val onResult: (DetectionResult?) -> Unit
) : ImageAnalysis.Analyzer {

    private val modelBuffer: ByteBuffer? = loadModelBuffer(context)
    private val interpreter: Interpreter? = modelBuffer?.let { buffer ->
        runCatching { Interpreter(buffer) }.getOrNull()
    }
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

        val scaleX = if (inputWidth > 0) image.width / inputWidth.toFloat() else 1f
        val scaleY = if (inputHeight > 0) image.height / inputHeight.toFloat() else 1f
        val imageWidth = image.width.toFloat().coerceAtLeast(1f)
        val imageHeight = image.height.toFloat().coerceAtLeast(1f)
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
            val bboxHeight = detection[3] * scaleY
            val bboxWidthPx = bboxWidth.roundToInt().coerceAtLeast(1)
            val distanceMeters = distanceEstimator.estimateMeters(bboxWidthPx, image.width)

            val centerX = detection[0] * scaleX
            val centerY = detection[1] * scaleY
            val halfWidth = bboxWidth / 2f
            val halfHeight = bboxHeight / 2f
            val left = (centerX - halfWidth).coerceIn(0f, imageWidth)
            val top = (centerY - halfHeight).coerceIn(0f, imageHeight)
            val right = (centerX + halfWidth).coerceIn(0f, imageWidth)
            val bottom = (centerY + halfHeight).coerceIn(0f, imageHeight)

            if (right <= left || bottom <= top) continue

            val normalizedBoundingBox = BoundingBox(
                left = left / imageWidth,
                top = top / imageHeight,
                right = right / imageWidth,
                bottom = bottom / imageHeight
            )

            bestConfidence = confidence
            bestResult = DetectionResult(
                label = label,
                distanceMeters = distanceMeters,
                confidence = confidence,
                boundingBox = normalizedBoundingBox
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

    private fun loadModelBuffer(context: Context): ByteBuffer? {
        return runCatching {
            context.assets.open(MODEL_FILE).use { inputStream ->
                val bytes = inputStream.readBytes()
                ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply {
                    put(bytes)
                    rewind()
                }
            }
        }.getOrNull()
    }
}
