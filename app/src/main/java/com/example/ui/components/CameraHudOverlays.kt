package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.camera.HorizonState
import com.example.model.AiActionEntry
import com.example.model.AiGuidance
import com.example.model.AiSuggestion
import com.example.model.GridType
import com.example.model.GuidanceDirection
import com.example.model.QrScanResult
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMint
import kotlin.math.roundToInt

@Composable
fun ViewfinderGuidesOverlay(
    gridType: GridType,
    showHorizon: Boolean,
    horizon: HorizonState,
    aiGuidance: AiGuidance,
    focusPoint: Offset?,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        // Grid Lines
        when (gridType) {
            GridType.THIRDS -> {
                val c = Color.White.copy(alpha = 0.24f)
                drawLine(c, Offset(size.width / 3f, 0f), Offset(size.width / 3f, size.height), 1.5f)
                drawLine(c, Offset(size.width * 2f / 3f, 0f), Offset(size.width * 2f / 3f, size.height), 1.5f)
                drawLine(c, Offset(0f, size.height / 3f), Offset(size.width, size.height / 3f), 1.5f)
                drawLine(c, Offset(0f, size.height * 2f / 3f), Offset(size.width, size.height * 2f / 3f), 1.5f)
            }
            GridType.GOLDEN -> {
                val c = Color(0xFFF59E0B).copy(alpha = 0.28f)
                val g1 = 0.382f
                val g2 = 0.618f
                drawLine(c, Offset(size.width * g1, 0f), Offset(size.width * g1, size.height), 1.5f)
                drawLine(c, Offset(size.width * g2, 0f), Offset(size.width * g2, size.height), 1.5f)
                drawLine(c, Offset(0f, size.height * g1), Offset(size.width, size.height * g1), 1.5f)
                drawLine(c, Offset(0f, size.height * g2), Offset(size.width, size.height * g2), 1.5f)
            }
            GridType.CENTER_BOX -> {
                val side = minOf(size.width, size.height) * 0.78f
                drawRect(
                    color = Color.White.copy(alpha = 0.26f),
                    topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2f),
                    size = Size(side, side),
                    style = Stroke(width = 2f)
                )
            }
            GridType.NONE -> Unit
        }

        // Horizon leveler in center
        if (showHorizon) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val levelColor = if (horizon.isLevel) EmeraldMint else Color.White.copy(alpha = 0.72f)
            drawLine(
                color = Color.White.copy(alpha = 0.25f),
                start = Offset(center.x - 95f, center.y),
                end = Offset(center.x - 42f, center.y),
                strokeWidth = 2.5f
            )
            drawLine(
                color = Color.White.copy(alpha = 0.25f),
                start = Offset(center.x + 42f, center.y),
                end = Offset(center.x + 95f, center.y),
                strokeWidth = 2.5f
            )
            rotate(degrees = -horizon.rollDegrees.coerceIn(-45f, 45f), pivot = center) {
                drawLine(
                    color = levelColor,
                    start = Offset(center.x - 40f, center.y),
                    end = Offset(center.x + 40f, center.y),
                    strokeWidth = if (horizon.isLevel) 4.5f else 3f
                )
            }
        }

        // AI Ideal Framing Box
        aiGuidance.idealFramingBox?.let { box ->
            val dash = PathEffect.dashPathEffect(floatArrayOf(16f, 12f), 0f)
            drawRoundRect(
                color = ElectricCyan.copy(alpha = 0.72f),
                topLeft = Offset(box.left * size.width, box.top * size.height),
                size = Size(box.width() * size.width, box.height() * size.height),
                cornerRadius = CornerRadius(24f, 24f),
                style = Stroke(width = 3f, pathEffect = dash)
            )
        }

        // Tap-to-focus reticle
        focusPoint?.let { pt ->
            drawCircle(
                color = ElectricCyan,
                radius = 48f,
                center = pt,
                style = Stroke(width = 3.5f)
            )
            drawCircle(
                color = ElectricCyan.copy(alpha = 0.25f),
                radius = 16f,
                center = pt
            )
        }
    }
}

