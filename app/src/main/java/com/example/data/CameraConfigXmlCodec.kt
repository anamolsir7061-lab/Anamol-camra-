package com.example.data

import android.util.Xml
import com.example.model.AwbBias
import com.example.model.ConfigXmlValidationResult
import com.example.model.GCamCameraConfig
import com.example.model.GCamModeOverride
import com.example.model.GCamProcessingParams
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.StringReader

object CameraConfigXmlCodec {

    fun parseAndValidate(xmlText: String, isBuiltIn: Boolean = false): ConfigXmlValidationResult {
        val trimmed = xmlText.trim()
        if (trimmed.isEmpty()) {
            return ConfigXmlValidationResult.Invalid(
                listOf("XML input is empty. Paste or import a <gcam-config version=\"8.5\"> XML file.")
            )
        }

        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        var id = ""
        var name = ""
        var icon = "📷"
        var author = "Custom Creator"
        var description = ""
        var deviceProfile = "Universal"
        var version = "8.5"
        var params = GCamProcessingParams()
        val overrides = mutableMapOf<String, GCamModeOverride>()

        var currentSection = ""
        var currentOverrideKey = ""
        var currentOverride = GCamModeOverride()
        var currentTag = ""
        var sawRoot = false

        fun parseClampedFloat(
            raw: String,
            tag: String,
            min: Float,
            max: Float,
            defaultVal: Float,
            line: Int
        ): Float {
            val v = raw.toFloatOrNull()
            if (v == null) {
                warnings.add("Line $line: Invalid float '$raw' in <$tag>; using safe default $defaultVal.")
                return defaultVal
            }
            if (v < min || v > max) {
                val clamped = v.coerceIn(min, max)
                warnings.add("Line $line: <$tag> value $v outside [$min..$max]; clamped to $clamped.")
                return clamped
            }
            return v
        }

        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(trimmed))

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                val line = parser.lineNumber
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tag = parser.name.orEmpty()
                        currentTag = tag
                        when (tag) {
                            "gcam-config", "anamol-config" -> {
                                sawRoot = true
                                val ver = parser.getAttributeValue(null, "version").orEmpty().trim()
                                if (ver.isNotBlank()) version = ver
                                else warnings.add("Line $line: Missing version attribute on <$tag>; assuming 8.5.")
                            }
                            "meta", "hdrplus", "color", "detail", "noise", "awb", "lens", "overrides" -> {
                                currentSection = tag
                            }
                            "mode" -> {
                                if (currentSection == "overrides") {
                                    val key = parser.getAttributeValue(null, "target").orEmpty().trim().uppercase()
                                    if (key.isBlank()) {
                                        warnings.add("Line $line: <mode> missing target attribute (e.g. target=\"NIGHT\"); skipping block.")
                                    } else {
                                        currentOverrideKey = key
                                        currentOverride = GCamModeOverride()
                                    }
                                }
                            }
                            else -> {
                                if (!KNOWN_VALUE_TAGS.contains(tag)) {
                                    warnings.add("Line $line: Unknown XML tag <$tag> ignored.")
                                }
                            }
                        }
                    }

                    XmlPullParser.TEXT -> {
                        val value = parser.text?.trim().orEmpty()
                        if (value.isNotEmpty() && currentTag.isNotEmpty()) {
                            if (currentSection == "overrides" && currentOverrideKey.isNotEmpty()) {
                                currentOverride = when (currentTag) {
                                    "exposureCompensation" -> currentOverride.copy(
                                        exposureCompensation = parseClampedFloat(value, currentTag, -2f, 2f, 0f, line)
                                    )
                                    "saturation" -> currentOverride.copy(
                                        saturation = parseClampedFloat(value, currentTag, 0f, 2f, 1f, line)
                                    )
                                    "contrast" -> currentOverride.copy(
                                        contrast = parseClampedFloat(value, currentTag, 0.5f, 1.8f, 1f, line)
                                    )
                                    "sharpness" -> currentOverride.copy(
                                        sharpness = parseClampedFloat(value, currentTag, 0f, 1.5f, 0.25f, line)
                                    )
                                    "nightSightStrength" -> currentOverride.copy(
                                        nightSightStrength = parseClampedFloat(value, currentTag, 0f, 1.5f, 0.35f, line)
                                    )
                                    "hdrPlusStrength" -> currentOverride.copy(
                                        hdrPlusStrength = parseClampedFloat(value, currentTag, 0f, 1.5f, 0.65f, line)
                                    )
                                    "awbBias" -> {
                                        val parsed = runCatching { AwbBias.valueOf(value.uppercase()) }.getOrNull()
                                        if (parsed == null) {
                                            warnings.add("Line $line: Unknown awbBias '$value'; falling back to AUTO.")
                                            currentOverride.copy(awbBias = AwbBias.AUTO)
                                        } else {
                                            currentOverride.copy(awbBias = parsed)
                                        }
                                    }
                                    else -> currentOverride
                                }
                            } else {
                                when (currentTag) {
                                    "id" -> id = value.replace(Regex("[^a-zA-Z0-9_-]"), "_").lowercase()
                                    "name" -> name = value
                                    "icon" -> icon = value
                                    "author" -> author = value
                                    "description" -> description = value
                                    "deviceProfile" -> deviceProfile = value
                                    "hdrPlusStrength" -> params = params.copy(
                                        hdrPlusStrength = parseClampedFloat(value, currentTag, 0f, 1.5f, params.hdrPlusStrength, line)
                                    )
                                    "toneMappingGamma" -> params = params.copy(
                                        toneMappingGamma = parseClampedFloat(value, currentTag, 0.6f, 1.6f, params.toneMappingGamma, line)
                                    )
                                    "shadows" -> params = params.copy(
                                        shadows = parseClampedFloat(value, currentTag, -1f, 1f, params.shadows, line)
                                    )
                                    "highlights" -> params = params.copy(
                                        highlights = parseClampedFloat(value, currentTag, -1f, 1f, params.highlights, line)
                                    )
                                    "blackLevel" -> params = params.copy(
                                        blackLevel = parseClampedFloat(value, currentTag, -0.2f, 0.2f, params.blackLevel, line)
                                    )
                                    "contrast" -> params = params.copy(
                                        contrast = parseClampedFloat(value, currentTag, 0.5f, 1.8f, params.contrast, line)
                                    )
                                    "saturation" -> params = params.copy(
                                        saturation = parseClampedFloat(value, currentTag, 0f, 2f, params.saturation, line)
                                    )
                                    "vibrance" -> params = params.copy(
                                        vibrance = parseClampedFloat(value, currentTag, -0.5f, 1f, params.vibrance, line)
                                    )
                                    "sharpness" -> params = params.copy(
                                        sharpness = parseClampedFloat(value, currentTag, 0f, 1.5f, params.sharpness, line)
                                    )
                                    "lumaNoiseReduction" -> params = params.copy(
                                        lumaNoiseReduction = parseClampedFloat(value, currentTag, 0f, 1f, params.lumaNoiseReduction, line)
                                    )
                                    "chromaNoiseReduction" -> params = params.copy(
                                        chromaNoiseReduction = parseClampedFloat(value, currentTag, 0f, 1f, params.chromaNoiseReduction, line)
                                    )
                                    "temperatureOffset" -> params = params.copy(
                                        temperatureOffset = parseClampedFloat(value, currentTag, -1f, 1f, params.temperatureOffset, line)
                                    )
                                    "tintOffset" -> params = params.copy(
                                        tintOffset = parseClampedFloat(value, currentTag, -0.5f, 0.5f, params.tintOffset, line)
                                    )
                                    "rgbGainR" -> params = params.copy(
                                        rgbGainR = parseClampedFloat(value, currentTag, 0.6f, 1.4f, params.rgbGainR, line)
                                    )
                                    "rgbGainG" -> params = params.copy(
                                        rgbGainG = parseClampedFloat(value, currentTag, 0.6f, 1.4f, params.rgbGainG, line)
                                    )
                                    "rgbGainB" -> params = params.copy(
                                        rgbGainB = parseClampedFloat(value, currentTag, 0.6f, 1.4f, params.rgbGainB, line)
                                    )
                                    "exposureCompensation" -> params = params.copy(
                                        exposureCompensation = parseClampedFloat(value, currentTag, -2f, 2f, params.exposureCompensation, line)
                                    )
                                    "nightSightStrength" -> params = params.copy(
                                        nightSightStrength = parseClampedFloat(value, currentTag, 0f, 1.5f, params.nightSightStrength, line)
                                    )
                                    "portraitBlurStrength" -> params = params.copy(
                                        portraitBlurStrength = parseClampedFloat(value, currentTag, 0f, 1f, params.portraitBlurStrength, line)
                                    )
                                    "skinToneWarmth" -> params = params.copy(
                                        skinToneWarmth = parseClampedFloat(value, currentTag, -0.5f, 0.5f, params.skinToneWarmth, line)
                                    )
                                    "awbBias" -> {
                                        val parsed = runCatching { AwbBias.valueOf(value.uppercase()) }.getOrNull()
                                        if (parsed == null) {
                                            warnings.add("Line $line: Invalid <awbBias> '$value'; falling back to AUTO.")
                                        } else {
                                            params = params.copy(awbBias = parsed)
                                        }
                                    }
                                    "vignette" -> params = params.copy(
                                        vignette = parseClampedFloat(value, currentTag, 0f, 1f, params.vignette, line)
                                    )
                                    "grain" -> params = params.copy(
                                        grain = parseClampedFloat(value, currentTag, 0f, 1f, params.grain, line)
                                    )
                                }
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        val endTag = parser.name.orEmpty()
                        when (endTag) {
                            "mode" -> {
                                if (currentOverrideKey.isNotEmpty()) {
                                    overrides[currentOverrideKey] = currentOverride
                                    currentOverrideKey = ""
                                }
                            }
                            "meta", "hdrplus", "color", "detail", "noise", "awb", "lens", "overrides" -> {
                                currentSection = ""
                            }
                        }
                        currentTag = ""
                    }
                }
                eventType = parser.next()
            }
        } catch (e: XmlPullParserException) {
            errors.add("Malformed XML near line ${e.lineNumber}: ${e.message?.substringBefore('\n') ?: "syntax error"}")
        } catch (e: Exception) {
            errors.add("Failed to parse config XML: ${e.localizedMessage ?: "Unknown error"}")
        }

        if (!sawRoot) {
            errors.add("Missing root <gcam-config version=\"8.5\"> element.")
        }
        if (id.isBlank()) {
            errors.add("Missing required <meta><id>...</id></meta> identifier.")
        }
        if (name.isBlank()) {
            errors.add("Missing required <meta><name>...</name></meta> display name.")
        }

        if (errors.isNotEmpty()) {
            return ConfigXmlValidationResult.Invalid(errors, warnings)
        }

        return ConfigXmlValidationResult.Valid(
            config = GCamCameraConfig(
                id = id,
                name = name,
                icon = icon,
                author = author,
                description = description,
                deviceProfile = deviceProfile,
                version = version,
                params = params,
                modeOverrides = overrides,
                isBuiltIn = isBuiltIn
            ),
            warnings = warnings
        )
    }

    fun toXml(cfg: GCamCameraConfig): String {
        val p = cfg.params
        return buildString {
            appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
            appendLine("""<gcam-config version="${escapeXml(cfg.version)}">""")
            appendLine("    <meta>")
            appendLine("        <id>${escapeXml(cfg.id)}</id>")
            appendLine("        <name>${escapeXml(cfg.name)}</name>")
            appendLine("        <icon>${escapeXml(cfg.icon)}</icon>")
            appendLine("        <author>${escapeXml(cfg.author)}</author>")
            appendLine("        <deviceProfile>${escapeXml(cfg.deviceProfile)}</deviceProfile>")
            appendLine("        <description>${escapeXml(cfg.description)}</description>")
            appendLine("    </meta>")
            appendLine("    <hdrplus>")
            appendLine("        <hdrPlusStrength>${fmt(p.hdrPlusStrength)}</hdrPlusStrength>")
            appendLine("        <toneMappingGamma>${fmt(p.toneMappingGamma)}</toneMappingGamma>")
            appendLine("        <exposureCompensation>${fmt(p.exposureCompensation)}</exposureCompensation>")
            appendLine("        <shadows>${fmt(p.shadows)}</shadows>")
            appendLine("        <highlights>${fmt(p.highlights)}</highlights>")
            appendLine("        <blackLevel>${fmt(p.blackLevel)}</blackLevel>")
            appendLine("        <nightSightStrength>${fmt(p.nightSightStrength)}</nightSightStrength>")
            appendLine("    </hdrplus>")
            appendLine("    <color>")
            appendLine("        <contrast>${fmt(p.contrast)}</contrast>")
            appendLine("        <saturation>${fmt(p.saturation)}</saturation>")
            appendLine("        <vibrance>${fmt(p.vibrance)}</vibrance>")
            appendLine("        <skinToneWarmth>${fmt(p.skinToneWarmth)}</skinToneWarmth>")
            appendLine("    </color>")
            appendLine("    <awb>")
            appendLine("        <awbBias>${p.awbBias.name}</awbBias>")
            appendLine("        <temperatureOffset>${fmt(p.temperatureOffset)}</temperatureOffset>")
            appendLine("        <tintOffset>${fmt(p.tintOffset)}</tintOffset>")
            appendLine("        <rgbGainR>${fmt(p.rgbGainR)}</rgbGainR>")
            appendLine("        <rgbGainG>${fmt(p.rgbGainG)}</rgbGainG>")
            appendLine("        <rgbGainB>${fmt(p.rgbGainB)}</rgbGainB>")
            appendLine("    </awb>")
            appendLine("    <detail>")
            appendLine("        <sharpness>${fmt(p.sharpness)}</sharpness>")
            appendLine("        <lumaNoiseReduction>${fmt(p.lumaNoiseReduction)}</lumaNoiseReduction>")
            appendLine("        <chromaNoiseReduction>${fmt(p.chromaNoiseReduction)}</chromaNoiseReduction>")
            appendLine("    </detail>")
            appendLine("    <lens>")
            appendLine("        <portraitBlurStrength>${fmt(p.portraitBlurStrength)}</portraitBlurStrength>")
            appendLine("        <vignette>${fmt(p.vignette)}</vignette>")
            appendLine("        <grain>${fmt(p.grain)}</grain>")
            appendLine("    </lens>")
            if (cfg.modeOverrides.isNotEmpty()) {
                appendLine("    <overrides>")
                cfg.modeOverrides.forEach { (target, ov) ->
                    appendLine("""        <mode target="${escapeXml(target)}">""")
                    ov.exposureCompensation?.let { appendLine("            <exposureCompensation>${fmt(it)}</exposureCompensation>") }
                    ov.hdrPlusStrength?.let { appendLine("            <hdrPlusStrength>${fmt(it)}</hdrPlusStrength>") }
                    ov.nightSightStrength?.let { appendLine("            <nightSightStrength>${fmt(it)}</nightSightStrength>") }
                    ov.contrast?.let { appendLine("            <contrast>${fmt(it)}</contrast>") }
                    ov.saturation?.let { appendLine("            <saturation>${fmt(it)}</saturation>") }
                    ov.sharpness?.let { appendLine("            <sharpness>${fmt(it)}</sharpness>") }
                    ov.awbBias?.let { appendLine("            <awbBias>${it.name}</awbBias>") }
                    appendLine("        </mode>")
                }
                appendLine("    </overrides>")
            }
            appendLine("</gcam-config>")
        }
    }

    private fun fmt(v: Float): String = "%.2f".format(v)

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private val KNOWN_VALUE_TAGS = setOf(
        "id", "name", "icon", "author", "description", "deviceProfile",
        "hdrPlusStrength", "toneMappingGamma", "exposureCompensation",
        "shadows", "highlights", "blackLevel", "nightSightStrength",
        "contrast", "saturation", "vibrance", "skinToneWarmth",
        "awbBias", "temperatureOffset", "tintOffset", "rgbGainR", "rgbGainG", "rgbGainB",
        "sharpness", "lumaNoiseReduction", "chromaNoiseReduction",
        "portraitBlurStrength", "vignette", "grain"
    )
}
