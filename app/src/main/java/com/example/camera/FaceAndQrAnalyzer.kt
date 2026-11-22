package com.example.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.model.FacePoint
import com.example.model.FaceTelemetry
import com.example.model.QrScanResult
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import java.util.concurrent.atomic.AtomicBoolean

class FaceAndQrAnalyzer(
    private val isFrontCameraProvider: () -> Boolean,
    private val isQrEnabledProvider: () -> Boolean,
    private val needsFilteredPreviewProvider: () -> Boolean,
    private val onLightSample: (Bitmap) -> Unit,
    private val onFacesDetected: (List<FaceTelemetry>) -> Unit,
    private val onQrDetected: (QrScanResult?) -> Unit,
    private val onVisionFrameReady: (Bitmap) -> Unit,
    private val onFilteredPreviewFrame: (Bitmap) -> Unit
) : ImageAnalysis.Analyzer {

    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .enableTracking()
            .build()
    )

    private val barcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build()
    )

    private val isProcessingMl = AtomicBoolean(false)
    private var lastMlTimestamp = 0L
    private var lastAiFrameTimestamp = 0L
    private var lastPreviewFrameTimestamp = 0L

    override fun analyze(image: ImageProxy) {
        try {
            val bitmap = image.toBitmap()
            val rotation = image.imageInfo.rotationDegrees
            val rotated = rotateBitmapIfNeeded(bitmap, rotation, isFrontCameraProvider())

            onLightSample(rotated)

            val now = System.currentTimeMillis()
            if (needsFilteredPreviewProvider() && now - lastPreviewFrameTimestamp >= 33L) {
                lastPreviewFrameTimestamp = now
                val copy = rotated.copy(Bitmap.Config.ARGB_8888, false)
                if (copy != null) onFilteredPreviewFrame(copy)
            }

            if (now - lastAiFrameTimestamp >= 900L) {
                lastAiFrameTimestamp = now
                val copy = rotated.copy(Bitmap.Config.ARGB_8888, false)
                if (copy != null) onVisionFrameReady(copy)
            }

            if (now - lastMlTimestamp >= 90L && isProcessingMl.compareAndSet(false, true)) {
                lastMlTimestamp = now
                val inputImage = InputImage.fromBitmap(rotated, 0)
                val imgW = rotated.width.toFloat().coerceAtLeast(1f)
                val imgH = rotated.height.toFloat().coerceAtLeast(1f)

                faceDetector.process(inputImage)
                    .addOnSuccessListener { faces ->
                        val mapped = faces.map { face ->
                            val b = face.boundingBox
                            val left = b.left / imgW
                            val right = b.right / imgW

                            fun mapPt(landmarkType: Int): FacePoint? {
                                val lm = face.getLandmark(landmarkType) ?: return null
                                return FacePoint(lm.position.x / imgW, lm.position.y / imgH)
                            }

                            FaceTelemetry(
                                trackingId = face.trackingId ?: 0,
                                boundsNormalized = RectF(left, b.top / imgH, right, b.bottom / imgH),
                                leftEye = mapPt(FaceLandmark.LEFT_EYE),
                                rightEye = mapPt(FaceLandmark.RIGHT_EYE),
                                noseBase = mapPt(FaceLandmark.NOSE_BASE),
                                mouthCenter = mapPt(FaceLandmark.MOUTH_BOTTOM),
                                leftCheek = mapPt(FaceLandmark.LEFT_CHEEK),
                                rightCheek = mapPt(FaceLandmark.RIGHT_CHEEK),
                                headRollDeg = face.headEulerAngleZ,
                                headYawDeg = face.headEulerAngleY,
                                smilingProbability = face.smilingProbability ?: -1f,
                                leftEyeOpenProbability = face.leftEyeOpenProbability ?: -1f,
                                rightEyeOpenProbability = face.rightEyeOpenProbability ?: -1f
                            )
                        }
                        onFacesDetected(mapped)
                    }
                    .addOnCompleteListener {
                        if (isQrEnabledProvider()) {
                            barcodeScanner.process(inputImage)
                                .addOnSuccessListener { barcodes ->
                                    val first = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }
                                    if (first != null) {
                                        val rect = first.boundingBox?.let { r ->
                                            RectF(r.left / imgW, r.top / imgH, r.right / imgW, r.bottom / imgH)
                                        }
                                        val raw = first.rawValue.orEmpty()
                                        onQrDetected(
                                            QrScanResult(
                                                rawValue = raw,
                                                formatLabel = if (first.format == Barcode.FORMAT_QR_CODE) "QR Code" else "Barcode",
                                                isUrl = raw.startsWith("http://") || raw.startsWith("https://"),
                                                boundsNormalized = rect
                                            )
                                        )
                                    } else {
                                        onQrDetected(null)
                                    }
                                }
                                .addOnCompleteListener {
                                    isProcessingMl.set(false)
                                }
                        } else {
                            onQrDetected(null)
                            isProcessingMl.set(false)
                        }
                    }
            }
        } catch (_: Exception) {
            isProcessingMl.set(false)
        } finally {
            image.close()
        }
    }

    private fun rotateBitmapIfNeeded(src: Bitmap, degrees: Int, mirrorX: Boolean): Bitmap {
        if (degrees == 0 && !mirrorX) return src
        val matrix = Matrix().apply {
            if (degrees != 0) postRotate(degrees.toFloat())
            if (mirrorX) postScale(-1f, 1f)
        }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    fun close() {
        runCatching { faceDetector.close() }
        runCatching { barcodeScanner.close() }
    }
}
