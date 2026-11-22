package com.example.model

import android.graphics.RectF

enum class CameraMode(val label: String) {
    PHOTO("Photo"),
    PORTRAIT("Portrait"),
    NIGHT("Night"),
    VIDEO("Video"),
    SLOW_MO("Slow-Mo"),
    TIME_LAPSE("Time-Lapse"),
    DOCUMENT("Doc Scan"),
    QR("QR Scan")
}

enum class AspectRatioOption(val label: String, val ratio: Float) {
    RATIO_9_16("9:16", 9f / 16f),
    RATIO_4_3("4:3", 3f / 4f),
    RATIO_1_1("1:1", 1f),
    RATIO_16_9("16:9", 16f / 9f)
}

enum class GridType(val label: String) {
    NONE("Off"),
    THIRDS("3x3"),
    GOLDEN("Golden"),
    CENTER_BOX("Square")
}

enum class FlashState(val label: String) {
    OFF("Off"),
    AUTO("Auto"),
    ON("On"),
    TORCH("Torch")
}

data class ProManualParams(
    val isProModeOpen: Boolean = false,
    val exposureEv: Float = 0f,        // -2.0 .. +2.0 EV
    val isoGain: Int = 200,            // 100 .. 3200
    val temperatureKelvin: Int = 5500, // 2500 .. 8500 K
    val contrast: Float = 1.0f,        // 0.5 .. 1.8
    val saturation: Float = 1.0f,      // 0.0 .. 2.0
    val sharpness: Float = 0.2f,       // 0.0 .. 1.0
    val vignette: Float = 0.0f,        // 0.0 .. 1.0
    val grain: Float = 0.0f,           // 0.0 .. 1.0
    val highlights: Float = 0.0f,      // -1.0 .. 1.0
    val shadows: Float = 0.0f,         // -1.0 .. 1.0
    val greenery: Float = 0.0f,        // -1.0 .. 1.0
    val brightness: Float = 0.0f       // -0.5 .. 0.5
)

data class LightAdaptationState(
    val meanLuma: Float = 128f,
    val shadowRatio: Float = 0f,
    val highlightClipRatio: Float = 0f,
    val adaptiveBrightnessOffset: Float = 0f,
    val adaptiveShadowLift: Float = 0f,
    val adaptiveHighlightRecovery: Float = 0f,
    val lowLightBoostActive: Boolean = false,
    val brightLightProtectActive: Boolean = false,
    val conditionLabel: String = "Balanced"
)

data class FacePoint(val x: Float, val y: Float)

data class FaceTelemetry(
    val trackingId: Int,
    val boundsNormalized: RectF, // 0..1 relative to preview
    val leftEye: FacePoint?,
    val rightEye: FacePoint?,
    val noseBase: FacePoint?,
    val mouthCenter: FacePoint?,
    val leftCheek: FacePoint?,
    val rightCheek: FacePoint?,
    val headRollDeg: Float,
    val headYawDeg: Float,
    val smilingProbability: Float,
    val leftEyeOpenProbability: Float,
    val rightEyeOpenProbability: Float
)

data class QrScanResult(
    val rawValue: String,
    val formatLabel: String,
    val isUrl: Boolean,
    val boundsNormalized: RectF?
)

enum class GuidanceDirection {
    NONE, LEFT, RIGHT, UP, DOWN, TILT_UP, TILT_DOWN, CLOSER, BACK
}

data class AiGuidance(
    val sceneLabel: String = "Ready",
    val subjectType: String = "General",
    val instruction: String = "",
    val direction: GuidanceDirection = GuidanceDirection.NONE,
    val idealFramingBox: RectF? = null, // normalized 0..1
    val compositionScore: Float = 0.7f,
    val timestampMs: Long = 0L
)

data class AiSuggestion(
    val id: String,
    val label: String,
    val detail: String,
    val toolName: String,
    val argsJson: String
)

data class CameraStateSnapshot(
    val activeConfig: GCamCameraConfig? = null,
    val activeFilters: List<ActiveFilterLayer>,
    val proParams: ProManualParams,
    val cameraMode: CameraMode,
    val hdrEnabled: Boolean,
    val zoomRatio: Float
)

data class AiActionEntry(
    val id: String,
    val summary: String,
    val timestampMs: Long,
    val previousSnapshot: CameraStateSnapshot
)

enum class MediaKind { PHOTO, VIDEO }

data class MediaItem(
    val id: String,
    val filePath: String,
    val originalBackupPath: String? = null,
    val kind: MediaKind,
    val createdAtMs: Long,
    val filterNames: List<String> = emptyList(),
    val sceneLabel: String = "",
    val width: Int = 0,
    val height: Int = 0
)
