package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.CamcorderProfile
import android.net.Uri
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.FlashAuto
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.example.camera.FaceAndQrAnalyzer
import com.example.camera.FilterBitmapProcessor
import com.example.data.MediaRepository
import com.example.model.AspectRatioOption
import com.example.model.CameraMode
import com.example.model.FlashState
import com.example.ui.components.AiCoachAndLogHud
import com.example.ui.components.AnamolLogoMark
import com.example.ui.components.FilterCarouselBar
import com.example.ui.components.FilterEffectAndArOverlay
import com.example.ui.components.ProControlsDrawer
import com.example.ui.components.QrScannerBanner
import com.example.ui.components.ThemeQuickToggleChip
import com.example.ui.components.ViewfinderGuidesOverlay
import com.example.ui.theme.AmberPro
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMint
import com.example.viewmodel.CameraCommand
import com.example.viewmodel.CameraViewModel
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    mediaRepository: MediaRepository,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFilterManager: () -> Unit,
    onOpenConfigManager: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val mediaItems by viewModel.mediaItems.collectAsStateWithLifecycle()
    val previewFrameBitmap by viewModel.previewFrameBitmap.collectAsStateWithLifecycle()

    fun tickHaptic() {
        if (settings.hapticsEnabled) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val needsFilteredPreview = remember(state.effectiveAdjustments, state.activeFilterLayers, state.activeConfig) {
        val adj = state.effectiveAdjustments
        state.activeFilterLayers.isNotEmpty() ||
            kotlin.math.abs(adj.brightness) > 0.025f ||
            kotlin.math.abs(adj.contrast - 1f) > 0.025f ||
            kotlin.math.abs(adj.saturation - 1f) > 0.025f ||
            kotlin.math.abs(adj.hue) > 1f ||
            kotlin.math.abs(adj.temperature) > 0.03f ||
            kotlin.math.abs(adj.tint) > 0.02f ||
            kotlin.math.abs(adj.greenery) > 0.03f ||
            kotlin.math.abs(adj.rgbGainR - 1f) > 0.02f ||
            kotlin.math.abs(adj.rgbGainG - 1f) > 0.02f ||
            kotlin.math.abs(adj.rgbGainB - 1f) > 0.02f ||
            adj.blur > 0.05f
    }

    LaunchedEffect(needsFilteredPreview) {
        if (!needsFilteredPreview) {
            viewModel.clearFilteredPreview()
        }
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasAudioPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearToast()
        }
    }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var activeVideoFile by remember { mutableStateOf<File?>(null) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var shutterFlashOverlay by remember { mutableStateOf(false) }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }

    val analyzer = remember {
        FaceAndQrAnalyzer(
            isFrontCameraProvider = { viewModel.uiState.value.isFrontCamera },
            isQrEnabledProvider = {
                val m = viewModel.uiState.value.cameraMode
                m == CameraMode.QR || m == CameraMode.PHOTO || m == CameraMode.DOCUMENT
            },
            needsFilteredPreviewProvider = {
                val st = viewModel.uiState.value
                val adj = st.effectiveAdjustments
                st.activeFilterLayers.isNotEmpty() ||
                    kotlin.math.abs(adj.brightness) > 0.025f ||
                    kotlin.math.abs(adj.contrast - 1f) > 0.025f ||
                    kotlin.math.abs(adj.saturation - 1f) > 0.025f ||
                    kotlin.math.abs(adj.hue) > 1f ||
                    kotlin.math.abs(adj.temperature) > 0.03f ||
                    kotlin.math.abs(adj.tint) > 0.02f ||
                    kotlin.math.abs(adj.greenery) > 0.03f ||
                    kotlin.math.abs(adj.rgbGainR - 1f) > 0.02f ||
                    kotlin.math.abs(adj.rgbGainG - 1f) > 0.02f ||
                    kotlin.math.abs(adj.rgbGainB - 1f) > 0.02f ||
                    adj.blur > 0.05f
            },
            onLightSample = { bmp -> viewModel.onLightFrameSample(bmp) },
            onFacesDetected = { faces -> viewModel.onFacesDetected(faces) },
            onQrDetected = { qr -> viewModel.onQrDetected(qr) },
            onVisionFrameReady = { bmp -> viewModel.onVisionFrameReady(bmp) },
            onFilteredPreviewFrame = { bmp -> viewModel.onFilteredPreviewFrame(bmp) }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            activeRecording?.stop()
            analyzer.close()
            analysisExecutor.shutdown()
            captureExecutor.shutdown()
        }
    }

    LaunchedEffect(focusPoint) {
        if (focusPoint != null) {
            delay(1200)
            focusPoint = null
        }
    }

    LaunchedEffect(state.shutterPulseTick) {
        if (state.shutterPulseTick > 0L) {
            shutterFlashOverlay = true
            delay(90)
            shutterFlashOverlay = false
        }
    }

    // Update targetRotation dynamically on existing ImageCapture / VideoCapture without restarting CameraX stream
    LaunchedEffect(state.horizonState.targetSurfaceRotation, imageCapture, videoCapture) {
        val rot = state.horizonState.targetSurfaceRotation
        imageCapture?.targetRotation = rot
        videoCapture?.targetRotation = rot
    }

    // Smooth shortest-path rotation for HUD icons matching physical device orientation
    val targetIconAngle = when (state.horizonState.deviceRotationDeg) {
        90 -> -90f
        180 -> 180f
        270 -> 90f
        else -> 0f
    }
    val iconRotation by animateFloatAsState(
        targetValue = targetIconAngle,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 260f),
        label = "hud_icon_rotation"
    )

    // Handle CameraX commands from ViewModel
    LaunchedEffect(viewModel) {
        viewModel.commands.collect { cmd ->
            when (cmd) {
                is CameraCommand.ApplyZoom -> {
                    camera?.cameraControl?.setZoomRatio(cmd.ratio)
                }
                is CameraCommand.ApplyExposureEv -> {
                    val cam = camera ?: return@collect
                    val expState = cam.cameraInfo.exposureState
                    if (expState.isExposureCompensationSupported) {
                        val step = expState.exposureCompensationStep.toFloat().coerceAtLeast(0.1f)
                        val idx = (cmd.ev / step).toInt()
                            .coerceIn(expState.exposureCompensationRange.lower, expState.exposureCompensationRange.upper)
                        cam.cameraControl.setExposureCompensationIndex(idx)
                    }
                }
                CameraCommand.CaptureSinglePhoto -> {
                    val cap = imageCapture ?: return@collect
                    if (settings.hapticsEnabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    cap.takePicture(
                        captureExecutor,
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                try {
                                    val bmp = imageProxyToRotatedBitmap(image, viewModel.uiState.value.isFrontCamera)
                                    viewModel.saveCapturedBitmap(bmp)
                                } finally {
                                    image.close()
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                viewModel.setToast("Capture failed: ${exception.localizedMessage}")
                            }
                        }
                    )
                }
                CameraCommand.ToggleVideoRecording -> {
                    val vc = videoCapture
                    if (vc == null) {
                        viewModel.setToast("Video recording is not supported on this camera hardware")
                        return@collect
                    }
                    val currentRec = activeRecording
                    if (currentRec != null) {
                        currentRec.stop()
                        activeRecording = null
                    } else {
                        if (!hasAudioPermission) {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                        val outFile = mediaRepository.createTempVideoFile()
                        activeVideoFile = outFile
                        val outputOptions = FileOutputOptions.Builder(outFile).build()
                        var pending = vc.output.prepareRecording(context, outputOptions)
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            pending = pending.withAudioEnabled()
                        }
                        activeRecording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
                            when (event) {
                                is VideoRecordEvent.Start -> viewModel.onVideoRecordingStarted()
                                is VideoRecordEvent.Finalize -> {
                                    val savedFile = if (!event.hasError()) activeVideoFile else null
                                    viewModel.onVideoRecordingFinalized(savedFile)
                                    activeRecording = null
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Apply Flash / Torch mode reactively
    LaunchedEffect(state.flashState, camera) {
        val cam = camera ?: return@LaunchedEffect
        val cap = imageCapture
        if (cam.cameraInfo.hasFlashUnit()) {
            cam.cameraControl.enableTorch(state.flashState == FlashState.TORCH)
            cap?.flashMode = when (state.flashState) {
                FlashState.ON -> ImageCapture.FLASH_MODE_ON
                FlashState.AUTO -> ImageCapture.FLASH_MODE_AUTO
                else -> ImageCapture.FLASH_MODE_OFF
            }
        }
    }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    val isVideoMode = state.cameraMode == CameraMode.VIDEO || state.cameraMode == CameraMode.SLOW_MO

    // Rebind CameraX ONLY when camera lens, permission, or video-vs-photo mode changes (never on orientation change)
    LaunchedEffect(hasCameraPermission, state.isFrontCamera, isVideoMode) {
        if (!hasCameraPermission) return@LaunchedEffect
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val selector = if (state.isFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                val preview = Preview.Builder().build().apply {
                    surfaceProvider = previewView.surfaceProvider
                }
                val imgAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build().apply {
                        setAnalyzer(analysisExecutor, analyzer)
                    }

                provider.unbindAll()

                val bound: Camera
                if (isVideoMode && hasAnyVideoEncoderProfile(context, state.isFrontCamera)) {
                    val recorder = Recorder.Builder()
                        .setQualitySelector(
                            QualitySelector.fromOrderedList(
                                listOf(Quality.HD, Quality.SD, Quality.LOWEST),
                                FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                            )
                        )
                        .build()
                    val vidCap = VideoCapture.withOutput(recorder).apply {
                        targetRotation = viewModel.uiState.value.horizonState.targetSurfaceRotation
                    }
                    bound = provider.bindToLifecycle(
                        lifecycleOwner,
                        selector,
                        preview,
                        vidCap,
                        imgAnalysis
                    )
                    videoCapture = vidCap
                    imageCapture = null
                } else {
                    val imgCap = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setTargetRotation(viewModel.uiState.value.horizonState.targetSurfaceRotation)
                        .build()
                    bound = provider.bindToLifecycle(
                        lifecycleOwner,
                        selector,
                        preview,
                        imgCap,
                        imgAnalysis
                    )
                    imageCapture = imgCap
                    videoCapture = null
                    if (isVideoMode) {
                        viewModel.setToast("Video profile unavailable on this virtual camera; photo capture remains ready")
                    }
                }
                camera = bound

                val zoomState = bound.cameraInfo.zoomState.value
                viewModel.setZoomLimits(
                    zoomState?.minZoomRatio ?: 1f,
                    (zoomState?.maxZoomRatio ?: 6f).coerceAtMost(10f)
                )
                viewModel.setHasFlashUnit(bound.cameraInfo.hasFlashUnit())
            } catch (e: Exception) {
                viewModel.setToast("Camera unavailable: ${e.localizedMessage ?: "Check permissions"}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    val liveColorMatrix = remember(state.effectiveAdjustments) {
        FilterBitmapProcessor.buildColorMatrix(state.effectiveAdjustments)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val isLandscape = maxWidth > maxHeight
        val hudOrientationRot = if (isLandscape) 0f else iconRotation

        if (!hasCameraPermission) {
            CameraPermissionPrompt(
                onRequestPermission = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                onOpenSettings = onOpenSettings
            )
        } else {
            // Viewfinder container respecting aspect ratio in both portrait & landscape
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val viewfinderModifier = if (isLandscape) {
                    when (state.aspectRatio) {
                        AspectRatioOption.RATIO_9_16, AspectRatioOption.RATIO_16_9 -> Modifier.fillMaxSize()
                        AspectRatioOption.RATIO_4_3 -> Modifier.fillMaxHeight().aspectRatio(4f / 3f)
                        AspectRatioOption.RATIO_1_1 -> Modifier.fillMaxHeight().aspectRatio(1f)
                    }
                } else {
                    when (state.aspectRatio) {
                        AspectRatioOption.RATIO_9_16 -> Modifier.fillMaxSize()
                        AspectRatioOption.RATIO_4_3 -> Modifier.fillMaxWidth().aspectRatio(3f / 4f)
                        AspectRatioOption.RATIO_1_1 -> Modifier.fillMaxWidth().aspectRatio(1f)
                        AspectRatioOption.RATIO_16_9 -> Modifier.fillMaxWidth().aspectRatio(9f / 16f)
                    }
                }

                Box(
                    modifier = viewfinderModifier
                        .clip(RoundedCornerShape(if (state.aspectRatio == AspectRatioOption.RATIO_9_16) 0.dp else 18.dp))
                        .pointerInput(state.zoomRatio) {
                            detectTransformGestures { _, _, zoom, _ ->
                                if (kotlin.math.abs(zoom - 1f) > 0.01f) {
                                    viewModel.setZoomRatio(state.zoomRatio * zoom)
                                }
                            }
                        }
                        .pointerInput(state.beforeAfterCompare) {
                            detectHorizontalDragGestures { change, dragAmount ->
                                change.consume()
                                if (state.beforeAfterCompare) {
                                    val deltaFrac = dragAmount / size.width.toFloat().coerceAtLeast(1f)
                                    viewModel.updateCompareSplit(state.compareSplitPosition + deltaFrac)
                                } else if (kotlin.math.abs(dragAmount) > 28f) {
                                    tickHaptic()
                                    viewModel.swipeFilter(if (dragAmount < 0) 1 else -1)
                                }
                            }
                        }
                ) {
                    AndroidView(
                        factory = {
                            previewView.setOnTouchListener { v, event ->
                                if (event.action == MotionEvent.ACTION_UP) {
                                    val factory = previewView.meteringPointFactory
                                    val point = factory.createPoint(event.x, event.y)
                                    val action = FocusMeteringAction.Builder(
                                        point,
                                        FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                                    )
                                        .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                        .build()
                                    camera?.cameraControl?.startFocusAndMetering(action)
                                    focusPoint = Offset(event.x, event.y)
                                    v.performClick()
                                }
                                true
                            }
                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (needsFilteredPreview && previewFrameBitmap != null) {
                        val bmp = previewFrameBitmap!!
                        val blurDp = (state.effectiveAdjustments.blur * 10f).dp
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            colorFilter = ColorFilter.colorMatrix(ColorMatrix(liveColorMatrix.array)),
                            modifier = Modifier
                                .fillMaxSize()
                                .then(if (blurDp > 0.5.dp) Modifier.blur(blurDp) else Modifier)
                                .drawWithContent {
                                    if (state.beforeAfterCompare) {
                                        clipRect(left = size.width * state.compareSplitPosition) {
                                            this@drawWithContent.drawContent()
                                        }
                                    } else {
                                        drawContent()
                                    }
                                }
                        )
                    }

                    FilterEffectAndArOverlay(
                        adjustments = state.effectiveAdjustments,
                        activeLayers = state.activeFilterLayers,
                        faces = state.faces,
                        beforeAfterCompare = state.beforeAfterCompare,
                        compareSplit = state.compareSplitPosition
                    )

                    ViewfinderGuidesOverlay(
                        gridType = state.gridType,
                        showHorizon = state.showHorizonLevel,
                        horizon = state.horizonState,
                        aiGuidance = state.aiGuidance,
                        focusPoint = focusPoint
                    )

                    if (shutterFlashOverlay) {
                        Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.55f)))
                    }
                }
            }

            if (state.countdownRemaining > 0) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = state.countdownRemaining.toString(),
                        fontSize = 88.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan,
                        modifier = Modifier.rotate(hudOrientationRot)
                    )
                }
            }

            if (state.isRecordingVideo) {
                Surface(
                    color = CoralAlert,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 52.dp)
                ) {
                    val m = state.recordingElapsedSec / 60
                    val s = state.recordingElapsedSec % 60
                    Text(
                        text = "REC %02d:%02d".format(m, s),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }

            if (!isLandscape) {
                // =============================================================
                // PORTRAIT LAYOUT: Top HUD + Bottom Controls
                // =============================================================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    TopActionBar(
                        state = state,
                        settings = settings,
                        iconRotation = hudOrientationRot,
                        onToggleAuto = { tickHaptic(); viewModel.toggleAutoMode() },
                        onOpenConfigs = { tickHaptic(); onOpenConfigManager() },
                        onCycleTheme = { tickHaptic(); viewModel.cycleThemeMode() },
                        onCycleFlash = { tickHaptic(); viewModel.cycleFlash() },
                        onCycleTimer = { tickHaptic(); viewModel.cycleTimer() },
                        onToggleCompare = { tickHaptic(); viewModel.toggleBeforeAfterCompare() },
                        onOpenSettings = { tickHaptic(); onOpenSettings() }
                    )

                    SecondaryToggleStrip(
                        state = state,
                        onCycleAspect = { tickHaptic(); viewModel.cycleAspectRatio() },
                        onCycleGrid = { tickHaptic(); viewModel.cycleGridType() },
                        onToggleHdr = { tickHaptic(); viewModel.toggleHdr() },
                        onToggleBurst = { tickHaptic(); viewModel.toggleBurstMode() },
                        onToggleSmile = { tickHaptic(); viewModel.toggleSmileAutoCapture() },
                        onTogglePro = { tickHaptic(); viewModel.toggleProMode() },
                        onOpenConfigs = { tickHaptic(); onOpenConfigManager() }
                    )

                    AiCoachAndLogHud(
                        aiGuidance = state.aiGuidance,
                        suggestions = state.aiSuggestions,
                        actionLog = state.aiActionLog,
                        isAnalyzing = state.isAiAnalyzing,
                        autoModeEnabled = settings.autoModeEnabled,
                        horizonRoll = state.horizonState.rollDegrees,
                        aiStatusNote = state.aiStatusNote,
                        onApplySuggestion = { viewModel.applySuggestion(it) },
                        onUndoAction = { viewModel.undoAiAction(it) }
                    )

                    if (state.showKeyPromptBanner) {
                        QuickApiKeyBanner(
                            onSaveKey = { viewModel.saveApiKeyFromBanner(it) },
                            onDismiss = { viewModel.dismissKeyBanner() }
                        )
                    }

                    state.qrResult?.let { qr ->
                        QrScannerBanner(
                            qr = qr,
                            onCopy = { txt ->
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("QR", txt))
                                viewModel.setToast("Copied QR text")
                            },
                            onOpenUrl = { url ->
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                            }
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ZoomPillsRow(
                        zoomRatio = state.zoomRatio,
                        maxZoomRatio = state.maxZoomRatio,
                        iconRotation = hudOrientationRot,
                        onSelectZoom = { tickHaptic(); viewModel.setZoomRatio(it) }
                    )

                    AnimatedVisibility(visible = state.proParams.isProModeOpen) {
                        ProControlsDrawer(
                            params = state.proParams,
                            onUpdate = { viewModel.updateProParams(it) },
                            onReset = { viewModel.resetProParams() },
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )
                    }

                    FilterCarouselBar(
                        filters = filters,
                        activeLayers = state.activeFilterLayers,
                        onSelectSingle = { tickHaptic(); viewModel.selectSingleFilter(it) },
                        onToggleStack = { tickHaptic(); viewModel.toggleStackFilter(it) },
                        onIntensityChange = { id, v -> viewModel.updateLayerIntensity(id, v) },
                        onBlendModeChange = { id, b -> viewModel.updateLayerBlendMode(id, b) },
                        onOpenFilterManager = onOpenFilterManager,
                        iconRotationDegrees = hudOrientationRot
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(CameraMode.entries) { mode ->
                            val selected = mode == state.cameraMode
                            Text(
                                text = mode.label.uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) ElectricCyan else Color.White.copy(alpha = 0.62f),
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        tickHaptic()
                                        viewModel.setCameraMode(mode)
                                    }
                                    .padding(horizontal = 4.dp, vertical = 4.dp)
                                    .testTag("mode_${mode.name}")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        GalleryThumbnailOrb(
                            latestPath = mediaItems.firstOrNull()?.filePath,
                            rotation = hudOrientationRot,
                            onClick = onOpenGallery
                        )
                        ShutterButton(
                            state = state,
                            onClick = { viewModel.triggerCapture() }
                        )
                        FlipCameraOrb(
                            rotation = hudOrientationRot,
                            onClick = { tickHaptic(); viewModel.toggleFrontBackCamera() }
                        )
                    }
                }
            } else {
                // =============================================================
                // LANDSCAPE / TABLET LAYOUT:
                // Left/Top HUD + Bottom Carousel + Right-Side Shutter & Mode Dock
                // =============================================================
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(start = 14.dp, top = 8.dp, end = 176.dp)
                        .widthIn(max = 540.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TopActionBar(
                        state = state,
                        settings = settings,
                        iconRotation = 0f,
                        onToggleAuto = { tickHaptic(); viewModel.toggleAutoMode() },
                        onOpenConfigs = { tickHaptic(); onOpenConfigManager() },
                        onCycleTheme = { tickHaptic(); viewModel.cycleThemeMode() },
                        onCycleFlash = { tickHaptic(); viewModel.cycleFlash() },
                        onCycleTimer = { tickHaptic(); viewModel.cycleTimer() },
                        onToggleCompare = { tickHaptic(); viewModel.toggleBeforeAfterCompare() },
                        onOpenSettings = { tickHaptic(); onOpenSettings() }
                    )

                    SecondaryToggleStrip(
                        state = state,
                        onCycleAspect = { tickHaptic(); viewModel.cycleAspectRatio() },
                        onCycleGrid = { tickHaptic(); viewModel.cycleGridType() },
                        onToggleHdr = { tickHaptic(); viewModel.toggleHdr() },
                        onToggleBurst = { tickHaptic(); viewModel.toggleBurstMode() },
                        onToggleSmile = { tickHaptic(); viewModel.toggleSmileAutoCapture() },
                        onTogglePro = { tickHaptic(); viewModel.toggleProMode() },
                        onOpenConfigs = { tickHaptic(); onOpenConfigManager() }
                    )

                    AiCoachAndLogHud(
                        aiGuidance = state.aiGuidance,
                        suggestions = state.aiSuggestions,
                        actionLog = state.aiActionLog,
                        isAnalyzing = state.isAiAnalyzing,
                        autoModeEnabled = settings.autoModeEnabled,
                        horizonRoll = state.horizonState.rollDegrees,
                        aiStatusNote = state.aiStatusNote,
                        onApplySuggestion = { viewModel.applySuggestion(it) },
                        onUndoAction = { viewModel.undoAiAction(it) }
                    )

                    if (state.showKeyPromptBanner) {
                        QuickApiKeyBanner(
                            onSaveKey = { viewModel.saveApiKeyFromBanner(it) },
                            onDismiss = { viewModel.dismissKeyBanner() }
                        )
                    }

                    state.qrResult?.let { qr ->
                        QrScannerBanner(
                            qr = qr,
                            onCopy = { txt ->
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("QR", txt))
                                viewModel.setToast("Copied QR text")
                            },
                            onOpenUrl = { url ->
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                            }
                        )
                    }
                }

                // Bottom-Left Zoom + Pro Drawer + Filter Carousel
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .navigationBarsPadding()
                        .padding(start = 12.dp, bottom = 10.dp, end = 176.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ZoomPillsRow(
                        zoomRatio = state.zoomRatio,
                        maxZoomRatio = state.maxZoomRatio,
                        iconRotation = 0f,
                        onSelectZoom = { tickHaptic(); viewModel.setZoomRatio(it) }
                    )

                    AnimatedVisibility(visible = state.proParams.isProModeOpen) {
                        ProControlsDrawer(
                            params = state.proParams,
                            onUpdate = { viewModel.updateProParams(it) },
                            onReset = { viewModel.resetProParams() },
                            modifier = Modifier.widthIn(max = 520.dp)
                        )
                    }

                    FilterCarouselBar(
                        filters = filters,
                        activeLayers = state.activeFilterLayers,
                        onSelectSingle = { tickHaptic(); viewModel.selectSingleFilter(it) },
                        onToggleStack = { tickHaptic(); viewModel.toggleStackFilter(it) },
                        onIntensityChange = { id, v -> viewModel.updateLayerIntensity(id, v) },
                        onBlendModeChange = { id, b -> viewModel.updateLayerBlendMode(id, b) },
                        onOpenFilterManager = onOpenFilterManager,
                        iconRotationDegrees = 0f
                    )
                }

                // Right-Side Glassmorphic Dock: Mode Switcher + Flip + Shutter + Gallery
                Surface(
                    color = Color(0xFF0A101B).copy(alpha = 0.66f),
                    shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(166.dp)
                        .navigationBarsPadding()
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Vertical Mode Selector Column
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(end = 6.dp)
                        ) {
                            items(CameraMode.entries) { mode ->
                                val selected = mode == state.cameraMode
                                Surface(
                                    color = if (selected) ElectricCyan.copy(alpha = 0.22f) else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            tickHaptic()
                                            viewModel.setCameraMode(mode)
                                        }
                                        .testTag("mode_${mode.name}")
                                ) {
                                    Text(
                                        text = mode.label.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) ElectricCyan else Color.White.copy(alpha = 0.65f),
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }

                        // Flip | Shutter | Gallery vertical column
                        Column(
                            modifier = Modifier.fillMaxHeight(),
                            verticalArrangement = Arrangement.SpaceEvenly,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            FlipCameraOrb(
                                rotation = 0f,
                                onClick = { tickHaptic(); viewModel.toggleFrontBackCamera() }
                            )
                            ShutterButton(
                                state = state,
                                onClick = { viewModel.triggerCapture() }
                            )
                            GalleryThumbnailOrb(
                                latestPath = mediaItems.firstOrNull()?.filePath,
                                rotation = 0f,
                                onClick = onOpenGallery
                            )
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (isLandscape) 86.dp else 118.dp, end = if (isLandscape) 166.dp else 0.dp)
        )
    }
}

@Composable
private fun TopActionBar(
    state: com.example.viewmodel.CameraUiState,
    settings: com.example.model.AppSettings,
    iconRotation: Float,
    onToggleAuto: () -> Unit,
    onOpenConfigs: () -> Unit,
    onCycleTheme: () -> Unit,
    onCycleFlash: () -> Unit,
    onCycleTimer: () -> Unit,
    onToggleCompare: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Surface(
                color = if (settings.autoModeEnabled) EmeraldMint else Color.Black.copy(alpha = 0.62f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .border(
                        1.dp,
                        if (settings.autoModeEnabled) EmeraldMint else ElectricCyan.copy(alpha = 0.6f),
                        RoundedCornerShape(20.dp)
                    )
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(onClick = onToggleAuto)
                    .testTag("auto_mode_toggle")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = "Auto AI Mode",
                        tint = if (settings.autoModeEnabled) Color.Black else ElectricCyan,
                        modifier = Modifier.size(15.dp).rotate(iconRotation)
                    )
                    Text(
                        text = if (settings.autoModeEnabled) "AUTO AI" else "ASSIST",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (settings.autoModeEnabled) Color.Black else Color.White,
                        maxLines = 1
                    )
                }
            }

            // Active GCam 8.5 Config quick pill
            state.activeConfig?.let { cfg ->
                Surface(
                    color = Color.Black.copy(alpha = 0.62f),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(
                        1.dp,
                        Brush.linearGradient(listOf(ElectricCyan.copy(alpha = 0.7f), EmeraldMint.copy(alpha = 0.7f)))
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(onClick = onOpenConfigs)
                        .testTag("camera_config_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(cfg.icon, fontSize = 12.sp, modifier = Modifier.rotate(iconRotation))
                        Text(
                            text = cfg.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 96.dp)
                        )
                    }
                }
            }

            if (settings.lightAdaptationEnabled) {
                Surface(
                    color = Color.Black.copy(alpha = 0.56f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = state.lightState.conditionLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            state.lightState.lowLightBoostActive -> AmberPro
                            state.lightState.brightLightProtectActive -> ElectricCyan
                            else -> Color.White.copy(alpha = 0.85f)
                        },
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            ThemeQuickToggleChip(
                themeMode = settings.themeMode,
                onToggle = onCycleTheme,
                hudOverlay = true
            )
            IconButton(onClick = onCycleFlash, modifier = Modifier.testTag("flash_btn")) {
                val icon = when (state.flashState) {
                    FlashState.OFF -> Icons.Rounded.FlashOff
                    FlashState.AUTO -> Icons.Rounded.FlashAuto
                    FlashState.ON -> Icons.Rounded.FlashOn
                    FlashState.TORCH -> Icons.Rounded.FlashlightOn
                }
                Icon(
                    icon,
                    contentDescription = "Flash",
                    tint = if (state.flashState != FlashState.OFF) AmberPro else Color.White,
                    modifier = Modifier.rotate(iconRotation)
                )
            }
            IconButton(onClick = onCycleTimer, modifier = Modifier.testTag("timer_btn")) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.rotate(iconRotation)) {
                    Icon(Icons.Rounded.Timer, contentDescription = "Timer", tint = if (state.timerSeconds > 0) ElectricCyan else Color.White)
                    if (state.timerSeconds > 0) {
                        Text("${state.timerSeconds}", style = MaterialTheme.typography.labelSmall, color = ElectricCyan, modifier = Modifier.align(Alignment.BottomEnd))
                    }
                }
            }
            IconButton(onClick = onToggleCompare, modifier = Modifier.testTag("compare_btn")) {
                Icon(
                    Icons.Rounded.Compare,
                    contentDescription = "Before/After Compare",
                    tint = if (state.beforeAfterCompare) ElectricCyan else Color.White,
                    modifier = Modifier.rotate(iconRotation)
                )
            }
            IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("settings_btn")) {
                Icon(
                    Icons.Rounded.Settings,
                    contentDescription = "Settings",
                    tint = Color.White,
                    modifier = Modifier.rotate(iconRotation)
                )
            }
        }
    }
}

