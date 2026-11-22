package com.example.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.example.camera.FilterBitmapProcessor
import com.example.model.MediaItem
import com.example.model.MediaKind
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMint
import com.example.viewmodel.GalleryViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollageScreen(
    galleryViewModel: GalleryViewModel,
    onBack: () -> Unit
) {
    val allItems by galleryViewModel.items.collectAsStateWithLifecycle()
    val photos = remember(allItems) { allItems.filter { it.kind == MediaKind.PHOTO } }
    val selectedIds = remember { mutableStateListOf<String>() }
    var layoutIndex by remember { mutableIntStateOf(0) }

    val selectedItems = photos.filter { it.id in selectedIds }
    val previewBitmap = remember(selectedItems, layoutIndex) {
        if (selectedItems.isEmpty()) null
        else {
            val bmps = selectedItems.mapNotNull { BitmapFactory.decodeFile(it.filePath) }
            if (bmps.isEmpty()) null else FilterBitmapProcessor.createCollage(bmps, layoutIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Collage Maker") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
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
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (previewBitmap != null) {
                    Image(
                        bitmap = previewBitmap.asImageBitmap(),
                        contentDescription = "Collage preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text("Select 2 to 4 photos below", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { layoutIndex = (layoutIndex + 1) % 2 },
                    modifier = Modifier.weight(1f)
                ) { Text("Switch Layout") }

                Button(
                    onClick = {
                        galleryViewModel.saveCollage(selectedItems, layoutIndex)
                        onBack()
                    },
                    enabled = selectedItems.size >= 2,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldMint, contentColor = Color.Black),
                    modifier = Modifier.weight(1f).testTag("save_collage_btn")
                ) { Text("Save Collage") }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(86.dp),
                contentPadding = PaddingValues(4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(photos, key = { it.id }) { photo ->
                    val isSelected = photo.id in selectedIds
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .border(2.5.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(10.dp))
                            .clickable {
                                if (isSelected) selectedIds.remove(photo.id)
                                else if (selectedIds.size < 4) selectedIds.add(photo.id)
                            }
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(File(photo.filePath)),
                            contentDescription = "Photo choice",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(22.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
