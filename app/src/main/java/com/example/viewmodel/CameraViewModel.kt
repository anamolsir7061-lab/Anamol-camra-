package com.example.viewmodel

import android.graphics.Bitmap
import android.graphics.RectF
import android.speech.tts.TextToSpeech
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.ai.GeminiApiClient
import com.example.ai.GeminiResult
import com.example.ai.GeminiToolCall
import com.example.camera.FilterBitmapProcessor
import com.example.camera.HorizonSensorManager
import com.example.camera.HorizonState
import com.example.camera.LightAdaptationEngine
import com.example.data.CameraConfigRepository
import com.example.data.FilterRepository
import com.example.data.MediaRepository
import com.example.data.SettingsRepository
import com.example.model.ActiveFilterLayer
import com.example.model.AiActionEntry
import com.example.model.AiGuidance
import com.example.model.AiSuggestion
import com.example.model.AppSettings
import com.example.model.AspectRatioOption
import com.example.model.CameraFilter
import com.example.model.CameraMode
import com.example.model.CameraStateSnapshot
import com.example.model.FaceTelemetry
import com.example.model.FilterAdjustments
import com.example.model.FilterBlendMode
import com.example.model.FlashState
import com.example.model.GCamCameraConfig
import com.example.model.GridType
import com.example.model.GuidanceDirection
import com.example.model.LightAdaptationState
import com.example.model.ProManualParams
import com.example.model.QrScanResult
import com.example.model.ThemeMode
import com.example.security.SecureKeyStoreManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

sealed interface CameraCommand {
    data object CaptureSinglePhoto : CameraCommand
    data object ToggleVideoRecording : CameraCommand
    data class ApplyExposureEv(val ev: Float) : CameraCommand
    data class ApplyZoom(val ratio: Float) : CameraCommand
}

data class CameraUiState(
    val cameraMode: CameraMode = CameraMode.PHOTO,
    val isFrontCamera: Boolean = false,
    val aspectRatio: AspectRatioOption = AspectRatioOption.RATIO_9_16,
    val gridType: GridType = GridType.THIRDS,
    val showHorizonLevel: Boolean = true,
    val flashState: FlashState = FlashState.OFF,
    val hasFlashUnit: Boolean = false,
    val timerSeconds: Int = 0,
    val countdownRemaining: Int = 0,
    val burstEnabled: Boolean = false,
    val isBurstRunning: Boolean = false,
    val isRecordingVideo: Boolean = false,
    val isTimeLapseRunning: Boolean = false,
    val recordingElapsedSec: Int = 0,
    val zoomRatio: Float = 1.0f,
    val minZoomRatio: Float = 1.0f,
    val maxZoomRatio: Float = 6.0f,
    val hdrEnabled: Boolean = false,
    val smileAutoCapture: Boolean = false,
    val beforeAfterCompare: Boolean = false,
    val compareSplitPosition: Float = 0.5f,
    val proParams: ProManualParams = ProManualParams(),
    val activeConfig: GCamCameraConfig? = null,
    val activeFilterLayers: List<ActiveFilterLayer> = emptyList(),
    val effectiveAdjustments: FilterAdjustments = FilterAdjustments(),
    val lightState: LightAdaptationState = LightAdaptationState(),
    val horizonState: HorizonState = HorizonState(),
    val faces: List<FaceTelemetry> = emptyList(),
    val qrResult: QrScanResult? = null,
    val aiGuidance: AiGuidance = AiGuidance(),
    val aiSuggestions: List<AiSuggestion> = emptyList(),
    val aiActionLog: List<AiActionEntry> = emptyList(),
    val aiStatusNote: String? = null,
    val isAiAnalyzing: Boolean = false,
    val showKeyPromptBanner: Boolean = false,
    val shutterPulseTick: Long = 0L,
    val toastMessage: String? = null
)

