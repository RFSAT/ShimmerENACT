package com.rfsat.shimmerenact.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rfsat.shimmerenact.data.models.*
import com.rfsat.shimmerenact.ui.theme.*
import com.rfsat.shimmerenact.viewmodel.ShimmerViewModel
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SamplingRateScreen(
    viewModel: ShimmerViewModel,
    onBack: () -> Unit
) {
    val activeType by viewModel.activeSensorType.collectAsState()
    val config     by viewModel.activeConfig.collectAsState()
    val signals    = remember(activeType) { signalsForType(activeType) }
    val supportedKeys by viewModel.supportedSignalKeys.collectAsState()
    val visibleSignals by remember(signals) {
        derivedStateOf {
            if (supportedKeys.isEmpty()) signals
            else signals.filter { it.key in supportedKeys }
        }
    }
    val focusManager = LocalFocusManager.current

    // Group signals by sensor subsystem for cleaner presentation
    val groups = remember(visibleSignals) { groupSignals(visibleSignals) }

    var showResetDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Sampling Rates", color = EnactOnSurface, fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold)
                        Text(config.displayName, fontSize = 11.sp,
                            color = EnactOnSurfaceDim)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = EnactGreen)
                    }
                },
                actions = {
                    IconButton(onClick = { showResetDialog = true }) {
                        Icon(Icons.Default.RestartAlt, "Reset to defaults", tint = EnactWarning)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EnactDarkMid)
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

            // ── Hardware (global) rate card ───────────────────────────────────
            HardwareRateCard(
                hardwareRateHz = config.hardwareRateHz,
                onRateChange = { viewModel.updateHardwareRate(activeType, it) }
            )

            // ── Info banner ───────────────────────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(containerColor = EnactSurfaceVar),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Info, null, tint = Color(0xFF39A8E0),
                        modifier = Modifier.size(16.dp).padding(top = 1.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "The hardware rate is sent to the Shimmer3 and applies to all channels. " +
                        "Per-signal rates below perform software decimation — " +
                        "a signal set to 50 Hz on a 250 Hz stream will emit every 5th sample.",
                        fontSize = 11.sp,
                        color = EnactOnSurfaceDim,
                        lineHeight = 15.sp
                    )
                }
            }

            // ── Per-signal rate cards, grouped ────────────────────────────────
            groups.forEach { (groupName, groupSignals) ->
                SignalGroupCard(
                    groupName = groupName,
                    signals = groupSignals,
                    config = config,
                    onSignalRateChange = { key, hz -> viewModel.updateSignalRate(key, hz) }
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    // Reset confirmation dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            containerColor = EnactSurface,
            title = { Text("Reset all rates?", color = EnactOnSurface) },
            text = { Text(
                "All per-signal rates will be reset to the hardware rate (${config.hardwareRateHz} Hz).",
                color = EnactOnSurface.copy(alpha = 0.7f)
            )},
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetAllSignalRates(activeType)
                    showResetDialog = false
                }) { Text("Reset", color = EnactWarning, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel", color = EnactGreen)
                }
            }
        )
    }
}

// ─── Hardware rate card ───────────────────────────────────────────────────────

@Composable
fun HardwareRateCard(
    hardwareRateHz: Int,
    onRateChange: (Int) -> Unit
) {
    var textValue by remember(hardwareRateHz) { mutableStateOf(hardwareRateHz.toString()) }
    var isEditing by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // Logarithmic slider: position 0..1 maps to 1..6000 Hz log-scale
    val sliderPos = remember(hardwareRateHz) { hzToSlider(hardwareRateHz) }

    Card(
        colors = CardDefaults.cardColors(containerColor = EnactSurface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, EnactGreen.copy(alpha = 0.4f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Speed, null, tint = EnactGreen, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Hardware Sampling Rate", fontWeight = FontWeight.SemiBold,
                    color = EnactGreen, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                // Numeric input
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { v ->
                        textValue = v.filter { it.isDigit() }.take(4)
                    },
                    modifier = Modifier
                        .width(90.dp)
                        .onFocusChanged { state ->
                            if (!state.isFocused && isEditing) {
                                val parsed = textValue.toIntOrNull()?.coerceIn(1, 6000) ?: hardwareRateHz
                                onRateChange(parsed)
                                textValue = parsed.toString()
                                isEditing = false
                            }
                            if (state.isFocused) isEditing = true
                        },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        val parsed = textValue.toIntOrNull()?.coerceIn(1, 6000) ?: hardwareRateHz
                        onRateChange(parsed)
                        textValue = parsed.toString()
                        focusManager.clearFocus()
                    }),
                    suffix = { Text("Hz", color = EnactGreen.copy(alpha = 0.7f), fontSize = 12.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EnactGreen,
                        unfocusedBorderColor = EnactSurfaceVar,
                        focusedTextColor = EnactOnSurface,
                        unfocusedTextColor = EnactOnSurface,
                        cursorColor = EnactGreen
                    ),
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, textAlign = TextAlign.Center)
                )
            }

            Spacer(Modifier.height(8.dp))

            // Log-scale slider
            Slider(
                value = sliderPos,
                onValueChange = { pos ->
                    val hz = sliderToHz(pos)
                    textValue = hz.toString()
                    onRateChange(hz)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = EnactGreen,
                    activeTrackColor = EnactGreen,
                    inactiveTrackColor = EnactSurfaceVar
                )
            )

            // Tick labels
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("1", "10", "100", "1k", "6k").forEach { label ->
                    Text(label, fontSize = 9.sp, color = EnactOnSurface.copy(alpha = 0.35f))
                }
            }

            Spacer(Modifier.height(8.dp))

            // Quick preset buttons
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Presets:", fontSize = 11.sp, color = EnactOnSurface.copy(alpha = 0.4f),
                    modifier = Modifier.align(Alignment.CenterVertically))
                listOf(1, 50, 250, 500, 1000, 4000).forEach { preset ->
                    val isActive = hardwareRateHz == preset
                    OutlinedButton(
                        onClick = { onRateChange(preset); textValue = preset.toString() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isActive) EnactGreen else EnactSurfaceVar
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isActive) EnactGreen.copy(alpha = 0.15f) else Color.Transparent
                        )
                    ) {
                        Text(
                            if (preset >= 1000) "${preset/1000}k" else "$preset",
                            fontSize = 11.sp,
                            color = if (isActive) EnactGreen else EnactOnSurfaceDim
                        )
                    }
                }
            }
        }
    }
}

