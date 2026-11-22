package com.example.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import com.example.model.ActiveFilterLayer
import com.example.model.ArOverlayType
import com.example.model.AwbBias
import com.example.model.CameraMode
import com.example.model.FaceTelemetry
import com.example.model.FilterAdjustments
import com.example.model.GCamCameraConfig
import com.example.model.LightAdaptationState
import com.example.model.ProManualParams
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

object FilterBitmapProcessor {

    fun mergeAdjustments(
        layers: List<ActiveFilterLayer>,
        pro: ProManualParams,
        light: LightAdaptationState,
        greeneryBias: Float = 0f,
        darknessBias: Float = 0f,
        activeConfig: GCamCameraConfig? = null,
        cameraMode: CameraMode = CameraMode.PHOTO,
        isFrontCamera: Boolean = false
    ): FilterAdjustments {
        // 1. Base computational pipeline from GCam 8.5 XML Config (+ mode/lens overrides)
        val cfgParams = activeConfig?.params
        val modeOv = activeConfig?.modeOverrides?.get(cameraMode.name)
        val lensOv = if (isFrontCamera) activeConfig?.modeOverrides?.get("FRONT") else null

        val cfgHdr = modeOv?.hdrPlusStrength ?: cfgParams?.hdrPlusStrength ?: 0f
        val cfgExposure = lensOv?.exposureCompensation ?: modeOv?.exposureCompensation ?: cfgParams?.exposureCompensation ?: 0f
        val cfgContrast = modeOv?.contrast ?: cfgParams?.contrast ?: 1.0f
        val cfgSat = modeOv?.saturation ?: cfgParams?.saturation ?: 1.0f
        val cfgSharp = modeOv?.sharpness ?: cfgParams?.sharpness ?: 0.0f
        val cfgNight = modeOv?.nightSightStrength ?: cfgParams?.nightSightStrength ?: 0.0f
        val cfgAwb = modeOv?.awbBias ?: cfgParams?.awbBias ?: AwbBias.AUTO

        val awbTempBias = when (cfgAwb) {
            AwbBias.WARM_TUNGSTEN -> 0.14f
            AwbBias.COOL_SHADE -> -0.14f
            AwbBias.DAYLIGHT -> 0.04f
            else -> 0f
        }

        val nightBoost = if (cameraMode == CameraMode.NIGHT) cfgNight * 0.16f else 0f

        var brightness = pro.brightness + light.adaptiveBrightnessOffset + (darknessBias * 0.22f) +
            (cfgExposure * 0.12f) + nightBoost
        var contrast = pro.contrast * cfgContrast
        var saturation = pro.saturation * cfgSat
        var hue = 0f
        var temperature = ((pro.temperatureKelvin - 5500f) / 3000f).coerceIn(-1f, 1f) +
            (cfgParams?.temperatureOffset ?: 0f) + awbTempBias + (cfgParams?.skinToneWarmth ?: 0f) * 0.35f
        val tint = cfgParams?.tintOffset ?: 0f
        var greenery = pro.greenery + greeneryBias
        val vibrance = cfgParams?.vibrance ?: 0f
        var blur = 0f
        var grain = maxOf(pro.grain, cfgParams?.grain ?: 0f)
        var vignette = (pro.vignette + (cfgParams?.vignette ?: 0f)).coerceIn(0f, 1f)
        var skinSmooth = if (cameraMode == CameraMode.PORTRAIT) {
            maxOf(cfgParams?.portraitBlurStrength ?: 0f, 0f) * 0.35f
        } else 0f
        var highlights = pro.highlights + light.adaptiveHighlightRecovery + (cfgParams?.highlights ?: 0f) - (cfgHdr * 0.12f)
        var shadows = pro.shadows + light.adaptiveShadowLift + (darknessBias * 0.35f) + (cfgParams?.shadows ?: 0f) + (cfgHdr * 0.14f)
        val blackLevel = cfgParams?.blackLevel ?: 0f
        val toneGamma = cfgParams?.toneMappingGamma ?: 1.0f
        val sharpness = (pro.sharpness + cfgSharp).coerceIn(0f, 1.5f)
        val lumaNr = cfgParams?.lumaNoiseReduction ?: 0f
        val rgbR = cfgParams?.rgbGainR ?: 1f
        val rgbG = cfgParams?.rgbGainG ?: 1f
        val rgbB = cfgParams?.rgbGainB ?: 1f
        var glitch = 0f
        var duotonePrimary: String? = null
        var duotoneSecondary: String? = null

        // 2. Stack Filter Packs cleanly on top of the GCam Config base
        for (layer in layers) {
            val w = layer.intensity.coerceIn(0f, 1f)
            val adj = layer.filter.adjustments
            brightness += adj.brightness * w
            contrast *= (1f + (adj.contrast - 1f) * w)
            saturation *= (1f + (adj.saturation - 1f) * w)
            hue += adj.hue * w
            temperature += adj.temperature * w
            greenery += adj.greenery * w
            blur = maxOf(blur, adj.blur * w)
            grain = maxOf(grain, adj.grain * w)
            vignette = (vignette + adj.vignette * w).coerceIn(0f, 1f)
            skinSmooth = maxOf(skinSmooth, adj.skinSmooth * w)
            highlights += adj.highlights * w
            shadows += adj.shadows * w
            glitch = maxOf(glitch, adj.glitch * w)
            if (adj.duotonePrimary != null && adj.duotoneSecondary != null) {
                duotonePrimary = adj.duotonePrimary
                duotoneSecondary = adj.duotoneSecondary
            }
        }

        return FilterAdjustments(
            brightness = brightness.coerceIn(-0.8f, 0.8f),
            contrast = contrast.coerceIn(0.3f, 2.2f),
            saturation = (saturation + vibrance * 0.45f).coerceIn(0f, 2.4f),
            hue = hue.coerceIn(-180f, 180f),
            temperature = temperature.coerceIn(-1f, 1f),
            tint = tint.coerceIn(-0.5f, 0.5f),
            greenery = greenery.coerceIn(-1f, 1f),
            vibrance = vibrance.coerceIn(-0.5f, 1f),
            blur = blur.coerceIn(0f, 1f),
            grain = grain.coerceIn(0f, 1f),
            vignette = vignette.coerceIn(0f, 1f),
            skinSmooth = skinSmooth.coerceIn(0f, 1f),
            highlights = highlights.coerceIn(-1f, 1f),
            shadows = shadows.coerceIn(-1f, 1f),
            blackLevel = blackLevel.coerceIn(-0.2f, 0.2f),
            toneMappingGamma = toneGamma.coerceIn(0.6f, 1.6f),
            hdrPlusStrength = cfgHdr.coerceIn(0f, 1.5f),
            sharpness = sharpness,
            lumaNoiseReduction = lumaNr.coerceIn(0f, 1f),
            rgbGainR = rgbR.coerceIn(0.6f, 1.4f),
            rgbGainG = rgbG.coerceIn(0.6f, 1.4f),
            rgbGainB = rgbB.coerceIn(0.6f, 1.4f),
            glitch = glitch.coerceIn(0f, 1f),
            duotonePrimary = duotonePrimary,
            duotoneSecondary = duotoneSecondary
        )
    }

