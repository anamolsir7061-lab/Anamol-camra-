package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.ai.GeminiApiClient
import com.example.ai.GeminiResult
import com.example.data.FilterRepository
import com.example.data.MediaRepository
import com.example.data.SettingsRepository
import com.example.model.AiLanguage
import com.example.model.AiPersonality
import com.example.model.AppSettings
import com.example.model.AutoAggressiveness
import com.example.model.ChatMessage
import com.example.model.GuidanceStrictness
import com.example.model.PhotoStyle
import com.example.security.SecureKeyStoreManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class SettingsUiState(
    val keyValidationMessage: String? = null,
    val isValidatingKey: Boolean = false,
    val isChatSending: Boolean = false,
    val storageBytes: Long = 0L,
    val toastMessage: String? = null
)

class SettingsViewModel(
    private val keyStore: SecureKeyStoreManager,
    private val settingsRepository: SettingsRepository,
    private val filterRepository: FilterRepository,
    private val mediaRepository: MediaRepository,
    private val geminiApiClient: GeminiApiClient
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val hasApiKey: StateFlow<Boolean> = keyStore.hasApiKey
    val maskedKey: StateFlow<String> = keyStore.maskedKey

    val chatHistory: StateFlow<List<ChatMessage>> = settingsRepository.chatHistoryFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refreshStorage()
    }

    fun refreshStorage() {
        _uiState.update { it.copy(storageBytes = mediaRepository.calculateStorageBytes()) }
    }

    fun saveAndVerifyApiKey(rawKey: String) {
        val trimmed = rawKey.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(keyValidationMessage = "Key cannot be empty.") }
            return
        }
        keyStore.saveApiKey(trimmed)
        _uiState.update { it.copy(isValidatingKey = true, keyValidationMessage = "Verifying with Gemini API...") }
        viewModelScope.launch {
            when (val res = geminiApiClient.validateApiKey(trimmed, settings.value.geminiModel)) {
                is GeminiResult.Success -> _uiState.update {
                    it.copy(isValidatingKey = false, keyValidationMessage = "Verified: ${res.data}")
                }
                is GeminiResult.Failure -> _uiState.update {
                    it.copy(isValidatingKey = false, keyValidationMessage = res.userMessage)
                }
            }
        }
    }

    fun clearApiKey() {
        keyStore.clearApiKey()
        _uiState.update { it.copy(keyValidationMessage = "API key removed from secure vault.") }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            settingsRepository.updateSettings(transform)
        }
    }

    fun sendCustomizationChat(prompt: String) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) return
        val userMsg = ChatMessage(id = UUID.randomUUID().toString(), isUser = true, text = trimmed)
        val currentHistory = chatHistory.value + userMsg
        viewModelScope.launch {
            settingsRepository.saveChatHistory(currentHistory)
            _uiState.update { it.copy(isChatSending = true) }

            val res = geminiApiClient.chatForSettingsCustomization(
                history = currentHistory,
                userPrompt = trimmed,
                settings = settings.value,
                availableFilters = filterRepository.filters.value
            )

            when (res) {
                is GeminiResult.Success -> {
                    val appliedLabels = mutableListOf<String>()
                    res.data.toolCalls.forEach { tc ->
                        if (tc.name == "update_user_preferences") {
                            val args = tc.args
                            settingsRepository.updateSettings { cur ->
                                var next = cur
                                if (args.has("greeneryBias")) {
                                    val v = args.optDouble("greeneryBias", cur.greeneryBias.toDouble()).toFloat().coerceIn(-0.8f, 0.8f)
                                    next = next.copy(greeneryBias = v)
                                    appliedLabels.add("Greenery ${if (v >= 0) "+" else ""}${(v * 100).toInt()}%")
                                }
                                if (args.has("darknessReductionBias")) {
                                    val v = args.optDouble("darknessReductionBias", cur.darknessReductionBias.toDouble()).toFloat().coerceIn(-0.5f, 0.8f)
                                    next = next.copy(darknessReductionBias = v)
                                    appliedLabels.add("Shadow lift ${if (v >= 0) "+" else ""}${(v * 100).toInt()}%")
                                }
                                if (args.has("skinSmoothingLevel")) {
                                    val v = args.optDouble("skinSmoothingLevel", cur.skinSmoothingLevel.toDouble()).toFloat().coerceIn(0f, 1f)
                                    next = next.copy(skinSmoothingLevel = v)
                                    appliedLabels.add("Skin Smooth ${(v * 100).toInt()}%")
                                }
                                if (args.has("portraitVintageLook")) {
                                    val v = args.optBoolean("portraitVintageLook", cur.portraitVintageLook)
                                    next = next.copy(portraitVintageLook = v)
                                    appliedLabels.add("Portrait Vintage: ${if (v) "ON" else "OFF"}")
                                }
                                if (args.has("preferredPhotoStyle")) {
                                    runCatching { PhotoStyle.valueOf(args.optString("preferredPhotoStyle").uppercase()) }.getOrNull()?.let {
                                        next = next.copy(preferredPhotoStyle = it)
                                        appliedLabels.add("Style: ${it.label}")
                                    }
                                }
                                if (args.has("aiPersonality")) {
                                    runCatching { AiPersonality.valueOf(args.optString("aiPersonality").uppercase()) }.getOrNull()?.let {
                                        next = next.copy(aiPersonality = it)
                                        appliedLabels.add("Personality: ${it.label}")
                                    }
                                }
                                if (args.has("guidanceStrictness")) {
                                    runCatching { GuidanceStrictness.valueOf(args.optString("guidanceStrictness").uppercase()) }.getOrNull()?.let {
                                        next = next.copy(guidanceStrictness = it)
                                        appliedLabels.add("Strictness: ${it.label}")
                                    }
                                }
                                if (args.has("autoAggressiveness")) {
                                    runCatching { AutoAggressiveness.valueOf(args.optString("autoAggressiveness").uppercase()) }.getOrNull()?.let {
                                        next = next.copy(autoAggressiveness = it)
                                        appliedLabels.add("Auto: ${it.label}")
                                    }
                                }
                                if (args.has("aiLanguage")) {
                                    runCatching { AiLanguage.valueOf(args.optString("aiLanguage").uppercase()) }.getOrNull()?.let {
                                        next = next.copy(aiLanguage = it)
                                        appliedLabels.add("Language: ${it.label}")
                                    }
                                }
                                if (args.has("voiceOutputEnabled")) {
                                    val v = args.optBoolean("voiceOutputEnabled", false)
                                    next = next.copy(voiceOutputEnabled = v)
                                    appliedLabels.add("Voice: ${if (v) "ON" else "OFF"}")
                                }
                                next
                            }
                        }
                    }
                    val botMsg = ChatMessage(
                        id = UUID.randomUUID().toString(),
                        isUser = false,
                        text = res.data.replyText,
                        appliedUpdates = appliedLabels
                    )
                    settingsRepository.saveChatHistory(currentHistory + botMsg)
                }
                is GeminiResult.Failure -> {
                    val errMsg = ChatMessage(
                        id = UUID.randomUUID().toString(),
                        isUser = false,
                        text = res.userMessage
                    )
                    settingsRepository.saveChatHistory(currentHistory + errMsg)
                }
            }
            _uiState.update { it.copy(isChatSending = false) }
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch { settingsRepository.saveChatHistory(emptyList()) }
    }

    fun clearMediaStorage() {
        viewModelScope.launch {
            mediaRepository.clearAllCacheAndMedia()
            refreshStorage()
            _uiState.update { it.copy(toastMessage = "Cleared saved in-app media storage") }
        }
    }

    fun resetAllDefaults() {
        viewModelScope.launch {
            settingsRepository.resetToDefaults()
            _uiState.update { it.copy(toastMessage = "Settings reset to defaults") }
        }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(
            keyStore = container.keyStoreManager,
            settingsRepository = container.settingsRepository,
            filterRepository = container.filterRepository,
            mediaRepository = container.mediaRepository,
            geminiApiClient = container.geminiApiClient
        ) as T
    }
}