@Composable
fun AiCoachAndLogHud(
    aiGuidance: AiGuidance,
    suggestions: List<AiSuggestion>,
    actionLog: List<AiActionEntry>,
    isAnalyzing: Boolean,
    autoModeEnabled: Boolean,
    horizonRoll: Float,
    aiStatusNote: String?,
    onApplySuggestion: (AiSuggestion) -> Unit,
    onUndoAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var logExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Scene Recognition & Composition Guidance Pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.58f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.border(1.dp, if (isAnalyzing) ElectricCyan else Color.White.copy(alpha = 0.16f), RoundedCornerShape(20.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = "AI Scene Brain",
                        tint = if (autoModeEnabled) EmeraldMint else ElectricCyan,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = aiGuidance.sceneLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        maxLines = 1
                    )
                    Text(
                        text = "• ${horizonRoll.roundToInt()}°",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.68f)
                    )
                }
            }

            if (actionLog.isNotEmpty()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.62f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { logExpanded = !logExpanded }
                        .testTag("ai_log_toggle")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "AI Log (${actionLog.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = EmeraldMint
                        )
                        Icon(
                            imageVector = if (logExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = "Toggle AI Log",
                            tint = EmeraldMint,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Visual Composition Instruction with Direction Arrow
        if (aiGuidance.instruction.isNotBlank()) {
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.border(1.dp, ElectricCyan.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val arrowIcon = when (aiGuidance.direction) {
                        GuidanceDirection.LEFT -> Icons.AutoMirrored.Rounded.ArrowBack
                        GuidanceDirection.RIGHT -> Icons.AutoMirrored.Rounded.ArrowForward
                        GuidanceDirection.UP, GuidanceDirection.TILT_UP -> Icons.Rounded.ArrowUpward
                        GuidanceDirection.DOWN, GuidanceDirection.TILT_DOWN -> Icons.Rounded.ArrowDownward
                        GuidanceDirection.CLOSER -> Icons.Rounded.ZoomIn
                        GuidanceDirection.BACK -> Icons.Rounded.ZoomOut
                        GuidanceDirection.NONE -> null
                    }
                    if (arrowIcon != null) {
                        Icon(
                            imageVector = arrowIcon,
                            contentDescription = "Framing direction",
                            tint = ElectricCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = aiGuidance.instruction,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // AI Status / Error Note (quota, offline, key invalid)
        if (!aiStatusNote.isNullOrBlank()) {
            Surface(
                color = Color(0xFF3F1D2B).copy(alpha = 0.88f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = aiStatusNote,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFDA4AF),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Tappable AI Suggestion Chips when Auto Mode is OFF
        if (!autoModeEnabled && suggestions.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(suggestions, key = { it.id }) { item ->
                    Surface(
                        color = ElectricCyan.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .border(1.dp, ElectricCyan.copy(alpha = 0.7f), RoundedCornerShape(18.dp))
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onApplySuggestion(item) }
                            .testTag("ai_suggestion_${item.toolName}")
                    ) {
                        Text(
                            text = "Tap: ${item.label}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // Expandable AI Actions Log with Undo
        AnimatedVisibility(visible = logExpanded && actionLog.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
            Surface(
                color = Color(0xFF0E1420).copy(alpha = 0.92f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    actionLog.take(4).forEach { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = entry.summary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Surface(
                                color = Color.White.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onUndoAction(entry.id) }
                                    .testTag("undo_ai_${entry.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "Undo AI change", tint = ElectricCyan, modifier = Modifier.size(14.dp))
                                    Text("Undo", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QrScannerBanner(
    qr: QrScanResult,
    onCopy: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF0E1420).copy(alpha = 0.92f),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, EmeraldMint, RoundedCornerShape(18.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(EmeraldMint.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.QrCode2, contentDescription = "QR Detected", tint = EmeraldMint)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(qr.formatLabel, style = MaterialTheme.typography.labelSmall, color = EmeraldMint, fontWeight = FontWeight.Bold)
                Text(qr.rawValue, style = MaterialTheme.typography.bodyMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { onCopy(qr.rawValue) }) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy QR", tint = Color.White)
            }
            if (qr.isUrl) {
                IconButton(onClick = { onOpenUrl(qr.rawValue) }) {
                    Icon(Icons.Rounded.OpenInNew, contentDescription = "Open Link", tint = ElectricCyan)
                }
            }
        }
    }
}