@Composable
private fun SecondaryToggleStrip(
    state: com.example.viewmodel.CameraUiState,
    onCycleAspect: () -> Unit,
    onCycleGrid: () -> Unit,
    onToggleHdr: () -> Unit,
    onToggleBurst: () -> Unit,
    onToggleSmile: () -> Unit,
    onTogglePro: () -> Unit,
    onOpenConfigs: () -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item { MiniTogglePill("GCam 8.5 XML", state.activeConfig != null, "configs_strip_btn", onOpenConfigs) }
        item { MiniTogglePill(state.aspectRatio.label, false, "aspect_btn", onCycleAspect) }
        item { MiniTogglePill("Grid ${state.gridType.label}", state.gridType.ordinal > 0, "grid_btn", onCycleGrid) }
        item { MiniTogglePill("HDR", state.hdrEnabled, "hdr_btn", onToggleHdr) }
        item { MiniTogglePill("Burst", state.burstEnabled, "burst_btn", onToggleBurst) }
        item { MiniTogglePill("Smile Shot", state.smileAutoCapture, "smile_btn", onToggleSmile) }
        item { MiniTogglePill("Pro DSLR", state.proParams.isProModeOpen, "pro_toggle_btn", onTogglePro) }
    }
}

@Composable
private fun ZoomPillsRow(
    zoomRatio: Float,
    maxZoomRatio: Float,
    iconRotation: Float,
    onSelectZoom: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(1.0f to "1x", 2.0f to "2x", 3.5f to "3.5x").forEach { (z, label) ->
            val active = kotlin.math.abs(zoomRatio - z) < 0.25f
            Surface(
                color = if (active) ElectricCyan else Color.Black.copy(alpha = 0.55f),
                shape = CircleShape,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .clip(CircleShape)
                    .clickable { onSelectZoom(z.coerceAtMost(maxZoomRatio)) }
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) Color.Black else Color.White,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp).rotate(iconRotation)
                )
            }
        }
    }
}

