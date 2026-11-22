package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoFixOff
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.camera.FilterBitmapProcessor
import com.example.model.MediaItem
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMint
import com.example.viewmodel.GalleryViewModel

private enum class EditorTool { DRAW, TEXT, STICKER, ERASER, CROP }

private data class DrawStroke(
    val pointsNormalized: List<Offset>,
    val color: Color,
    val widthPx: Float
)

private data class StickerStamp(
    val text: String,
    val normPos: Offset,
    val isHeadlineText: Boolean,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoEditorScreen(
    item: MediaItem,
    galleryViewModel: GalleryViewModel,
    onBack: () -> Unit
) {
    var workingBitmap by remember(item.filePath) {
        mutableStateOf(BitmapFactory.decodeFile(item.filePath))
    }
    var activeTool by remember { mutableStateOf(EditorTool.DRAW) }
    var brushColor by remember { mutableStateOf(ElectricCyan) }
    var textDraft by remember { mutableStateOf("") }
    var cropAspect by remember { mutableStateOf<Float?>(null) } // null = original

    val strokes = remember { mutableStateListOf<DrawStroke>() }
    val currentStroke = remember { mutableStateListOf<Offset>() }
    val stamps = remember { mutableStateListOf<StickerStamp>() }

    val bmp = workingBitmap ?: run {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Unable to load photo")
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Photo Studio") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex)
                        else if (stamps.isNotEmpty()) stamps.removeAt(stamps.lastIndex)
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "Undo")
                    }
                    IconButton(
                        onClick = {
                            val rendered = renderEditsOntoBitmap(bmp, strokes, stamps, cropAspect)
                            galleryViewModel.saveEditedBitmap(item, rendered, "Studio Edit")
                            onBack()
                        },
                        modifier = Modifier.testTag("save_editor_btn")
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = "Save Edit", tint = EmeraldMint)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(12.dp)
                    .background(Color.Black, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                val ratio = cropAspect ?: (bmp.width.toFloat() / bmp.height.toFloat().coerceAtLeast(1f))
                Box(
                    modifier = Modifier
                        .aspectRatio(ratio)
                        .clip(RoundedCornerShape(12.dp))
                        .pointerInput(activeTool, brushColor, textDraft) {
                            if (activeTool == EditorTool.DRAW) {
                                detectDragGestures(
                                    onDragStart = { start ->
                                        currentStroke.clear()
                                        currentStroke.add(Offset(start.x / size.width, start.y / size.height))
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        currentStroke.add(Offset(change.position.x / size.width, change.position.y / size.height))
                                    },
                                    onDragEnd = {
                                        if (currentStroke.size > 1) {
                                            strokes.add(DrawStroke(currentStroke.toList(), brushColor, 10f))
                                        }
                                        currentStroke.clear()
                                    }
                                )
                            } else {
                                detectTapGestures { pos ->
                                    val nx = (pos.x / size.width).coerceIn(0.05f, 0.95f)
                                    val ny = (pos.y / size.height).coerceIn(0.05f, 0.95f)
                                    when (activeTool) {
                                        EditorTool.ERASER -> {
                                            workingBitmap = FilterBitmapProcessor.applySpotEraser(bmp, nx, ny)
                                        }
                                        EditorTool.TEXT -> {
                                            if (textDraft.isNotBlank()) {
                                                stamps.add(StickerStamp(textDraft, Offset(nx, ny), true, brushColor))
                                            }
                                        }
                                        EditorTool.STICKER -> {
                                            stamps.add(StickerStamp("✨", Offset(nx, ny), false, brushColor))
                                        }
                                        else -> Unit
                                    }
                                }
                            }
                        }
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Editing canvas",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        fun drawList(pts: List<Offset>, c: Color, w: Float) {
                            if (pts.size < 2) return
                            val path = Path().apply {
                                moveTo(pts.first().x * size.width, pts.first().y * size.height)
                                pts.drop(1).forEach { lineTo(it.x * size.width, it.y * size.height) }
                            }
                            drawPath(path, c, style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                        strokes.forEach { drawList(it.pointsNormalized, it.color, it.widthPx) }
                        if (currentStroke.isNotEmpty()) drawList(currentStroke, brushColor, 10f)
                    }

                    // Render sticker & text preview stamps
                    stamps.forEach { stamp ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    start = (stamp.normPos.x * 220).dp,
                                    top = (stamp.normPos.y * 280).dp
                                )
                        ) {
                            Text(
                                text = stamp.text,
                                fontSize = if (stamp.isHeadlineText) 20.sp else 28.sp,
                                color = stamp.color
                            )
                        }
                    }
                }
            }

            // Tool-specific subbar
            when (activeTool) {
                EditorTool.DRAW -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Brush:", style = MaterialTheme.typography.labelMedium)
                        listOf(ElectricCyan, EmeraldMint, Color.White, Color(0xFFF59E0B), Color(0xFFFB7185)).forEach { c ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(2.dp, if (brushColor == c) Color.White else Color.Transparent, CircleShape)
                                    .clickable { brushColor = c }
                            )
                        }
                    }
                }
                EditorTool.TEXT -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = textDraft,
                            onValueChange = { textDraft = it },
                            placeholder = { Text("Type caption, then tap photo") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                EditorTool.STICKER -> {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(listOf("🔥", "✨", "🌿", "⚡", "🎬", "❤️", "😎", "👑")) { emoji ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clickable { stamps.add(StickerStamp(emoji, Offset(0.5f, 0.5f), false, Color.White)) }
                            ) {
                                Box(contentAlignment = Alignment.Center) { Text(emoji, fontSize = 22.sp) }
                            }
                        }
                    }
                }
                EditorTool.ERASER -> {
                    Text(
                        "Tap any blemish or small object on the photo to heal & erase it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
                EditorTool.CROP -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Orig" to null, "1:1" to 1f, "4:5" to 0.8f, "16:9" to (16f / 9f)).forEach { (lbl, ar) ->
                            Button(
                                onClick = { cropAspect = ar },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (cropAspect == ar) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (cropAspect == ar) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            ) { Text(lbl) }
                        }
                    }
                }
            }

            // Bottom tool tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                EditorTab(Icons.Rounded.Brush, "Draw", activeTool == EditorTool.DRAW) { activeTool = EditorTool.DRAW }
                EditorTab(Icons.Rounded.TextFields, "Text", activeTool == EditorTool.TEXT) { activeTool = EditorTool.TEXT }
                EditorTab(Icons.Rounded.EmojiEmotions, "Sticker", activeTool == EditorTool.STICKER) { activeTool = EditorTool.STICKER }
                EditorTab(Icons.Rounded.AutoFixOff, "Eraser", activeTool == EditorTool.ERASER) { activeTool = EditorTool.ERASER }
                EditorTab(Icons.Rounded.Crop, "Auto-Crop", activeTool == EditorTool.CROP) { activeTool = EditorTool.CROP }
            }
        }
    }
}

