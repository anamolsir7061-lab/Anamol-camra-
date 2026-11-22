package com.example.data

import android.content.Context
import com.example.model.ConfigXmlValidationResult
import com.example.model.GCamCameraConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

class CameraConfigRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    private val customConfigsDir = File(context.filesDir, "camera_configs").apply { mkdirs() }
    private val exportDir = File(context.filesDir, "exported_configs").apply { mkdirs() }

    private val _configs = MutableStateFlow<List<GCamCameraConfig>>(emptyList())
    val configs: StateFlow<List<GCamCameraConfig>> = _configs.asStateFlow()

    suspend fun loadConfigs() = withContext(Dispatchers.IO) {
        val favorites = settingsRepository.favoriteConfigIdsFlow.first()
        val builtIn = loadBuiltInAssets()
        val custom = loadCustomConfigs()

        val merged = (builtIn + custom)
            .distinctBy { it.id }
            .map { it.copy(isFavorite = favorites.contains(it.id)) }
        _configs.value = merged
    }

    fun loadSchemaMarkdown(): String = runCatching {
        context.assets.open("configs/CONFIG_SCHEMA.md").bufferedReader().use { it.readText() }
    }.getOrDefault("Anamol Camera — GCam 8.5 XML Config Schema")

    suspend fun importOrSaveConfig(xmlText: String): ConfigXmlValidationResult = withContext(Dispatchers.IO) {
        val result = CameraConfigXmlCodec.parseAndValidate(xmlText, isBuiltIn = false)
        if (result is ConfigXmlValidationResult.Valid) {
            val outFile = File(customConfigsDir, "${result.config.id}.xml")
            outFile.writeText(CameraConfigXmlCodec.toXml(result.config))
            loadConfigs()
        }
        result
    }

    suspend fun saveEditedConfig(config: GCamCameraConfig): ConfigXmlValidationResult = withContext(Dispatchers.IO) {
        val cleanId = config.id.trim().replace(Regex("[^a-zA-Z0-9_-]"), "_").lowercase()
            .ifBlank { "gcam_cfg_${System.currentTimeMillis() % 10000}" }
        val normalized = config.copy(id = cleanId, isBuiltIn = false)
        val xml = CameraConfigXmlCodec.toXml(normalized)
        val validated = CameraConfigXmlCodec.parseAndValidate(xml, isBuiltIn = false)
        if (validated is ConfigXmlValidationResult.Valid) {
            File(customConfigsDir, "$cleanId.xml").writeText(xml)
            loadConfigs()
        }
        validated
    }

    suspend fun duplicateConfig(source: GCamCameraConfig): GCamCameraConfig? = withContext(Dispatchers.IO) {
        val newId = "${source.id}_copy_${System.currentTimeMillis() % 1000}"
        val copy = source.copy(
            id = newId,
            name = "${source.name} Copy",
            author = "You",
            isBuiltIn = false
        )
        val xml = CameraConfigXmlCodec.toXml(copy)
        File(customConfigsDir, "$newId.xml").writeText(xml)
        loadConfigs()
        _configs.value.find { it.id == newId }
    }

    suspend fun renameConfig(config: GCamCameraConfig, newName: String): Boolean = withContext(Dispatchers.IO) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return@withContext false
        if (config.isBuiltIn) {
            // Built-in presets create a renamed custom copy so built-in remains intact
            val newId = "${config.id}_custom_${System.currentTimeMillis() % 1000}"
            val updated = config.copy(id = newId, name = trimmed, isBuiltIn = false, author = "You")
            File(customConfigsDir, "$newId.xml").writeText(CameraConfigXmlCodec.toXml(updated))
        } else {
            val updated = config.copy(name = trimmed)
            File(customConfigsDir, "${config.id}.xml").writeText(CameraConfigXmlCodec.toXml(updated))
        }
        loadConfigs()
        true
    }

    suspend fun deleteCustomConfig(configId: String) = withContext(Dispatchers.IO) {
        val f = File(customConfigsDir, "$configId.xml")
        if (f.exists()) {
            f.delete()
            loadConfigs()
        }
    }

    suspend fun toggleFavorite(configId: String) {
        settingsRepository.toggleFavoriteConfig(configId)
        loadConfigs()
    }

    suspend fun exportConfigToFile(config: GCamCameraConfig): File = withContext(Dispatchers.IO) {
        val f = File(exportDir, "${config.id}_gcam85.xml")
        f.writeText(CameraConfigXmlCodec.toXml(config))
        f
    }

    fun sampleXmlTemplate(): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <gcam-config version="8.5">
            <meta>
                <id>my_pixel_tune</id>
                <name>My Pixel 8.5 Tune</name>
                <icon>📸</icon>
                <author>You</author>
                <deviceProfile>Custom Main</deviceProfile>
                <description>Balanced HDR+ with warm skin tones and crisp sharpness.</description>
            </meta>
            <hdrplus>
                <hdrPlusStrength>0.95</hdrPlusStrength>
                <toneMappingGamma>0.96</toneMappingGamma>
                <exposureCompensation>0.05</exposureCompensation>
                <shadows>0.18</shadows>
                <highlights>-0.20</highlights>
                <blackLevel>0.00</blackLevel>
                <nightSightStrength>0.65</nightSightStrength>
            </hdrplus>
            <color>
                <contrast>1.08</contrast>
                <saturation>1.10</saturation>
                <vibrance>0.18</vibrance>
                <skinToneWarmth>0.10</skinToneWarmth>
            </color>
            <awb>
                <awbBias>AUTO</awbBias>
                <temperatureOffset>0.04</temperatureOffset>
                <tintOffset>0.00</tintOffset>
                <rgbGainR>1.02</rgbGainR>
                <rgbGainG>1.00</rgbGainG>
                <rgbGainB>0.99</rgbGainB>
            </awb>
            <detail>
                <sharpness>0.48</sharpness>
                <lumaNoiseReduction>0.35</lumaNoiseReduction>
                <chromaNoiseReduction>0.45</chromaNoiseReduction>
            </detail>
            <lens>
                <portraitBlurStrength>0.50</portraitBlurStrength>
                <vignette>0.08</vignette>
                <grain>0.00</grain>
            </lens>
        </gcam-config>
    """.trimIndent()

    private fun loadBuiltInAssets(): List<GCamCameraConfig> {
        val assetFiles = context.assets.list("configs")
            ?.filter { it.endsWith(".xml") }
            ?.sorted()
            .orEmpty()
        return assetFiles.mapNotNull { fileName ->
            runCatching {
                val text = context.assets.open("configs/$fileName").bufferedReader().use { it.readText() }
                (CameraConfigXmlCodec.parseAndValidate(text, isBuiltIn = true) as? ConfigXmlValidationResult.Valid)?.config
            }.getOrNull()
        }
    }

    private fun loadCustomConfigs(): List<GCamCameraConfig> {
        val files = customConfigsDir.listFiles { f -> f.extension.equals("xml", ignoreCase = true) }.orEmpty()
        return files.sortedBy { it.lastModified() }.mapNotNull { file ->
            runCatching {
                val text = file.readText()
                (CameraConfigXmlCodec.parseAndValidate(text, isBuiltIn = false) as? ConfigXmlValidationResult.Valid)?.config
            }.getOrNull()
        }
    }
}
