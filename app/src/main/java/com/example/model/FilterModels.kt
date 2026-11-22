package com.example.model

enum class FilterCategory(val label: String) {
    COLOR_GRADE("Color Grade"),
    VINTAGE("Vintage"),
    BW("B&W"),
    CINEMATIC("Cinematic"),
    NEON("Neon"),
    GLITCH("Glitch"),
    BEAUTY("Beauty"),
    AR_LENS("AR Lens"),
    NATURE("Nature"),
    DUOTONE("Duotone")
}

enum class FilterBlendMode(val label: String) {
    NORMAL("Normal"),
    MULTIPLY("Multiply"),
    SCREEN("Screen"),
    OVERLAY("Overlay"),
    SOFT_LIGHT("Soft Light")
}

enum class LutPreset(val label: String) {
    NONE("None"),
    TEAL_ORANGE("Teal & Orange"),
    KODAK_WARM("Kodak Warm"),
    FUJI_PRO("Fuji Pro"),
    CYBER_MAGENTA("Cyber Magenta"),
    NOIR_SILVER("Noir Silver")
}

enum class ArOverlayType(val label: String) {
    NONE("None"),
    NEON_VISOR("Neon Visor"),
    SUNGLASSES("Studio Shades"),
    CAT_EARS("Neko Ears"),
    CROWN("Royal Crown"),
    HEART_BLUSH("Heart Blush"),
    HALO("Angel Halo"),
    CYBER_HUD("Cyber Face HUD")
}

enum class ArAnchorPoint {
    EYES,
    FOREHEAD,
    CHEEKS,
    FULL_FACE
}

enum class ArAnimation {
    NONE,
    PULSE,
    FLOAT,
    SPARKLE
}

data class ArOverlayConfig(
    val type: ArOverlayType = ArOverlayType.NONE,
    val anchor: ArAnchorPoint = ArAnchorPoint.FULL_FACE,
    val scale: Float = 1.0f,
    val offsetY: Float = 0.0f,
    val tintHex: String = "#00E5FF",
    val animation: ArAnimation = ArAnimation.NONE
)

data class FilterAdjustments(
    val brightness: Float = 0.0f,      // -1.0 .. 1.0
    val contrast: Float = 1.0f,        // 0.2 .. 2.5
    val saturation: Float = 1.0f,      // 0.0 .. 2.5
    val hue: Float = 0.0f,             // -180 .. 180
    val temperature: Float = 0.0f,     // -1.0 .. 1.0
    val tint: Float = 0.0f,            // -0.5 .. 0.5
    val greenery: Float = 0.0f,        // -1.0 .. 1.0
    val vibrance: Float = 0.0f,        // -0.5 .. 1.0
    val blur: Float = 0.0f,            // 0.0 .. 1.0
    val grain: Float = 0.0f,           // 0.0 .. 1.0
    val vignette: Float = 0.0f,        // 0.0 .. 1.0
    val skinSmooth: Float = 0.0f,      // 0.0 .. 1.0
    val highlights: Float = 0.0f,      // -1.0 .. 1.0
    val shadows: Float = 0.0f,         // -1.0 .. 1.0
    val blackLevel: Float = 0.0f,      // -0.2 .. 0.2
    val toneMappingGamma: Float = 1.0f,// 0.6 .. 1.6
    val hdrPlusStrength: Float = 0.0f, // 0.0 .. 1.5
    val sharpness: Float = 0.0f,       // 0.0 .. 1.5
    val lumaNoiseReduction: Float = 0.0f, // 0.0 .. 1.0
    val rgbGainR: Float = 1.0f,        // 0.6 .. 1.4
    val rgbGainG: Float = 1.0f,        // 0.6 .. 1.4
    val rgbGainB: Float = 1.0f,        // 0.6 .. 1.4
    val glitch: Float = 0.0f,          // 0.0 .. 1.0
    val duotonePrimary: String? = null,
    val duotoneSecondary: String? = null,
    val blendMode: FilterBlendMode = FilterBlendMode.NORMAL,
    val lutPreset: LutPreset = LutPreset.NONE
)

data class CameraFilter(
    val id: String,
    val name: String,
    val icon: String,
    val category: FilterCategory,
    val author: String = "Anamol Studio",
    val description: String = "",
    val adjustments: FilterAdjustments = FilterAdjustments(),
    val arOverlay: ArOverlayConfig = ArOverlayConfig(),
    val isBuiltIn: Boolean = false,
    val isFavorite: Boolean = false
)

data class ActiveFilterLayer(
    val filter: CameraFilter,
    val intensity: Float = 1.0f,
    val blendModeOverride: FilterBlendMode? = null
)

sealed interface XmlValidationResult {
    data class Valid(val filter: CameraFilter, val warnings: List<String> = emptyList()) : XmlValidationResult
    data class Invalid(val errors: List<String>) : XmlValidationResult
}