class CameraViewModel(
    private val appContext: Context,
    private val keyStore: SecureKeyStoreManager,
    private val settingsRepository: SettingsRepository,
    private val filterRepository: FilterRepository,
    private val cameraConfigRepository: CameraConfigRepository,
    private val mediaRepository: MediaRepository,
    private val geminiApiClient: GeminiApiClient
) : ViewModel() {

    private val lightEngine = LightAdaptationEngine()
    private val horizonManager = HorizonSensorManager(appContext)
    private val latestVisionFrame = AtomicReference<Bitmap?>(null)

    private var tts: TextToSpeech? = null
    private var timerJob: Job? = null
    private var timeLapseJob: Job? = null
    private var lastAutoCaptureMs = 0L

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val _previewFrameBitmap = MutableStateFlow<Bitmap?>(null)
    val previewFrameBitmap: StateFlow<Bitmap?> = _previewFrameBitmap.asStateFlow()

    private val _commands = MutableSharedFlow<CameraCommand>(extraBufferCapacity = 16)
    val commands: SharedFlow<CameraCommand> = _commands.asSharedFlow()

    val settings: StateFlow<AppSettings> = settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val filters: StateFlow<List<CameraFilter>> = filterRepository.filters
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val cameraConfigs: StateFlow<List<GCamCameraConfig>> = cameraConfigRepository.configs
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val hasApiKey: StateFlow<Boolean> = keyStore.hasApiKey

    val mediaItems = mediaRepository.items

    init {
        horizonManager.start()
        viewModelScope.launch { mediaRepository.refresh() }
        viewModelScope.launch { cameraConfigRepository.loadConfigs() }

        viewModelScope.launch {
            horizonManager.horizonState.collect { horizon ->
                _uiState.update { it.copy(horizonState = horizon) }
            }
        }

        // Sync active GCam 8.5 config from persisted activeCameraConfigId + loaded catalog
        viewModelScope.launch {
            combine(cameraConfigs, settings) { cfgs, cfgSettings ->
                cfgs.find { it.id == cfgSettings.activeCameraConfigId } ?: cfgs.firstOrNull()
            }.collect { resolved ->
                if (resolved != null) {
                    _uiState.update { st ->
                        if (st.activeConfig?.id == resolved.id && st.activeConfig == resolved) st
                        else st.copy(activeConfig = resolved)
                    }
                }
            }
        }

        viewModelScope.launch {
            combine(_uiState, settings) { state, cfg ->
                FilterBitmapProcessor.mergeAdjustments(
                    layers = state.activeFilterLayers,
                    pro = state.proParams,
                    light = state.lightState,
                    greeneryBias = cfg.greeneryBias,
                    darknessBias = cfg.darknessReductionBias,
                    activeConfig = state.activeConfig,
                    cameraMode = state.cameraMode,
                    isFrontCamera = state.isFrontCamera
                )
            }.collect { merged ->
                _uiState.update { it.copy(effectiveAdjustments = merged) }
            }
        }

        viewModelScope.launch {
            hasApiKey.collect { hasKey ->
                _uiState.update { it.copy(showKeyPromptBanner = !hasKey) }
            }
        }

        startAiBrainLoop()
    }

    private fun startAiBrainLoop() {
        viewModelScope.launch {
            while (isActive) {
                val cfg = settings.value
                val intervalMs = (cfg.aiIntervalSeconds * 1000L).toLong().coerceIn(1200L, 10000L)
                delay(intervalMs)

                if (!cfg.aiBrainEnabled || !cfg.sendPreviewFramesToAi || !keyStore.hasApiKey.value) {
                    continue
                }
                val frame = latestVisionFrame.get() ?: continue
                if (_uiState.value.isRecordingVideo) continue

                _uiState.update { it.copy(isAiAnalyzing = true) }
                val stateSnap = _uiState.value
                val avgSmile = stateSnap.faces.mapNotNull { if (it.smilingProbability >= 0f) it.smilingProbability else null }
                    .average().let { if (it.isNaN()) 0f else it.toFloat() }

                try {
                    val result = geminiApiClient.analyzePreviewFrame(
                        frameBitmap = frame,
                        settings = cfg,
                        currentMode = stateSnap.cameraMode,
                        proParams = stateSnap.proParams,
                        activeFilterIds = stateSnap.activeFilterLayers.map { it.filter.id },
                        availableFilters = filters.value,
                        activeConfigId = stateSnap.activeConfig?.id ?: cfg.activeCameraConfigId,
                        availableConfigs = cameraConfigs.value,
                        lightState = stateSnap.lightState,
                        faceCount = stateSnap.faces.size,
                        avgSmile = avgSmile
                    )

                    when (result) {
                        is GeminiResult.Success -> {
                            _uiState.update { it.copy(aiStatusNote = null, isAiAnalyzing = false) }
                            handleAiToolCalls(result.data.toolCalls, cfg)
                        }
                        is GeminiResult.Failure -> {
                            _uiState.update {
                                it.copy(
                                    aiStatusNote = result.userMessage,
                                    isAiAnalyzing = false,
                                    showKeyPromptBanner = result.isKeyError
                                )
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    _uiState.update { it.copy(isAiAnalyzing = false) }
                }
            }
        }
    }

    private fun handleAiToolCalls(calls: List<GeminiToolCall>, cfg: AppSettings) {
        val pendingSuggestions = mutableListOf<AiSuggestion>()

        for (call in calls) {
            when (call.name) {
                "set_composition_guidance" -> {
                    val args = call.args
                    val dir = runCatching {
                        GuidanceDirection.valueOf(args.optString("direction", "NONE").uppercase())
                    }.getOrDefault(GuidanceDirection.NONE)
                    val hasBox = args.has("boxLeft") && args.has("boxRight") && args.has("boxTop") && args.has("boxBottom")
                    val box = if (hasBox) {
                        RectF(
                            args.optDouble("boxLeft", 0.15).toFloat().coerceIn(0f, 0.8f),
                            args.optDouble("boxTop", 0.15).toFloat().coerceIn(0f, 0.8f),
                            args.optDouble("boxRight", 0.85).toFloat().coerceIn(0.2f, 1f),
                            args.optDouble("boxBottom", 0.85).toFloat().coerceIn(0.2f, 1f)
                        )
                    } else null

                    val instruction = args.optString("instruction", "")
                    val guidance = AiGuidance(
                        sceneLabel = args.optString("sceneLabel", "Ready"),
                        subjectType = args.optString("subjectType", "general"),
                        instruction = instruction,
                        direction = dir,
                        idealFramingBox = box,
                        compositionScore = args.optDouble("compositionScore", 0.7).toFloat().coerceIn(0f, 1f),
                        timestampMs = System.currentTimeMillis()
                    )
                    _uiState.update { it.copy(aiGuidance = guidance) }
                    if (cfg.voiceOutputEnabled && instruction.isNotBlank()) {
                        speakIfOptedIn(instruction)
                    }
                }
                else -> {
                    if (cfg.autoModeEnabled) {
                        executeToolCall(call, cfg)
                    } else {
                        describeSuggestion(call)?.let { pendingSuggestions.add(it) }
                    }
                }
            }
        }

        if (!cfg.autoModeEnabled && pendingSuggestions.isNotEmpty()) {
            _uiState.update { it.copy(aiSuggestions = pendingSuggestions.take(3)) }
        } else if (cfg.autoModeEnabled) {
            _uiState.update { it.copy(aiSuggestions = emptyList()) }
        }
    }

    private fun describeSuggestion(call: GeminiToolCall): AiSuggestion? {
        val args = call.args
        return when (call.name) {
            "apply_camera_config" -> {
                val id = args.optString("configId")
                val c = cameraConfigs.value.find { it.id == id } ?: return null
                AiSuggestion(UUID.randomUUID().toString(), "${c.icon} Config: ${c.name}", "GCam XML preset", call.name, args.toString())
            }
            "tweak_camera_config" -> AiSuggestion(UUID.randomUUID().toString(), "Tune HDR+ Config", "Optimize GCam tone curve", call.name, args.toString())
            "apply_filter" -> {
                val id = args.optString("filterId")
                val f = filters.value.find { it.id == id } ?: return null
                AiSuggestion(UUID.randomUUID().toString(), "${f.icon} Try ${f.name}", "Filter suggestion", call.name, args.toString())
            }
            "adjust_camera_params" -> AiSuggestion(UUID.randomUUID().toString(), "Tune Color & Light", "Optimize contrast/greenery", call.name, args.toString())
            "switch_camera_mode" -> {
                val m = args.optString("mode", "PHOTO")
                AiSuggestion(UUID.randomUUID().toString(), "Switch to $m", "Recommended mode", call.name, args.toString())
            }
            "toggle_hdr" -> {
                val en = args.optBoolean("enabled", true)
                AiSuggestion(UUID.randomUUID().toString(), if (en) "Enable HDR" else "Disable HDR", "Dynamic range", call.name, args.toString())
            }
            "remove_filters" -> AiSuggestion(UUID.randomUUID().toString(), "Clear Filters", "Natural sensor look", call.name, args.toString())
            else -> null
        }
    }

    fun applySuggestion(suggestion: AiSuggestion) {
        val args = runCatching { JSONObject(suggestion.argsJson) }.getOrDefault(JSONObject())
        executeToolCall(GeminiToolCall(suggestion.toolName, args), settings.value)
        _uiState.update { state ->
            state.copy(aiSuggestions = state.aiSuggestions.filterNot { it.id == suggestion.id })
        }
    }

    private fun executeToolCall(call: GeminiToolCall, cfg: AppSettings) {
        val snap = captureSnapshot()
        val args = call.args
        when (call.name) {
            "apply_camera_config" -> {
                val id = args.optString("configId")
                val target = cameraConfigs.value.find { it.id == id } ?: return
                selectCameraConfig(target.id)
                val reason = args.optString("reason", "").let { if (it.isNotBlank()) " ($it)" else "" }
                logAiAction("Config -> ${target.name}$reason", snap)
            }
            "tweak_camera_config" -> {
                val curCfg = _uiState.value.activeConfig ?: cameraConfigs.value.firstOrNull() ?: return
                val p = curCfg.params
                val tweaked = curCfg.copy(
                    params = p.copy(
                        hdrPlusStrength = if (args.has("hdrPlusStrength")) args.optDouble("hdrPlusStrength", p.hdrPlusStrength.toDouble()).toFloat().coerceIn(0.2f, 1.4f) else p.hdrPlusStrength,
                        shadows = if (args.has("shadows")) args.optDouble("shadows", p.shadows.toDouble()).toFloat().coerceIn(-0.6f, 0.7f) else p.shadows,
                        highlights = if (args.has("highlights")) args.optDouble("highlights", p.highlights.toDouble()).toFloat().coerceIn(-0.7f, 0.5f) else p.highlights,
                        contrast = if (args.has("contrast")) args.optDouble("contrast", p.contrast.toDouble()).toFloat().coerceIn(0.7f, 1.5f) else p.contrast,
                        saturation = if (args.has("saturation")) args.optDouble("saturation", p.saturation.toDouble()).toFloat().coerceIn(0.5f, 1.6f) else p.saturation,
                        vibrance = if (args.has("vibrance")) args.optDouble("vibrance", p.vibrance.toDouble()).toFloat().coerceIn(-0.3f, 0.6f) else p.vibrance,
                        sharpness = if (args.has("sharpness")) args.optDouble("sharpness", p.sharpness.toDouble()).toFloat().coerceIn(0f, 1.2f) else p.sharpness,
                        nightSightStrength = if (args.has("nightSightStrength")) args.optDouble("nightSightStrength", p.nightSightStrength.toDouble()).toFloat().coerceIn(0f, 1.4f) else p.nightSightStrength
                    )
                )
                _uiState.update { it.copy(activeConfig = tweaked) }
                logAiAction("Tuned GCam HDR+/tone in ${curCfg.name}", snap)
            }
            "apply_filter" -> {
                val id = args.optString("filterId")
                val intensity = args.optDouble("intensity", 0.8).toFloat().coerceIn(0.2f, 1f)
                val target = filters.value.find { it.id == id } ?: return
                _uiState.update {
                    it.copy(activeFilterLayers = listOf(ActiveFilterLayer(target, intensity)))
                }
                logAiAction("Applied ${target.name} (${(intensity * 100).toInt()}%)", snap)
            }
            "remove_filters" -> {
                if (_uiState.value.activeFilterLayers.isNotEmpty()) {
                    _uiState.update { it.copy(activeFilterLayers = emptyList()) }
                    logAiAction("Cleared active filters", snap)
                }
            }
            "adjust_camera_params" -> {
                val cur = _uiState.value.proParams
                val next = cur.copy(
                    brightness = if (args.has("brightness")) args.optDouble("brightness", cur.brightness.toDouble()).toFloat().coerceIn(-0.4f, 0.4f) else cur.brightness,
                    contrast = if (args.has("contrast")) args.optDouble("contrast", cur.contrast.toDouble()).toFloat().coerceIn(0.7f, 1.5f) else cur.contrast,
                    saturation = if (args.has("saturation")) args.optDouble("saturation", cur.saturation.toDouble()).toFloat().coerceIn(0.4f, 1.6f) else cur.saturation,
                    greenery = if (args.has("greenery")) args.optDouble("greenery", cur.greenery.toDouble()).toFloat().coerceIn(-0.5f, 0.8f) else cur.greenery,
                    shadows = if (args.has("shadows")) args.optDouble("shadows", cur.shadows.toDouble()).toFloat().coerceIn(-0.5f, 0.6f) else cur.shadows,
                    highlights = if (args.has("highlights")) args.optDouble("highlights", cur.highlights.toDouble()).toFloat().coerceIn(-0.6f, 0.5f) else cur.highlights,
                    temperatureKelvin = if (args.has("temperatureKelvin")) args.optInt("temperatureKelvin", cur.temperatureKelvin).coerceIn(3200, 7500) else cur.temperatureKelvin,
                    vignette = if (args.has("vignette")) args.optDouble("vignette", cur.vignette.toDouble()).toFloat().coerceIn(0f, 0.6f) else cur.vignette
                )
                _uiState.update { it.copy(proParams = next) }
                if (args.has("zoom")) {
                    val z = args.optDouble("zoom", _uiState.value.zoomRatio.toDouble()).toFloat()
                    setZoomRatio(z)
                }
                logAiAction("Adjusted tone & color balance", snap)
            }
            "switch_camera_mode" -> {
                val modeStr = args.optString("mode", "PHOTO").uppercase()
                val mode = runCatching { CameraMode.valueOf(modeStr) }.getOrNull() ?: return
                if (_uiState.value.cameraMode != mode) {
                    _uiState.update { it.copy(cameraMode = mode) }
                    logAiAction("Switched to ${mode.label} mode", snap)
                }
            }
            "toggle_hdr" -> {
                val en = args.optBoolean("enabled", true)
                _uiState.update { it.copy(hdrEnabled = en) }
                logAiAction(if (en) "Enabled HDR boost" else "Disabled HDR", snap)
            }
            "start_timer" -> {
                val sec = args.optInt("seconds", 3)
                _uiState.update { it.copy(timerSeconds = sec) }
                logAiAction("Set ${sec}s shutter timer", snap)
            }
            "capture_photo" -> {
                val now = System.currentTimeMillis()
                if (now - lastAutoCaptureMs > 6000L && _uiState.value.horizonState.isSteady) {
                    lastAutoCaptureMs = now
                    val reason = args.optString("reason", "Optimal framing")
                    logAiAction("Auto-captured ($reason)", snap)
                    triggerCapture()
                }
            }
        }
    }

    private fun logAiAction(summary: String, previousSnapshot: CameraStateSnapshot) {
        val entry = AiActionEntry(
            id = UUID.randomUUID().toString(),
            summary = summary,
            timestampMs = System.currentTimeMillis(),
            previousSnapshot = previousSnapshot
        )
        _uiState.update { state ->
            state.copy(aiActionLog = (listOf(entry) + state.aiActionLog).take(12))
        }
    }

    fun undoAiAction(entryId: String) {
        val target = _uiState.value.aiActionLog.find { it.id == entryId } ?: return
        val snap = target.previousSnapshot
        snap.activeConfig?.let { prevCfg ->
            viewModelScope.launch {
                settingsRepository.updateSettings { it.copy(activeCameraConfigId = prevCfg.id) }
            }
        }
        _uiState.update { state ->
            state.copy(
                activeConfig = snap.activeConfig ?: state.activeConfig,
                activeFilterLayers = snap.activeFilters,
                proParams = snap.proParams,
                cameraMode = snap.cameraMode,
                hdrEnabled = snap.hdrEnabled,
                zoomRatio = snap.zoomRatio,
                aiActionLog = state.aiActionLog.filterNot { it.id == entryId },
                toastMessage = "Undid: ${target.summary}"
            )
        }
        _commands.tryEmit(CameraCommand.ApplyZoom(snap.zoomRatio))
        _commands.tryEmit(CameraCommand.ApplyExposureEv(snap.proParams.exposureEv))
    }

    private fun captureSnapshot(): CameraStateSnapshot {
        val s = _uiState.value
        return CameraStateSnapshot(
            activeConfig = s.activeConfig,
            activeFilters = s.activeFilterLayers,
            proParams = s.proParams,
            cameraMode = s.cameraMode,
            hdrEnabled = s.hdrEnabled,
            zoomRatio = s.zoomRatio
        )
    }

    fun onLightFrameSample(bitmap: Bitmap) {
        val cfg = settings.value
        val light = lightEngine.analyzeFrame(bitmap, cfg.lightAdaptationEnabled, cfg.lightAdaptationSensitivity)
        _uiState.update { it.copy(lightState = light) }
    }

    fun onFacesDetected(faces: List<FaceTelemetry>) {
        _uiState.update { it.copy(faces = faces) }
        val s = _uiState.value
        if (s.smileAutoCapture && faces.isNotEmpty() && !s.isRecordingVideo && s.countdownRemaining == 0) {
            val happyAndOpen = faces.any {
                it.smilingProbability > 0.78f &&
                    it.leftEyeOpenProbability > 0.65f &&
                    it.rightEyeOpenProbability > 0.65f
            }
            val now = System.currentTimeMillis()
            if (happyAndOpen && now - lastAutoCaptureMs > 5000L && s.horizonState.isSteady) {
                lastAutoCaptureMs = now
                triggerCapture()
            }
        }
    }

    fun onQrDetected(qr: QrScanResult?) {
        _uiState.update { it.copy(qrResult = qr) }
    }

    fun onVisionFrameReady(bitmap: Bitmap) {
        val old = latestVisionFrame.getAndSet(bitmap)
        if (old != null && old !== bitmap && !old.isRecycled) {
            old.recycle()
        }
    }

    fun onFilteredPreviewFrame(bitmap: Bitmap) {
        _previewFrameBitmap.value = bitmap
    }

    fun clearFilteredPreview() {
        _previewFrameBitmap.value = null
    }

    fun toggleAutoMode() {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(autoModeEnabled = !it.autoModeEnabled) }
        }
    }

    fun cycleThemeMode() {
        viewModelScope.launch {
            settingsRepository.updateSettings { cur ->
                val all = ThemeMode.entries
                val next = all[(all.indexOf(cur.themeMode) + 1) % all.size]
                cur.copy(themeMode = next)
            }
        }
    }

    fun selectCameraConfig(configId: String) {
        val found = cameraConfigs.value.find { it.id == configId }
        if (found != null) {
            _uiState.update { it.copy(activeConfig = found, toastMessage = "Config: ${found.name}") }
        }
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(activeCameraConfigId = configId) }
        }
    }

    fun saveApiKeyFromBanner(rawKey: String) {
        if (keyStore.saveApiKey(rawKey)) {
            _uiState.update { it.copy(showKeyPromptBanner = false, toastMessage = "Gemini API key saved securely.") }
        }
    }

    fun dismissKeyBanner() {
        _uiState.update { it.copy(showKeyPromptBanner = false) }
    }

    fun setCameraMode(mode: CameraMode) {
        _uiState.update { it.copy(cameraMode = mode) }
    }

    fun toggleFrontBackCamera() {
        _uiState.update { it.copy(isFrontCamera = !it.isFrontCamera, faces = emptyList()) }
    }

    fun cycleAspectRatio() {
        val values = AspectRatioOption.entries
        val next = values[(values.indexOf(_uiState.value.aspectRatio) + 1) % values.size]
        _uiState.update { it.copy(aspectRatio = next) }
    }

    fun cycleGridType() {
        val values = GridType.entries
        val next = values[(values.indexOf(_uiState.value.gridType) + 1) % values.size]
        _uiState.update { it.copy(gridType = next) }
    }

    fun toggleHorizonLevel() {
        _uiState.update { it.copy(showHorizonLevel = !it.showHorizonLevel) }
    }

    fun cycleFlash() {
        val values = FlashState.entries
        val next = values[(values.indexOf(_uiState.value.flashState) + 1) % values.size]
        _uiState.update { it.copy(flashState = next) }
    }

    fun cycleTimer() {
        val next = when (_uiState.value.timerSeconds) {
            0 -> 3
            3 -> 5
            5 -> 10
            else -> 0
        }
        _uiState.update { it.copy(timerSeconds = next) }
    }

    fun toggleBurstMode() {
        _uiState.update { it.copy(burstEnabled = !it.burstEnabled) }
    }

    fun toggleHdr() {
        _uiState.update { it.copy(hdrEnabled = !it.hdrEnabled) }
    }

    fun toggleSmileAutoCapture() {
        _uiState.update { it.copy(smileAutoCapture = !it.smileAutoCapture) }
    }

    fun toggleBeforeAfterCompare() {
        _uiState.update { it.copy(beforeAfterCompare = !it.beforeAfterCompare) }
    }

    fun updateCompareSplit(fraction: Float) {
        _uiState.update { it.copy(compareSplitPosition = fraction.coerceIn(0.05f, 0.95f)) }
    }

    fun toggleProMode() {
        _uiState.update { it.copy(proParams = it.proParams.copy(isProModeOpen = !it.proParams.isProModeOpen)) }
    }

    fun updateProParams(transform: (ProManualParams) -> ProManualParams) {
        val oldEv = _uiState.value.proParams.exposureEv
        val updated = transform(_uiState.value.proParams)
        _uiState.update { it.copy(proParams = updated) }
        if (updated.exposureEv != oldEv) {
            _commands.tryEmit(CameraCommand.ApplyExposureEv(updated.exposureEv))
        }
    }

    fun resetProParams() {
        _uiState.update { it.copy(proParams = ProManualParams(isProModeOpen = it.proParams.isProModeOpen)) }
        _commands.tryEmit(CameraCommand.ApplyExposureEv(0f))
    }

    fun selectSingleFilter(filter: CameraFilter?) {
        if (filter == null) {
            _uiState.update { it.copy(activeFilterLayers = emptyList()) }
        } else {
            _uiState.update { it.copy(activeFilterLayers = listOf(ActiveFilterLayer(filter, 1.0f))) }
        }
    }

    fun toggleStackFilter(filter: CameraFilter) {
        _uiState.update { state ->
            val exists = state.activeFilterLayers.any { it.filter.id == filter.id }
            val next = if (exists) {
                state.activeFilterLayers.filterNot { it.filter.id == filter.id }
            } else {
                (state.activeFilterLayers + ActiveFilterLayer(filter, 0.75f)).take(4)
            }
            state.copy(activeFilterLayers = next)
        }
    }

    fun updateLayerIntensity(filterId: String, intensity: Float) {
        _uiState.update { state ->
            state.copy(
                activeFilterLayers = state.activeFilterLayers.map {
                    if (it.filter.id == filterId) it.copy(intensity = intensity.coerceIn(0f, 1f)) else it
                }
            )
        }
    }

    fun updateLayerBlendMode(filterId: String, mode: FilterBlendMode) {
        _uiState.update { state ->
            state.copy(
                activeFilterLayers = state.activeFilterLayers.map {
                    if (it.filter.id == filterId) it.copy(blendModeOverride = mode) else it
                }
            )
        }
    }

    fun swipeFilter(delta: Int) {
        val list = filters.value
        if (list.isEmpty()) return
        val currentId = _uiState.value.activeFilterLayers.firstOrNull()?.filter?.id
        val currentIndex = if (currentId == null) -1 else list.indexOfFirst { it.id == currentId }
        val nextIndex = (currentIndex + delta).let {
            when {
                it < -1 -> list.lastIndex
                it > list.lastIndex -> -1
                else -> it
            }
        }
        selectSingleFilter(if (nextIndex == -1) null else list[nextIndex])
    }

    fun setZoomLimits(min: Float, max: Float) {
        _uiState.update { it.copy(minZoomRatio = min, maxZoomRatio = max) }
    }

    fun setZoomRatio(ratio: Float) {
        val s = _uiState.value
        val clamped = ratio.coerceIn(s.minZoomRatio, s.maxZoomRatio)
        _uiState.update { it.copy(zoomRatio = clamped) }
        _commands.tryEmit(CameraCommand.ApplyZoom(clamped))
    }

    fun setHasFlashUnit(hasFlash: Boolean) {
        _uiState.update { it.copy(hasFlashUnit = hasFlash) }
    }

    fun triggerCapture() {
        val s = _uiState.value
        when (s.cameraMode) {
            CameraMode.VIDEO, CameraMode.SLOW_MO -> {
                _commands.tryEmit(CameraCommand.ToggleVideoRecording)
                return
            }
            CameraMode.TIME_LAPSE -> {
                if (s.isTimeLapseRunning) {
                    timeLapseJob?.cancel()
                    _uiState.update { it.copy(isTimeLapseRunning = false, toastMessage = "Time-Lapse finished") }
                } else {
                    _uiState.update { it.copy(isTimeLapseRunning = true, toastMessage = "Time-Lapse started (1.5s interval)") }
                    timeLapseJob = viewModelScope.launch {
                        while (isActive) {
                            _commands.tryEmit(CameraCommand.CaptureSinglePhoto)
                            delay(1500L)
                        }
                    }
                }
                return
            }
            else -> Unit
        }

        if (s.timerSeconds > 0 && timerJob?.isActive != true) {
            timerJob = viewModelScope.launch {
                for (sec in s.timerSeconds downTo 1) {
                    _uiState.update { it.copy(countdownRemaining = sec) }
                    delay(1000L)
                }
                _uiState.update { it.copy(countdownRemaining = 0) }
                executePhotoOrBurst()
            }
        } else {
            executePhotoOrBurst()
        }
    }

    private fun executePhotoOrBurst() {
        if (_uiState.value.burstEnabled) {
            viewModelScope.launch {
                _uiState.update { it.copy(isBurstRunning = true) }
                repeat(5) {
                    _commands.tryEmit(CameraCommand.CaptureSinglePhoto)
                    delay(260L)
                }
                _uiState.update { it.copy(isBurstRunning = false, toastMessage = "Saved 5-frame burst") }
            }
        } else {
            _commands.tryEmit(CameraCommand.CaptureSinglePhoto)
        }
    }

    fun saveCapturedBitmap(rawBitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.Default) {
            val s = _uiState.value
            val cfg = settings.value
            val processed = FilterBitmapProcessor.applyFullPipeline(
                source = rawBitmap,
                adj = s.effectiveAdjustments,
                faces = s.faces,
                arLayers = s.activeFilterLayers,
                isHdr = s.hdrEnabled,
                isNight = s.cameraMode == CameraMode.NIGHT,
                isDocumentScan = s.cameraMode == CameraMode.DOCUMENT,
                isPortraitBokeh = s.cameraMode == CameraMode.PORTRAIT
            )
            mediaRepository.savePhoto(
                processedBitmap = processed,
                rawOriginalBitmap = rawBitmap,
                quality = cfg.captureQuality.jpegQuality,
                filterNames = s.activeFilterLayers.map { it.filter.name },
                sceneLabel = s.aiGuidance.sceneLabel
            )
            if (processed !== rawBitmap && !processed.isRecycled) processed.recycle()
            if (!rawBitmap.isRecycled) rawBitmap.recycle()
            _uiState.update { it.copy(shutterPulseTick = System.currentTimeMillis()) }
        }
    }

    fun onVideoRecordingStarted() {
        _uiState.update { it.copy(isRecordingVideo = true, recordingElapsedSec = 0) }
        viewModelScope.launch {
            while (_uiState.value.isRecordingVideo) {
                delay(1000L)
                if (_uiState.value.isRecordingVideo) {
                    _uiState.update { it.copy(recordingElapsedSec = it.recordingElapsedSec + 1) }
                }
            }
        }
    }

    fun onVideoRecordingFinalized(file: java.io.File?) {
        _uiState.update { it.copy(isRecordingVideo = false, recordingElapsedSec = 0) }
        if (file != null && file.exists()) {
            viewModelScope.launch {
                mediaRepository.registerVideo(file, _uiState.value.aiGuidance.sceneLabel)
                _uiState.update { it.copy(toastMessage = "Video saved to gallery") }
            }
        }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun setToast(msg: String) {
        _uiState.update { it.copy(toastMessage = msg) }
    }

    private fun speakIfOptedIn(text: String) {
        if (tts == null) {
            tts = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.US
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "lensmind_tts")
                }
            }
        } else {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "lensmind_tts")
        }
    }

    override fun onCleared() {
        super.onCleared()
        horizonManager.stop()
        tts?.shutdown()
        latestVisionFrame.getAndSet(null)?.recycle()
    }

    class Factory(private val context: Context, private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CameraViewModel(
            appContext = context.applicationContext,
            keyStore = container.keyStoreManager,
            settingsRepository = container.settingsRepository,
            filterRepository = container.filterRepository,
            cameraConfigRepository = container.cameraConfigRepository,
            mediaRepository = container.mediaRepository,
            geminiApiClient = container.geminiApiClient
        ) as T
    }
}
