package com.rfsat.shimmerenact.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rfsat.shimmerenact.ui.theme.*
import com.rfsat.shimmerenact.viewmodel.ShimmerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SamplingRateScreen(
    viewModel: ShimmerViewModel,
    onBack: () -> Unit
) {
    val activeType by viewModel.activeSensorType.collectAsState()
    val config     by viewModel.activeConfig.collectAsState()

    // Slider state — local until saved
    var rateSlider by remember(config.samplingRateHz) {
        mutableStateOf(config.samplingRateHz.toFloat())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sampling Rate", color = EnactOnSurface) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = EnactGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EnactDark)
            )
        },
        containerColor = EnactDark
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hardware rate card
            Card(colors = CardDefaults.cardColors(containerColor = EnactDarkMid)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Hardware Sampling Rate", color = EnactOnSurface, fontWeight = FontWeight.Bold)
                    Text(
                        "Current: ${rateSlider.toInt()} Hz",
                        color = EnactGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold
                    )
                    Slider(
                        value = rateSlider,
                        onValueChange = { rateSlider = it },
                        valueRange = 1f..512f,
                        steps = 0,
                        colors = SliderDefaults.colors(thumbColor = EnactGreen, activeTrackColor = EnactGreen),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("1 Hz", color = EnactOnSurface.copy(alpha = 0.5f), fontSize = 11.sp)
                        Text("512 Hz", color = EnactOnSurface.copy(alpha = 0.5f), fontSize = 11.sp)
                    }
                    Button(
                        onClick = { viewModel.updateSamplingRate(activeType, rateSlider.toInt()) },
                        colors = ButtonDefaults.buttonColors(containerColor = EnactGreen),
                        modifier = Modifier.align(Alignment.End)
                    ) { Text("Apply", color = EnactDark) }
                }
            }

            // Info card
            Card(colors = CardDefaults.cardColors(containerColor = EnactDarkMid)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Notes", color = EnactOnSurface, fontWeight = FontWeight.Bold)
                    Text(
                        "The hardware rate is set via the Shimmer3 rate register: reg = ⌊32768 / Hz⌋. " +
                        "Not all integer Hz values are achievable exactly. Common rates: " +
                        "512 Hz (reg=64), 256 Hz (reg=128), 128 Hz (reg=256), 51 Hz (reg=645).",
                        color = EnactOnSurface.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}
