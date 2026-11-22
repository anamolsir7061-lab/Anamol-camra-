package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.AiLanguage
import com.example.model.AiPersonality
import com.example.model.AutoAggressiveness
import com.example.model.CaptureQuality
import com.example.model.GuidanceStrictness
import com.example.model.OrientationLockMode
import com.example.model.PhotoStyle
import com.example.model.ThemeMode
import com.example.ui.components.AnamolLogoMark
import com.example.ui.components.ThemeQuickToggleChip
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.EmeraldMint
import com.example.viewmodel.SettingsViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenConfigs: () -> Unit,
    onReplayOnboarding: () -> Unit,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val hasApiKey by viewModel.hasApiKey.collectAsStateWithLifecycle()
    val maskedKey by viewModel.maskedKey.collectAsStateWithLifecycle()
    val chatHistory by viewModel.chatHistory.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var apiKeyInput by remember { mutableStateOf("") }
    var showKeyPlaintext by remember { mutableStateOf(false) }
    var chatInput by remember { mutableStateOf("") }

    val accent = MaterialTheme.colorScheme.primary
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.clearToast()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Anamol Camera Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_btn")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    ThemeQuickToggleChip(
                        themeMode = settings.themeMode,
                        onToggle = {
                            val all = ThemeMode.entries
                            val next = all[(all.indexOf(settings.themeMode) + 1) % all.size]
                            viewModel.updateSettings { it.copy(themeMode = next) }
                        },
                        hudOverlay = false,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 820.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 0. Display, Orientation & Watermark
                SectionCard(title = "Display, Orientation & Watermark") {
                    Text("App Theme", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ThemeMode.entries.forEach { t ->
                            ChoiceChip(t.label, settings.themeMode == t, testTag = "theme_choice_${t.name}") {
                                viewModel.updateSettings { it.copy(themeMode = t) }
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text("Screen Orientation", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(OrientationLockMode.entries) { mode ->
                            ChoiceChip(
                                mode.label,
                                settings.orientationLockMode == mode,
                                testTag = "orientation_choice_${mode.name}"
                            ) {
                                viewModel.updateSettings { it.copy(orientationLockMode = mode) }
                            }
                        }
                    }

                    SettingSwitchRow(
                        "Add \"Anamol Camera\" Watermark on Share",
                        settings.shareWatermarkEnabled
                    ) { en ->
                        viewModel.updateSettings { it.copy(shareWatermarkEnabled = en) }
                    }

                    OutlinedButton(
                        onClick = onOpenConfigs,
                        modifier = Modifier.fillMaxWidth().testTag("settings_open_configs_btn")
                    ) {
                        Icon(Icons.Rounded.Tune, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open GCam 8.5 Camera Configs")
                    }
                }

                // 1. Secure Gemini API Key Vault
                SectionCard(title = "Gemini API Key (Android Keystore)") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Key, contentDescription = null, tint = if (hasApiKey) EmeraldMint else accent)
                        Text(
                            text = if (hasApiKey) "Stored key: $maskedKey" else "No API key stored yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (hasApiKey) EmeraldMint else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        placeholder = { Text("Paste new Gemini API key") },
                        visualTransformation = if (showKeyPlaintext) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showKeyPlaintext = !showKeyPlaintext }) {
                                Icon(
                                    if (showKeyPlaintext) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = "Toggle key visibility"
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("settings_api_key_input")
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                viewModel.saveAndVerifyApiKey(apiKeyInput)
                                apiKeyInput = ""
                            },
                            enabled = apiKeyInput.isNotBlank() && !uiState.isValidatingKey,
                            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = MaterialTheme.colorScheme.onPrimary),
                            modifier = Modifier.weight(1f).testTag("settings_save_key_btn")
                        ) {
                            if (uiState.isValidatingKey) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            else Text("Save & Verify Key")
                        }
                        if (hasApiKey) {
                            OutlinedButton(onClick = { viewModel.clearApiKey() }) {
                                Text("Remove", color = CoralAlert)
                            }
                        }
                    }

                    uiState.keyValidationMessage?.let { msg ->
                        Text(msg, style = MaterialTheme.typography.labelMedium, color = accent)
                    }

                    Text("Flash Model:", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf("gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.5-pro")) { m ->
                            ChoiceChip(m, settings.geminiModel == m) {
                                viewModel.updateSettings { it.copy(geminiModel = m) }
                            }
                        }
                    }
                }

                // 2. AI Brain Customization Chat
                SectionCard(title = "Customize AI via Chat") {
                    Text(
                        "Tell Anamol Camera AI how you shoot (e.g. \"make greenery stronger\", \"reduce darkness\", \"use a vintage look for portraits\", \"speak in Hinglish\"). Changes persist automatically.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(
                            listOf(
                                "Make greenery stronger",
                                "Reduce darkness",
                                "Use a vintage look for portraits",
                                "Speak to me in Hinglish"
                            )
                        ) { preset ->
                            Surface(
                                color = accent.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { viewModel.sendCustomizationChat(preset) }
                            ) {
                                Text(preset, style = MaterialTheme.typography.labelSmall, color = accent, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                            }
                        }
                    }

                    if (chatHistory.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            chatHistory.takeLast(6).forEach { msg ->
                                Column(
                                    horizontalAlignment = if (msg.isUser) Alignment.End else Alignment.Start,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Surface(
                                        color = if (msg.isUser) accent.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                                            Text(msg.text, style = MaterialTheme.typography.bodyMedium)
                                            if (msg.appliedUpdates.isNotEmpty()) {
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    "Applied: ${msg.appliedUpdates.joinToString(" • ")}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = EmeraldMint
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = chatInput,
                            onValueChange = { chatInput = it },
                            placeholder = { Text("Type an AI preference command...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("ai_chat_input")
                        )
                        IconButton(
                            onClick = {
                                val prompt = chatInput
                                chatInput = ""
                                viewModel.sendCustomizationChat(prompt)
                            },
                            enabled = chatInput.isNotBlank() && !uiState.isChatSending,
                            modifier = Modifier.testTag("ai_chat_send_btn")
                        ) {
                            if (uiState.isChatSending) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send", tint = accent)
                        }
                    }
                    if (chatHistory.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clearChatHistory() }) {
                            Text("Clear Chat History", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // 3. AI Brain & Personality Settings
                SectionCard(title = "AI Camera Brain Behavior") {
                    SettingSwitchRow("AI Camera Brain Enabled", settings.aiBrainEnabled) { en ->
                        viewModel.updateSettings { it.copy(aiBrainEnabled = en) }
                    }
                    SettingSwitchRow("Auto Mode (AI executes tools)", settings.autoModeEnabled) { en ->
                        viewModel.updateSettings { it.copy(autoModeEnabled = en) }
                    }
                    SettingSwitchRow("Voice Hints (OFF = Silent by default)", settings.voiceOutputEnabled) { en ->
                        viewModel.updateSettings { it.copy(voiceOutputEnabled = en) }
                    }

                    Text("AI Frame Interval: ${"%.1f".format(settings.aiIntervalSeconds)}s", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = settings.aiIntervalSeconds,
                        onValueChange = { v -> viewModel.updateSettings { it.copy(aiIntervalSeconds = v) } },
                        valueRange = 1.5f..6.0f
                    )

                    Text("Personality", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(AiPersonality.entries) { p ->
                            ChoiceChip(p.label, settings.aiPersonality == p) {
                                viewModel.updateSettings { it.copy(aiPersonality = p) }
                            }
                        }
                    }

                    Text("Guidance Strictness", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GuidanceStrictness.entries.forEach { s ->
                            ChoiceChip(s.label, settings.guidanceStrictness == s) {
                                viewModel.updateSettings { it.copy(guidanceStrictness = s) }
                            }
                        }
                    }

                    Text("Auto Mode Aggressiveness", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AutoAggressiveness.entries.forEach { a ->
                            ChoiceChip(a.label, settings.autoAggressiveness == a) {
                                viewModel.updateSettings { it.copy(autoAggressiveness = a) }
                            }
                        }
                    }

                    Text("Preferred Photo Style", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(PhotoStyle.entries) { st ->
                            ChoiceChip(st.label, settings.preferredPhotoStyle == st) {
                                viewModel.updateSettings { it.copy(preferredPhotoStyle = st) }
                            }
                        }
                    }

                    Text("Language", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AiLanguage.entries.forEach { lang ->
                            ChoiceChip(lang.label, settings.aiLanguage == lang) {
                                viewModel.updateSettings { it.copy(aiLanguage = lang) }
                            }
                        }
                    }

                    Text("Skin Smoothing Default: ${(settings.skinSmoothingLevel * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = settings.skinSmoothingLevel,
                        onValueChange = { v -> viewModel.updateSettings { it.copy(skinSmoothingLevel = v) } },
                        valueRange = 0f..1f
                    )
                }

                // 4. Local Automatic Light Adaptation
                SectionCard(title = "Automatic Light Adaptation (On-Device)") {
                    SettingSwitchRow("Auto Exposure & Shadow/Highlight Guard", settings.lightAdaptationEnabled) { en ->
                        viewModel.updateSettings { it.copy(lightAdaptationEnabled = en) }
                    }
                    Text("Sensitivity: ${"%.2f".format(settings.lightAdaptationSensitivity)}x", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = settings.lightAdaptationSensitivity,
                        onValueChange = { v -> viewModel.updateSettings { it.copy(lightAdaptationSensitivity = v) } },
                        valueRange = 0.2f..2.0f
                    )
                }

                // 5. Capture Quality, Privacy, Storage
                SectionCard(title = "Camera, Privacy & Storage") {
                    Text("Capture Quality", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(CaptureQuality.entries) { q ->
                            ChoiceChip(q.label, settings.captureQuality == q) {
                                viewModel.updateSettings { it.copy(captureQuality = q) }
                            }
                        }
                    }

                    SettingSwitchRow("Send Throttled Preview Frames to Gemini", settings.sendPreviewFramesToAi) { en ->
                        viewModel.updateSettings { it.copy(sendPreviewFramesToAi = en) }
                    }
                    SettingSwitchRow("Haptic Shutter Feedback", settings.hapticsEnabled) { en ->
                        viewModel.updateSettings { it.copy(hapticsEnabled = en) }
                    }

                    val mb = uiState.storageBytes / (1024f * 1024f)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Local Media Storage: ${"%.2f".format(mb)} MB", style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { viewModel.clearMediaStorage() }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = CoralAlert, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Clear Media", color = CoralAlert)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = onReplayOnboarding, modifier = Modifier.weight(1f)) {
                            Text("Tutorial")
                        }
                        OutlinedButton(onClick = { viewModel.resetAllDefaults() }, modifier = Modifier.weight(1f)) {
                            Text("Reset Defaults", color = CoralAlert)
                        }
                    }
                }

                // 6. About Anamol Camera
                SectionCard(title = "About Anamol Camera") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AnamolLogoMark(size = 48.dp)
                        Column {
                            Text("Anamol Camera v2.0", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "DSLR Pro + GCam 8.5 XML Configs + XML Filter Studio + Silent Gemini AI Camera Brain",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun SettingSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    testTag: String? = null,
    onClick: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        color = if (selected) accent.copy(alpha = 0.22f) else MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .border(1.dp, if (selected) accent else MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .let { if (testTag != null) it.testTag(testTag) else it }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
        )
    }
}
