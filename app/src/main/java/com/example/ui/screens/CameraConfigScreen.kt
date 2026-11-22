package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.camera.FilterBitmapProcessor
import com.example.data.CameraConfigRepository
import com.example.data.CameraConfigXmlCodec
import com.example.data.MediaRepository
import com.example.model.AwbBias
import com.example.model.ConfigXmlValidationResult
import com.example.model.GCamCameraConfig
import com.example.model.GCamProcessingParams
import com.example.model.LightAdaptationState
import com.example.model.ProManualParams
import com.example.model.ThemeMode
import com.example.ui.components.ThemeQuickToggleChip
import com.example.ui.theme.AmberPro
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.EmeraldMint
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraConfigScreen(
    configRepository: CameraConfigRepository,
    mediaRepository: MediaRepository,
    activeConfigId: String,
    themeMode: ThemeMode,
    onToggleTheme: () -> Unit,
    onApplyConfigToCamera: (GCamCameraConfig) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configs by configRepository.configs.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var selectedTab by remember { mutableIntStateOf(0) }
    val activeConfig = configs.find { it.id == activeConfigId } ?: configs.firstOrNull()

    var editingConfig by remember(activeConfig?.id) {
        mutableStateOf(
            activeConfig?.copy(
                id = if (activeConfig.isBuiltIn) "${activeConfig.id}_tune" else activeConfig.id,
                isBuiltIn = false
            ) ?: GCamCameraConfig(
                id = "custom_gcam85",
                name = "Custom GCam 8.5",
                icon = "📸",
                author = "You"
            )
        )
    }

    var xmlDraft by remember { mutableStateOf(configRepository.sampleXmlTemplate()) }
    var validationErrors by remember { mutableStateOf<List<String>>(emptyList()) }
    var validationWarnings by remember { mutableStateOf<List<String>>(emptyList()) }
    var validationSuccessMsg by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<GCamCameraConfig?>(null) }
    var renameDraft by remember { mutableStateOf("") }

    val schemaMarkdown = remember { configRepository.loadSchemaMarkdown() }

    val importXmlLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val content = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull().orEmpty()
            when (val res = configRepository.importOrSaveConfig(content)) {
                is ConfigXmlValidationResult.Valid -> {
                    validationErrors = emptyList()
                    validationWarnings = res.warnings
                    validationSuccessMsg = "Imported '${res.config.name}' (v${res.config.version})"
                    snackbar.showSnackbar("Imported config: ${res.config.name}")
                }
                is ConfigXmlValidationResult.Invalid -> {
                    xmlDraft = content
                    validationErrors = res.errors
                    validationWarnings = res.warnings
                    validationSuccessMsg = null
                    selectedTab = 2
                }
            }
        }
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Config") },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    label = { Text("Config Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            configRepository.renameConfig(target, renameDraft)
                            renameTarget = null
                            snackbar.showSnackbar("Saved name '$renameDraft'")
                        }
                    },
                    enabled = renameDraft.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Camera Configs (GCam 8.5 XML)") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("config_mgr_back_btn")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    ThemeQuickToggleChip(
                        themeMode = themeMode,
                        onToggle = onToggleTheme,
                        hudOverlay = false
                    )
                    IconButton(
                        onClick = { importXmlLauncher.launch(arrayOf("text/xml", "application/xml", "*/*")) },
                        modifier = Modifier.testTag("import_config_xml_btn")
                    ) {
                        Icon(
                            Icons.Rounded.FileUpload,
                            contentDescription = "Import Config XML",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Configs (${configs.size})") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Preview & Sliders") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("XML & Schema") })
            }

            when (selectedTab) {
                0 -> {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val isWide = maxWidth >= 660.dp
                        if (configs.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No GCam configs loaded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else if (isWide) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(configs, key = { it.id }) { cfg ->
                                    ConfigItemCard(
                                        cfg = cfg,
                                        isActive = cfg.id == activeConfigId,
                                        onApply = { onApplyConfigToCamera(cfg) },
                                        onToggleFav = { scope.launch { configRepository.toggleFavorite(cfg.id) } },
                                        onEdit = {
                                            editingConfig = cfg.copy(
                                                id = if (cfg.isBuiltIn) "${cfg.id}_tune" else cfg.id,
                                                isBuiltIn = false
                                            )
                                            xmlDraft = CameraConfigXmlCodec.toXml(cfg)
                                            selectedTab = 1
                                        },
                                        onRename = {
                                            renameTarget = cfg
                                            renameDraft = cfg.name
                                        },
                                        onDuplicate = {
                                            scope.launch {
                                                configRepository.duplicateConfig(cfg)
                                                snackbar.showSnackbar("Duplicated '${cfg.name}'")
                                            }
                                        },
                                        onExport = {
                                            scope.launch {
                                                val f = configRepository.exportConfigToFile(cfg)
                                                val intent = mediaRepository.createXmlShareIntent(f)
                                                context.startActivity(Intent.createChooser(intent, "Export ${cfg.name}.xml"))
                                            }
                                        },
                                        onDelete = {
                                            scope.launch {
                                                configRepository.deleteCustomConfig(cfg.id)
                                                snackbar.showSnackbar("Deleted '${cfg.name}'")
                                            }
                                        }
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(configs, key = { it.id }) { cfg ->
                                    ConfigItemCard(
                                        cfg = cfg,
                                        isActive = cfg.id == activeConfigId,
                                        onApply = { onApplyConfigToCamera(cfg) },
                                        onToggleFav = { scope.launch { configRepository.toggleFavorite(cfg.id) } },
                                        onEdit = {
                                            editingConfig = cfg.copy(
                                                id = if (cfg.isBuiltIn) "${cfg.id}_tune" else cfg.id,
                                                isBuiltIn = false
                                            )
                                            xmlDraft = CameraConfigXmlCodec.toXml(cfg)
                                            selectedTab = 1
                                        },
                                        onRename = {
                                            renameTarget = cfg
                                            renameDraft = cfg.name
                                        },
                                        onDuplicate = {
                                            scope.launch {
                                                configRepository.duplicateConfig(cfg)
                                                snackbar.showSnackbar("Duplicated '${cfg.name}'")
                                            }
                                        },
                                        onExport = {
                                            scope.launch {
                                                val f = configRepository.exportConfigToFile(cfg)
                                                val intent = mediaRepository.createXmlShareIntent(f)
                                                context.startActivity(Intent.createChooser(intent, "Export ${cfg.name}.xml"))
                                            }
                                        },
                                        onDelete = {
                                            scope.launch {
                                                configRepository.deleteCustomConfig(cfg.id)
                                                snackbar.showSnackbar("Deleted '${cfg.name}'")
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                1 -> {
                    ConfigLiveEditorTab(
                        config = editingConfig,
                        onUpdate = { editingConfig = it },
                        onSaveAndApply = {
                            scope.launch {
                                when (val res = configRepository.saveEditedConfig(editingConfig)) {
                                    is ConfigXmlValidationResult.Valid -> {
                                        xmlDraft = CameraConfigXmlCodec.toXml(res.config)
                                        onApplyConfigToCamera(res.config)
                                    }
                                    is ConfigXmlValidationResult.Invalid -> {
                                        snackbar.showSnackbar(res.errors.firstOrNull() ?: "Save failed")
                                    }
                                }
                            }
                        },
                        onExport = {
                            scope.launch {
                                val f = configRepository.exportConfigToFile(editingConfig)
                                val intent = mediaRepository.createXmlShareIntent(f)
                                context.startActivity(Intent.createChooser(intent, "Export ${editingConfig.name}.xml"))
                            }
                        }
                    )
                }

                2 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Column(
                            modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Paste & Validate GCam 8.5 Config XML", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = xmlDraft,
                                onValueChange = { xmlDraft = it },
                                minLines = 8,
                                maxLines = 16,
                                modifier = Modifier.fillMaxWidth().testTag("config_xml_paste_field"),
                                textStyle = MaterialTheme.typography.labelMedium
                            )

                            Button(
                                onClick = {
                                    scope.launch {
                                        when (val res = configRepository.importOrSaveConfig(xmlDraft)) {
                                            is ConfigXmlValidationResult.Valid -> {
                                                validationErrors = emptyList()
                                                validationWarnings = res.warnings
                                                validationSuccessMsg = "Valid GCam 8.5 XML! Imported '${res.config.name}'."
                                            }
                                            is ConfigXmlValidationResult.Invalid -> {
                                                validationErrors = res.errors
                                                validationWarnings = res.warnings
                                                validationSuccessMsg = null
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldMint, contentColor = Color.Black),
                                modifier = Modifier.fillMaxWidth().testTag("validate_import_config_btn")
                            ) { Text("Validate & Import Config XML", fontWeight = FontWeight.SemiBold) }

                            validationSuccessMsg?.let { msg ->
                                Surface(color = EmeraldMint.copy(alpha = 0.18f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                                    Text(msg, color = EmeraldMint, modifier = Modifier.padding(10.dp))
                                }
                            }

                            if (validationWarnings.isNotEmpty()) {
                                Surface(color = AmberPro.copy(alpha = 0.16f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("Warnings (clamped / ignored tags):", color = AmberPro, style = MaterialTheme.typography.titleMedium)
                                        validationWarnings.forEach { w ->
                                            Text("• $w", color = AmberPro, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }

                            if (validationErrors.isNotEmpty()) {
                                Surface(color = CoralAlert.copy(alpha = 0.16f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("Validation Errors:", color = CoralAlert, style = MaterialTheme.typography.titleMedium)
                                        validationErrors.forEach { err ->
                                            Text("• $err", color = CoralAlert, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(4.dp))
                            Text("Documented GCam 8.5 Config Schema", style = MaterialTheme.typography.titleMedium)
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = schemaMarkdown,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigItemCard(
    cfg: GCamCameraConfig,
    isActive: Boolean,
    onApply: () -> Unit,
    onToggleFav: () -> Unit,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        color = if (isActive) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, if (isActive) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onApply)
            .testTag("config_card_${cfg.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(cfg.icon, fontSize = 26.sp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(cfg.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (isActive) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = "Active Config", tint = EmeraldMint, modifier = Modifier.size(18.dp))
                        }
                    }
                    Text(
                        "${cfg.deviceProfile} • ${if (cfg.isBuiltIn) "Built-in v${cfg.version}" else "Custom v${cfg.version}"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent
                    )
                }
                IconButton(onClick = onToggleFav) {
                    Icon(
                        if (cfg.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = "Favorite Config",
                        tint = if (cfg.isFavorite) AmberPro else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (cfg.description.isNotBlank()) {
                Text(
                    cfg.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                "HDR+ ${(cfg.params.hdrPlusStrength * 100).toInt()}% • γ ${"%.2f".format(cfg.params.toneMappingGamma)} • Sharp ${(cfg.params.sharpness * 100).toInt()}% • AWB ${cfg.params.awbBias.label}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onApply,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isActive) EmeraldMint else accent,
                        contentColor = Color.Black
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(if (isActive) "Active" else "Apply", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Rounded.Tune, contentDescription = "Tune Config", tint = accent)
                    }
                    IconButton(onClick = onRename) {
                        Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = "Rename Config")
                    }
                    IconButton(onClick = onDuplicate) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Duplicate Config")
                    }
                    IconButton(onClick = onExport) {
                        Icon(Icons.Rounded.Share, contentDescription = "Export Config XML", tint = EmeraldMint)
                    }
                    if (!cfg.isBuiltIn) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete Config", tint = CoralAlert)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigLiveEditorTab(
    config: GCamCameraConfig,
    onUpdate: (GCamCameraConfig) -> Unit,
    onSaveAndApply: () -> Unit,
    onExport: () -> Unit
) {
    var compareSplit by remember { mutableFloatStateOf(0.45f) }
    val previewAdj = remember(config.params) {
        FilterBitmapProcessor.mergeAdjustments(
            layers = emptyList(),
            pro = ProManualParams(),
            light = LightAdaptationState(),
            activeConfig = config
        )
    }
    val previewCm = remember(previewAdj) {
        ColorMatrix(FilterBitmapProcessor.buildColorMatrix(previewAdj).array)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Interactive Before / After Live Pipeline Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, drag ->
                            change.consume()
                            compareSplit = (compareSplit + drag / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0.08f, 0.92f)
                        }
                    }
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_onboarding_hero_1795317059336),
                    contentDescription = "Raw Before Config",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Image(
                    painter = painterResource(id = R.drawable.img_onboarding_hero_1795317059336),
                    contentDescription = "After GCam 8.5 Config",
                    contentScale = ContentScale.Crop,
                    colorFilter = ColorFilter.colorMatrix(previewCm),
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            val splitX = size.width * compareSplit
                            clipRect(left = splitX) {
                                this@drawWithContent.drawContent()
                            }
                            drawLine(Color.White, Offset(splitX, 0f), Offset(splitX, size.height), strokeWidth = 4f)
                        }
                )
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
                ) {
                    Text("RAW", style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
                Surface(
                    color = EmeraldMint,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
                ) {
                    Text("GCAM 8.5 (Drag Split)", style = MaterialTheme.typography.labelSmall, color = Color.Black, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = config.name,
                    onValueChange = { onUpdate(config.copy(name = it)) },
                    label = { Text("Config Name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = config.icon,
                    onValueChange = { onUpdate(config.copy(icon = it.take(4))) },
                    label = { Text("Icon") },
                    singleLine = true,
                    modifier = Modifier.width(80.dp)
                )
            }

            val p = config.params
            fun updateP(transform: (GCamProcessingParams) -> GCamProcessingParams) {
                onUpdate(config.copy(params = transform(p)))
            }

            Text("HDR+ & Tone Mapping", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            ConfigSlider("HDR+ Merge", p.hdrPlusStrength, 0f..1.5f) { v -> updateP { it.copy(hdrPlusStrength = v) } }
            ConfigSlider("Tone Gamma", p.toneMappingGamma, 0.6f..1.6f) { v -> updateP { it.copy(toneMappingGamma = v) } }
            ConfigSlider("Exposure EV", p.exposureCompensation, -2f..2f) { v -> updateP { it.copy(exposureCompensation = v) } }
            ConfigSlider("Shadows Lift", p.shadows, -1f..1f) { v -> updateP { it.copy(shadows = v) } }
            ConfigSlider("Highlights", p.highlights, -1f..1f) { v -> updateP { it.copy(highlights = v) } }
            ConfigSlider("Black Level", p.blackLevel, -0.2f..0.2f) { v -> updateP { it.copy(blackLevel = v) } }

            Text("Color, Skin Tone & AWB Gains", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            ConfigSlider("Contrast", p.contrast, 0.5f..1.8f) { v -> updateP { it.copy(contrast = v) } }
            ConfigSlider("Saturation", p.saturation, 0f..2f) { v -> updateP { it.copy(saturation = v) } }
            ConfigSlider("Vibrance", p.vibrance, -0.5f..1f) { v -> updateP { it.copy(vibrance = v) } }
            ConfigSlider("Skin Warmth", p.skinToneWarmth, -0.5f..0.5f) { v -> updateP { it.copy(skinToneWarmth = v) } }
            ConfigSlider("Temp Offset", p.temperatureOffset, -1f..1f) { v -> updateP { it.copy(temperatureOffset = v) } }
            ConfigSlider("Tint Offset", p.tintOffset, -0.5f..0.5f) { v -> updateP { it.copy(tintOffset = v) } }
            ConfigSlider("RGB Gain R", p.rgbGainR, 0.6f..1.4f) { v -> updateP { it.copy(rgbGainR = v) } }
            ConfigSlider("RGB Gain G", p.rgbGainG, 0.6f..1.4f) { v -> updateP { it.copy(rgbGainG = v) } }
            ConfigSlider("RGB Gain B", p.rgbGainB, 0.6f..1.4f) { v -> updateP { it.copy(rgbGainB = v) } }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(AwbBias.entries) { bias ->
                    val sel = p.awbBias == bias
                    Surface(
                        color = if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (sel) MaterialTheme.colorScheme.primary else Color.Transparent),
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { updateP { it.copy(awbBias = bias) } }
                    ) {
                        Text(
                            "AWB ${bias.label}",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Text("Detail, Noise & Optics", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            ConfigSlider("Sharpness", p.sharpness, 0f..1.5f) { v -> updateP { it.copy(sharpness = v) } }
            ConfigSlider("Luma NR", p.lumaNoiseReduction, 0f..1f) { v -> updateP { it.copy(lumaNoiseReduction = v) } }
            ConfigSlider("Chroma NR", p.chromaNoiseReduction, 0f..1f) { v -> updateP { it.copy(chromaNoiseReduction = v) } }
            ConfigSlider("Night Sight", p.nightSightStrength, 0f..1.5f) { v -> updateP { it.copy(nightSightStrength = v) } }
            ConfigSlider("Portrait Blur", p.portraitBlurStrength, 0f..1f) { v -> updateP { it.copy(portraitBlurStrength = v) } }
            ConfigSlider("Vignette", p.vignette, 0f..1f) { v -> updateP { it.copy(vignette = v) } }
            ConfigSlider("Film Grain", p.grain, 0f..1f) { v -> updateP { it.copy(grain = v) } }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onSaveAndApply,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldMint, contentColor = Color.Black),
                    modifier = Modifier.weight(1f).testTag("save_apply_config_btn")
                ) { Text("Save & Apply Config", fontWeight = FontWeight.SemiBold) }

                OutlinedButton(
                    onClick = onExport,
                    modifier = Modifier.weight(1f)
                ) { Text("Export XML") }
            }
        }
    }
}

@Composable
private fun ConfigSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(0.30f))
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(0.54f))
        Text("%.2f".format(value), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(0.16f))
    }
}
