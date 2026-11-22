package com.example.viewmodel

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.camera.FilterBitmapProcessor
import com.example.data.MediaRepository
import com.example.data.SettingsRepository
import com.example.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GalleryUiState(
    val selectedItem: MediaItem? = null,
    val isProcessing: Boolean = false,
    val statusMessage: String? = null,
    val compareSplit: Float = 0.5f,
    val showBeforeAfter: Boolean = false
)

class GalleryViewModel(
    private val mediaRepository: MediaRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val items: StateFlow<List<MediaItem>> = mediaRepository.items
    val shareWatermarkEnabled: StateFlow<Boolean> = settingsRepository.settingsFlow
        .map { it.shareWatermarkEnabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { mediaRepository.refresh() }
    }

    fun selectItem(item: MediaItem?) {
        _uiState.update { it.copy(selectedItem = item, showBeforeAfter = false, compareSplit = 0.5f) }
    }

    fun toggleBeforeAfter() {
        _uiState.update { it.copy(showBeforeAfter = !it.showBeforeAfter) }
    }

    fun updateCompareSplit(v: Float) {
        _uiState.update { it.copy(compareSplit = v.coerceIn(0.05f, 0.95f)) }
    }

    fun applyMagicEnhance(item: MediaItem) {
        processAndSaveCopy(item, "Magic Enhance") { src ->
            FilterBitmapProcessor.applyMagicEnhance(src)
        }
    }

    fun applyUpscale2x(item: MediaItem) {
        processAndSaveCopy(item, "2x Crisp Upscale") { src ->
            FilterBitmapProcessor.applyCrispUpscale2x(src)
        }
    }

    fun applyBackgroundBokeh(item: MediaItem) {
        processAndSaveCopy(item, "Studio Bokeh") { src ->
            FilterBitmapProcessor.applyBackgroundBokeh(src)
        }
    }

    fun saveEditedBitmap(item: MediaItem?, edited: Bitmap, label: String) {
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update { it.copy(isProcessing = true) }
            val saved = mediaRepository.savePhoto(
                processedBitmap = edited,
                rawOriginalBitmap = null,
                quality = 94,
                filterNames = (item?.filterNames.orEmpty() + label).distinct(),
                sceneLabel = item?.sceneLabel ?: label
            )
            _uiState.update { it.copy(isProcessing = false, selectedItem = saved, statusMessage = "Saved $label copy") }
        }
    }

    fun saveCollage(selectedItems: List<MediaItem>, layoutIndex: Int) {
        if (selectedItems.isEmpty()) return
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update { it.copy(isProcessing = true) }
            val bitmaps = selectedItems.mapNotNull { BitmapFactory.decodeFile(it.filePath) }
            if (bitmaps.isNotEmpty()) {
                val collage = FilterBitmapProcessor.createCollage(bitmaps, layoutIndex)
                val saved = mediaRepository.savePhoto(
                    processedBitmap = collage,
                    quality = 94,
                    filterNames = listOf("Collage"),
                    sceneLabel = "Collage"
                )
                bitmaps.forEach { if (!it.isRecycled) it.recycle() }
                if (!collage.isRecycled) collage.recycle()
                _uiState.update { it.copy(isProcessing = false, selectedItem = saved, statusMessage = "Collage saved to gallery") }
            } else {
                _uiState.update { it.copy(isProcessing = false, statusMessage = "Could not load selected photos") }
            }
        }
    }

    fun toggleShareWatermark() {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(shareWatermarkEnabled = !it.shareWatermarkEnabled) }
        }
    }

    fun exportToDeviceGallery(item: MediaItem) {
        viewModelScope.launch {
            val uri = mediaRepository.exportToDeviceGallery(item)
            _uiState.update {
                it.copy(statusMessage = if (uri != null) "Saved to device Pictures/AnamolCamera" else "Export failed")
            }
        }
    }

    fun createShareIntent(item: MediaItem): Intent? =
        mediaRepository.createShareIntent(item, withWatermark = shareWatermarkEnabled.value)

    fun deleteItem(item: MediaItem) {
        viewModelScope.launch {
            mediaRepository.deleteItem(item.id)
            _uiState.update { it.copy(selectedItem = null, statusMessage = "Deleted") }
        }
    }

    fun clearStatus() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    private fun processAndSaveCopy(item: MediaItem, tag: String, transform: (Bitmap) -> Bitmap) {
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update { it.copy(isProcessing = true) }
            val src = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(item.filePath) }
            if (src != null) {
                val out = transform(src)
                val orig = item.originalBackupPath?.let { BitmapFactory.decodeFile(it) } ?: src
                val saved = mediaRepository.savePhoto(
                    processedBitmap = out,
                    rawOriginalBitmap = orig,
                    quality = 94,
                    filterNames = (item.filterNames + tag).distinct(),
                    sceneLabel = item.sceneLabel
                )
                if (orig !== src && !orig.isRecycled) orig.recycle()
                if (out !== src && !out.isRecycled) out.recycle()
                if (!src.isRecycled) src.recycle()
                _uiState.update { it.copy(isProcessing = false, selectedItem = saved, statusMessage = "$tag applied & saved") }
            } else {
                _uiState.update { it.copy(isProcessing = false, statusMessage = "Unable to decode photo") }
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            GalleryViewModel(container.mediaRepository, container.settingsRepository) as T
    }
}
