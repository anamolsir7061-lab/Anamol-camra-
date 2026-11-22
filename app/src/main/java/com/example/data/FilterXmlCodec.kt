package com.example.data

import android.util.Xml
import com.example.model.ArAnchorPoint
import com.example.model.ArAnimation
import com.example.model.ArOverlayConfig
import com.example.model.ArOverlayType
import com.example.model.CameraFilter
import com.example.model.FilterAdjustments
import com.example.model.FilterBlendMode
import com.example.model.FilterCategory
import com.example.model.LutPreset
import com.example.model.XmlValidationResult
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

object FilterXmlCodec {

    private val ID_REGEX = Regex("^[a-z0-9_-]{2,48}$")
    private val HEX_REGEX = Regex("^#[0-9A-Fa-f]{6}$")

    fun parseAndValidate(xmlContent: String, isBuiltIn: Boolean = false): XmlValidationResult {
        if (xmlContent.isBlank()) {
            return XmlValidationResult.Invalid(listOf("XML input is empty. Paste or select a <lensmind-filter> XML file."))
        }

        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        var id: String? = null
        var name: String? = null
        var icon = "🎨"
        var category = FilterCategory.COLOR_GRADE
        var author = "Community"
        var description = ""

        var brightness = 0f
        var contrast = 1f
        var saturation = 1f
        var hue = 0f
        var temperature = 0f
        var greenery = 0f
        var blur = 0f
        var grain = 0f
        var vignette = 0f
        var skinSmooth = 0f
        var highlights = 0f
        var shadows = 0f
        var glitch = 0f
        var duotonePrimary: String? = null
        var duotoneSecondary: String? = null
        var blendMode = FilterBlendMode.NORMAL
        var lutPreset = LutPreset.NONE

        var arOverlay = ArOverlayConfig()
        var sawRoot = false
        var sawMeta = false
        var sawAdjustments = false

        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xmlContent.trim()))

            var eventType = parser.eventType
            var currentSection = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tag = parser.name
                        val line = parser.lineNumber
                        when (tag) {
                            "lensmind-filter" -> {
                                sawRoot = true
                                val version = parser.getAttributeValue(null, "version")
                                if (version.isNullOrBlank()) {
                                    warnings.add("Line $line: Missing version attribute on <lensmind-filter>; assuming 1.0.")
                                }
                            }
                            "meta" -> {
                                sawMeta = true
                                currentSection = "meta"
                            }
                            "adjustments" -> {
                                sawAdjustments = true
                                currentSection = "adjustments"
                            }
                            "ar-overlay" -> {
                                val typeStr = parser.getAttributeValue(null, "type") ?: "NONE"
                                val anchorStr = parser.getAttributeValue(null, "anchor") ?: "FULL_FACE"
                                val scaleStr = parser.getAttributeValue(null, "scale") ?: "1.0"
                                val offsetYStr = parser.getAttributeValue(null, "offsetY") ?: "0.0"
                                val tintStr = parser.getAttributeValue(null, "tint") ?: "#00E5FF"
                                val animStr = parser.getAttributeValue(null, "animation") ?: "NONE"

                                val arType = parseEnumOrError<ArOverlayType>(typeStr, line, "ar-overlay@type", errors) ?: ArOverlayType.NONE
                                val anchor = parseEnumOrError<ArAnchorPoint>(anchorStr, line, "ar-overlay@anchor", errors) ?: ArAnchorPoint.FULL_FACE
                                val anim = parseEnumOrError<ArAnimation>(animStr, line, "ar-overlay@animation", errors) ?: ArAnimation.NONE
                                val scale = parseFloatRange(scaleStr, 0.5f, 2.0f, line, "ar-overlay@scale", errors, 1.0f)
                                val offsetY = parseFloatRange(offsetYStr, -0.5f, 0.5f, line, "ar-overlay@offsetY", errors, 0.0f)
                                if (!HEX_REGEX.matches(tintStr)) {
                                    errors.add("Line $line: ar-overlay@tint '$tintStr' must be #RRGGBB hex.")
                                }

                                arOverlay = ArOverlayConfig(
                                    type = arType,
                                    anchor = anchor,
                                    scale = scale,
                                    offsetY = offsetY,
                                    tintHex = if (HEX_REGEX.matches(tintStr)) tintStr else "#00E5FF",
                                    animation = anim
                                )
                            }
                            else -> {
                                val text = safeNextText(parser).trim()
                                when (currentSection) {
                                    "meta" -> when (tag) {
                                        "id" -> {
                                            if (!ID_REGEX.matches(text)) {
                                                errors.add("Line $line: <id> '$text' must match [a-z0-9_-]{2,48}.")
                                            }
                                            id = text
                                        }
                                        "name" -> {
                                            if (text.isBlank() || text.length > 32) {
                                                errors.add("Line $line: <name> must be 1–32 characters.")
                                            }
                                            name = text
                                        }
                                        "icon" -> if (text.isNotBlank()) icon = text
                                        "category" -> {
                                            category = parseEnumOrError(text, line, "category", errors) ?: FilterCategory.COLOR_GRADE
                                        }
                                        "author" -> if (text.isNotBlank()) author = text
                                        "description" -> description = text
                                        else -> warnings.add("Line $line: Unknown <meta> tag <$tag> ignored.")
                                    }
                                    "adjustments" -> when (tag) {
                                        "brightness" -> brightness = parseFloatRange(text, -1f, 1f, line, tag, errors, 0f)
                                        "contrast" -> contrast = parseFloatRange(text, 0.2f, 2.5f, line, tag, errors, 1f)
                                        "saturation" -> saturation = parseFloatRange(text, 0f, 2.5f, line, tag, errors, 1f)
                                        "hue" -> hue = parseFloatRange(text, -180f, 180f, line, tag, errors, 0f)
                                        "temperature" -> temperature = parseFloatRange(text, -1f, 1f, line, tag, errors, 0f)
                                        "greenery" -> greenery = parseFloatRange(text, -1f, 1f, line, tag, errors, 0f)
                                        "blur" -> blur = parseFloatRange(text, 0f, 1f, line, tag, errors, 0f)
                                        "grain" -> grain = parseFloatRange(text, 0f, 1f, line, tag, errors, 0f)
                                        "vignette" -> vignette = parseFloatRange(text, 0f, 1f, line, tag, errors, 0f)
                                        "skinSmooth" -> skinSmooth = parseFloatRange(text, 0f, 1f, line, tag, errors, 0f)
                                        "highlights" -> highlights = parseFloatRange(text, -1f, 1f, line, tag, errors, 0f)
                                        "shadows" -> shadows = parseFloatRange(text, -1f, 1f, line, tag, errors, 0f)
                                        "glitch" -> glitch = parseFloatRange(text, 0f, 1f, line, tag, errors, 0f)
                                        "duotonePrimary" -> {
                                            if (text.isNotBlank()) {
                                                if (!HEX_REGEX.matches(text)) {
                                                    errors.add("Line $line: <duotonePrimary> '$text' must be #RRGGBB.")
                                                } else {
                                                    duotonePrimary = text
                                                }
                                            }
                                        }
                                        "duotoneSecondary" -> {
                                            if (text.isNotBlank()) {
                                                if (!HEX_REGEX.matches(text)) {
                                                    errors.add("Line $line: <duotoneSecondary> '$text' must be #RRGGBB.")
                                                } else {
                                                    duotoneSecondary = text
                                                }
                                            }
                                        }
                                        "blendMode" -> {
                                            blendMode = parseEnumOrError(text, line, "blendMode", errors) ?: FilterBlendMode.NORMAL
                                        }
                                        "lutPreset" -> {
                                            lutPreset = parseEnumOrError(text, line, "lutPreset", errors) ?: LutPreset.NONE
                                        }
                                        else -> warnings.add("Line $line: Unknown <adjustments> tag <$tag> ignored.")
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "meta" || parser.name == "adjustments") {
                            currentSection = ""
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            errors.add("XML syntax error: ${e.localizedMessage ?: "Malformed XML structure"}")
        }

        if (!sawRoot) errors.add("Missing root <lensmind-filter version=\"1.0\"> element.")
        if (!sawMeta) errors.add("Missing required <meta> section.")
        if (!sawAdjustments) errors.add("Missing required <adjustments> section.")
        if (id.isNullOrBlank()) errors.add("Missing required <meta><id> element.")
        if (name.isNullOrBlank()) errors.add("Missing required <meta><name> element.")

        return if (errors.isEmpty()) {
            XmlValidationResult.Valid(
                filter = CameraFilter(
                    id = id!!,
                    name = name!!,
                    icon = icon,
                    category = category,
                    author = author,
                    description = description,
                    adjustments = FilterAdjustments(
                        brightness = brightness,
                        contrast = contrast,
                        saturation = saturation,
                        hue = hue,
                        temperature = temperature,
                        greenery = greenery,
                        blur = blur,
                        grain = grain,
                        vignette = vignette,
                        skinSmooth = skinSmooth,
                        highlights = highlights,
                        shadows = shadows,
                        glitch = glitch,
                        duotonePrimary = duotonePrimary,
                        duotoneSecondary = duotoneSecondary,
                        blendMode = blendMode,
                        lutPreset = lutPreset
                    ),
                    arOverlay = arOverlay,
                    isBuiltIn = isBuiltIn
                ),
                warnings = warnings
            )
        } else {
            XmlValidationResult.Invalid(errors)
        }
    }

    fun toXml(filter: CameraFilter): String {
        val adj = filter.adjustments
        val ar = filter.arOverlay
        return buildString {
            appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
            appendLine("""<lensmind-filter version="1.0">""")
            appendLine("    <meta>")
            appendLine("        <id>${escapeXml(filter.id)}</id>")
            appendLine("        <name>${escapeXml(filter.name)}</name>")
            appendLine("        <icon>${escapeXml(filter.icon)}</icon>")
            appendLine("        <category>${filter.category.name}</category>")
            appendLine("        <author>${escapeXml(filter.author)}</author>")
            appendLine("        <description>${escapeXml(filter.description)}</description>")
            appendLine("    </meta>")
            appendLine("    <adjustments>")
            appendLine("        <brightness>${fmt(adj.brightness)}</brightness>")
            appendLine("        <contrast>${fmt(adj.contrast)}</contrast>")
            appendLine("        <saturation>${fmt(adj.saturation)}</saturation>")
            appendLine("        <hue>${fmt(adj.hue)}</hue>")
            appendLine("        <temperature>${fmt(adj.temperature)}</temperature>")
            appendLine("        <greenery>${fmt(adj.greenery)}</greenery>")
            appendLine("        <blur>${fmt(adj.blur)}</blur>")
            appendLine("        <grain>${fmt(adj.grain)}</grain>")
            appendLine("        <vignette>${fmt(adj.vignette)}</vignette>")
            appendLine("        <skinSmooth>${fmt(adj.skinSmooth)}</skinSmooth>")
            appendLine("        <highlights>${fmt(adj.highlights)}</highlights>")
            appendLine("        <shadows>${fmt(adj.shadows)}</shadows>")
            appendLine("        <glitch>${fmt(adj.glitch)}</glitch>")
            if (!adj.duotonePrimary.isNullOrBlank()) {
                appendLine("        <duotonePrimary>${escapeXml(adj.duotonePrimary)}</duotonePrimary>")
            }
            if (!adj.duotoneSecondary.isNullOrBlank()) {
                appendLine("        <duotoneSecondary>${escapeXml(adj.duotoneSecondary)}</duotoneSecondary>")
            }
            appendLine("        <blendMode>${adj.blendMode.name}</blendMode>")
            appendLine("        <lutPreset>${adj.lutPreset.name}</lutPreset>")
            appendLine("    </adjustments>")
            appendLine("""    <ar-overlay type="${ar.type.name}" anchor="${ar.anchor.name}" scale="${fmt(ar.scale)}" offsetY="${fmt(ar.offsetY)}" tint="${escapeXml(ar.tintHex)}" animation="${ar.animation.name}" />""")
            appendLine("</lensmind-filter>")
        }
    }

    private fun fmt(v: Float): String = String.format(java.util.Locale.US, "%.2f", v)

    private fun escapeXml(input: String): String =
        input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private fun safeNextText(parser: XmlPullParser): String {
        return if (parser.next() == XmlPullParser.TEXT) {
            val res = parser.text ?: ""
            parser.nextTag()
            res
        } else {
            ""
        }
    }

    private fun parseFloatRange(
        raw: String,
        min: Float,
        max: Float,
        line: Int,
        field: String,
        errors: MutableList<String>,
        fallback: Float
    ): Float {
        val v = raw.toFloatOrNull()
        if (v == null) {
            errors.add("Line $line: <$field> '$raw' is not a valid number.")
            return fallback
        }
        if (v < min || v > max) {
            errors.add("Line $line: <$field> value $v is out of range [$min .. $max].")
        }
        return v.coerceIn(min, max)
    }

    private inline fun <reified T : Enum<T>> parseEnumOrError(
        raw: String,
        line: Int,
        field: String,
        errors: MutableList<String>
    ): T? {
        return try {
            enumValueOf<T>(raw.trim().uppercase())
        } catch (_: Exception) {
            val allowed = enumValues<T>().joinToString(", ") { it.name }
            errors.add("Line $line: <$field> '$raw' is invalid. Allowed: $allowed")
            null
        }
    }
}
