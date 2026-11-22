package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ThemeMode
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianVoid
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Custom vector mark for Anamol Camera:
 * Minimal obsidian aperture ring + cyan-to-emerald gradient bezel + crisp 4-point spark.
 */
@Composable
fun AnamolLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f
        val r = minOf(w, h) * 0.46f

        val gradient = Brush.linearGradient(
            colors = listOf(ElectricCyan, EmeraldMint),
            start = Offset(cx - r, cy - r),
            end = Offset(cx + r, cy + r)
        )

        // Outer lens barrel
        drawCircle(
            color = ObsidianSurface,
            radius = r,
            center = Offset(cx, cy)
        )
        drawCircle(
            brush = gradient,
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = r * 0.14f)
        )

        // Inner optical glass ring
        drawCircle(
            color = ElectricCyan.copy(alpha = 0.22f),
            radius = r * 0.68f,
            center = Offset(cx, cy),
            style = Stroke(width = r * 0.06f)
        )
        drawCircle(
            color = Color(0xFF070B14),
            radius = r * 0.56f,
            center = Offset(cx, cy)
        )

        // Subtle lens reflection arc
        drawArc(
            color = Color.White.copy(alpha = 0.42f),
            startAngle = 205f,
            sweepAngle = 55f,
            useCenter = false,
            topLeft = Offset(cx - r * 0.46f, cy - r * 0.46f),
            size = androidx.compose.ui.geometry.Size(r * 0.92f, r * 0.92f),
            style = Stroke(width = r * 0.07f, cap = StrokeCap.Round)
        )

        // 4-point AI Spark in upper-right optical glass
        val sx = cx + r * 0.34f
        val sy = cy - r * 0.34f
        val sr = r * 0.34f
        val inner = sr * 0.28f
        val sparkPath = Path().apply {
            moveTo(sx, sy - sr)
            lineTo(sx + inner, sy - inner)
            lineTo(sx + sr, sy)
            lineTo(sx + inner, sy + inner)
            lineTo(sx, sy + sr)
            lineTo(sx - inner, sy + inner)
            lineTo(sx - sr, sy)
            lineTo(sx - inner, sy - inner)
            close()
        }
        drawPath(path = sparkPath, brush = gradient)
    }
}

@Composable
fun AnamolAnimatedSplashOverlay(
    visible: Boolean,
    onFinished: () -> Unit
) {
    val logoScale = remember { Animatable(0.62f) }
    val logoRotate = remember { Animatable(-24f) }
    val textAlpha = remember { Animatable(0f) }

    LaunchedEffect(visible) {
        if (visible) {
            launch {
                logoScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            }
            launch {
                logoRotate.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 620, easing = FastOutSlowInEasing)
                )
            }
            launch {
                delay(180)
                textAlpha.animateTo(1f, animationSpec = tween(durationMillis = 360))
            }
            delay(1050)
            onFinished()
        }
    }

    AnimatedVisibility(
        visible = visible,
        exit = fadeOut(animationSpec = tween(260))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF0E1B2E), ObsidianVoid),
                        radius = 1200f
                    )
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onFinished
                )
                .testTag("anamol_splash_overlay"),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnamolLogoMark(
                    size = 96.dp,
                    modifier = Modifier
                        .scale(logoScale.value)
                        .rotate(logoRotate.value)
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "Anamol Camera",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.alpha(textAlpha.value)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "COMPUTATIONAL OPTICS • GCAM XML • AI BRAIN",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan,
                    letterSpacing = 1.4.sp,
                    modifier = Modifier.alpha(textAlpha.value)
                )
            }
        }
    }
}

@Composable
fun AnamolGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 18.dp,
    hudOverlay: Boolean = false,
    content: @Composable () -> Unit
) {
    val bg = if (hudOverlay) {
        Color(0xFF0A101B).copy(alpha = 0.68f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
    }
    val stroke = if (hudOverlay) {
        Color.White.copy(alpha = 0.16f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(cornerRadius),
        border = BorderStroke(1.dp, stroke),
        modifier = modifier
    ) {
        content()
    }
}

@Composable
fun ThemeQuickToggleChip(
    themeMode: ThemeMode,
    onToggle: () -> Unit,
    hudOverlay: Boolean = true,
    modifier: Modifier = Modifier
) {
    val icon = when (themeMode) {
        ThemeMode.DARK -> Icons.Rounded.DarkMode
        ThemeMode.LIGHT -> Icons.Rounded.LightMode
        ThemeMode.SYSTEM -> Icons.Rounded.BrightnessAuto
    }
    val bg = if (hudOverlay) Color.Black.copy(alpha = 0.56f) else MaterialTheme.colorScheme.surfaceVariant
    val tint = if (hudOverlay) ElectricCyan else MaterialTheme.colorScheme.primary
    val textColor = if (hudOverlay) Color.White else MaterialTheme.colorScheme.onSurface

    Surface(
        color = bg,
        shape = CircleShape,
        border = BorderStroke(1.dp, tint.copy(alpha = 0.45f)),
        modifier = modifier
            .clip(CircleShape)
            .clickable(onClick = onToggle)
            .testTag("theme_quick_toggle")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = "Theme: ${themeMode.label}", tint = tint, modifier = Modifier.size(16.dp))
            Text(themeMode.label, style = MaterialTheme.typography.labelSmall, color = textColor)
        }
    }
}
