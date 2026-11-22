package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Camera
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.AnamolLogoMark
import com.example.ui.theme.EmeraldMint
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val title: String,
    val body: String,
    val icon: ImageVector
)

@Composable
fun OnboardingScreen(
    onSaveApiKey: (String) -> Unit,
    onFinish: () -> Unit
) {
    val pages = remember {
        listOf(
            OnboardingPage(
                "Anamol Camera • Portrait & Landscape",
                "Swipe real-time lenses, stack filter layers, enable AR face stickers, and shoot smoothly upright or sideways with zero stream restarts.",
                Icons.Rounded.Camera
            ),
            OnboardingPage(
                "GCam 8.5 XML Config Engine",
                "Import, preview, and tune computational HDR+ XML configs (tone gamma, Night Sight, AWB gains, noise reduction) with 9 built-in studio profiles.",
                Icons.Rounded.Tune
            ),
            OnboardingPage(
                "Silent AI Camera Brain",
                "Powered by Gemini Vision & Function Calling. Get subtle framing arrows, or flip AUTO AI ON so the brain picks configs, colors, and shoots smiles.",
                Icons.Rounded.AutoAwesome
            ),
            OnboardingPage(
                "XML Filter-Pack System",
                "Import, validate, remix, and export portable .xml filter packs with 12 presets, live sliders, and documented schema.",
                Icons.Rounded.Code
            ),
            OnboardingPage(
                "Connect Your Gemini API Key",
                "Paste your Gemini API key below (encrypted inside Android Keystore, never logged). You can also add or update it anytime in Settings.",
                Icons.Rounded.Key
            )
        )
    }

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    var apiKeyDraft by remember { mutableStateOf("") }
    val accent = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AnamolLogoMark(size = 34.dp)
                    Text("Anamol Camera", style = MaterialTheme.typography.titleLarge, color = accent, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onFinish, modifier = Modifier.testTag("onboarding_skip_btn")) {
                    Text("Skip", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(24.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_onboarding_hero_1795317059336),
                    contentDescription = "Anamol Camera studio lens",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.72f))
                            )
                        )
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(270.dp)
            ) { index ->
                val p = pages[index]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        color = accent.copy(alpha = 0.16f),
                        shape = CircleShape,
                        modifier = Modifier.size(62.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(p.icon, contentDescription = null, tint = accent, modifier = Modifier.size(30.dp))
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        p.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        p.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    if (index == pages.lastIndex) {
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            value = apiKeyDraft,
                            onValueChange = { apiKeyDraft = it },
                            placeholder = { Text("Paste Gemini API key (optional now)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("onboarding_api_key_input")
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(pages.size) { idx ->
                        val active = idx == pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .size(width = if (active) 24.dp else 8.dp, height = 8.dp)
                                .clip(CircleShape)
                                .background(if (active) accent else MaterialTheme.colorScheme.outline)
                        )
                    }
                }

                if (pagerState.currentPage < pages.lastIndex) {
                    Button(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = MaterialTheme.colorScheme.onPrimary),
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("onboarding_next_btn")
                    ) {
                        Text("Next", fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = onFinish,
                            modifier = Modifier.weight(1f).height(52.dp)
                        ) { Text("Use Offline") }
                        Button(
                            onClick = {
                                if (apiKeyDraft.isNotBlank()) onSaveApiKey(apiKeyDraft)
                                onFinish()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldMint, contentColor = Color.Black),
                            modifier = Modifier.weight(1f).height(52.dp).testTag("onboarding_start_btn")
                        ) { Text("Launch Camera", fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
    }
}
