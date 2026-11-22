package com.example.model

enum class AwbBias(val label: String) {
    AUTO("Auto"),
    NATURAL("Natural"),
    DAYLIGHT("Daylight"),
    WARM_TUNGSTEN("Warm"),
    COOL_SHADE("Cool")
}

data class GCamModeOverride(
    val exposureCompensation: Float? = null,
    val saturation: Float? = null,
    val contrast: Float? = null,
    val sharpness: Float? = null,
    val nightSightStrength: Float? = null,
    val hdrPlusStrength: Float? = null,
    val awbBias: AwbBias? = null
)

data class GCamProcessingParams(
    val hdrPlusStrength: Float = 0.65f,      // 0.0 .. 1.5
    val toneMappingGamma: Float = 1.0f,      // 0.6 .. 1.6
    val shadows: Float = 0.05f,              // -1.0 .. 1.0
    val highlights: Float = -0.05f,          // -1.0 .. 1.0
    val blackLevel: Float = 0.0f,            // -0.2 .. 0.2
    val contrast: Float = 1.02f,             // 0.5 .. 1.8
    val saturation: Float = 1.0f,            // 0.0 .. 2.0
    val vibrance: Float = 0.05f,             // -0.5 .. 1.0
    val sharpness: Float = 0.25f,            // 0.0 .. 1.5
    val lumaNoiseReduction: Float = 0.25f,   // 0.0 .. 1.0
    val chromaNoiseReduction: Float = 0.30f, // 0.0 .. 1.0
    val temperatureOffset: Float = 0.0f,     // -1.0 .. 1.0
    val tintOffset: Float = 0.0f,            // -0.5 .. 0.5
    val rgbGainR: Float = 1.0f,              // 0.6 .. 1.4
    val rgbGainG: Float = 1.0f,              // 0.6 .. 1.4
    val rgbGainB: Float = 1.0f,              // 0.6 .. 1.4
    val exposureCompensation: Float = 0.0f,  // -2.0 .. 2.0
    val nightSightStrength: Float = 0.35f,   // 0.0 .. 1.5
    val portraitBlurStrength: Float = 0.35f, // 0.0 .. 1.0
    val skinToneWarmth: Float = 0.05f,       // -0.5 .. 0.5
    val awbBias: AwbBias = AwbBias.AUTO,
    val vignette: Float = 0.0f,              // 0.0 .. 1.0
    val grain: Float = 0.0f                  // 0.0 .. 1.0
)

data class GCamCameraConfig(
    val id: String,
    val name: String,
    val icon: String = "📷",
    val author: String = "Anamol Studio",
    val description: String = "",
    val deviceProfile: String = "Pixel / Universal",
    val version: String = "8.5",
    val params: GCamProcessingParams = GCamProcessingParams(),
    val modeOverrides: Map<String, GCamModeOverride> = emptyMap(), // PHOTO, PORTRAIT, NIGHT, VIDEO, FRONT, WIDE
    val isBuiltIn: Boolean = false,
    val isFavorite: Boolean = false
)

sealed interface ConfigXmlValidationResult {
    data class Valid(
        val config: GCamCameraConfig,
        val warnings: List<String> = emptyList()
    ) : ConfigXmlValidationResult

    data class Invalid(
        val errors: List<String>,
        val warnings: List<String> = emptyList()
    ) : ConfigXmlValidationResult
}
