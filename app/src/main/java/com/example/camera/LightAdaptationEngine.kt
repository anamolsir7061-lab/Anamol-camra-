package com.example.camera

import android.graphics.Bitmap
import com.example.model.LightAdaptationState

class LightAdaptationEngine {

    private var smoothedLuma = 128f
    private var smoothedShadowRatio = 0f
    private var smoothedHighlightRatio = 0f

    fun analyzeFrame(bitmap: Bitmap, enabled: Boolean, sensitivity: Float): LightAdaptationState {
        val w = bitmap.width
        val h = bitmap.height
        val stepX = (w / 16).coerceAtLeast(1)
        val stepY = (h / 16).coerceAtLeast(1)

        var totalLuma = 0.0
        var count = 0
        var darkCount = 0
        var brightCount = 0

        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val px = bitmap.getPixel(x, y)
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF
                val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b
                totalLuma += luma
                if (luma < 45f) darkCount++
                if (luma > 232f) brightCount++
                count++
                x += stepX
            }
            y += stepY
        }

        val instantLuma = if (count > 0) (totalLuma / count).toFloat() else 128f
        val instantShadow = if (count > 0) darkCount.toFloat() / count else 0f
        val instantHighlight = if (count > 0) brightCount.toFloat() / count else 0f

        // Exponential moving average prevents sudden flicker
        val alpha = 0.12f
        smoothedLuma = smoothedLuma * (1f - alpha) + instantLuma * alpha
        smoothedShadowRatio = smoothedShadowRatio * (1f - alpha) + instantShadow * alpha
        smoothedHighlightRatio = smoothedHighlightRatio * (1f - alpha) + instantHighlight * alpha

        if (!enabled) {
            return LightAdaptationState(
                meanLuma = smoothedLuma,
                shadowRatio = smoothedShadowRatio,
                highlightClipRatio = smoothedHighlightRatio,
                conditionLabel = "Manual"
            )
        }

        val sens = sensitivity.coerceIn(0.2f, 2.0f)
        val lowLight = smoothedLuma < 72f || smoothedShadowRatio > 0.42f
        val brightLight = smoothedLuma > 188f || smoothedHighlightRatio > 0.28f

        var brightnessOffset = 0f
        var shadowLift = 0f
        var highlightRec = 0f
        var label = "Balanced"

        if (lowLight) {
            val deficit = ((78f - smoothedLuma) / 78f).coerceIn(0f, 1f)
            brightnessOffset = (deficit * 0.26f * sens).coerceIn(0f, 0.35f)
            shadowLift = (deficit * 0.42f * sens).coerceIn(0f, 0.55f)
            label = "Low Light • Night Boost"
        } else if (brightLight) {
            val excess = ((smoothedLuma - 180f) / 75f).coerceIn(0f, 1f)
            brightnessOffset = -(excess * 0.24f * sens).coerceIn(0f, 0.32f)
            highlightRec = -(excess * 0.45f * sens).coerceIn(0f, 0.55f)
            label = "Bright • Highlight Guard"
        }

        return LightAdaptationState(
            meanLuma = smoothedLuma,
            shadowRatio = smoothedShadowRatio,
            highlightClipRatio = smoothedHighlightRatio,
            adaptiveBrightnessOffset = brightnessOffset,
            adaptiveShadowLift = shadowLift,
            adaptiveHighlightRecovery = highlightRec,
            lowLightBoostActive = lowLight,
            brightLightProtectActive = brightLight,
            conditionLabel = label
        )
    }
}