// ─── Signal group card ────────────────────────────────────────────────────────

@Composable
fun SignalGroupCard(
    groupName: String,
    signals: List<ShimmerSignal>,
    config: SensorConfig,
    onSignalRateChange: (String, Int) -> Unit
) {
    var expanded by remember { mutableStateOf(true) }

    Card(
        colors = CardDefaults.cardColors(containerColor = EnactSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            // Group header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(groupName, fontWeight = FontWeight.SemiBold,
                    color = EnactOnSurface, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null, tint = EnactGreen.copy(alpha = 0.6f), modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Divider(color = EnactSurfaceVar)
                    signals.forEach { signal ->
                        SignalRateRow(
                            signal = signal,
                            currentHz = config.effectiveRateHz(signal.key, signal.rateConstraints),
                            hardwareRateHz = config.hardwareRateHz,
                            onRateChange = { hz -> onSignalRateChange(signal.key, hz) }
                        )
                        if (signal != signals.last()) {
                            Divider(
                                color = EnactSurfaceVar.copy(alpha = 0.4f),
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Single signal rate row ───────────────────────────────────────────────────

@Composable
fun SignalRateRow(
    signal: ShimmerSignal,
    currentHz: Int,
    hardwareRateHz: Int,
    onRateChange: (Int) -> Unit
) {
    val constraints = signal.rateConstraints
    val accentColor = Color(signal.color)
    val focusManager = LocalFocusManager.current

    var textValue by remember(currentHz) { mutableStateOf(currentHz.toString()) }
    var isEditing by remember { mutableStateOf(false) }

    val isAtHardwareRate = currentHz == hardwareRateHz
    val decimationFactor = if (hardwareRateHz > 0 && currentHz > 0) hardwareRateHz / currentHz else 1

    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Colour dot
            Box(
                Modifier
                    .size(8.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(accentColor)
            )
            Spacer(Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(signal.displayName, fontSize = 13.sp, color = EnactOnSurface,
                    fontWeight = FontWeight.Medium)
                Row {
                    Text(signal.unit, fontSize = 10.sp, color = EnactOnSurface.copy(alpha = 0.4f))
                    Text("  •  ", fontSize = 10.sp, color = EnactOnSurface.copy(alpha = 0.25f))
                    Text(
                        "limit: ${constraints.minHz}–${constraints.maxHz} Hz",
                        fontSize = 10.sp, color = EnactOnSurface.copy(alpha = 0.35f)
                    )
                }
            }

            // Rate input field
            OutlinedTextField(
                value = textValue,
                onValueChange = { v -> textValue = v.filter { it.isDigit() }.take(4) },
                modifier = Modifier
                    .width(88.dp)
                    .onFocusChanged { state ->
                        if (!state.isFocused && isEditing) {
                            val parsed = textValue.toIntOrNull()
                                ?.coerceIn(constraints.minHz, minOf(constraints.maxHz, hardwareRateHz))
                            val clamped = constraints.clamp(parsed ?: currentHz)
                            onRateChange(clamped)
                            textValue = clamped.toString()
                            isEditing = false
                        }
                        if (state.isFocused) isEditing = true
                    },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = {
                    val parsed = textValue.toIntOrNull()
                        ?.coerceIn(constraints.minHz, minOf(constraints.maxHz, hardwareRateHz))
                    val clamped = constraints.clamp(parsed ?: currentHz)
                    onRateChange(clamped)
                    textValue = clamped.toString()
                    focusManager.clearFocus()
                }),
                suffix = { Text("Hz", fontSize = 10.sp, color = accentColor.copy(alpha = 0.7f)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = if (isAtHardwareRate) EnactSurfaceVar else accentColor.copy(alpha = 0.5f),
                    focusedTextColor = EnactOnSurface,
                    unfocusedTextColor = EnactOnSurface,
                    cursorColor = accentColor
                ),
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, textAlign = TextAlign.Center)
            )
        }

        // For ExG (fixed allowed values), show chips; otherwise show a mini slider
        if (constraints.allowedValues != null) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                constraints.allowedValues.filter { it <= hardwareRateHz }.forEach { allowed ->
                    val isSelected = currentHz == allowed
                    FilterChip(
                        selected = isSelected,
                        onClick = { onRateChange(allowed); textValue = allowed.toString() },
                        label = { Text("${allowed}Hz", fontSize = 10.sp) },
                        modifier = Modifier.height(26.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accentColor.copy(alpha = 0.2f),
                            selectedLabelColor = accentColor,
                            labelColor = EnactOnSurface.copy(alpha = 0.4f)
                        )
                    )
                }
            }
        } else {
            // Mini log-scale slider
            Spacer(Modifier.height(4.dp))
            val effectiveMax = minOf(constraints.maxHz, hardwareRateHz)
            val sliderPos = hzToSliderBounded(currentHz, constraints.minHz, effectiveMax)
            Slider(
                value = sliderPos,
                onValueChange = { pos ->
                    val hz = sliderToHzBounded(pos, constraints.minHz, effectiveMax)
                    textValue = hz.toString()
                    onRateChange(hz)
                },
                modifier = Modifier.fillMaxWidth().height(24.dp),
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = EnactSurfaceVar
                )
            )
        }

        // Decimation indicator
        if (decimationFactor > 1) {
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FilterAlt, null,
                    tint = EnactWarning.copy(alpha = 0.7f),
                    modifier = Modifier.size(11.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    "Decimated ÷$decimationFactor  ($hardwareRateHz Hz hardware → $currentHz Hz output)",
                    fontSize = 9.sp, color = EnactWarning.copy(alpha = 0.65f)
                )
            }
        }
    }
}

