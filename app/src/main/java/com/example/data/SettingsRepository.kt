package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AiLanguage
import com.example.model.AiPersonality
import com.example.model.AppSettings
import com.example.model.AutoAggressiveness
import com.example.model.CaptureQuality
import com.example.model.ChatMessage
import com.example.model.GuidanceStrictness
import com.example.model.OrientationLockMode
import com.example.model.PhotoStyle
import com.example.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lensmind_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val AI_BRAIN_ENABLED = booleanPreferencesKey("ai_brain_enabled")
        val AUTO_MODE_ENABLED = booleanPreferencesKey("auto_mode_enabled")
        val AI_INTERVAL = floatPreferencesKey("ai_interval_sec")
        val VOICE_OUTPUT = booleanPreferencesKey("voice_output")
        val PERSONALITY = stringPreferencesKey("ai_personality")
        val STRICTNESS = stringPreferencesKey("guidance_strictness")
        val AUTO_AGGRESSIVENESS = stringPreferencesKey("auto_aggressiveness")
        val PHOTO_STYLE = stringPreferencesKey("preferred_photo_style")
        val LANGUAGE = stringPreferencesKey("ai_language")
        val SKIN_SMOOTHING = floatPreferencesKey("skin_smoothing")
        val GREENERY_BIAS = floatPreferencesKey("greenery_bias")
        val DARKNESS_REDUCTION = floatPreferencesKey("darkness_reduction")
        val PORTRAIT_VINTAGE = booleanPreferencesKey("portrait_vintage")
        val LIGHT_ADAPT_ENABLED = booleanPreferencesKey("light_adapt_enabled")
        val LIGHT_ADAPT_SENS = floatPreferencesKey("light_adapt_sens")
        val CAPTURE_QUALITY = stringPreferencesKey("capture_quality")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ORIENTATION_LOCK = stringPreferencesKey("orientation_lock")
        val SHARE_WATERMARK = booleanPreferencesKey("share_watermark")
        val ACTIVE_CONFIG_ID = stringPreferencesKey("active_camera_config_id")
        val FAVORITE_CONFIGS = stringSetPreferencesKey("favorite_config_ids")
        val HAPTICS = booleanPreferencesKey("haptics_enabled")
        val PRIVACY_REDACT = booleanPreferencesKey("privacy_redact")
        val SEND_FRAMES_AI = booleanPreferencesKey("send_frames_ai")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val FAVORITE_FILTERS = stringSetPreferencesKey("favorite_filter_ids")
        val FILTER_ORDER = stringPreferencesKey("filter_order_csv")
        val CHAT_HISTORY = stringPreferencesKey("ai_chat_history_json")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            geminiModel = prefs[Keys.GEMINI_MODEL] ?: "gemini-2.5-flash",
            aiBrainEnabled = prefs[Keys.AI_BRAIN_ENABLED] ?: true,
            autoModeEnabled = prefs[Keys.AUTO_MODE_ENABLED] ?: false,
            aiIntervalSeconds = prefs[Keys.AI_INTERVAL] ?: 2.0f,
            voiceOutputEnabled = prefs[Keys.VOICE_OUTPUT] ?: false,
            aiPersonality = enumValueOrDefault(prefs[Keys.PERSONALITY], AiPersonality.PRO_PHOTOGRAPHER),
            guidanceStrictness = enumValueOrDefault(prefs[Keys.STRICTNESS], GuidanceStrictness.BALANCED),
            autoAggressiveness = enumValueOrDefault(prefs[Keys.AUTO_AGGRESSIVENESS], AutoAggressiveness.BALANCED),
            preferredPhotoStyle = enumValueOrDefault(prefs[Keys.PHOTO_STYLE], PhotoStyle.NATURAL),
            aiLanguage = enumValueOrDefault(prefs[Keys.LANGUAGE], AiLanguage.ENGLISH),
            skinSmoothingLevel = prefs[Keys.SKIN_SMOOTHING] ?: 0.25f,
            greeneryBias = prefs[Keys.GREENERY_BIAS] ?: 0f,
            darknessReductionBias = prefs[Keys.DARKNESS_REDUCTION] ?: 0f,
            portraitVintageLook = prefs[Keys.PORTRAIT_VINTAGE] ?: false,
            lightAdaptationEnabled = prefs[Keys.LIGHT_ADAPT_ENABLED] ?: true,
            lightAdaptationSensitivity = prefs[Keys.LIGHT_ADAPT_SENS] ?: 1.0f,
            captureQuality = enumValueOrDefault(prefs[Keys.CAPTURE_QUALITY], CaptureQuality.HIGH),
            themeMode = enumValueOrDefault(prefs[Keys.THEME_MODE], ThemeMode.DARK),
            orientationLockMode = enumValueOrDefault(prefs[Keys.ORIENTATION_LOCK], OrientationLockMode.AUTO_ROTATE),
            shareWatermarkEnabled = prefs[Keys.SHARE_WATERMARK] ?: true,
            activeCameraConfigId = prefs[Keys.ACTIVE_CONFIG_ID] ?: "natural_leica",
            hapticsEnabled = prefs[Keys.HAPTICS] ?: true,
            privacyRedactLocation = prefs[Keys.PRIVACY_REDACT] ?: true,
            sendPreviewFramesToAi = prefs[Keys.SEND_FRAMES_AI] ?: true,
            onboardingCompleted = prefs[Keys.ONBOARDING_DONE] ?: false
        )
    }

    val favoriteFilterIdsFlow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[Keys.FAVORITE_FILTERS] ?: setOf("obsidian_cinema", "emerald_foliage_pro")
    }

    val favoriteConfigIdsFlow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[Keys.FAVORITE_CONFIGS] ?: setOf("natural_leica", "vivid_pixel_pop")
    }

    val filterOrderFlow: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[Keys.FILTER_ORDER]?.split(",")?.filter { it.isNotBlank() }.orEmpty()
    }

    val chatHistoryFlow: Flow<List<ChatMessage>> = context.dataStore.data.map { prefs ->
        parseChatHistory(prefs[Keys.CHAT_HISTORY])
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val current = AppSettings(
                geminiModel = prefs[Keys.GEMINI_MODEL] ?: "gemini-2.5-flash",
                aiBrainEnabled = prefs[Keys.AI_BRAIN_ENABLED] ?: true,
                autoModeEnabled = prefs[Keys.AUTO_MODE_ENABLED] ?: false,
                aiIntervalSeconds = prefs[Keys.AI_INTERVAL] ?: 2.0f,
                voiceOutputEnabled = prefs[Keys.VOICE_OUTPUT] ?: false,
                aiPersonality = enumValueOrDefault(prefs[Keys.PERSONALITY], AiPersonality.PRO_PHOTOGRAPHER),
                guidanceStrictness = enumValueOrDefault(prefs[Keys.STRICTNESS], GuidanceStrictness.BALANCED),
                autoAggressiveness = enumValueOrDefault(prefs[Keys.AUTO_AGGRESSIVENESS], AutoAggressiveness.BALANCED),
                preferredPhotoStyle = enumValueOrDefault(prefs[Keys.PHOTO_STYLE], PhotoStyle.NATURAL),
                aiLanguage = enumValueOrDefault(prefs[Keys.LANGUAGE], AiLanguage.ENGLISH),
                skinSmoothingLevel = prefs[Keys.SKIN_SMOOTHING] ?: 0.25f,
                greeneryBias = prefs[Keys.GREENERY_BIAS] ?: 0f,
                darknessReductionBias = prefs[Keys.DARKNESS_REDUCTION] ?: 0f,
                portraitVintageLook = prefs[Keys.PORTRAIT_VINTAGE] ?: false,
                lightAdaptationEnabled = prefs[Keys.LIGHT_ADAPT_ENABLED] ?: true,
                lightAdaptationSensitivity = prefs[Keys.LIGHT_ADAPT_SENS] ?: 1.0f,
                captureQuality = enumValueOrDefault(prefs[Keys.CAPTURE_QUALITY], CaptureQuality.HIGH),
                themeMode = enumValueOrDefault(prefs[Keys.THEME_MODE], ThemeMode.DARK),
                orientationLockMode = enumValueOrDefault(prefs[Keys.ORIENTATION_LOCK], OrientationLockMode.AUTO_ROTATE),
                shareWatermarkEnabled = prefs[Keys.SHARE_WATERMARK] ?: true,
                activeCameraConfigId = prefs[Keys.ACTIVE_CONFIG_ID] ?: "natural_leica",
                hapticsEnabled = prefs[Keys.HAPTICS] ?: true,
                privacyRedactLocation = prefs[Keys.PRIVACY_REDACT] ?: true,
                sendPreviewFramesToAi = prefs[Keys.SEND_FRAMES_AI] ?: true,
                onboardingCompleted = prefs[Keys.ONBOARDING_DONE] ?: false
            )
            val updated = transform(current)
            prefs[Keys.GEMINI_MODEL] = updated.geminiModel
            prefs[Keys.AI_BRAIN_ENABLED] = updated.aiBrainEnabled
            prefs[Keys.AUTO_MODE_ENABLED] = updated.autoModeEnabled
            prefs[Keys.AI_INTERVAL] = updated.aiIntervalSeconds
            prefs[Keys.VOICE_OUTPUT] = updated.voiceOutputEnabled
            prefs[Keys.PERSONALITY] = updated.aiPersonality.name
            prefs[Keys.STRICTNESS] = updated.guidanceStrictness.name
            prefs[Keys.AUTO_AGGRESSIVENESS] = updated.autoAggressiveness.name
            prefs[Keys.PHOTO_STYLE] = updated.preferredPhotoStyle.name
            prefs[Keys.LANGUAGE] = updated.aiLanguage.name
            prefs[Keys.SKIN_SMOOTHING] = updated.skinSmoothingLevel.coerceIn(0f, 1f)
            prefs[Keys.GREENERY_BIAS] = updated.greeneryBias.coerceIn(-1f, 1f)
            prefs[Keys.DARKNESS_REDUCTION] = updated.darknessReductionBias.coerceIn(-1f, 1f)
            prefs[Keys.PORTRAIT_VINTAGE] = updated.portraitVintageLook
            prefs[Keys.LIGHT_ADAPT_ENABLED] = updated.lightAdaptationEnabled
            prefs[Keys.LIGHT_ADAPT_SENS] = updated.lightAdaptationSensitivity.coerceIn(0.2f, 2.0f)
            prefs[Keys.CAPTURE_QUALITY] = updated.captureQuality.name
            prefs[Keys.THEME_MODE] = updated.themeMode.name
            prefs[Keys.ORIENTATION_LOCK] = updated.orientationLockMode.name
            prefs[Keys.SHARE_WATERMARK] = updated.shareWatermarkEnabled
            prefs[Keys.ACTIVE_CONFIG_ID] = updated.activeCameraConfigId
            prefs[Keys.HAPTICS] = updated.hapticsEnabled
            prefs[Keys.PRIVACY_REDACT] = updated.privacyRedactLocation
            prefs[Keys.SEND_FRAMES_AI] = updated.sendPreviewFramesToAi
            prefs[Keys.ONBOARDING_DONE] = updated.onboardingCompleted
        }
    }

    suspend fun toggleFavoriteFilter(filterId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.FAVORITE_FILTERS]?.toMutableSet()
                ?: mutableSetOf("obsidian_cinema", "emerald_foliage_pro")
            if (!current.add(filterId)) {
                current.remove(filterId)
            }
            prefs[Keys.FAVORITE_FILTERS] = current
        }
    }

    suspend fun toggleFavoriteConfig(configId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.FAVORITE_CONFIGS]?.toMutableSet()
                ?: mutableSetOf("natural_leica", "vivid_pixel_pop")
            if (!current.add(configId)) {
                current.remove(configId)
            }
            prefs[Keys.FAVORITE_CONFIGS] = current
        }
    }

    suspend fun saveFilterOrder(orderIds: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.FILTER_ORDER] = orderIds.joinToString(",")
        }
    }

    suspend fun saveChatHistory(messages: List<ChatMessage>) {
        val arr = JSONArray()
        messages.takeLast(40).forEach { msg ->
            val obj = JSONObject().apply {
                put("id", msg.id)
                put("isUser", msg.isUser)
                put("text", msg.text)
                put("timestampMs", msg.timestampMs)
                put("appliedUpdates", JSONArray(msg.appliedUpdates))
            }
            arr.put(obj)
        }
        context.dataStore.edit { prefs ->
            prefs[Keys.CHAT_HISTORY] = arr.toString()
        }
    }

    suspend fun resetToDefaults() {
        context.dataStore.edit { prefs ->
            val onboardingDone = prefs[Keys.ONBOARDING_DONE] ?: true
            prefs.clear()
            prefs[Keys.ONBOARDING_DONE] = onboardingDone
        }
    }

    private fun parseChatHistory(raw: String?): List<ChatMessage> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { idx ->
                val obj = arr.getJSONObject(idx)
                val updatesArr = obj.optJSONArray("appliedUpdates") ?: JSONArray()
                val updates = (0 until updatesArr.length()).map { updatesArr.getString(it) }
                ChatMessage(
                    id = obj.getString("id"),
                    isUser = obj.getBoolean("isUser"),
                    text = obj.getString("text"),
                    appliedUpdates = updates,
                    timestampMs = obj.optLong("timestampMs", System.currentTimeMillis())
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T {
        if (value == null) return default
        return runCatching { enumValueOf<T>(value) }.getOrDefault(default)
    }
}
