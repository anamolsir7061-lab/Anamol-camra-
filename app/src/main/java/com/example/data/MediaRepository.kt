package com.example.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.model.MediaItem
import com.example.model.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class MediaRepository(private val context: Context) {

    private val mediaDir = File(context.filesDir, "media").apply { mkdirs() }
    private val originalsDir = File(context.filesDir, "media_originals").apply { mkdirs() }
    private val indexFile = File(context.filesDir, "media_index.json")

    private val _items = MutableStateFlow<List<MediaItem>>(emptyList())
    val items: StateFlow<List<MediaItem>> = _items.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val loaded = readIndex().filter { File(it.filePath).exists() }
            .sortedByDescending { it.createdAtMs }
        _items.value = loaded
    }

    suspend fun savePhoto(
        processedBitmap: Bitmap,
        rawOriginalBitmap: Bitmap? = null,
        quality: Int = 92,
        filterNames: List<String> = emptyList(),
        sceneLabel: String = ""
    ): MediaItem = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val file = File(mediaDir, "IMG_${System.currentTimeMillis()}_${id.take(6)}.jpg")
        FileOutputStream(file).use { out ->
            processedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }

        var backupPath: String? = null
        if (rawOriginalBitmap != null) {
            val backupFile = File(originalsDir, "RAW_${id}.jpg")
            FileOutputStream(backupFile).use { out ->
                rawOriginalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }
            backupPath = backupFile.absolutePath
        }

        val item = MediaItem(
            id = id,
            filePath = file.absolutePath,
            originalBackupPath = backupPath,
            kind = MediaKind.PHOTO,
            createdAtMs = System.currentTimeMillis(),
            filterNames = filterNames,
            sceneLabel = sceneLabel,
            width = processedBitmap.width,
            height = processedBitmap.height
        )
        appendToIndex(item)
        item
    }

    suspend fun registerVideo(videoFile: File, sceneLabel: String = "Video"): MediaItem =
        withContext(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            val item = MediaItem(
                id = id,
                filePath = videoFile.absolutePath,
                kind = MediaKind.VIDEO,
                createdAtMs = System.currentTimeMillis(),
                filterNames = emptyList(),
                sceneLabel = sceneLabel
            )
            appendToIndex(item)
            item
        }

    fun createTempVideoFile(): File =
        File(mediaDir, "VID_${System.currentTimeMillis()}.mp4")

    suspend fun deleteItem(id: String) = withContext(Dispatchers.IO) {
        val current = readIndex().toMutableList()
        val target = current.find { it.id == id }
        if (target != null) {
            runCatching { File(target.filePath).delete() }
            target.originalBackupPath?.let { runCatching { File(it).delete() } }
            current.removeAll { it.id == id }
            writeIndex(current)
            _items.value = current.sortedByDescending { it.createdAtMs }
        }
    }

    suspend fun clearAllCacheAndMedia() = withContext(Dispatchers.IO) {
        mediaDir.listFiles()?.forEach { it.delete() }
        originalsDir.listFiles()?.forEach { it.delete() }
        writeIndex(emptyList())
        _items.value = emptyList()
    }

    fun calculateStorageBytes(): Long {
        val mediaSize = mediaDir.listFiles()?.sumOf { it.length() } ?: 0L
        val origSize = originalsDir.listFiles()?.sumOf { it.length() } ?: 0L
        return mediaSize + origSize
    }

    suspend fun exportToDeviceGallery(item: MediaItem): Uri? = withContext(Dispatchers.IO) {
        val sourceFile = File(item.filePath)
        if (!sourceFile.exists()) return@withContext null
        val resolver = context.contentResolver
        val isVideo = item.kind == MediaKind.VIDEO
        val collection = if (isVideo) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, sourceFile.name)
            put(MediaStore.MediaColumns.MIME_TYPE, if (isVideo) "video/mp4" else "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    if (isVideo) "${Environment.DIRECTORY_MOVIES}/AnamolCamera" else "${Environment.DIRECTORY_PICTURES}/AnamolCamera"
                )
            }
        }
        val uri = resolver.insert(collection, values) ?: return@withContext null
        resolver.openOutputStream(uri)?.use { out ->
            sourceFile.inputStream().use { input -> input.copyTo(out) }
        }
        uri
    }

    fun createShareIntent(item: MediaItem, withWatermark: Boolean = false): Intent? {
        val file = File(item.filePath)
        if (!file.exists()) return null
        val targetFile = if (withWatermark && item.kind == MediaKind.PHOTO) {
            runCatching {
                val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                if (bmp != null) {
                    val watermarked = com.example.camera.FilterBitmapProcessor.applyShareWatermark(bmp, "Anamol Camera")
                    val shareDir = File(context.cacheDir, "shared").apply { mkdirs() }
                    val out = File(shareDir, "share_${file.name}")
                    FileOutputStream(out).use { fos ->
                        watermarked.compress(Bitmap.CompressFormat.JPEG, 94, fos)
                    }
                    if (watermarked !== bmp) watermarked.recycle()
                    bmp.recycle()
                    out
                } else file
            }.getOrDefault(file)
        } else {
            file
        }
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, targetFile)
        return Intent(Intent.ACTION_SEND).apply {
            type = if (item.kind == MediaKind.VIDEO) "video/mp4" else "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun createXmlShareIntent(xmlFile: File): Intent {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, xmlFile)
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun appendToIndex(item: MediaItem) {
        val list = readIndex().toMutableList()
        list.add(0, item)
        writeIndex(list)
        _items.value = list.sortedByDescending { it.createdAtMs }
    }

    private fun readIndex(): List<MediaItem> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(indexFile.readText())
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val filtersArr = obj.optJSONArray("filterNames") ?: JSONArray()
                MediaItem(
                    id = obj.getString("id"),
                    filePath = obj.getString("filePath"),
                    originalBackupPath = obj.optString("originalBackupPath").ifBlank { null },
                    kind = MediaKind.valueOf(obj.getString("kind")),
                    createdAtMs = obj.getLong("createdAtMs"),
                    filterNames = (0 until filtersArr.length()).map { filtersArr.getString(it) },
                    sceneLabel = obj.optString("sceneLabel", ""),
                    width = obj.optInt("width", 0),
                    height = obj.optInt("height", 0)
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun writeIndex(items: List<MediaItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("filePath", item.filePath)
                    put("originalBackupPath", item.originalBackupPath ?: "")
                    put("kind", item.kind.name)
                    put("createdAtMs", item.createdAtMs)
                    put("filterNames", JSONArray(item.filterNames))
                    put("sceneLabel", item.sceneLabel)
                    put("width", item.width)
                    put("height", item.height)
                }
            )
        }
        indexFile.writeText(arr.toString())
    }
}