    fun buildColorMatrix(adj: FilterAdjustments): ColorMatrix {
        val cm = ColorMatrix()

        // Saturation
        val satMatrix = ColorMatrix().apply { setSaturation(adj.saturation) }
        cm.postConcat(satMatrix)

        // Contrast, Brightness & Black Level
        val scale = adj.contrast
        val blackOffset = adj.blackLevel * 255f
        val translate = (-0.5f * scale + 0.5f + adj.brightness) * 255f + blackOffset
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        cm.postConcat(contrastMatrix)

        // GCam RGB Gains & Tint
        if (abs(adj.rgbGainR - 1f) > 0.01f || abs(adj.rgbGainG - 1f) > 0.01f ||
            abs(adj.rgbGainB - 1f) > 0.01f || abs(adj.tint) > 0.01f
        ) {
            val tintG = adj.tint * 24f
            val gainMatrix = ColorMatrix(
                floatArrayOf(
                    adj.rgbGainR, 0f, 0f, 0f, 0f,
                    0f, adj.rgbGainG, 0f, 0f, -tintG,
                    0f, 0f, adj.rgbGainB, 0f, tintG * 0.5f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            cm.postConcat(gainMatrix)
        }

        // Temperature (warm amber vs cool cyan)
        if (abs(adj.temperature) > 0.01f) {
            val t = adj.temperature * 36f
            val tempMatrix = ColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, t,
                    0f, 1f, 0f, 0f, t * 0.25f,
                    0f, 0f, 1f, 0f, -t,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            cm.postConcat(tempMatrix)
        }

        // Greenery channel boost
        if (abs(adj.greenery) > 0.01f) {
            val gBoost = adj.greenery * 0.35f
            val greenMatrix = ColorMatrix(
                floatArrayOf(
                    1f - gBoost * 0.15f, 0f, 0f, 0f, 0f,
                    0f, 1f + gBoost, 0f, 0f, gBoost * 14f,
                    0f, 0f, 1f - gBoost * 0.10f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            cm.postConcat(greenMatrix)
        }

        // Hue shift
        if (abs(adj.hue) > 1f) {
            val rad = Math.toRadians(adj.hue.toDouble())
            val cosV = cos(rad).toFloat()
            val sinV = sin(rad).toFloat()
            val lumR = 0.213f
            val lumG = 0.715f
            val lumB = 0.072f
            val hueMat = ColorMatrix(
                floatArrayOf(
                    lumR + cosV * (1 - lumR) + sinV * (-lumR), lumG + cosV * (-lumG) + sinV * (-lumG), lumB + cosV * (-lumB) + sinV * (1 - lumB), 0f, 0f,
                    lumR + cosV * (-lumR) + sinV * 0.143f, lumG + cosV * (1 - lumG) + sinV * 0.140f, lumB + cosV * (-lumB) + sinV * (-0.283f), 0f, 0f,
                    lumR + cosV * (-lumR) + sinV * (-(1 - lumR)), lumG + cosV * (-lumG) + sinV * lumG, lumB + cosV * (1 - lumB) + sinV * lumB, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            cm.postConcat(hueMat)
        }

        return cm
    }

    fun applyFullPipeline(
        source: Bitmap,
        adj: FilterAdjustments,
        faces: List<FaceTelemetry> = emptyList(),
        arLayers: List<ActiveFilterLayer> = emptyList(),
        isHdr: Boolean = false,
        isNight: Boolean = false,
        isDocumentScan: Boolean = false,
        isPortraitBokeh: Boolean = false
    ): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        val effectiveAdj = when {
            isDocumentScan -> adj.copy(contrast = maxOf(adj.contrast, 1.55f), saturation = 0.15f, highlights = 0.22f)
            isNight -> adj.copy(brightness = (adj.brightness + 0.14f).coerceIn(-0.8f, 0.8f), shadows = (adj.shadows + 0.36f).coerceIn(-1f, 1f))
            isHdr -> adj.copy(highlights = (adj.highlights - 0.25f).coerceIn(-1f, 1f), shadows = (adj.shadows + 0.28f).coerceIn(-1f, 1f), saturation = (adj.saturation * 1.12f).coerceIn(0f, 2.4f))
            else -> adj
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(buildColorMatrix(effectiveAdj))
        }
        canvas.drawBitmap(source, 0f, 0f, paint)

        // Shadows / Highlights / Gamma + Duotone + Glitch + Grain
        if (abs(effectiveAdj.shadows) > 0.04f || abs(effectiveAdj.highlights) > 0.04f ||
            abs(effectiveAdj.toneMappingGamma - 1f) > 0.03f ||
            effectiveAdj.duotonePrimary != null || effectiveAdj.glitch > 0.05f || effectiveAdj.grain > 0.05f
        ) {
            applyPixelPasses(out, effectiveAdj)
        }

        // Portrait / Blur bokeh / Luma NR soft pass
        if (isPortraitBokeh || effectiveAdj.skinSmooth > 0.2f || effectiveAdj.blur > 0.1f || effectiveAdj.lumaNoiseReduction > 0.55f) {
            val softStr = maxOf(
                if (isPortraitBokeh) 0.55f else 0f,
                effectiveAdj.blur,
                effectiveAdj.skinSmooth * 0.4f,
                (effectiveAdj.lumaNoiseReduction - 0.5f).coerceAtLeast(0f) * 0.35f
            )
            applySoftGlowOrBokeh(out, softStr, faces)
        }

        // Vignette
        if (effectiveAdj.vignette > 0.02f) {
            drawVignette(canvas, out.width.toFloat(), out.height.toFloat(), effectiveAdj.vignette)
        }

        // AR face overlays baked onto captured image
        for (layer in arLayers) {
            if (layer.filter.arOverlay.type != ArOverlayType.NONE) {
                drawArStickersToCanvas(canvas, out.width.toFloat(), out.height.toFloat(), faces, layer.filter.arOverlay)
            }
        }

        return out
    }

    fun applyShareWatermark(source: Bitmap, label: String = "Anamol Camera"): Bitmap {
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val w = out.width.toFloat()
        val h = out.height.toFloat()
        val textSizePx = (minOf(w, h) * 0.028f).coerceIn(22f, 56f)

        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(168, 9, 13, 24)
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, 0, 229, 255)
            style = Paint.Style.STROKE
            strokeWidth = (textSizePx * 0.08f).coerceAtLeast(2f)
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = textSizePx
            isFakeBoldText = true
        }
        val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 229, 255)
            style = Paint.Style.FILL
        }

        val textWidth = textPaint.measureText(label)
        val padH = textSizePx * 0.9f
        val padV = textSizePx * 0.55f
        val margin = minOf(w, h) * 0.03f
        val iconWidth = textSizePx * 1.15f
        val rectW = textWidth + padH * 2f + iconWidth
        val rectH = textSizePx + padV * 2f
        val rect = android.graphics.RectF(w - margin - rectW, h - margin - rectH, w - margin, h - margin)

        canvas.drawRoundRect(rect, rectH / 2f, rectH / 2f, badgePaint)
        canvas.drawRoundRect(rect, rectH / 2f, rectH / 2f, borderPaint)

        // 4-point spark emblem
        val cx = rect.left + padH + textSizePx * 0.38f
        val cy = rect.centerY()
        val sr = textSizePx * 0.42f
        val ir = sr * 0.30f
        val sparkPath = Path().apply {
            moveTo(cx, cy - sr)
            lineTo(cx + ir, cy - ir)
            lineTo(cx + sr, cy)
            lineTo(cx + ir, cy + ir)
            lineTo(cx, cy + sr)
            lineTo(cx - ir, cy + ir)
            lineTo(cx - sr, cy)
            lineTo(cx - ir, cy - ir)
            close()
        }
        canvas.drawPath(sparkPath, sparkPaint)
        canvas.drawText(label, rect.left + padH + iconWidth, rect.centerY() + textSizePx * 0.35f, textPaint)
        return out
    }

    fun applyMagicEnhance(source: Bitmap): Bitmap {
        val adj = FilterAdjustments(
            brightness = 0.05f,
            contrast = 1.18f,
            saturation = 1.20f,
            greenery = 0.14f,
            highlights = -0.15f,
            shadows = 0.22f,
            vignette = 0.12f
        )
        return applyFullPipeline(source, adj, isHdr = true)
    }

    fun applyCrispUpscale2x(source: Bitmap): Bitmap {
        val targetW = (source.width * 2).coerceAtMost(3200)
        val targetH = (source.height * 2).coerceAtMost(3200)
        val scaled = Bitmap.createScaledBitmap(source, targetW, targetH, true)
        val adj = FilterAdjustments(contrast = 1.10f, saturation = 1.06f)
        val out = applyFullPipeline(scaled, adj)
        if (scaled !== source) scaled.recycle()
        return out
    }

    fun applySpotEraser(source: Bitmap, normX: Float, normY: Float, radiusFraction: Float = 0.06f): Bitmap {
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        val cx = (normX * out.width).toInt().coerceIn(0, out.width - 1)
        val cy = (normY * out.height).toInt().coerceIn(0, out.height - 1)
        val r = (minOf(out.width, out.height) * radiusFraction).toInt().coerceIn(8, 120)

        // Sample ring outside radius to cleanly heal/inpaint spot
        val ringSamples = mutableListOf<Int>()
        for (deg in 0 until 360 step 20) {
            val rad = Math.toRadians(deg.toDouble())
            val sx = (cx + (r + 6) * cos(rad)).toInt().coerceIn(0, out.width - 1)
            val sy = (cy + (r + 6) * sin(rad)).toInt().coerceIn(0, out.height - 1)
            ringSamples.add(out.getPixel(sx, sy))
        }
        if (ringSamples.isEmpty()) return out

        val avgR = ringSamples.sumOf { Color.red(it) } / ringSamples.size
        val avgG = ringSamples.sumOf { Color.green(it) } / ringSamples.size
        val avgB = ringSamples.sumOf { Color.blue(it) } / ringSamples.size

        for (y in (cy - r).coerceAtLeast(0)..(cy + r).coerceAtMost(out.height - 1)) {
            for (x in (cx - r).coerceAtLeast(0)..(cx + r).coerceAtMost(out.width - 1)) {
                val dx = (x - cx).toFloat()
                val dy = (y - cy).toFloat()
                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                if (dist <= r) {
                    val blend = (1f - (dist / r)).coerceIn(0f, 1f)
                    val orig = out.getPixel(x, y)
                    val nr = (Color.red(orig) * (1f - blend) + avgR * blend).toInt().coerceIn(0, 255)
                    val ng = (Color.green(orig) * (1f - blend) + avgG * blend).toInt().coerceIn(0, 255)
                    val nb = (Color.blue(orig) * (1f - blend) + avgB * blend).toInt().coerceIn(0, 255)
                    out.setPixel(x, y, Color.rgb(nr, ng, nb))
                }
            }
        }
        return out
    }

    fun applyBackgroundBokeh(source: Bitmap, faces: List<FaceTelemetry> = emptyList()): Bitmap {
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        val small = Bitmap.createScaledBitmap(out, (out.width / 10).coerceAtLeast(16), (out.height / 10).coerceAtLeast(16), true)
        val blurred = Bitmap.createScaledBitmap(small, out.width, out.height, true)
        small.recycle()

        val focusCx = faces.firstOrNull()?.boundsNormalized?.centerX() ?: 0.5f
        val focusCy = faces.firstOrNull()?.boundsNormalized?.centerY() ?: 0.45f

        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val w = out.width.toFloat()
        val h = out.height.toFloat()
        val maskShader = RadialGradient(
            focusCx * w, focusCy * h, maxOf(w, h) * 0.52f,
            intArrayOf(Color.TRANSPARENT, Color.argb(180, 0, 0, 0), Color.BLACK),
            floatArrayOf(0.28f, 0.65f, 1f),
            Shader.TileMode.CLAMP
        )
        // Draw blurred over edges
        val temp = Bitmap.createBitmap(out.width, out.height, Bitmap.Config.ARGB_8888)
        val tCanvas = Canvas(temp)
        tCanvas.drawBitmap(blurred, 0f, 0f, null)
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
        paint.shader = maskShader
        tCanvas.drawRect(0f, 0f, w, h, paint)
        paint.xfermode = null
        paint.shader = null
        canvas.drawBitmap(temp, 0f, 0f, null)
        temp.recycle()
        blurred.recycle()
        return out
    }

    fun createCollage(bitmaps: List<Bitmap>, layoutIndex: Int, gapPx: Int = 18, bgColor: Int = Color.parseColor("#0B0F17")): Bitmap {
        val size = 1440
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(bgColor)
        if (bitmaps.isEmpty()) return out

        val rects = when (bitmaps.size) {
            1 -> listOf(android.graphics.Rect(gapPx, gapPx, size - gapPx, size - gapPx))
            2 -> if (layoutIndex % 2 == 0) {
                val half = size / 2
                listOf(
                    android.graphics.Rect(gapPx, gapPx, half - gapPx / 2, size - gapPx),
                    android.graphics.Rect(half + gapPx / 2, gapPx, size - gapPx, size - gapPx)
                )
            } else {
                val half = size / 2
                listOf(
                    android.graphics.Rect(gapPx, gapPx, size - gapPx, half - gapPx / 2),
                    android.graphics.Rect(gapPx, half + gapPx / 2, size - gapPx, size - gapPx)
                )
            }
            3 -> {
                val half = size / 2
                listOf(
                    android.graphics.Rect(gapPx, gapPx, half - gapPx / 2, size - gapPx),
                    android.graphics.Rect(half + gapPx / 2, gapPx, size - gapPx, half - gapPx / 2),
                    android.graphics.Rect(half + gapPx / 2, half + gapPx / 2, size - gapPx, size - gapPx)
                )
            }
            else -> {
                val half = size / 2
                listOf(
                    android.graphics.Rect(gapPx, gapPx, half - gapPx / 2, half - gapPx / 2),
                    android.graphics.Rect(half + gapPx / 2, gapPx, size - gapPx, half - gapPx / 2),
                    android.graphics.Rect(gapPx, half + gapPx / 2, half - gapPx / 2, size - gapPx),
                    android.graphics.Rect(half + gapPx / 2, half + gapPx / 2, size - gapPx, size - gapPx)
                )
            }
        }

        rects.forEachIndexed { idx, dst ->
            val srcBmp = bitmaps[idx % bitmaps.size]
            val srcRect = centerCropSrcRect(srcBmp.width, srcBmp.height, dst.width().toFloat() / dst.height().toFloat())
            canvas.drawBitmap(srcBmp, srcRect, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        return out
    }

    private fun centerCropSrcRect(w: Int, h: Int, targetAspect: Float): android.graphics.Rect {
        val currentAspect = w.toFloat() / h.toFloat()
        return if (currentAspect > targetAspect) {
            val newW = (h * targetAspect).toInt()
            val x = (w - newW) / 2
            android.graphics.Rect(x, 0, x + newW, h)
        } else {
            val newH = (w / targetAspect).toInt()
            val y = (h - newH) / 2
            android.graphics.Rect(0, y, w, y + newH)
        }
    }

    private fun applyPixelPasses(bitmap: Bitmap, adj: FilterAdjustments) {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val hasDuotone = adj.duotonePrimary != null && adj.duotoneSecondary != null
        val pColor = if (hasDuotone) Color.parseColor(adj.duotonePrimary) else 0
        val sColor = if (hasDuotone) Color.parseColor(adj.duotoneSecondary) else 0
        val pR = Color.red(pColor); val pG = Color.green(pColor); val pB = Color.blue(pColor)
        val sR = Color.red(sColor); val sG = Color.green(sColor); val sB = Color.blue(sColor)

        val grainAmp = (adj.grain * 38f).toInt()
        val glitchShift = (adj.glitch * (w * 0.018f)).toInt()
        val rng = Random(42)

        val origCopy = if (glitchShift > 0) pixels.clone() else null

        for (y in 0 until h) {
            val rowOffset = y * w
            val rowJitter = if (glitchShift > 0 && y % 14 < 3) glitchShift else 0
            for (x in 0 until w) {
                val idx = rowOffset + x
                var r = Color.red(pixels[idx])
                var g = Color.green(pixels[idx])
                var b = Color.blue(pixels[idx])

                if (origCopy != null && glitchShift > 0) {
                    val rx = (x + glitchShift + rowJitter).coerceIn(0, w - 1)
                    val bx = (x - glitchShift).coerceIn(0, w - 1)
                    r = Color.red(origCopy[rowOffset + rx])
                    b = Color.blue(origCopy[rowOffset + bx])
                }

                val luma = (0.299f * r + 0.587f * g + 0.114f * b) / 255f

                // Tone mapping gamma
                if (abs(adj.toneMappingGamma - 1f) > 0.03f) {
                    val invGamma = (1f / adj.toneMappingGamma).coerceIn(0.5f, 1.8f)
                    r = (r / 255f).pow(invGamma).times(255f).toInt()
                    g = (g / 255f).pow(invGamma).times(255f).toInt()
                    b = (b / 255f).pow(invGamma).times(255f).toInt()
                }

                // Shadows & Highlights
                if (abs(adj.shadows) > 0.02f && luma < 0.45f) {
                    val factor = (1f - luma / 0.45f) * adj.shadows * 58f
                    r = (r + factor).toInt()
                    g = (g + factor).toInt()
                    b = (b + factor).toInt()
                }
                if (abs(adj.highlights) > 0.02f && luma > 0.55f) {
                    val factor = ((luma - 0.55f) / 0.45f) * adj.highlights * 58f
                    r = (r + factor).toInt()
                    g = (g + factor).toInt()
                    b = (b + factor).toInt()
                }

                // Duotone blend
                if (hasDuotone) {
                    val dr = (pR * (1f - luma) + sR * luma)
                    val dg = (pG * (1f - luma) + sG * luma)
                    val db = (pB * (1f - luma) + sB * luma)
                    r = (r * 0.55f + dr * 0.45f).toInt()
                    g = (g * 0.55f + dg * 0.45f).toInt()
                    b = (b * 0.55f + db * 0.45f).toInt()
                }

                // Film grain
                if (grainAmp > 0) {
                    val noise = rng.nextInt(-grainAmp, grainAmp + 1)
                    r += noise; g += noise; b += noise
                }

                pixels[idx] = Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
            }
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    private fun applySoftGlowOrBokeh(bitmap: Bitmap, strength: Float, faces: List<FaceTelemetry>) {
        if (strength <= 0.05f) return
        val small = Bitmap.createScaledBitmap(bitmap, (bitmap.width / 8).coerceAtLeast(16), (bitmap.height / 8).coerceAtLeast(16), true)
        val soft = Bitmap.createScaledBitmap(small, bitmap.width, bitmap.height, true)
        small.recycle()
        val canvas = Canvas(bitmap)
        val alpha = (strength * 95f).toInt().coerceIn(10, 110)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.alpha = alpha }
        canvas.drawBitmap(soft, 0f, 0f, paint)
        soft.recycle()
    }

    private fun drawVignette(canvas: Canvas, w: Float, h: Float, strength: Float) {
        val radius = maxOf(w, h) * 0.72f
        val gradient = RadialGradient(
            w / 2f, h / 2f, radius,
            intArrayOf(Color.TRANSPARENT, Color.argb((strength * 195).toInt().coerceIn(0, 210), 0, 0, 0)),
            floatArrayOf(0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = gradient }
        canvas.drawRect(0f, 0f, w, h, paint)
    }

    private fun drawArStickersToCanvas(
        canvas: Canvas,
        w: Float,
        h: Float,
        faces: List<FaceTelemetry>,
        config: com.example.model.ArOverlayConfig
    ) {
        val tint = runCatching { Color.parseColor(config.tintHex) }.getOrDefault(Color.CYAN)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tint
            style = Paint.Style.STROKE
            strokeWidth = maxOf(4f, w * 0.008f)
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(110, Color.red(tint), Color.green(tint), Color.blue(tint))
            style = Paint.Style.FILL
        }

        for (face in faces) {
            val fx = face.boundsNormalized.centerX() * w
            val fy = face.boundsNormalized.centerY() * h
            val fw = face.boundsNormalized.width() * w * config.scale
            val fh = face.boundsNormalized.height() * h * config.scale

            canvas.save()
            canvas.rotate(-face.headRollDeg, fx, fy)

            when (config.type) {
                ArOverlayType.NEON_VISOR, ArOverlayType.SUNGLASSES -> {
                    val eyeY = ((face.leftEye?.y ?: face.boundsNormalized.top + 0.35f * face.boundsNormalized.height()) +
                        (face.rightEye?.y ?: face.boundsNormalized.top + 0.35f * face.boundsNormalized.height())) * 0.5f * h
                    val visorRect = android.graphics.RectF(fx - fw * 0.42f, eyeY - fh * 0.12f, fx + fw * 0.42f, eyeY + fh * 0.12f)
                    canvas.drawRoundRect(visorRect, fh * 0.06f, fh * 0.06f, fillPaint)
                    canvas.drawRoundRect(visorRect, fh * 0.06f, fh * 0.06f, paint)
                }
                ArOverlayType.CAT_EARS -> {
                    val topY = face.boundsNormalized.top * h
                    val leftEar = Path().apply {
                        moveTo(fx - fw * 0.36f, topY)
                        lineTo(fx - fw * 0.24f, topY - fh * 0.28f)
                        lineTo(fx - fw * 0.06f, topY - fh * 0.04f)
                        close()
                    }
                    val rightEar = Path().apply {
                        moveTo(fx + fw * 0.36f, topY)
                        lineTo(fx + fw * 0.24f, topY - fh * 0.28f)
                        lineTo(fx + fw * 0.06f, topY - fh * 0.04f)
                        close()
                    }
                    canvas.drawPath(leftEar, fillPaint); canvas.drawPath(leftEar, paint)
                    canvas.drawPath(rightEar, fillPaint); canvas.drawPath(rightEar, paint)
                }
                ArOverlayType.HALO -> {
                    val haloY = face.boundsNormalized.top * h - fh * 0.18f
                    val haloRect = android.graphics.RectF(fx - fw * 0.35f, haloY - fh * 0.07f, fx + fw * 0.35f, haloY + fh * 0.07f)
                    canvas.drawOval(haloRect, paint)
                }
                ArOverlayType.HEART_BLUSH -> {
                    face.leftCheek?.let { canvas.drawCircle(it.x * w, it.y * h, fw * 0.08f, fillPaint) }
                    face.rightCheek?.let { canvas.drawCircle(it.x * w, it.y * h, fw * 0.08f, fillPaint) }
                }
                ArOverlayType.CROWN -> {
                    val topY = face.boundsNormalized.top * h - fh * 0.05f
                    val crown = Path().apply {
                        moveTo(fx - fw * 0.3f, topY)
                        lineTo(fx - fw * 0.32f, topY - fh * 0.22f)
                        lineTo(fx - fw * 0.12f, topY - fh * 0.10f)
                        lineTo(fx, topY - fh * 0.26f)
                        lineTo(fx + fw * 0.12f, topY - fh * 0.10f)
                        lineTo(fx + fw * 0.32f, topY - fh * 0.22f)
                        lineTo(fx + fw * 0.3f, topY)
                        close()
                    }
                    canvas.drawPath(crown, fillPaint); canvas.drawPath(crown, paint)
                }
                ArOverlayType.CYBER_HUD -> {
                    val r = android.graphics.RectF(fx - fw * 0.5f, fy - fh * 0.5f, fx + fw * 0.5f, fy + fh * 0.5f)
                    canvas.drawRect(r, paint)
                }
                ArOverlayType.NONE -> Unit
            }
            canvas.restore()
        }
    }
}
