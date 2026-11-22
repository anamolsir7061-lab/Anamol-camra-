package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.model.ActiveFilterLayer
import com.example.model.ArAnimation
import com.example.model.ArOverlayType
import com.example.model.FaceTelemetry
import com.example.model.FilterAdjustments
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun FilterEffectAndArOverlay(
    adjustments: FilterAdjustments,
    activeLayers: List<ActiveFilterLayer>,
    faces: List<FaceTelemetry>,
    beforeAfterCompare: Boolean,
    compareSplit: Float,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "ar_anim")
    val pulse by infinite.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val floatPhase by infinite.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse),
        label = "float"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val clipLeft = if (beforeAfterCompare) size.width * compareSplit else 0f

        clipRect(left = clipLeft, top = 0f, right = size.width, bottom = size.height) {
            // Duotone wash
            if (adjustments.duotonePrimary != null && adjustments.duotoneSecondary != null) {
                val c1 = parseHexColor(adjustments.duotonePrimary, Color(0xFF1E1B4B)).copy(alpha = 0.24f)
                val c2 = parseHexColor(adjustments.duotoneSecondary, Color(0xFF00E5FF)).copy(alpha = 0.22f)
                drawRect(
                    brush = Brush.linearGradient(listOf(c1, c2)),
                    blendMode = BlendMode.Overlay
                )
            }

            // Warm/Cool temperature tint fallback for devices where RenderEffect isn't active
            if (kotlin.math.abs(adjustments.temperature) > 0.05f) {
                val tint = if (adjustments.temperature > 0) {
                    Color(0xFFF59E0B).copy(alpha = (adjustments.temperature * 0.14f).coerceIn(0f, 0.2f))
                } else {
                    Color(0xFF06B6D4).copy(alpha = (-adjustments.temperature * 0.14f).coerceIn(0f, 0.2f))
                }
                drawRect(color = tint, blendMode = BlendMode.Softlight)
            }

            // Greenery subtle boost tint
            if (adjustments.greenery > 0.12f) {
                drawRect(
                    color = Color(0xFF10B981).copy(alpha = (adjustments.greenery * 0.10f).coerceIn(0f, 0.15f)),
                    blendMode = BlendMode.Softlight
                )
            }

            // Film grain speckles
            if (adjustments.grain > 0.06f) {
                val count = (adjustments.grain * 260).toInt().coerceIn(10, 260)
                val rng = Random((floatPhase * 1000).toInt())
                for (i in 0 until count) {
                    val gx = rng.nextFloat() * size.width
                    val gy = rng.nextFloat() * size.height
                    val alpha = adjustments.grain * 0.22f
                    drawCircle(
                        color = if (i % 2 == 0) Color.White.copy(alpha = alpha) else Color.Black.copy(alpha = alpha),
                        radius = 1.6f,
                        center = Offset(gx, gy)
                    )
                }
            }

            // Glitch CRT scanlines & RGB bars
            if (adjustments.glitch > 0.06f) {
                val bars = (adjustments.glitch * 10).toInt().coerceIn(2, 12)
                val rng = Random((pulse * 500).toInt())
                for (i in 0 until bars) {
                    val gy = rng.nextFloat() * size.height
                    val gh = 4f + rng.nextFloat() * 14f
                    drawRect(
                        color = if (i % 2 == 0) Color(0xFF00E5FF).copy(alpha = 0.20f) else Color(0xFFEC4899).copy(alpha = 0.20f),
                        topLeft = Offset(0f, gy),
                        size = Size(size.width, gh)
                    )
                }
            }

            // Vignette
            if (adjustments.vignette > 0.02f) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = (adjustments.vignette * 0.78f).coerceIn(0f, 0.85f))
                        ),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = maxOf(size.width, size.height) * 0.72f
                    )
                )
            }

            // Face-anchored AR lenses
            for (layer in activeLayers) {
                val ar = layer.filter.arOverlay
                if (ar.type == ArOverlayType.NONE) continue
                val tint = parseHexColor(ar.tintHex, Color(0xFF00E5FF))
                val animScale = when (ar.animation) {
                    ArAnimation.PULSE -> pulse
                    else -> 1f
                }
                val yBob = when (ar.animation) {
                    ArAnimation.FLOAT -> floatPhase * 12f
                    else -> 0f
                }

                for (face in faces) {
                    val fx = face.boundsNormalized.centerX() * size.width
                    val fy = face.boundsNormalized.centerY() * size.height + yBob
                    val fw = face.boundsNormalized.width() * size.width * ar.scale * animScale
                    val fh = face.boundsNormalized.height() * size.height * ar.scale * animScale

                    rotate(degrees = -face.headRollDeg, pivot = Offset(fx, fy)) {
                        when (ar.type) {
                            ArOverlayType.NEON_VISOR, ArOverlayType.SUNGLASSES -> {
                                val eyeY = ((face.leftEye?.y ?: (face.boundsNormalized.top + 0.35f * face.boundsNormalized.height())) +
                                    (face.rightEye?.y ?: (face.boundsNormalized.top + 0.35f * face.boundsNormalized.height()))) * 0.5f * size.height + yBob
                                val topLeft = Offset(fx - fw * 0.43f, eyeY - fh * 0.12f)
                                val visorSize = Size(fw * 0.86f, fh * 0.24f)
                                drawRoundRect(
                                    color = tint.copy(alpha = if (ar.type == ArOverlayType.SUNGLASSES) 0.78f else 0.32f),
                                    topLeft = topLeft,
                                    size = visorSize,
                                    cornerRadius = CornerRadius(fh * 0.08f, fh * 0.08f)
                                )
                                drawRoundRect(
                                    color = tint,
                                    topLeft = topLeft,
                                    size = visorSize,
                                    cornerRadius = CornerRadius(fh * 0.08f, fh * 0.08f),
                                    style = Stroke(width = 5f)
                                )
                            }
                            ArOverlayType.CAT_EARS -> {
                                val topY = face.boundsNormalized.top * size.height + yBob
                                val leftEar = Path().apply {
                                    moveTo(fx - fw * 0.36f, topY)
                                    lineTo(fx - fw * 0.24f, topY - fh * 0.28f)
                                    lineTo(fx - fw * 0.06f, topY - fh * 0.04f)
                                    close()
                                }
                                val rightEar = Path().apply {
                                    moveTo(fx + fw * 0.36f, topY)
                                    lineTo(fx + fw * 0.24f, topY - fh * 0.28f)
                                    lineTo(fx + fw * 0.06f, topY - fh * 0.04f)
                                    close()
                                }
                                drawPath(leftEar, tint.copy(alpha = 0.38f))
                                drawPath(leftEar, tint, style = Stroke(width = 5f))
                                drawPath(rightEar, tint.copy(alpha = 0.38f))
                                drawPath(rightEar, tint, style = Stroke(width = 5f))
                            }
                            ArOverlayType.HALO -> {
                                val haloY = face.boundsNormalized.top * size.height - fh * 0.18f + yBob
                                drawOval(
                                    color = tint.copy(alpha = 0.28f),
                                    topLeft = Offset(fx - fw * 0.38f, haloY - fh * 0.07f),
                                    size = Size(fw * 0.76f, fh * 0.14f)
                                )
                                drawOval(
                                    color = tint,
                                    topLeft = Offset(fx - fw * 0.38f, haloY - fh * 0.07f),
                                    size = Size(fw * 0.76f, fh * 0.14f),
                                    style = Stroke(width = 6f)
                                )
                            }
                            ArOverlayType.HEART_BLUSH -> {
                                val r = fw * 0.08f
                                face.leftCheek?.let {
                                    drawCircle(tint.copy(alpha = 0.42f), radius = r, center = Offset(it.x * size.width, it.y * size.height + yBob))
                                }
                                face.rightCheek?.let {
                                    drawCircle(tint.copy(alpha = 0.42f), radius = r, center = Offset(it.x * size.width, it.y * size.height + yBob))
                                }
                            }
                            ArOverlayType.CROWN -> {
                                val topY = face.boundsNormalized.top * size.height - fh * 0.04f + yBob
                                val crown = Path().apply {
                                    moveTo(fx - fw * 0.30f, topY)
                                    lineTo(fx - fw * 0.32f, topY - fh * 0.22f)
                                    lineTo(fx - fw * 0.12f, topY - fh * 0.10f)
                                    lineTo(fx, topY - fh * 0.26f)
                                    lineTo(fx + fw * 0.12f, topY - fh * 0.10f)
                                    lineTo(fx + fw * 0.32f, topY - fh * 0.22f)
                                    lineTo(fx + fw * 0.30f, topY)
                                    close()
                                }
                                drawPath(crown, tint.copy(alpha = 0.35f))
                                drawPath(crown, tint, style = Stroke(width = 5f))
                            }
                            ArOverlayType.CYBER_HUD -> {
                                val boxLeft = fx - fw * 0.52f
                                val boxTop = fy - fh * 0.52f
                                drawRect(
                                    color = tint.copy(alpha = 0.7f),
                                    topLeft = Offset(boxLeft, boxTop),
                                    size = Size(fw * 1.04f, fh * 1.04f),
                                    style = Stroke(width = 3f)
                                )
                                drawLine(
                                    color = tint,
                                    start = Offset(boxLeft, fy),
                                    end = Offset(boxLeft + fw * 0.12f, fy),
                                    strokeWidth = 3f
                                )
                                drawLine(
                                    color = tint,
                                    start = Offset(boxLeft + fw * 0.92f, fy),
                                    end = Offset(boxLeft + fw * 1.04f, fy),
                                    strokeWidth = 3f
                                )
                            }
                            ArOverlayType.NONE -> Unit
                        }
                    }
                }
            }
        }

        // Before / After vertical divider line
        if (beforeAfterCompare) {
            val sx = size.width * compareSplit
            drawLine(
                color = Color.White,
                start = Offset(sx, 0f),
                end = Offset(sx, size.height),
                strokeWidth = 3f
            )
            drawCircle(
                color = Color(0xFF00E5FF),
                radius = 18f,
                center = Offset(sx, size.height * 0.5f)
            )
        }
    }
}

private fun parseHexColor(hex: String, fallback: Color): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        fallback
    }
}
