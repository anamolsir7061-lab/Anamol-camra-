package com.example.ai

import android.graphics.Bitmap
import android.util.Base64
import com.example.model.AppSettings
import com.example.model.CameraFilter
import com.example.model.CameraMode
import com.example.model.ChatMessage
import com.example.model.GCamCameraConfig
import com.example.model.LightAdaptationState
import com.example.model.ProManualParams
import com.example.security.SecureKeyStoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

data class GeminiToolCall(
    val name: String,
    val args: JSONObject
)

sealed interface GeminiResult<out T> {
    data class Success<T>(val data: T) : GeminiResult<T>
    data class Failure(val userMessage: String, val isKeyError: Boolean = false, val isQuotaError: Boolean = false) : GeminiResult<Nothing>
}

data class CameraBrainResponse(
    val toolCalls: List<GeminiToolCall>,
    val textNote: String
)

data class SettingsChatResponse(
    val replyText: String,
    val toolCalls: List<GeminiToolCall>
)

class GeminiApiClient(private val keyStore: SecureKeyStoreManager) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun validateApiKey(rawKey: String, model: String): GeminiResult<String> = withContext(Dispatchers.IO) {
        val trimmed = rawKey.trim()
        if (trimmed.isBlank()) {
            return@withContext GeminiResult.Failure("Please paste your Gemini API key first.", isKeyError = true)
        }
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", "Reply with OK")))
            }))
            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", 8)
            })
        }
        when (val res = executeRequest(trimmed, model, body)) {
            is GeminiResult.Success -> GeminiResult.Success("Connected to $model")
            is GeminiResult.Failure -> res
        }
    }

    suspend fun analyzePreviewFrame(
        frameBitmap: Bitmap,
        settings: AppSettings,
        currentMode: CameraMode,
        proParams: ProManualParams,
        activeFilterIds: List<String>,
        availableFilters: List<CameraFilter>,
        activeConfigId: String,
        availableConfigs: List<GCamCameraConfig>,
        lightState: LightAdaptationState,
        faceCount: Int,
        avgSmile: Float
    ): GeminiResult<CameraBrainResponse> = withContext(Dispatchers.IO) {
        val apiKey = keyStore.getApiKey()
        if (apiKey.isNullOrBlank()) {
            return@withContext GeminiResult.Failure("Gemini API key missing. Add your key in Settings.", isKeyError = true)
        }

        val scaled = scaleBitmapForVision(frameBitmap, 480)
        val jpegBytes = ByteArrayOutputStream().use { bos ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 68, bos)
            bos.toByteArray()
        }
        if (scaled !== frameBitmap) scaled.recycle()
        val base64Jpeg = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)

        val filterCatalog = availableFilters.joinToString(", ") { "${it.id}(${it.category.name})" }
        val configCatalog = availableConfigs.joinToString(", ") { "${it.id}(${it.name})" }
        val systemText = buildString {
            appendLine("You are Anamol Camera AI, a SILENT DSLR Max + GCam 8.5 + Snapchat Camera Brain.")
            appendLine("Personality: ${settings.aiPersonality.label} - ${settings.aiPersonality.systemHint}")
            appendLine("Language: ${settings.aiLanguage.promptInstruction}")
            appendLine("Guidance Strictness: ${settings.guidanceStrictness.label}. Auto Mode: ${if (settings.autoModeEnabled) "ON (${settings.autoAggressiveness.label})" else "OFF (Suggest only)"}.")
            appendLine("User Saved Style Preferences: preferredStyle=${settings.preferredPhotoStyle.label}, greeneryBias=${settings.greeneryBias}, darknessReductionBias=${settings.darknessReductionBias}, portraitVintage=${settings.portraitVintageLook}, skinSmooth=${settings.skinSmoothingLevel}.")
            appendLine("Available GCam 8.5 Base Config IDs: $configCatalog (active=$activeConfigId).")
            appendLine("Available XML Filter IDs: $filterCatalog.")
            appendLine("ALWAYS call `set_composition_guidance` to classify the scene & give concise visual framing guidance (3-8 words).")
            appendLine("If improvements help (GCam base config like night_sight_boost in dark scenes, tweak_camera_config, filter, exposure, greenery, shadows, portrait/night/doc mode), emit tool calls. Only call `capture_photo` when Auto Mode is ON with FULL_AUTO or scene has high composition + open eyes + smile.")
        }

        val telemetryText = "Mode=${currentMode.name}, Luma=${lightState.meanLuma.toInt()}/255 (${lightState.conditionLabel}), " +
            "Faces=$faceCount, Smile=${"%.2f".format(avgSmile)}, ActiveConfig=$activeConfigId, ActiveFilters=$activeFilterIds, " +
            "EV=${proParams.exposureEv}, Contrast=${proParams.contrast}, Sat=${proParams.saturation}, Greenery=${proParams.greenery}, Shadows=${proParams.shadows}."

        val body = JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", systemText)))
            })
            put("contents", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().apply {
                    put(JSONObject().put("inlineData", JSONObject().apply {
                        put("mimeType", "image/jpeg")
                        put("data", base64Jpeg)
                    }))
                    put(JSONObject().put("text", "Analyze viewfinder frame and telemetry: $telemetryText"))
                })
            }))
            put("tools", JSONArray().put(JSONObject().put("functionDeclarations", cameraFunctionDeclarations())))
            put("toolConfig", JSONObject().apply {
                put("functionCallingConfig", JSONObject().apply {
                    put("mode", "AUTO")
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.35)
                put("maxOutputTokens", 512)
            })
        }

        when (val res = executeRequest(apiKey, settings.geminiModel, body)) {
            is GeminiResult.Failure -> res
            is GeminiResult.Success -> parseCameraResponse(res.data)
        }
    }

    suspend fun chatForSettingsCustomization(
        history: List<ChatMessage>,
        userPrompt: String,
        settings: AppSettings,
        availableFilters: List<CameraFilter>
    ): GeminiResult<SettingsChatResponse> = withContext(Dispatchers.IO) {
        val apiKey = keyStore.getApiKey()
        if (apiKey.isNullOrBlank()) {
            return@withContext GeminiResult.Failure("Paste your Gemini API key above to chat with the AI Camera Brain.", isKeyError = true)
        }

        val filterIds = availableFilters.joinToString(", ") { it.id }
        val systemText = """
            You are Anamol Camera's silent Camera Brain Customization Assistant.
            Help the user tune their camera preferences and remember them permanently.
            Current settings: personality=${settings.aiPersonality.name}, strictness=${settings.guidanceStrictness.name},
            autoAggressiveness=${settings.autoAggressiveness.name}, preferredPhotoStyle=${settings.preferredPhotoStyle.name},
            language=${settings.aiLanguage.name}, greeneryBias=${settings.greeneryBias},
            darknessReductionBias=${settings.darknessReductionBias}, portraitVintageLook=${settings.portraitVintageLook},
            skinSmoothingLevel=${settings.skinSmoothingLevel}, voiceOutputEnabled=${settings.voiceOutputEnabled}.
            Available filters: $filterIds.
            When the user asks to change any camera behavior, color bias, greenery, darkness, portrait vintage look, personality, language, or style, ALWAYS call `update_user_preferences` with the matching fields AND confirm concisely in ${settings.aiLanguage.label}.
        """.trimIndent()

        val contents = JSONArray()
        history.takeLast(8).forEach { msg ->
            contents.put(JSONObject().apply {
                put("role", if (msg.isUser) "user" else "model")
                put("parts", JSONArray().put(JSONObject().put("text", msg.text)))
            })
        }
        contents.put(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().put(JSONObject().put("text", userPrompt)))
        })

        val body = JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", systemText)))
            })
            put("contents", contents)
            put("tools", JSONArray().put(JSONObject().put("functionDeclarations", settingsFunctionDeclarations())))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.4)
                put("maxOutputTokens", 400)
            })
        }

        when (val res = executeRequest(apiKey, settings.geminiModel, body)) {
            is GeminiResult.Failure -> res
            is GeminiResult.Success -> parseSettingsChatResponse(res.data)
        }
    }

    private fun executeRequest(apiKey: String, model: String, payload: JSONObject): GeminiResult<JSONObject> {
        val safeModel = model.trim().ifBlank { "gemini-2.5-flash" }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$safeModel:generateContent"
        val req = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        return try {
            client.newCall(req).execute().use { resp ->
                val rawBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val errMsg = runCatching {
                        JSONObject(rawBody).optJSONObject("error")?.optString("message").orEmpty()
                    }.getOrDefault("")
                    return when (resp.code) {
                        400, 401, 403 -> GeminiResult.Failure(
                            errMsg.ifBlank { "Invalid or unauthorized Gemini API key (HTTP ${resp.code})." },
                            isKeyError = true
                        )
                        429 -> GeminiResult.Failure(
                            "Gemini API quota exceeded (HTTP 429). Increase AI call interval or try later.",
                            isQuotaError = true
                        )
                        404 -> GeminiResult.Failure(
                            "Model '$safeModel' not available for this key. Switch model in Settings."
                        )
                        else -> GeminiResult.Failure(
                            "Gemini API error (${resp.code}): ${errMsg.ifBlank { "Request failed" }}"
                        )
                    }
                }
                GeminiResult.Success(JSONObject(rawBody))
            }
        } catch (_: IOException) {
            GeminiResult.Failure("Offline or network timeout. Local light adaptation & XML filters remain active.")
        } catch (e: Exception) {
            GeminiResult.Failure(e.localizedMessage ?: "Unexpected Gemini response error.")
        }
    }

    private fun parseCameraResponse(root: JSONObject): GeminiResult<CameraBrainResponse> {
        val calls = mutableListOf<GeminiToolCall>()
        val textParts = StringBuilder()
        val candidates = root.optJSONArray("candidates") ?: return GeminiResult.Success(CameraBrainResponse(emptyList(), ""))
        if (candidates.length() == 0) return GeminiResult.Success(CameraBrainResponse(emptyList(), ""))
        val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            part.optJSONObject("functionCall")?.let { fc ->
                val name = fc.optString("name")
                val args = fc.optJSONObject("args") ?: JSONObject()
                if (name.isNotBlank()) calls.add(GeminiToolCall(name, args))
            }
            val txt = part.optString("text")
            if (txt.isNotBlank()) textParts.append(txt).append(' ')
        }
        return GeminiResult.Success(CameraBrainResponse(calls, textParts.toString().trim()))
    }

    private fun parseSettingsChatResponse(root: JSONObject): GeminiResult<SettingsChatResponse> {
        val calls = mutableListOf<GeminiToolCall>()
        val textParts = StringBuilder()
        val candidates = root.optJSONArray("candidates") ?: return GeminiResult.Failure("Empty Gemini response.")
        if (candidates.length() == 0) return GeminiResult.Failure("No candidate returned by Gemini.")
        val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            part.optJSONObject("functionCall")?.let { fc ->
                val name = fc.optString("name")
                val args = fc.optJSONObject("args") ?: JSONObject()
                if (name.isNotBlank()) calls.add(GeminiToolCall(name, args))
            }
            val txt = part.optString("text")
            if (txt.isNotBlank()) {
                if (textParts.isNotEmpty()) textParts.append('\n')
                textParts.append(txt.trim())
            }
        }
        val reply = textParts.toString().ifBlank {
            if (calls.isNotEmpty()) "Updated your camera brain preferences." else "Ready to customize your camera."
        }
        return GeminiResult.Success(SettingsChatResponse(reply, calls))
    }

    private fun scaleBitmapForVision(src: Bitmap, maxDim: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= maxDim && h <= maxDim) return src
        val ratio = maxDim.toFloat() / maxOf(w, h).toFloat()
        return Bitmap.createScaledBitmap(src, (w * ratio).toInt().coerceAtLeast(1), (h * ratio).toInt().coerceAtLeast(1), true)
    }

    private fun cameraFunctionDeclarations(): JSONArray = JSONArray().apply {
        put(func("set_composition_guidance", "Provide silent visual framing guidance and scene detection", mapOf(
            "sceneLabel" to prop("STRING", "Short scene tag e.g. Golden Portrait, Lush Nature, Night Street, Document"),
            "subjectType" to prop("STRING", "One of: portrait, food, landscape, night, pet, document, group_selfie, general"),
            "instruction" to prop("STRING", "Concise 3-7 word guidance e.g. 'Tilt phone up 8°' or 'Move slightly left'"),
            "direction" to prop("STRING", "Arrow direction: NONE, LEFT, RIGHT, UP, DOWN, TILT_UP, TILT_DOWN, CLOSER, BACK"),
            "boxLeft" to prop("NUMBER", "Ideal framing box left 0.0-0.6"),
            "boxTop" to prop("NUMBER", "Ideal framing box top 0.0-0.6"),
            "boxRight" to prop("NUMBER", "Ideal framing box right 0.4-1.0"),
            "boxBottom" to prop("NUMBER", "Ideal framing box bottom 0.4-1.0"),
            "compositionScore" to prop("NUMBER", "0.0 to 1.0 quality score")
        ), listOf("sceneLabel", "subjectType", "instruction", "direction", "compositionScore")))

        put(func("apply_camera_config", "Apply a GCam 8.5 style XML computational processing config", mapOf(
            "configId" to prop("STRING", "Exact config id from catalog e.g. natural_leica, night_sight_boost, greenery_pop_hdr, soft_portrait_pro"),
            "reason" to prop("STRING", "Short reason e.g. Low light detected")
        ), listOf("configId")))

        put(func("tweak_camera_config", "Fine-tune active GCam 8.5 processing parameters", mapOf(
            "hdrPlusStrength" to prop("NUMBER", "0.2 to 1.4"),
            "shadows" to prop("NUMBER", "-0.6 to 0.7"),
            "highlights" to prop("NUMBER", "-0.7 to 0.5"),
            "contrast" to prop("NUMBER", "0.7 to 1.5"),
            "saturation" to prop("NUMBER", "0.5 to 1.6"),
            "vibrance" to prop("NUMBER", "-0.3 to 0.6"),
            "sharpness" to prop("NUMBER", "0.0 to 1.2"),
            "nightSightStrength" to prop("NUMBER", "0.0 to 1.4")
        ), emptyList()))

        put(func("apply_filter", "Apply an XML filter from the available catalog", mapOf(
            "filterId" to prop("STRING", "Exact filter id from catalog"),
            "intensity" to prop("NUMBER", "0.2 to 1.0")
        ), listOf("filterId", "intensity")))

        put(func("remove_filters", "Clear all active filters", emptyMap(), emptyList()))

        put(func("adjust_camera_params", "Adjust DSLR color & exposure parameters", mapOf(
            "brightness" to prop("NUMBER", "-0.4 to 0.4"),
            "contrast" to prop("NUMBER", "0.7 to 1.5"),
            "saturation" to prop("NUMBER", "0.4 to 1.6"),
            "greenery" to prop("NUMBER", "-0.5 to 0.8"),
            "shadows" to prop("NUMBER", "-0.5 to 0.6"),
            "highlights" to prop("NUMBER", "-0.6 to 0.5"),
            "temperatureKelvin" to prop("INTEGER", "3200 to 7500"),
            "vignette" to prop("NUMBER", "0.0 to 0.6"),
            "zoom" to prop("NUMBER", "1.0 to 4.0")
        ), emptyList()))

        put(func("switch_camera_mode", "Switch camera mode for best capture", mapOf(
            "mode" to prop("STRING", "PHOTO, PORTRAIT, NIGHT, DOCUMENT, QR, VIDEO")
        ), listOf("mode")))

        put(func("toggle_hdr", "Enable or disable HDR capture", mapOf(
            "enabled" to prop("BOOLEAN", "true to turn HDR on")
        ), listOf("enabled")))

        put(func("start_timer", "Set shutter countdown timer", mapOf(
            "seconds" to prop("INTEGER", "0, 3, 5, or 10")
        ), listOf("seconds")))

        put(func("capture_photo", "Trigger shutter capture when moment is optimal", mapOf(
            "reason" to prop("STRING", "Short reason e.g. Smile & steady framing")
        ), listOf("reason")))
    }

    private fun settingsFunctionDeclarations(): JSONArray = JSONArray().apply {
        put(func("update_user_preferences", "Persist user AI camera customization preferences", mapOf(
            "greeneryBias" to prop("NUMBER", "-0.8 to 0.8 greenery/foliage bias"),
            "darknessReductionBias" to prop("NUMBER", "-0.5 to 0.8 shadow/darkness lift bias"),
            "skinSmoothingLevel" to prop("NUMBER", "0.0 to 1.0 skin smoothness"),
            "portraitVintageLook" to prop("BOOLEAN", "true to prefer vintage filters on portraits"),
            "preferredPhotoStyle" to prop("STRING", "NATURAL, CINEMATIC, VIBRANT, MOODY"),
            "aiPersonality" to prop("STRING", "PRO_PHOTOGRAPHER, CREATIVE_DIRECTOR, MINIMALIST_COACH, FRIENDLY_BUDDY"),
            "guidanceStrictness" to prop("STRING", "RELAXED, BALANCED, STRICT"),
            "autoAggressiveness" to prop("STRING", "SUBTLE, BALANCED, FULL_AUTO"),
            "aiLanguage" to prop("STRING", "ENGLISH, HINDI, HINGLISH"),
            "voiceOutputEnabled" to prop("BOOLEAN", "false by default")
        ), emptyList()))
    }

    private fun func(name: String, desc: String, props: Map<String, JSONObject>, required: List<String>): JSONObject =
        JSONObject().apply {
            put("name", name)
            put("description", desc)
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    props.forEach { (k, v) -> put(k, v) }
                })
                if (required.isNotEmpty()) put("required", JSONArray(required))
            })
        }

    private fun prop(type: String, desc: String): JSONObject = JSONObject().apply {
        put("type", type)
        put("description", desc)
    }
}
