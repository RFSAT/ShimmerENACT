package com.rfsat.shimmerenact.ui.screens

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
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

    val isoFmt = remember { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    } }
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }

    LaunchedEffect(recordingFile.path) {
        isLoading = true
        loadError = null
        try {
            val loaded = withContext(Dispatchers.IO) {
                val file = File(recordingFile.path)
                if (!file.exists()) throw Exception("File not found: ${recordingFile.path}")
                val result = mutableListOf<CsvPoint>()
                var headerParsed = false
                var tsCol = 0
                var valCol = 2
                file.forEachLine { line ->
                    if (line.startsWith("#") || line.isBlank()) return@forEachLine
                    if (!headerParsed) {
                        // Detect column indices from header row
                        val cols = line.split(",")
                        tsCol = cols.indexOfFirst { it.startsWith("timestamp_ms") }
                            .takeIf { it >= 0 } ?: 1
                        valCol = if (cols.size > 2) 2 else 1
                        headerParsed = true
                        return@forEachLine
                    }
                    val cols = line.split(",")
                    if (cols.size <= valCol) return@forEachLine
                    val tsMs = cols.getOrNull(tsCol)?.trim()?.toLongOrNull()
                        ?: run {
                            // Try parsing ISO timestamp in col 0
                            cols.getOrNull(0)?.trim()?.let { iso ->
                                runCatching { isoFmt.parse(iso)?.time }.getOrNull()
                            }
                        } ?: return@forEachLine
                    val v = cols.getOrNull(valCol)?.trim()?.toDoubleOrNull() ?: return@forEachLine
                    result.add(CsvPoint(tsMs, v))
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

    // Derived stats
    val stats = remember(points) {
        if (points.isEmpty()) null else {
            val values = points.map { it.value }
            val min = values.min()
            val max = values.max()
            val mean = values.average()
            Triple(min, max, mean)
        }
    }

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
                                if (recordingFile.signalUnit.isNotBlank()) append("[${recordingFile.signalUnit}]  •  ")
                                if (recordingFile.rateHz > 0) append("${recordingFile.rateHz} Hz  •  ")
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
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = EnactGreen)
                            Spacer(Modifier.height(12.dp))
                            Text("Loading ${recordingFile.name}…", color = EnactOnSurfaceDim)
                        }
                    }
                }

                loadError != null -> {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.ErrorOutline, null,
                                tint = EnactError, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("Failed to load recording", color = EnactError,
                                fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(loadError ?: "", color = EnactOnSurfaceDim,
                                fontSize = 12.sp)
                        }
                    }
                }

                points.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.BarChart, null,
                                tint = EnactGreen.copy(alpha = 0.3f),
                                modifier = Modifier.size(56.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("No data in file", color = EnactOnSurfaceDim)
                        }
                    }
                }

                else -> {
                    // ── Stats row ──────────────────────────────────────────────
                    stats?.let { (min, max, mean) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(EnactDarkMid)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatChip("MIN", "%.3f".format(min), EnactGreen)
                            StatChip("MEAN", "%.3f".format(mean), EnactLime)
                            StatChip("MAX", "%.3f".format(max), EnactError)
                            StatChip("SAMPLES", "${points.size}", EnactOnSurfaceDim)
                        }
                    }

                    // ── Selected value readout ─────────────────────────────────
                    selectedPoint?.let { pt ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(EnactGreen.copy(alpha = 0.08f))
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.MyLocation, null,
                                tint = EnactGreen, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "t = ${timeFmt.format(Date(pt.timestampMs))}",
                                fontSize = 12.sp, color = EnactOnSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "%.4f ${recordingFile.signalUnit}".trim(),
                                fontSize = 13.sp, color = EnactGreen,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "#${selectedIndex + 1}",
                                fontSize = 11.sp, color = EnactOnSurfaceDim
                            )
                        }
                    } ?: run {
                        // Placeholder row to keep layout stable
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(EnactDark)
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text("Tap graph to trace value",
                                fontSize = 12.sp, color = EnactOnSurfaceDim.copy(alpha = 0.5f))
                        }
                    }

                    // ── Chart ──────────────────────────────────────────────────
                    val chartColor = Color(0xFF43AF81)  // EnactGreen
                    val accentArgb = EnactGreen.toArgb()
                    val surfaceArgb = EnactSurface.toArgb()
                    val onSurfaceArgb = EnactOnSurface.toArgb()
                    val onSurfaceDimArgb = EnactOnSurfaceDim.toArgb()
                    val bgArgb = EnactDark.toArgb()

                    AndroidView(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
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
                                isScaleYEnabled = false
                                setPinchZoom(true)
                                setDrawMarkers(false)
                                isHighlightPerDragEnabled = true

                                xAxis.apply {
                                    position = XAxis.XAxisPosition.BOTTOM
                                    setDrawGridLines(true)
                                    gridColor = AndroidColor.argb(40, 255, 255, 255)
                                    textColor = onSurfaceDimArgb
                                    textSize = 9f
                                    labelCount = 5
                                    granularity = 1f
                                    setAvoidFirstLastClipping(true)
                                }

                                axisLeft.apply {
                                    setDrawGridLines(true)
                                    gridColor = AndroidColor.argb(30, 255, 255, 255)
                                    textColor = onSurfaceDimArgb
                                    textSize = 9f
                                    setDrawAxisLine(false)
                                }
                                axisRight.isEnabled = false

                                setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                                    override fun onValueSelected(e: Entry?, h: Highlight?) {}
                                    override fun onNothingSelected() {}
                                })
                            }
                        },
                        update = { chart ->
                            if (points.isEmpty()) {
                                chart.data = null
                                chart.invalidate()
                                return@AndroidView
                            }

                            val t0 = points.first().timestampMs.toFloat()
                            val entries = points.mapIndexed { i, pt ->
                                Entry((pt.timestampMs - t0) / 1000f, pt.value.toFloat(), i)
                            }

                            val dataset = LineDataSet(entries, "").apply {
                                color = accentArgb
                                lineWidth = 1.5f
                                setDrawCircles(points.size < 500)
                                setCircleColor(accentArgb)
                                circleRadius = 2.5f
                                setDrawValues(false)
                                mode = if (points.size > 1000)
                                    LineDataSet.Mode.LINEAR else LineDataSet.Mode.CUBIC_BEZIER
                                setDrawFilled(true)
                                fillColor = accentArgb
                                fillAlpha = 25
                                highLightColor = AndroidColor.WHITE
                                highlightLineWidth = 1f
                                isHighlightEnabled = true
                            }

                            // X axis label: seconds from start
                            chart.xAxis.valueFormatter = object : ValueFormatter() {
                                override fun getFormattedValue(value: Float): String =
                                    "%.1fs".format(value)
                            }

                            chart.data = LineData(dataset)
                            chart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                                override fun onValueSelected(e: Entry?, h: Highlight?) {
                                    e ?: return
                                    val idx = (e.data as? Int) ?: return
                                    if (idx in points.indices) {
                                        selectedPoint = points[idx]
                                        selectedIndex = idx
                                    }
                                }
                                override fun onNothingSelected() {
                                    selectedPoint = null
                                    selectedIndex = -1
                                }
                            })
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
        Text(label, fontSize = 9.sp, color = color.copy(alpha = 0.7f),
            letterSpacing = 0.8.sp, fontWeight = FontWeight.SemiBold)
        Text(value, fontSize = 12.sp, color = color, fontWeight = FontWeight.Medium)
    }
}
