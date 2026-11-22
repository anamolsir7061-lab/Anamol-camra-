package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.ProManualParams
import com.example.ui.theme.AmberPro
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMint
import kotlin.math.roundToInt

private enum class ProControlAxis(val label: String) {
    EV("EV"),
    ISO("ISO"),
    WB("WB"),
    CONTRAST("Contrast"),
    SAT("Sat"),
    GREEN("Greenery"),
    SHADOWS("Shadows"),
    HIGHLIGHTS("Highlights"),
    SHARP("Sharp"),
    VIGNETTE("Vignette"),
    GRAIN("Grain")
}

@Composable
fun ProControlsDrawer(
    params: ProManualParams,
    onUpdate: ((ProManualParams) -> ProManualParams) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedAxis by remember { mutableStateOf(ProControlAxis.EV) }

    Surface(
        color = Color(0xFF0B0F17).copy(alpha = 0.88f),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(20.dp))
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "PRO DSLR • ISO ${params.isoGain} • ${params.temperatureKelvin}K • EV ${"%.1f".format(params.exposureEv)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = AmberPro
                )
                TextButton(onClick = onReset, modifier = Modifier.testTag("pro_reset_btn")) {
                    Text("Reset", style = MaterialTheme.typography.labelMedium, color = ElectricCyan)
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(ProControlAxis.entries) { axis ->
                    val selected = axis == selectedAxis
                    Surface(
                        color = if (selected) ElectricCyan.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .border(1.dp, if (selected) ElectricCyan else Color.Transparent, RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedAxis = axis }
                            .testTag("pro_axis_${axis.name}")
                    ) {
                        Text(
                            text = axis.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) ElectricCyan else Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            val (value, range, display) = when (selectedAxis) {
                ProControlAxis.EV -> Triple(params.exposureEv, -2f..2f, "${"%.1f".format(params.exposureEv)} EV")
                ProControlAxis.ISO -> Triple(params.isoGain.toFloat(), 100f..3200f, "ISO ${params.isoGain}")
                ProControlAxis.WB -> Triple(params.temperatureKelvin.toFloat(), 2500f..8500f, "${params.temperatureKelvin} K")
                ProControlAxis.CONTRAST -> Triple(params.contrast, 0.5f..1.8f, "${(params.contrast * 100).roundToInt()}%")
                ProControlAxis.SAT -> Triple(params.saturation, 0f..2f, "${(params.saturation * 100).roundToInt()}%")
                ProControlAxis.GREEN -> Triple(params.greenery, -1f..1f, "${(params.greenery * 100).roundToInt()}%")
                ProControlAxis.SHADOWS -> Triple(params.shadows, -1f..1f, "${(params.shadows * 100).roundToInt()}%")
                ProControlAxis.HIGHLIGHTS -> Triple(params.highlights, -1f..1f, "${(params.highlights * 100).roundToInt()}%")
                ProControlAxis.SHARP -> Triple(params.sharpness, 0f..1f, "${(params.sharpness * 100).roundToInt()}%")
                ProControlAxis.VIGNETTE -> Triple(params.vignette, 0f..1f, "${(params.vignette * 100).roundToInt()}%")
                ProControlAxis.GRAIN -> Triple(params.grain, 0f..1f, "${(params.grain * 100).roundToInt()}%")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(selectedAxis.label, style = MaterialTheme.typography.labelMedium, color = Color.White, modifier = Modifier.weight(0.24f))
                Slider(
                    value = value,
                    onValueChange = { v ->
                        onUpdate { p ->
                            when (selectedAxis) {
                                ProControlAxis.EV -> p.copy(exposureEv = v)
                                ProControlAxis.ISO -> p.copy(isoGain = ((v / 50f).roundToInt() * 50).coerceIn(100, 3200))
                                ProControlAxis.WB -> p.copy(temperatureKelvin = ((v / 100f).roundToInt() * 100).coerceIn(2500, 8500))
                                ProControlAxis.CONTRAST -> p.copy(contrast = v)
                                ProControlAxis.SAT -> p.copy(saturation = v)
                                ProControlAxis.GREEN -> p.copy(greenery = v)
                                ProControlAxis.SHADOWS -> p.copy(shadows = v)
                                ProControlAxis.HIGHLIGHTS -> p.copy(highlights = v)
                                ProControlAxis.SHARP -> p.copy(sharpness = v)
                                ProControlAxis.VIGNETTE -> p.copy(vignette = v)
                                ProControlAxis.GRAIN -> p.copy(grain = v)
                            }
                        }
                    },
                    valueRange = range,
                    modifier = Modifier.weight(0.56f),
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricCyan,
                        activeTrackColor = EmeraldMint,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                    )
                )
                Text(display, style = MaterialTheme.typography.labelMedium, color = ElectricCyan, modifier = Modifier.weight(0.20f))
            }
        }
    }
}
