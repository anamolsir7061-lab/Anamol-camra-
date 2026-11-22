package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.camera.FilterBitmapProcessor
import com.example.data.FilterRepository
import com.example.data.FilterXmlCodec
import com.example.data.MediaRepository
import com.example.model.ActiveFilterLayer
import com.example.model.ArOverlayType
import com.example.model.CameraFilter
import com.example.model.FilterBlendMode
import com.example.model.FilterCategory
import com.example.model.XmlValidationResult
import com.example.ui.components.FilterEffectAndArOverlay
import com.example.ui.theme.AmberPro
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMint
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterManagerScreen(
    filterRepository: FilterRepository,
    mediaRepository: MediaRepository,
    onApplyFilterToCamera: (CameraFilter) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val filters by filterRepository.filters.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var selectedTab by remember { mutableIntStateOf(0) }
    var editingFilter by remember {
        mutableStateOf(
            CameraFilter(
                id = "custom_look_${System.currentTimeMillis() % 1000}",
                name = "My Custom XML",
                icon = "💎",
                category = FilterCategory.COLOR_GRADE,
                author = "You"
            )
        )
    }
    var xmlDraft by remember { mutableStateOf(filterRepository.sampleXmlTemplate()) }
    var validationErrors by remember { mutableStateOf<List<String>>(emptyList()) }
    var validationSuccessMsg by remember { mutableStateOf<String?>(null) }

    val importXmlLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val content = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull().orEmpty()
            when (val res = filterRepository.importOrSaveFilter(content)) {
                is XmlValidationResult.Valid -> {
                    validationErrors = emptyList()
                    validationSuccessMsg = "Imported '${res.filter.name}'!"
                    snackbar.showSnackbar("Imported XML filter: ${res.filter.name}")
                }
                is XmlValidationResult.Invalid -> {
                    xmlDraft = content
                    validationErrors = res.errors
                    validationSuccessMsg = null
                    selectedTab = 2
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("XML Filter Studio") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("filter_mgr_back_btn")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { importXmlLauncher.launch(arrayOf("text/xml", "application/xml", "*/*")) },
                        modifier = Modifier.testTag("import_xml_btn")
                    ) {
                        Icon(Icons.Rounded.FileUpload, contentDescription = "Import XML file", tint = MaterialTheme.colorScheme.primary)
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
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Pack (${filters.size})") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Live Editor") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("XML & Schema") })
            }

            when (selectedTab) {
                0 -> {
                    LazyColumn(
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filters, key = { it.id }) { filter ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .clickable { onApplyFilterToCamera(filter) }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(filter.icon, fontSize = 26.sp)
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(filter.name, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "${filter.category.label} • ${if (filter.isBuiltIn) "Built-in XML" else "Custom XML"}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (filter.description.isNotBlank()) {
                                            Text(filter.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                        }
                                    }
                                    IconButton(onClick = { scope.launch { filterRepository.toggleFavorite(filter.id) } }) {
                                        Icon(
                                            if (filter.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                            contentDescription = "Favorite",
                                            tint = if (filter.isFavorite) AmberPro else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { scope.launch { filterRepository.moveFilter(filter.id, -1) } }) {
                                        Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move up")
                                    }
                                    IconButton(onClick = { scope.launch { filterRepository.moveFilter(filter.id, 1) } }) {
                                        Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move down")
                                    }
                                    IconButton(onClick = {
                                        editingFilter = filter.copy(
                                            id = if (filter.isBuiltIn) "${filter.id}_remix" else filter.id,
                                            isBuiltIn = false
                                        )
                                        selectedTab = 1
                                    }) {
                                        Icon(Icons.Rounded.Edit, contentDescription = "Edit in Live Editor", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = {
                                        scope.launch {
                                            val file = filterRepository.exportFilterToFile(filter)
                                            val intent = mediaRepository.createXmlShareIntent(file)
                                            context.startActivity(Intent.createChooser(intent, "Export ${filter.name}.xml"))
                                        }
                                    }) {
                                        Icon(Icons.Rounded.Share, contentDescription = "Export XML", tint = EmeraldMint)
                                    }
                                    if (!filter.isBuiltIn) {
                                        IconButton(onClick = {
                                            scope.launch {
                                                filterRepository.deleteCustomFilter(filter.id)
                                                snackbar.showSnackbar("Deleted ${filter.name}")
                                            }
                                        }) {
                                            Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = CoralAlert)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Live preview card
                        val previewCm = remember(editingFilter.adjustments) {
                            ColorMatrix(FilterBitmapProcessor.buildColorMatrix(editingFilter.adjustments).array)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(170.dp)
                                .clip(RoundedCornerShape(16.dp))
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_onboarding_hero_1795317059336),
                                contentDescription = "Live filter preview",
                                contentScale = ContentScale.Crop,
                                colorFilter = ColorFilter.colorMatrix(previewCm),
                                modifier = Modifier.fillMaxSize()
                            )
                            FilterEffectAndArOverlay(
                                adjustments = editingFilter.adjustments,
                                activeLayers = listOf(ActiveFilterLayer(editingFilter, 1f)),
                                faces = emptyList(),
                                beforeAfterCompare = false,
                                compareSplit = 0.5f
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = editingFilter.name,
                                onValueChange = { editingFilter = editingFilter.copy(name = it) },
                                label = { Text("Name") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = editingFilter.icon,
                                onValueChange = { editingFilter = editingFilter.copy(icon = it.take(4)) },
                                label = { Text("Icon") },
                                singleLine = true,
                                modifier = Modifier.width(86.dp)
                            )
                        }

                        val adj = editingFilter.adjustments
                        EditorSlider("Brightness", adj.brightness, -0.8f..0.8f) {
                            editingFilter = editingFilter.copy(adjustments = adj.copy(brightness = it))
                        }
                        EditorSlider("Contrast", adj.contrast, 0.3f..2.2f) {
                            editingFilter = editingFilter.copy(adjustments = adj.copy(contrast = it))
                        }
                        EditorSlider("Saturation", adj.saturation, 0f..2.4f) {
                            editingFilter = editingFilter.copy(adjustments = adj.copy(saturation = it))
                        }
                        EditorSlider("Temperature", adj.temperature, -1f..1f) {
                            editingFilter = editingFilter.copy(adjustments = adj.copy(temperature = it))
                        }
                        EditorSlider("Greenery", adj.greenery, -1f..1f) {
                            editingFilter = editingFilter.copy(adjustments = adj.copy(greenery = it))
                        }
                        EditorSlider("Vignette", adj.vignette, 0f..1f) {
                            editingFilter = editingFilter.copy(adjustments = adj.copy(vignette = it))
                        }
                        EditorSlider("Grain", adj.grain, 0f..1f) {
                            editingFilter = editingFilter.copy(adjustments = adj.copy(grain = it))
                        }
                        EditorSlider("Glitch", adj.glitch, 0f..1f) {
                            editingFilter = editingFilter.copy(adjustments = adj.copy(glitch = it))
                        }
                        EditorSlider("Skin Smooth", adj.skinSmooth, 0f..1f) {
                            editingFilter = editingFilter.copy(adjustments = adj.copy(skinSmooth = it))
                        }

                        Text("AR Face Lens Overlay", style = MaterialTheme.typography.titleMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(ArOverlayType.entries) { arType ->
                                val selected = editingFilter.arOverlay.type == arType
                                Surface(
                                    color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(12.dp))
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            editingFilter = editingFilter.copy(
                                                arOverlay = editingFilter.arOverlay.copy(type = arType)
                                            )
                                        }
                                ) {
                                    Text(arType.label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        val safeId = editingFilter.id.lowercase().replace(Regex("[^a-z0-9_-]"), "_").ifBlank { "custom_filter" }
                                        val saved = editingFilter.copy(id = safeId, isBuiltIn = false)
                                        filterRepository.saveEditedFilter(saved)
                                        xmlDraft = FilterXmlCodec.toXml(saved)
                                        snackbar.showSnackbar("Saved '${saved.name}' to filter pack!")
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.weight(1f).testTag("save_edited_xml_btn")
                            ) { Text("Save to Pack") }

                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        val file = filterRepository.exportFilterToFile(editingFilter)
                                        val intent = mediaRepository.createXmlShareIntent(file)
                                        context.startActivity(Intent.createChooser(intent, "Export XML"))
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Export XML") }
                        }
                    }
                }

                2 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Paste & Validate Filter XML", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = xmlDraft,
                            onValueChange = { xmlDraft = it },
                            minLines = 8,
                            maxLines = 14,
                            modifier = Modifier.fillMaxWidth().testTag("xml_paste_field"),
                            textStyle = MaterialTheme.typography.labelMedium
                        )

                        Button(
                            onClick = {
                                scope.launch {
                                    when (val res = filterRepository.importOrSaveFilter(xmlDraft)) {
                                        is XmlValidationResult.Valid -> {
                                            validationErrors = emptyList()
                                            validationSuccessMsg = "Valid XML! Imported '${res.filter.name}'."
                                        }
                                        is XmlValidationResult.Invalid -> {
                                            validationErrors = res.errors
                                            validationSuccessMsg = null
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldMint, contentColor = Color.Black),
                            modifier = Modifier.fillMaxWidth().testTag("validate_import_xml_btn")
                        ) { Text("Validate & Import XML") }

                        validationSuccessMsg?.let { msg ->
                            Surface(color = EmeraldMint.copy(alpha = 0.2f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                                Text(msg, color = EmeraldMint, modifier = Modifier.padding(10.dp))
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

                        Spacer(Modifier.height(6.dp))
                        Text("Documented XML Schema (v1.0)", style = MaterialTheme.typography.titleMedium)
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = filterRepository.schemaDocumentation,
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

@Composable
private fun EditorSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(0.28f))
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(0.56f))
        Text("%.2f".format(value), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(0.16f))
    }
}
