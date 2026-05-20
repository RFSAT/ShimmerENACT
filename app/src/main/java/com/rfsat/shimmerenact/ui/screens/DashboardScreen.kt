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
        mutableStateOf(signals.take(6).map { it.key }.toSet())  // gsr, ppg, accel_xyz, gyro_x by default
    }
    var showSignalSelector by remember { mutableStateOf(false) }
    var showChart by remember { mutableStateOf(true) }

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
                onStart = viewModel::startRecording,
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
                            setDrawLabels(false)
                            setDrawGridLines(false)
                            axisLineColor = EnactSurfaceVar.toArgb()
                        }
                        axisLeft.apply {
                            textColor = EnactOnSurface.copy(alpha = 0.5f).toArgb()
                            gridColor = EnactSurfaceVar.copy(alpha = 0.5f).toArgb()
                            axisLineColor = EnactSurfaceVar.toArgb()
                        }
                        axisRight.isEnabled = false
                        setNoDataText("Waiting for data…")
                        setNoDataTextColor(EnactGreen.toArgb())
                    }
                },
                update = { chart ->
                    if (samples.isEmpty() || signals.isEmpty()) return@AndroidView
                    val dataSets = signals.mapIndexed { idx, sig ->
                        val color = ChartColors.getOrElse(idx) { EnactGreen }
                        val entries = samples.mapIndexed { i, s ->
                            Entry(i.toFloat(), s.values[sig.key]?.toFloat() ?: 0f)
                        }
                        LineDataSet(entries, sig.displayName).apply {
                            this.color = color.toArgb()
                            setDrawCircles(false)
                            setDrawValues(false)
                            lineWidth = 1.5f
                            mode = LineDataSet.Mode.CUBIC_BEZIER
                        }
                    }
                    chart.data = LineData(dataSets)
                    chart.invalidate()
                }
            )

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
                // Pulsing record dot
                Box(
                    Modifier.size(10.dp).clip(CircleShape)
                        .background(EnactError)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Recording  $elapsed",
                        color = EnactError, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("${recordingState.sampleCount} samples",
                        color = EnactOnSurface.copy(alpha = 0.55f), fontSize = 11.sp)
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