@Composable
private fun GalleryThumbnailOrb(
    latestPath: String?,
    rotation: Float,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .rotate(rotation)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .border(1.5.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("open_gallery_btn"),
        contentAlignment = Alignment.Center
    ) {
        if (latestPath != null) {
            Image(
                painter = rememberAsyncImagePainter(File(latestPath)),
                contentDescription = "Open Gallery",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(Icons.Rounded.PhotoLibrary, contentDescription = "Open Gallery", tint = Color.White)
        }
    }
}

@Composable
private fun ShutterButton(
    state: com.example.viewmodel.CameraUiState,
    onClick: () -> Unit
) {
    val shutterScale by animateFloatAsState(
        targetValue = if (state.isRecordingVideo || state.isBurstRunning) 0.88f else 1f,
        animationSpec = spring(),
        label = "shutter_scale"
    )
    val isVideoOrTimeLapse = state.cameraMode == CameraMode.VIDEO ||
        state.cameraMode == CameraMode.SLOW_MO ||
        state.cameraMode == CameraMode.TIME_LAPSE

    Box(
        modifier = Modifier
            .size(78.dp)
            .scale(shutterScale)
            .clip(CircleShape)
            .border(4.dp, if (isVideoOrTimeLapse) CoralAlert else Color.White, CircleShape)
            .padding(7.dp)
            .clip(if (state.isRecordingVideo || state.isTimeLapseRunning) RoundedCornerShape(14.dp) else CircleShape)
            .background(if (isVideoOrTimeLapse) CoralAlert else Color.White)
            .clickable(onClick = onClick)
            .testTag("shutter_button"),
        contentAlignment = Alignment.Center
    ) {
        if (state.isRecordingVideo || state.isTimeLapseRunning) {
            Box(modifier = Modifier.size(22.dp).background(Color.Black, RoundedCornerShape(4.dp)))
        }
    }
}

@Composable
private fun FlipCameraOrb(
    rotation: Float,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .rotate(rotation)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f))
            .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
            .clickable(onClick = onClick)
            .testTag("flip_camera_btn"),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Rounded.Cameraswitch, contentDescription = "Switch Front/Back Camera", tint = Color.White)
    }
}