// ─── Signal grouping ──────────────────────────────────────────────────────────

fun groupSignals(signals: List<ShimmerSignal>): List<Pair<String, List<ShimmerSignal>>> {
    val groups = LinkedHashMap<String, MutableList<ShimmerSignal>>()
    for (sig in signals) {
        val groupName = when {
            sig.key.startsWith("exg")           -> "ExG (ECG/EMG)"
            sig.key.startsWith("accel")         -> "Accelerometer"
            sig.key.startsWith("gyro")          -> "Gyroscope"
            sig.key.startsWith("mag")           -> "Magnetometer"
            sig.key == "gsr_kohm"               -> "GSR"
            sig.key == "ppg_mv"                 -> "PPG"
            sig.key == "temp_c"                 -> "Environment"
            sig.key == "batt_mv"                -> "System"
            sig.key.startsWith("ch")            -> "Channels"
            else                                -> "Other"
        }
        groups.getOrPut(groupName) { mutableListOf() }.add(sig)
    }
    return groups.entries.map { it.key to it.value }
}

// ─── Log-scale slider helpers ─────────────────────────────────────────────────

private const val SLIDER_MIN_HZ = 1f
private const val SLIDER_MAX_HZ = 6000f

fun hzToSlider(hz: Int): Float {
    val logMin = log2(SLIDER_MIN_HZ)
    val logMax = log2(SLIDER_MAX_HZ)
    return ((log2(hz.toFloat().coerceIn(SLIDER_MIN_HZ, SLIDER_MAX_HZ)) - logMin) / (logMax - logMin))
        .coerceIn(0f, 1f)
}

fun sliderToHz(pos: Float): Int {
    val logMin = log2(SLIDER_MIN_HZ)
    val logMax = log2(SLIDER_MAX_HZ)
    return 2f.pow(logMin + pos.coerceIn(0f, 1f) * (logMax - logMin))
        .roundToInt().coerceIn(1, 6000)
}

fun hzToSliderBounded(hz: Int, minHz: Int, maxHz: Int): Float {
    if (minHz >= maxHz) return 0f
    val logMin = log2(minHz.toFloat())
    val logMax = log2(maxHz.toFloat())
    return ((log2(hz.toFloat().coerceIn(minHz.toFloat(), maxHz.toFloat())) - logMin) / (logMax - logMin))
        .coerceIn(0f, 1f)
}

fun sliderToHzBounded(pos: Float, minHz: Int, maxHz: Int): Int {
    if (minHz >= maxHz) return minHz
    val logMin = log2(minHz.toFloat())
    val logMax = log2(maxHz.toFloat())
    return 2f.pow(logMin + pos.coerceIn(0f, 1f) * (logMax - logMin))
        .roundToInt().coerceIn(minHz, maxHz)
}
