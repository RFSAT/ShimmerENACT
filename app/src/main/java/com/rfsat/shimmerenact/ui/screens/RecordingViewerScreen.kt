package com.rfsat.shimmerenact.ui.screens

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.rfsat.shimmerenact.data.repository.RecordingFile
import com.rfsat.shimmerenact.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class CsvPoint(val timestampMs: Long, val value: Double)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingViewerScreen(
    recordingFile: RecordingFile,
    onBack: () -> Unit
) {
    var points by remember { mutableStateOf<List<CsvPoint>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedPoint by remember { mutableStateOf<CsvPoint?>(null) }
    var selectedIndex by remember { mutableStateOf(-1) }

    val isoFmt = remember {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }

    // Load CSV data
    LaunchedEffect(recordingFile.path) {
        isLoading = true
        loadError = null
        selectedPoint = null
        selectedIndex = -1
        try {
            val loaded = withContext(Dispatchers.IO) {
                val file = File(recordingFile.path)
                if (!file.exists()) throw Exception("File not found:\n${recordingFile.path}")
                val result = mutableListOf<CsvPoint>()
                var headerParsed = false
                var tsCol = 1      // default: col 1 = timestamp_ms
                var valCol = 2     // default: col 2 = signal value

                file.useLines { seq ->
                    for (line in seq) {
                        val trimmed = line.trim()
                        if (trimmed.startsWith("#") || trimmed.isEmpty()) continue
                        if (!headerParsed) {
                            // Parse the column header row
                            val cols = trimmed.split(",")
                            val tsMsIdx = cols.indexOfFirst { it.trim().startsWith("timestamp_ms") }
                            val isoIdx  = cols.indexOfFirst { it.trim().startsWith("timestamp_iso") }
                            // Value column: first column that is not a timestamp column
                            val vIdx = cols.indices.firstOrNull { i ->
                                i != tsMsIdx && i != isoIdx
                            } ?: cols.lastIndex
                            tsCol = if (tsMsIdx >= 0) tsMsIdx else -1
                            valCol = vIdx
                            headerParsed = true
                            continue
                        }
                        // Skip comment lines that may appear as footer (# Session end …)
                        if (trimmed.startsWith("#")) continue
                        val cols = trimmed.split(",")
                        if (cols.size <= valCol) continue

                        // Resolve timestamp in ms — try timestamp_ms column first, then ISO col 0
                        val tsMs: Long = (
                            if (tsCol >= 0) cols.getOrNull(tsCol)?.trim()?.toLongOrNull()
                            else null
                        ) ?: cols.getOrNull(0)?.trim()?.let { iso ->
                            runCatching { isoFmt.parse(iso)?.time }.getOrNull()
                        } ?: continue

                        val v = cols.getOrNull(valCol)?.trim()?.toDoubleOrNull() ?: continue
                        result.add(CsvPoint(tsMs, v))
                    }
                }
                result
            }
            points = loaded
        } catch (e: Exception) {
            loadError = e.message ?: "Failed to load file"
        } finally {
            isLoading = false
        }
    }

    // Stats derived from loaded points
    val stats = remember(points) {
        if (points.isEmpty()) null else {
            val values = points.map { it.value }
            Triple(values.min(), values.average(), values.max())
        }
    }

    // Colours computed once (stable across recompositions)
    val accentArgb       = EnactGreen.toArgb()
    val onSurfaceDimArgb = EnactOnSurfaceDim.toArgb()
    val bgArgb           = EnactDark.toArgb()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            recordingFile.signalDisplayName.ifBlank { recordingFile.name },
                            color = EnactOnSurface, fontSize = 15.sp
                        )
                        Text(
                            buildString {
                                if (recordingFile.signalUnit.isNotBlank())
                                    append("[${recordingFile.signalUnit}]  •  ")
                                if (recordingFile.rateHz > 0)
                                    append("${recordingFile.rateHz} Hz  •  ")
                                append("${points.size} pts")
                            },
                            fontSize = 11.sp, color = EnactOnSurfaceDim
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = EnactGreen)
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
        ) {
            when {
                // ── Loading ───────────────────────────────────────────────────
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = EnactGreen)
                            Spacer(Modifier.height(12.dp))
                            Text("Loading ${recordingFile.name}…", color = EnactOnSurfaceDim)
                        }
                    }
                }

                // ── Error ─────────────────────────────────────────────────────
                loadError != null -> {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.ErrorOutline, null,
                                tint = EnactError, modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Failed to load recording",
                                color = EnactError, fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                loadError ?: "", color = EnactOnSurfaceDim, fontSize = 12.sp
                            )
                        }
                    }
                }

                // ── No data ───────────────────────────────────────────────────
                points.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.BarChart, null,
                                tint = EnactGreen.copy(alpha = 0.3f),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text("No data in file", color = EnactOnSurfaceDim)
                        }
                    }
                }

                // ── Chart ─────────────────────────────────────────────────────
                else -> {
                    // Stats strip
                    stats?.let { (min, mean, max) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(EnactDarkMid)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatChip("MIN",     "%.4f".format(min),  EnactGreen)
                            StatChip("MEAN",    "%.4f".format(mean), EnactLime)
                            StatChip("MAX",     "%.4f".format(max),  EnactError)
                            StatChip("SAMPLES", "${points.size}",     EnactOnSurfaceDim)
                        }
                    }

                    // Cursor readout (fixed height so chart doesn't jump)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (selectedPoint != null)
                                    EnactGreen.copy(alpha = 0.08f) else EnactDark
                            )
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        if (selectedPoint != null) {
                            val pt = selectedPoint!!
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.MyLocation, null,
                                    tint = EnactGreen, modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "t = ${timeFmt.format(Date(pt.timestampMs))}",
                                    fontSize = 12.sp, color = EnactOnSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "%.6f".format(pt.value) +
                                        if (recordingFile.signalUnit.isNotBlank())
                                            "  ${recordingFile.signalUnit}" else "",
                                    fontSize = 13.sp, color = EnactGreen,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "#${selectedIndex + 1}",
                                    fontSize = 11.sp, color = EnactOnSurfaceDim
                                )
                            }
                        } else {
                            Text(
                                "Tap graph to trace value",
                                fontSize = 12.sp,
                                color = EnactOnSurfaceDim.copy(alpha = 0.4f)
                            )
                        }
                    }

                    // Build chart entries once per data load, not on every recompose
                    val entries = remember(points) {
                        val t0 = points.first().timestampMs
                        points.mapIndexed { i, pt ->
                            Entry((pt.timestampMs - t0) / 1000f, pt.value.toFloat(), i)
                        }
                    }

                    // MPAndroidChart — factory creates the view once, update applies data
                    AndroidView(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 4.dp, start = 4.dp, end = 4.dp),
                        factory = { ctx ->
                            LineChart(ctx).apply {
                                description.isEnabled = false
                                legend.isEnabled = false
                                setBackgroundColor(bgArgb)
                                setGridBackgroundColor(bgArgb)
                                setDrawGridBackground(false)
                                setTouchEnabled(true)
                                isDragEnabled = true
                                isScaleXEnabled = true
                                isScaleYEnabled = true
                                setPinchZoom(false)   // separate X/Y zoom
                                setDrawMarkers(false)
                                isHighlightPerDragEnabled = false
                                setNoDataText("No data")

                                xAxis.apply {
                                    position = XAxis.XAxisPosition.BOTTOM
                                    setDrawGridLines(true)
                                    gridColor = AndroidColor.argb(40, 255, 255, 255)
                                    textColor = onSurfaceDimArgb
                                    textSize = 9f
                                    labelCount = 6
                                    granularity = 0.001f   // min interval in axis units (seconds)
                                    isGranularityEnabled = true
                                    setAvoidFirstLastClipping(true)
                                    valueFormatter = object : ValueFormatter() {
                                        override fun getFormattedValue(v: Float): String =
                                            if (v < 60f) "%.1fs".format(v)
                                            else "%.0fm%.0fs".format(v / 60, v % 60)
                                    }
                                }
                                axisLeft.apply {
                                    setDrawGridLines(true)
                                    gridColor = AndroidColor.argb(30, 255, 255, 255)
                                    textColor = onSurfaceDimArgb
                                    textSize = 9f
                                    setDrawAxisLine(false)
                                }
                                axisRight.isEnabled = false
                            }
                        },
                        update = { chart ->
                            // Build dataset
                            val drawCircles = entries.size < 300
                            val dataset = LineDataSet(entries, "").apply {
                                color = accentArgb
                                lineWidth = if (entries.size > 2000) 1f else 1.5f
                                setDrawCircles(drawCircles)
                                setCircleColor(accentArgb)
                                circleRadius = 2f
                                setDrawValues(false)
                                mode = LineDataSet.Mode.LINEAR
                                setDrawFilled(true)
                                fillColor = accentArgb
                                fillAlpha = 20
                                highLightColor = AndroidColor.WHITE
                                highlightLineWidth = 1.5f
                                isHighlightEnabled = true
                            }

                            chart.data = LineData(dataset)
                            chart.data.notifyDataChanged()
                            chart.notifyDataSetChanged()

                            // Fit full time range on screen — key fix for single-line appearance
                            chart.fitScreen()
                            chart.setVisibleXRangeMaximum(Float.MAX_VALUE)

                            // Register value-selection listener once data is set
                            chart.setOnChartValueSelectedListener(
                                object : OnChartValueSelectedListener {
                                    override fun onValueSelected(e: Entry?, h: Highlight?) {
                                        e ?: return
                                        val idx = e.data as? Int ?: return
                                        if (idx in points.indices) {
                                            selectedPoint = points[idx]
                                            selectedIndex = idx
                                        }
                                    }
                                    override fun onNothingSelected() {
                                        selectedPoint = null
                                        selectedIndex = -1
                                    }
                                }
                            )
                            chart.invalidate()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label, fontSize = 9.sp, color = color.copy(alpha = 0.7f),
            letterSpacing = 0.8.sp, fontWeight = FontWeight.SemiBold
        )
        Text(value, fontSize = 12.sp, color = color, fontWeight = FontWeight.Medium)
    }
}
