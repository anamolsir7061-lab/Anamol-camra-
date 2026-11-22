package com.example.model

enum class AiPersonality(val label: String, val systemHint: String) {
    PRO_PHOTOGRAPHER("Pro Photographer", "Crisp DSLR-focused guidance on framing, lighting, and exposure."),
    CREATIVE_DIRECTOR("Creative Director", "Editorial, expressive, bold color grades and mood framing."),
    MINIMALIST_COACH("Minimalist Coach", "Ultra-short 3-5 word hints only when helpful."),
    FRIENDLY_BUDDY("Friendly Buddy", "Upbeat Snapchat-style vibe with fun filter suggestions.")
}

enum class GuidanceStrictness(val label: String) {
    RELAXED("Relaxed"),
    BALANCED("Balanced"),
    STRICT("Strict")
}

enum class AutoAggressiveness(val label: String) {
    SUBTLE("Subtle"),
    BALANCED("Balanced"),
    FULL_AUTO("Full Auto + Shutter")
}

enum class PhotoStyle(val label: String) {
    NATURAL("Natural"),
    CINEMATIC("Cinematic"),
    VIBRANT("Vibrant"),
    MOODY("Moody")
}

enum class AiLanguage(val label: String, val promptInstruction: String) {
    ENGLISH("English", "Respond in concise English."),
    HINDI("Hindi", "Respond in clear Devanagari Hindi."),
    HINGLISH("Hinglish", "Respond in natural casual Hinglish (Hindi words in Roman script + English).")
}

enum class CaptureQuality(val label: String, val jpegQuality: Int) {
    STANDARD("Standard (Fast)", 82),
    HIGH("High (Balanced)", 92),
    ULTRA("Ultra (Max Detail)", 98)
}

enum class ThemeMode(val label: String) {
    DARK("Dark"),
    LIGHT("Light"),
    SYSTEM("System")
}

enum class OrientationLockMode(val label: String) {
    AUTO_ROTATE("Auto-Rotate"),
    LOCK_PORTRAIT("Lock Portrait"),
    LOCK_LANDSCAPE("Lock Landscape")
}

data class AppSettings(
    val geminiModel: String = "gemini-2.5-flash",
    val aiBrainEnabled: Boolean = true,
    val autoModeEnabled: Boolean = false,
    val aiIntervalSeconds: Float = 2.0f,
    val voiceOutputEnabled: Boolean = false,
    val aiPersonality: AiPersonality = AiPersonality.PRO_PHOTOGRAPHER,
    val guidanceStrictness: GuidanceStrictness = GuidanceStrictness.BALANCED,
    val autoAggressiveness: AutoAggressiveness = AutoAggressiveness.BALANCED,
    val preferredPhotoStyle: PhotoStyle = PhotoStyle.NATURAL,
    val aiLanguage: AiLanguage = AiLanguage.ENGLISH,
    val skinSmoothingLevel: Float = 0.25f,
    val greeneryBias: Float = 0.0f,
    val darknessReductionBias: Float = 0.0f,
    val portraitVintageLook: Boolean = false,
    val lightAdaptationEnabled: Boolean = true,
    val lightAdaptationSensitivity: Float = 1.0f,
    val captureQuality: CaptureQuality = CaptureQuality.HIGH,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val orientationLockMode: OrientationLockMode = OrientationLockMode.AUTO_ROTATE,
    val shareWatermarkEnabled: Boolean = true,
    val activeCameraConfigId: String = "natural_leica",
    val hapticsEnabled: Boolean = true,
    val privacyRedactLocation: Boolean = true,
    val sendPreviewFramesToAi: Boolean = true,
    val onboardingCompleted: Boolean = false
)

data class ChatMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val appliedUpdates: List<String> = emptyList(),
    val timestampMs: Long = System.currentTimeMillis()
)
