package com.rfsat.shimmerenact.ui.screens

import android.graphics.Color as AndroidColor
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import com.rfsat.shimmerenact.data.models.*
import com.rfsat.shimmerenact.ui.theme.*
import com.rfsat.shimmerenact.viewmodel.ShimmerViewModel
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: ShimmerViewModel,
    onDisconnect: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val recordingState by viewModel.recordingState.collectAsState()
    val activeConfig by viewModel.activeConfig.collectAsState()

    val signals = remember(activeConfig.sensorType) { signalsForType(activeConfig.sensorType) }

    // Chart signal selection — show first 4 by default
    var selectedChartSignals by remember(signals) {
        mutableStateOf(signals.take(4).map { it.key }.toSet())
    }
    var showSignalSelector by remember { mutableStateOf(false) }
    var showChart by remember { mutableStateOf(true) }
    var showRecordingSetup by remember { mutableStateOf(false) }

    // Recording elapsed time
    var elapsedSec by remember { mutableStateOf(0L) }
    LaunchedEffect(recordingState.isRecording, recordingState.startTimeMs) {
        if (recordingState.isRecording) {
            while (true) {
                elapsedSec = (System.currentTimeMillis() - recordingState.startTimeMs) / 1000
                kotlinx.coroutines.delay(500)
            }
        } else elapsedSec = 0L
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(activeConfig.displayName, color = EnactOnSurface,
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(6.dp).clip(CircleShape)
                                .background(
                                    if (uiState.connectionState == ConnectionState.CONNECTED) EnactGreen
                                    else EnactError
                                ))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "${uiState.connectionState.name} • ${"%.1f".format(uiState.samplesPerSecond)} Hz",
                                fontSize = 11.sp,
                                color = EnactOnSurface.copy(alpha = 0.55f)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showChart = !showChart }) {
                        Icon(if (showChart) Icons.Default.BarChart else Icons.Default.Analytics,
                            null, tint = EnactGreen)
                    }
                    IconButton(onClick = onDisconnect) {
                        Icon(Icons.Default.BluetoothDisabled, null, tint = EnactError)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EnactDarkMid)
            )
        },
        bottomBar = {
            RecordingBar(
                recordingState = recordingState,
                elapsedSec = elapsedSec,
                onStart = { showRecordingSetup = true },
                onStop = viewModel::stopRecording
            )
        },
        containerColor = EnactDark
    ) { padding ->

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // Live chart
            if (showChart && uiState.recentSamples.isNotEmpty()) {
                item {
                    LiveChartCard(
                        samples = uiState.recentSamples,
                        signals = signals.filter { it.key in selectedChartSignals },
                        onSelectSignals = { showSignalSelector = true }
                    )
                }
            }

            // Signal gauge cards — two per row
            val chunked = signals.chunked(2)
            items(chunked) { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { sig ->
                        SignalGaugeCard(
                            signal = sig,
                            value = uiState.latestSample?.values?.get(sig.key),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Fill remaining space if odd signal
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }

    // Signal selector sheet
    if (showSignalSelector) {
        SignalSelectorSheet(
            signals = signals,
            selected = selectedChartSignals,
            onDismiss = { showSignalSelector = false },
            onConfirm = { selectedChartSignals = it; showSignalSelector = false }
        )
    }

    if (showRecordingSetup) {
        RecordingSetupSheet(
            viewModel = viewModel,
            onDismiss = { showRecordingSetup = false },
            onStart = {
                showRecordingSetup = false
                viewModel.startRecording()
            }
        )
    }
}

// ─── Live chart ───────────────────────────────────────────────────────────────

@Composable
fun LiveChartCard(
    samples: List<ShimmerSample>,
    signals: List<ShimmerSignal>,
    onSelectSignals: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().height(220.dp),
        colors = CardDefaults.cardColors(containerColor = EnactSurface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize().padding(4.dp),
                factory = { ctx ->
                    LineChart(ctx).apply {
                        description.isEnabled = false
                        legend.isEnabled = false
                        setTouchEnabled(true)
                        isDragEnabled = true
                        setScaleEnabled(true)
                        setDrawGridBackground(false)
                        setBackgroundColor(AndroidColor.TRANSPARENT)

                        xAxis.apply {
                            setDrawLabels(true)
                            setDrawGridLines(true)
                            gridColor = EnactSurfaceVar.copy(alpha = 0.4f).toArgb()
                            axisLineColor = EnactSurfaceVar.toArgb()
                            textColor = EnactOnSurface.copy(alpha = 0.45f).toArgb()
                            textSize = 8f
                            labelCount = 5
                            // X values are seconds; format as "Xs"
                            valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                                override fun getFormattedValue(value: Float) =
                                    "${"%.1f".format(value)}s"
                            }
                            position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                        }
                        axisLeft.apply {
                            textColor = EnactOnSurface.copy(alpha = 0.5f).toArgb()
                            gridColor = EnactSurfaceVar.copy(alpha = 0.5f).toArgb()
                            axisLineColor = EnactSurfaceVar.toArgb()
                            textSize = 8f
                        }
                        axisRight.isEnabled = false
                        setNoDataText("Waiting for data…")
                        setNoDataTextColor(EnactGreen.toArgb())
                    }
                },
                update = { chart ->
                    if (samples.isEmpty() || signals.isEmpty()) return@AndroidView

                    // Use real timestamps: X = seconds since first sample in the buffer
                    val t0 = samples.first().timestampMs

                    val dataSets = signals.mapIndexed { idx, sig ->
                        val color = ChartColors.getOrElse(idx) { EnactGreen }
                        val entries = samples.mapNotNull { s ->
                            val v = s.values[sig.key] ?: return@mapNotNull null
                            val xSec = (s.timestampMs - t0) / 1000f
                            Entry(xSec, v.toFloat())
                        }
                        if (entries.isEmpty()) return@mapIndexed null
                        LineDataSet(entries, sig.displayName).apply {
                            this.color = color.toArgb()
                            setDrawCircles(false)
                            setDrawValues(false)
                            lineWidth = 1.5f
                            mode = LineDataSet.Mode.LINEAR   // LINEAR is faster than CUBIC for live data
                        }
                    }.filterNotNull()

                    if (dataSets.isEmpty()) return@AndroidView
                    chart.data = LineData(dataSets)
                    chart.notifyDataSetChanged()
                    chart.invalidate()
                }
            )

            // Window duration label (top-left)
            if (samples.size >= 2) {
                val windowSec = (samples.last().timestampMs - samples.first().timestampMs) / 1000f
                Text(
                    "%.1fs window".format(windowSec),
                    fontSize = 9.sp,
                    color = EnactOnSurface.copy(alpha = 0.3f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 8.dp, top = 4.dp)
                )
            }

            // Select signals button
            IconButton(
                onClick = onSelectSignals,
                modifier = Modifier.align(Alignment.TopEnd).size(32.dp).padding(4.dp)
            ) {
                Icon(Icons.Default.FilterList, null, tint = EnactGreen.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ─── Signal gauge card ────────────────────────────────────────────────────────

@Composable
fun SignalGaugeCard(
    signal: ShimmerSignal,
    value: Double?,
    modifier: Modifier = Modifier
) {
    val accentColor = Color(signal.color)
    val displayValue = value?.let { "%.3f".format(it) } ?: "---"

    Card(
        modifier = modifier.height(80.dp),
        colors = CardDefaults.cardColors(containerColor = EnactSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Colour indicator
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(0.6f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(signal.displayName, fontSize = 11.sp,
                    color = EnactOnSurface.copy(alpha = 0.6f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        displayValue,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (value != null) accentColor else EnactOnSurface.copy(alpha = 0.3f),
                        maxLines = 1
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(signal.unit, fontSize = 10.sp,
                        color = EnactOnSurface.copy(alpha = 0.45f),
                        modifier = Modifier.padding(bottom = 2.dp))
                }
            }
        }
    }
}

// ─── Recording bar ────────────────────────────────────────────────────────────

@Composable
fun RecordingBar(
    recordingState: RecordingState,
    elapsedSec: Long,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val elapsed = "%02d:%02d".format(elapsedSec / 60, elapsedSec % 60)
    Surface(
        color = if (recordingState.isRecording) EnactError.copy(alpha = 0.15f) else EnactDarkMid,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (recordingState.isRecording) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(EnactError))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Recording  $elapsed",
                        color = EnactError, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(
                        "${recordingState.fileCount} file${if (recordingState.fileCount != 1) "s" else ""}  •  " +
                        "${recordingState.rowsWritten} rows written",
                        color = EnactOnSurface.copy(alpha = 0.55f), fontSize = 11.sp
                    )
                }
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(containerColor = EnactError),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Stop, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Stop", color = Color.White, fontWeight = FontWeight.Bold)
                }
            } else {
                Icon(Icons.Default.FiberManualRecord, null,
                    tint = EnactOnSurface.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Not recording", color = EnactOnSurface.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f), fontSize = 13.sp)
                Button(
                    onClick = onStart,
                    colors = ButtonDefaults.buttonColors(containerColor = EnactGreen),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.FiberManualRecord, null,
                        tint = EnactDark, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Record", color = EnactDark, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─── Signal selector bottom sheet ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignalSelectorSheet(
    signals: List<ShimmerSignal>,
    selected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    var localSelected by remember { mutableStateOf(selected) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = EnactSurface
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Select chart signals", fontWeight = FontWeight.Bold,
                color = EnactOnSurface, fontSize = 16.sp)
            Text("Maximum 6 for readability", fontSize = 12.sp,
                color = EnactOnSurface.copy(alpha = 0.5f))
            Spacer(Modifier.height(12.dp))
            signals.forEach { sig ->
                val isOn = sig.key in localSelected
                val accentColor = Color(sig.color)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            localSelected = if (isOn) localSelected - sig.key
                            else if (localSelected.size < 6) localSelected + sig.key
                            else localSelected
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isOn,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(
                            checkedColor = accentColor,
                            uncheckedColor = EnactOnSurface.copy(alpha = 0.4f)
                        )
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.size(8.dp).clip(CircleShape).background(accentColor))
                    Spacer(Modifier.width(8.dp))
                    Text(sig.displayName, color = EnactOnSurface, fontSize = 14.sp)
                    Spacer(Modifier.weight(1f))
                    Text(sig.unit, color = EnactOnSurface.copy(alpha = 0.4f), fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onConfirm(localSelected) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = EnactGreen),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Apply", color = EnactDark, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ─── Recording setup sheet ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingSetupSheet(
    viewModel: ShimmerViewModel,
    onDismiss: () -> Unit,
    onStart: () -> Unit
) {
    val activeType by viewModel.activeSensorType.collectAsState()
    val config     by viewModel.activeConfig.collectAsState()
    val allSignals  = remember(activeType) { signalsForType(activeType) }

    // Local copy of selection so user can cancel without committing
    var localSelection by remember(config) {
        mutableStateOf(config.resolvedRecordingSignals(allSignals))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = EnactSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FiberManualRecord, null,
                    tint = EnactError, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Recording Setup", fontWeight = FontWeight.Bold,
                    color = EnactOnSurface, fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                // Select all / none
                TextButton(onClick = {
                    localSelection = if (localSelection.size == allSignals.size) emptySet()
                    else allSignals.map { it.key }.toSet()
                }) {
                    Text(
                        if (localSelection.size == allSignals.size) "None" else "All",
                        color = EnactGreen, fontSize = 12.sp
                    )
                }
            }

            Text(
                "Choose which parameters to record and their output rates (from Settings).",
                fontSize = 12.sp, color = EnactOnSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Signal list with per-signal rate display
            allSignals.forEach { sig ->
                val isSelected = sig.key in localSelection
                val accentColor = Color(sig.color)
                val rateHz = config.effectiveRateHz(sig.key, sig.rateConstraints)
                val decimFactor = if (config.hardwareRateHz > 0 && rateHz > 0)
                    config.hardwareRateHz / rateHz else 1

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) accentColor.copy(alpha = 0.08f)
                            else Color.Transparent
                        )
                        .clickable {
                            localSelection = if (isSelected) localSelection - sig.key
                            else localSelection + sig.key
                        }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(
                            checkedColor = accentColor,
                            uncheckedColor = EnactOnSurface.copy(alpha = 0.3f)
                        )
                    )
                    Box(
                        Modifier.size(8.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(accentColor)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(sig.displayName, color = EnactOnSurface, fontSize = 13.sp)
                        Text(sig.unit, fontSize = 10.sp, color = EnactOnSurface.copy(alpha = 0.4f))
                    }
                    // Rate badge
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "$rateHz Hz",
                            fontSize = 12.sp,
                            color = if (isSelected) accentColor else EnactOnSurface.copy(alpha = 0.3f),
                            fontWeight = FontWeight.SemiBold
                        )
                        if (decimFactor > 1) {
                            Text(
                                "÷$decimFactor",
                                fontSize = 9.sp,
                                color = EnactWarning.copy(alpha = if (isSelected) 0.8f else 0.3f)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Summary line
            val totalHz = localSelection.sumOf { key ->
                val sig = allSignals.find { it.key == key }
                if (sig != null) config.effectiveRateHz(key, sig.rateConstraints).toLong() else 0L
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(EnactSurfaceVar)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Info, null, tint = EnactGreen.copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "${localSelection.size} of ${allSignals.size} signals  •  " +
                    "hw: ${config.hardwareRateHz} Hz  •  " +
                    "~$totalHz rows/s total",
                    fontSize = 11.sp, color = EnactOnSurface.copy(alpha = 0.55f)
                )
            }

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = {
                    // Commit selection then start
                    viewModel.setRecordingSignals(localSelection)
                    onStart()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = localSelection.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = EnactError),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.FiberManualRecord, null,
                    tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Start Recording (${localSelection.size} signals)",
                    color = Color.White, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