@Composable
private fun MiniTogglePill(
    label: String,
    active: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        color = if (active) ElectricCyan.copy(alpha = 0.24f) else Color.Black.copy(alpha = 0.56f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .border(1.dp, if (active) ElectricCyan else Color.White.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) ElectricCyan else Color.White,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun QuickApiKeyBanner(
    onSaveKey: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var inputKey by remember { mutableStateOf("") }
    Surface(
        color = Color(0xFF131A26).copy(alpha = 0.94f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ElectricCyan.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.Key, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                    Text("Connect Anamol AI Camera Brain", style = MaterialTheme.typography.titleMedium, color = Color.White)
                }
                TextButton(onClick = onDismiss) {
                    Text("Later", color = Color.White.copy(alpha = 0.7f))
                }
            }
            OutlinedTextField(
                value = inputKey,
                onValueChange = { inputKey = it },
                placeholder = { Text("Paste Gemini API key (stored in Android Keystore)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("quick_api_key_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = ElectricCyan,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                )
            )
            Button(
                onClick = { onSaveKey(inputKey) },
                enabled = inputKey.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth().testTag("quick_api_key_save")
            ) {
                Text("Save Key & Activate AI", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun CameraPermissionPrompt(
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnamolLogoMark(size = 72.dp)
        Spacer(Modifier.height(14.dp))
        Text("Anamol Camera Needs Access", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Spacer(Modifier.height(10.dp))
        Text(
            "Anamol Camera uses camera access for live DSLR controls, GCam 8.5 XML configs, XML filter packs, AR face lenses, and AI framing guidance.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.78f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color.Black),
            modifier = Modifier.testTag("grant_camera_permission_btn")
        ) {
            Text("Enable Camera", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onOpenSettings) {
            Text("Open Settings", color = ElectricCyan)
        }
    }
}

private fun imageProxyToRotatedBitmap(image: ImageProxy, mirrorX: Boolean): Bitmap {
    val bmp = image.toBitmap()
    val rot = image.imageInfo.rotationDegrees.toFloat()
    val matrix = Matrix().apply {
        if (rot != 0f) postRotate(rot)
        if (mirrorX) postScale(-1f, 1f)
    }
    return if (rot != 0f || mirrorX) {
        Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
    } else {
        bmp
    }
}

@Suppress("DEPRECATION")
private fun hasAnyVideoEncoderProfile(context: Context, isFrontCamera: Boolean): Boolean {
    return runCatching {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return false
        val desiredFacing = if (isFrontCamera) {
            CameraCharacteristics.LENS_FACING_FRONT
        } else {
            CameraCharacteristics.LENS_FACING_BACK
        }
        val camId = manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == desiredFacing
        }?.toIntOrNull() ?: return false

        CamcorderProfile.hasProfile(camId, CamcorderProfile.QUALITY_720P) ||
            CamcorderProfile.hasProfile(camId, CamcorderProfile.QUALITY_480P) ||
            CamcorderProfile.hasProfile(camId, CamcorderProfile.QUALITY_HIGH) ||
            CamcorderProfile.hasProfile(camId, CamcorderProfile.QUALITY_LOW)
    }.getOrDefault(false)
}
