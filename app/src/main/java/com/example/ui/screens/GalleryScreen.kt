package com.example.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.BrandingWatermark
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Hd
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.example.model.MediaItem
import com.example.model.MediaKind
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMint
import com.example.viewmodel.GalleryViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    onBack: () -> Unit,
    onOpenEditor: (MediaItem) -> Unit,
    onOpenCollage: () -> Unit
) {
    val context = LocalContext.current
    val items by viewModel.items.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val watermarkEnabled by viewModel.shareWatermarkEnabled.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val accent = MaterialTheme.colorScheme.primary

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.clearStatus()
        }
    }

    val selected = uiState.selectedItem

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selected == null) "Anamol Gallery (${items.size})"
                        else selected.sceneLabel.ifBlank { "Captured Media" }
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (selected != null) viewModel.selectItem(null) else onBack()
                        },
                        modifier = Modifier.testTag("gallery_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selected == null && items.count { it.kind == MediaKind.PHOTO } >= 2) {
                        TextButton(onClick = onOpenCollage, modifier = Modifier.testTag("open_collage_btn")) {
                            Icon(Icons.Rounded.Collections, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Collage", color = accent)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        if (selected == null) {
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, tint = accent, modifier = Modifier.size(56.dp))
                        Text("No captures yet", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Shoot a photo or video to edit, enhance, or create collages.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(116.dp),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().padding(padding)
                ) {
                    items(items, key = { it.id }) { item ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(0.85f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { viewModel.selectItem(item) }
                                .testTag("gallery_item_${item.id}")
                        ) {
                            Image(
                                painter = rememberAsyncImagePainter(File(item.filePath)),
                                contentDescription = item.sceneLabel.ifBlank { "Media item" },
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (item.kind == MediaKind.VIDEO) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(38.dp)
                                        .background(Color.Black.copy(alpha = 0.65f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Video", tint = Color.White)
                                }
                            }
                            if (item.filterNames.isNotEmpty()) {
                                Surface(
                                    color = Color.Black.copy(alpha = 0.65f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                                ) {
                                    Text(
                                        text = item.filterNames.first(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ElectricCyan,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .navigationBarsPadding()
            ) {
                val isWide = maxWidth > maxHeight && maxWidth >= 620.dp

                val viewerPane: @Composable (Modifier) -> Unit = { viewerMod ->
                    Box(
                        modifier = viewerMod
                            .padding(12.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black)
                            .pointerInput(uiState.showBeforeAfter) {
                                if (uiState.showBeforeAfter) {
                                    detectHorizontalDragGestures { change, drag ->
                                        change.consume()
                                        val delta = drag / size.width.toFloat().coerceAtLeast(1f)
                                        viewModel.updateCompareSplit(uiState.compareSplit + delta)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val editedBitmap = remember(selected.filePath) { BitmapFactory.decodeFile(selected.filePath) }
                        val originalBitmap = remember(selected.originalBackupPath) {
                            selected.originalBackupPath?.let { BitmapFactory.decodeFile(it) }
                        }

                        if (editedBitmap != null) {
                            if (uiState.showBeforeAfter && originalBitmap != null) {
                                Image(
                                    bitmap = originalBitmap.asImageBitmap(),
                                    contentDescription = "Original before filter",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Image(
                                    bitmap = editedBitmap.asImageBitmap(),
                                    contentDescription = "Processed after filter",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .drawWithContent {
                                            val splitX = size.width * uiState.compareSplit
                                            clipRect(left = splitX) {
                                                this@drawWithContent.drawContent()
                                            }
                                            drawLine(Color.White, Offset(splitX, 0f), Offset(splitX, size.height), strokeWidth = 4f)
                                        }
                                )
                                Surface(
                                    color = Color.Black.copy(alpha = 0.7f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
                                ) {
                                    Text("BEFORE", style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.padding(6.dp))
                                }
                                Surface(
                                    color = ElectricCyan.copy(alpha = 0.85f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
                                ) {
                                    Text("AFTER", style = MaterialTheme.typography.labelSmall, color = Color.Black, modifier = Modifier.padding(6.dp))
                                }
                            } else {
                                Image(
                                    bitmap = editedBitmap.asImageBitmap(),
                                    contentDescription = "Selected photo",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        if (uiState.isProcessing) {
                            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = ElectricCyan)
                            }
                        }
                    }
                }

                val controlsPane: @Composable (Modifier) -> Unit = { ctrlMod ->
                    Column(
                        modifier = ctrlMod,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (selected.kind == MediaKind.PHOTO) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (selected.originalBackupPath != null) {
                                    item {
                                        GalleryActionChip(
                                            icon = Icons.Rounded.Compare,
                                            label = if (uiState.showBeforeAfter) "Exit Compare" else "Before/After",
                                            accent = accent
                                        ) { viewModel.toggleBeforeAfter() }
                                    }
                                }
                                item {
                                    GalleryActionChip(Icons.Rounded.AutoFixHigh, "Magic Enhance", EmeraldMint) {
                                        viewModel.applyMagicEnhance(selected)
                                    }
                                }
                                item {
                                    GalleryActionChip(Icons.Rounded.Hd, "2x Upscale", accent) {
                                        viewModel.applyUpscale2x(selected)
                                    }
                                }
                                item {
                                    GalleryActionChip(Icons.Rounded.BlurOn, "Studio Bokeh", accent) {
                                        viewModel.applyBackgroundBokeh(selected)
                                    }
                                }
                                item {
                                    GalleryActionChip(Icons.Rounded.Edit, "Draw / Erase", EmeraldMint) {
                                        onOpenEditor(selected)
                                    }
                                }
                                item {
                                    GalleryActionChip(
                                        icon = Icons.AutoMirrored.Rounded.BrandingWatermark,
                                        label = if (watermarkEnabled) "Watermark ON" else "Watermark OFF",
                                        accent = if (watermarkEnabled) EmeraldMint else MaterialTheme.colorScheme.onSurfaceVariant
                                    ) {
                                        viewModel.toggleShareWatermark()
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            GalleryActionChip(Icons.Rounded.Download, "Save Device", accent) {
                                viewModel.exportToDeviceGallery(selected)
                            }
                            GalleryActionChip(Icons.Rounded.Share, "Share", MaterialTheme.colorScheme.onSurface) {
                                viewModel.createShareIntent(selected)?.let { intent ->
                                    context.startActivity(android.content.Intent.createChooser(intent, "Share via"))
                                }
                            }
                            GalleryActionChip(Icons.Rounded.Delete, "Delete", CoralAlert) {
                                viewModel.deleteItem(selected)
                            }
                        }
                    }
                }

                if (isWide) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        viewerPane(Modifier.weight(1.25f).fillMaxHeight())
                        controlsPane(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 12.dp)
                        )
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        viewerPane(Modifier.weight(1f).fillMaxWidth())
                        controlsPane(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = label, tint = accent, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
