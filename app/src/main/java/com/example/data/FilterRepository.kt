package com.example.data

import android.content.Context
import com.example.model.CameraFilter
import com.example.model.XmlValidationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FilterRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val customDir = File(context.filesDir, "custom_filters").apply { mkdirs() }

    private val _rawFilters = MutableStateFlow<List<CameraFilter>>(emptyList())
    private val _filters = MutableStateFlow<List<CameraFilter>>(emptyList())
    val filters: StateFlow<List<CameraFilter>> = _filters.asStateFlow()

    val schemaDocumentation: String by lazy {
        runCatching {
            context.assets.open("filters/FILTER_SCHEMA.md").bufferedReader().use { it.readText() }
        }.getOrDefault("Anamol Camera XML Filter Schema v1.0")
    }

    init {
        scope.launch {
            loadAllFilters()
            combine(
                _rawFilters,
                settingsRepository.favoriteFilterIdsFlow,
                settingsRepository.filterOrderFlow
            ) { raw, favs, order ->
                val withFavs = raw.map { it.copy(isFavorite = it.id in favs) }
                val orderMap = order.withIndex().associate { it.value to it.index }
                withFavs.sortedWith(
                    compareByDescending<CameraFilter> { it.isFavorite }
                        .thenBy { orderMap[it.id] ?: Int.MAX_VALUE }
                        .thenBy { !it.isBuiltIn }
                        .thenBy { it.name }
                )
            }.collect { sorted ->
                _filters.value = sorted
            }
        }
    }

    suspend fun loadAllFilters() = withContext(Dispatchers.IO) {
        val loaded = mutableListOf<CameraFilter>()
        val assetFiles = context.assets.list("filters")
            ?.filter { it.endsWith(".xml", ignoreCase = true) }
            ?.sorted()
            .orEmpty()

        for (fileName in assetFiles) {
            val content = context.assets.open("filters/$fileName").bufferedReader().use { it.readText() }
            val result = FilterXmlCodec.parseAndValidate(content, isBuiltIn = true)
            if (result is XmlValidationResult.Valid) {
                loaded.add(result.filter)
            }
        }

        val customFiles = customDir.listFiles { f -> f.extension.equals("xml", ignoreCase = true) }
            ?.sortedBy { it.name }
            .orEmpty()

        for (file in customFiles) {
            val content = runCatching { file.readText() }.getOrNull() ?: continue
            val result = FilterXmlCodec.parseAndValidate(content, isBuiltIn = false)
            if (result is XmlValidationResult.Valid) {
                loaded.removeAll { it.id == result.filter.id }
                loaded.add(result.filter)
            }
        }

        _rawFilters.value = loaded
    }

    suspend fun importOrSaveFilter(xmlContent: String): XmlValidationResult {
        val result = FilterXmlCodec.parseAndValidate(xmlContent, isBuiltIn = false)
        if (result is XmlValidationResult.Valid) {
            withContext(Dispatchers.IO) {
                val target = File(customDir, "${result.filter.id}.xml")
                target.writeText(FilterXmlCodec.toXml(result.filter))
                loadAllFilters()
            }
        }
        return result
    }

    suspend fun saveEditedFilter(filter: CameraFilter) {
        withContext(Dispatchers.IO) {
            val target = File(customDir, "${filter.id}.xml")
            target.writeText(FilterXmlCodec.toXml(filter.copy(isBuiltIn = false)))
            loadAllFilters()
        }
    }

    suspend fun deleteCustomFilter(filterId: String): Boolean {
        return withContext(Dispatchers.IO) {
            val file = File(customDir, "$filterId.xml")
            val deleted = if (file.exists()) file.delete() else false
            if (deleted) loadAllFilters()
            deleted
        }
    }

    suspend fun toggleFavorite(filterId: String) {
        settingsRepository.toggleFavoriteFilter(filterId)
    }

    suspend fun moveFilter(filterId: String, delta: Int) {
        val currentIds = _filters.value.map { it.id }.toMutableList()
        val idx = currentIds.indexOf(filterId)
        if (idx == -1) return
        val newIdx = (idx + delta).coerceIn(0, currentIds.lastIndex)
        if (newIdx == idx) return
        val item = currentIds.removeAt(idx)
        currentIds.add(newIdx, item)
        settingsRepository.saveFilterOrder(currentIds)
    }

    suspend fun exportFilterToFile(filter: CameraFilter): File = withContext(Dispatchers.IO) {
        val exportDir = File(context.filesDir, "exports").apply { mkdirs() }
        val file = File(exportDir, "${filter.id}.xml")
        file.writeText(FilterXmlCodec.toXml(filter))
        file
    }

    fun sampleXmlTemplate(): String = FilterXmlCodec.toXml(
        CameraFilter(
            id = "custom_prism_${System.currentTimeMillis() % 1000}",
            name = "Custom Prism",
            icon = "💎",
            category = com.example.model.FilterCategory.COLOR_GRADE,
            author = "You",
            description = "Custom Anamol Camera XML filter preset"
        )
    )
}
