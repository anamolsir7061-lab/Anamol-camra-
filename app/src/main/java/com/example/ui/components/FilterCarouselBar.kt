package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ActiveFilterLayer
import com.example.model.CameraFilter
import com.example.model.FilterBlendMode
import com.example.ui.theme.AmberPro
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMint
import kotlin.math.roundToInt

@Composable
fun FilterCarouselBar(
    filters: List<CameraFilter>,
    activeLayers: List<ActiveFilterLayer>,
    onSelectSingle: (CameraFilter?) -> Unit,
    onToggleStack: (CameraFilter) -> Unit,
    onIntensityChange: (String, Float) -> Unit,
    onBlendModeChange: (String, FilterBlendMode) -> Unit,
    onOpenFilterManager: () -> Unit,
    iconRotationDegrees: Float = 0f,
    modifier: Modifier = Modifier
) {
    var stackMode by remember { mutableStateOf(false) }
    var showIntensityDrawer by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        if (showIntensityDrawer && activeLayers.isNotEmpty()) {
            Surface(
                color = Color(0xFF0B0F17).copy(alpha = 0.88f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    activeLayers.forEach { layer ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${layer.filter.icon} ${layer.filter.name}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(0.32f)
                            )
                            Slider(
                                value = layer.intensity,
                                onValueChange = { onIntensityChange(layer.filter.id, it) },
                                valueRange = 0f..1f,
                                modifier = Modifier.weight(0.42f),
                                colors = SliderDefaults.colors(thumbColor = ElectricCyan, activeTrackColor = EmeraldMint)
                            )
                            val curBlend = layer.blendModeOverride ?: layer.filter.adjustments.blendMode
                            Surface(
                                color = Color.White.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        val all = FilterBlendMode.entries
                                        val next = all[(all.indexOf(curBlend) + 1) % all.size]
                                        onBlendModeChange(layer.filter.id, next)
                                    }
                            ) {
                                Text(
                                    text = "${(layer.intensity * 100).roundToInt()}% • ${curBlend.label}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricCyan,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                FilterChipOrb(
                    emoji = "✕",
                    label = "Off",
                    selected = activeLayers.isEmpty(),
                    isFavorite = false,
                    iconRotationDegrees = iconRotationDegrees,
                    onClick = { onSelectSingle(null) },
                    testTag = "filter_none"
                )
            }

            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(if (stackMode) EmeraldMint.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.55f))
                            .border(1.5.dp, if (stackMode) EmeraldMint else Color.White.copy(alpha = 0.25f), CircleShape)
                            .clickable { stackMode = !stackMode }
                            .testTag("filter_stack_toggle"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Layers,
                            contentDescription = "Stack multiple filters",
                            tint = if (stackMode) EmeraldMint else Color.White,
                            modifier = Modifier.rotate(iconRotationDegrees)
                        )
                    }
                    Text(
                        text = if (stackMode) "Stack ON" else "Stack",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (stackMode) EmeraldMint else Color.White.copy(alpha = 0.8f)
                    )
                }
            }

            if (activeLayers.isNotEmpty()) {
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(if (showIntensityDrawer) ElectricCyan.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.55f))
                                .border(1.5.dp, ElectricCyan, CircleShape)
                                .clickable { showIntensityDrawer = !showIntensityDrawer }
                                .testTag("filter_intensity_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Tune,
                                contentDescription = "Filter intensity and blend mode",
                                tint = ElectricCyan,
                                modifier = Modifier.rotate(iconRotationDegrees)
                            )
                        }
                        Text("Mix", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                    }
                }
            }

            items(filters, key = { it.id }) { filter ->
                val isSelected = activeLayers.any { it.filter.id == filter.id }
                FilterChipOrb(
                    emoji = filter.icon,
                    label = filter.name,
                    selected = isSelected,
                    isFavorite = filter.isFavorite,
                    iconRotationDegrees = iconRotationDegrees,
                    onClick = {
                        if (stackMode) onToggleStack(filter) else onSelectSingle(filter)
                    },
                    testTag = "filter_item_${filter.id}"
                )
            }

            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                            .clickable { onOpenFilterManager() }
                            .testTag("open_xml_manager_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "XML",
                            style = MaterialTheme.typography.labelMedium,
                            color = ElectricCyan,
                            modifier = Modifier.rotate(iconRotationDegrees)
                        )
                    }
                    Text("Manage", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
                }
            }
        }
    }
}

@Composable
private fun FilterChipOrb(
    emoji: String,
    label: String,
    selected: Boolean,
    isFavorite: Boolean,
    iconRotationDegrees: Float,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(58.dp)
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(if (selected) ElectricCyan.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.58f))
                    .border(
                        width = if (selected) 2.2.dp else 1.dp,
                        color = if (selected) ElectricCyan else Color.White.copy(alpha = 0.22f),
                        shape = CircleShape
                    )
                    .clickable(onClick = onClick)
                    .testTag(testTag),
                contentAlignment = Alignment.Center
            ) {
                Text(text = emoji, fontSize = 20.sp, modifier = Modifier.rotate(iconRotationDegrees))
            }
            if (isFavorite) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = "Favorite",
                    tint = AmberPro,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) ElectricCyan else Color.White.copy(alpha = 0.86f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