@Composable
private fun EditorTab(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val activeColor = MaterialTheme.colorScheme.primary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = label, tint = if (selected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun renderEditsOntoBitmap(
    source: Bitmap,
    strokes: List<DrawStroke>,
    stamps: List<StickerStamp>,
    cropAspect: Float?
): Bitmap {
    val base = if (cropAspect != null) {
        val srcAspect = source.width.toFloat() / source.height.toFloat()
        if (srcAspect > cropAspect) {
            val newW = (source.height * cropAspect).toInt().coerceAtLeast(1)
            val x = (source.width - newW) / 2
            Bitmap.createBitmap(source, x, 0, newW, source.height)
        } else {
            val newH = (source.width / cropAspect).toInt().coerceAtLeast(1)
            val y = (source.height - newH) / 2
            Bitmap.createBitmap(source, 0, y, source.width, newH)
        }
    } else {
        source
    }

    val out = base.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = AndroidCanvas(out)
    val w = out.width.toFloat()
    val h = out.height.toFloat()

    strokes.forEach { stroke ->
        if (stroke.pointsNormalized.size >= 2) {
            val p = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                color = stroke.color.toArgb()
                style = AndroidPaint.Style.STROKE
                strokeWidth = stroke.widthPx * (w / 400f)
                strokeCap = AndroidPaint.Cap.ROUND
                strokeJoin = AndroidPaint.Join.ROUND
            }
            val path = AndroidPath().apply {
                moveTo(stroke.pointsNormalized.first().x * w, stroke.pointsNormalized.first().y * h)
                stroke.pointsNormalized.drop(1).forEach { lineTo(it.x * w, it.y * h) }
            }
            canvas.drawPath(path, p)
        }
    }

    stamps.forEach { stamp ->
        val p = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = stamp.color.toArgb()
            textSize = (if (stamp.isHeadlineText) 26f else 48f) * (w / 400f)
        }
        canvas.drawText(stamp.text, stamp.normPos.x * w, stamp.normPos.y * h, p)
    }

    return out
}
