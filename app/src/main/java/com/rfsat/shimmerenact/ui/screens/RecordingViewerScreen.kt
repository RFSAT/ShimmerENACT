package com.rfsat.shimmerenact.ui.screens

import android.graphics.Color as AndroidColor
import android.view.MotionEvent
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.github.mikephil.charting.listener.ChartTouchListener
import com.github.mikephil.charting.listener.OnChartGestureListener
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.rfsat.shimmerenact.data.repository.RecordingFile
import com.rfsat.shimmerenact.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class CsvPoint(
    val timestampMs: Long,
    val value: Double,
    val latitude: Double? = null,
    val longitude: Double? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingViewerScreen(
    recordingFile: RecordingFile,
    onBack: () -> Unit
) {
    var points        by remember { mutableStateOf<List<CsvPoint>>(emptyList()) }
    var loadError     by remember { mutableStateOf<String?>(null) }
    var isLoading     by remember { mutableStateOf(true) }
    var selectedPoint by remember { mutableStateOf<CsvPoint?>(null) }
    var selectedIndex by remember { mutableStateOf(-1) }

    val isoFmt = remember {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }

    // ── Load CSV ───────────────────────────────────────────────────────────────
    LaunchedEffect(recordingFile.path) {
        isLoading     = true
        loadError     = null
        selectedPoint = null
        selectedIndex = -1
        try {
            val loaded = withContext(Dispatchers.IO) {
                val file = File(recordingFile.path)
                if (!file.exists()) throw Exception("File not found:\n${recordingFile.path}")
                val result   = mutableListOf<CsvPoint>()
                var headerParsed = false
                var tsCol  = 1
                var valCol = 2
                var latCol = -1
                var lonCol = -1

                file.useLines(Charsets.UTF_8) { seq ->
                    for (rawLine in seq) {
                        val line = rawLine.trim()
                        if (line.isEmpty() || line.startsWith("#")) continue
                        if (!headerParsed) {
                            val cols = line.split(",")
                            val tsMsIdx = cols.indexOfFirst { it.trim().startsWith("timestamp_ms") }
                            val isoIdx  = cols.indexOfFirst { it.trim().startsWith("timestamp_iso") }
                            latCol = cols.indexOfFirst { it.trim().startsWith("latitude") }
                            lonCol = cols.indexOfFirst { it.trim().startsWith("longitude") }
                            valCol = cols.indices.firstOrNull { i ->
                                i != tsMsIdx && i != isoIdx && i != latCol && i != lonCol &&
                                    !cols[i].trim().startsWith("altitude") &&
                                    !cols[i].trim().startsWith("location_accuracy")
                            } ?: cols.lastIndex
                            tsCol = if (tsMsIdx >= 0) tsMsIdx else -1
                            headerParsed = true
                            continue
                        }
                        val cols = line.split(",")
                        if (cols.size <= valCol) continue

                        val tsMs: Long = (
                            if (tsCol >= 0) cols.getOrNull(tsCol)?.trim()?.toLongOrNull()
                            else null
                        ) ?: cols.getOrNull(0)?.trim()?.let { iso ->
                            runCatching { isoFmt.parse(iso)?.time }.getOrNull()
                        } ?: continue

                        val v = cols.getOrNull(valCol)?.trim()?.toDoubleOrNull() ?: continue
                        val lat = if (latCol >= 0) cols.getOrNull(latCol)?.trim()?.toDoubleOrNull() else null
                        val lon = if (lonCol >= 0) cols.getOrNull(lonCol)?.trim()?.toDoubleOrNull() else null

                        result.add(CsvPoint(tsMs, v, lat, lon))
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

    // ── Stats ──────────────────────────────────────────────────────────────────
    val stats = remember(points) {
        if (points.isEmpty()) null else {
            val values = points.map { it.value }
            Triple(values.min(), values.average(), values.max())
        }
    }

    // Chart entries built once per data load
    val entries = remember(points) {
        if (points.isEmpty()) emptyList()
        else {
            val t0 = points.first().timestampMs
            points.mapIndexed { i, pt ->
                Entry((pt.timestampMs - t0) / 1000f, pt.value.toFloat(), i)
            }
        }
    }

    // GPS points with valid coordinates
    val gpsPoints = remember(points) {
        points.filter { it.latitude != null && it.longitude != null }
    }
    val hasGps = gpsPoints.isNotEmpty()

    // Stable ARGB values
    val accentArgb       = EnactGreen.toArgb()
    val onSurfaceDimArgb = EnactOnSurfaceDim.toArgb()
    val bgArgb           = EnactDark.toArgb()

    // WebView reference for JS map updates
    var mapWebView by remember { mutableStateOf<WebView?>(null) }

    // ── Push selected marker to map whenever it changes ────────────────────────
    LaunchedEffect(selectedIndex) {
        val wv = mapWebView ?: return@LaunchedEffect
        val sp = selectedPoint
        if (sp?.latitude != null && sp.longitude != null) {
            wv.post {
                wv.evaluateJavascript(
                    "moveSelected(${sp.latitude}, ${sp.longitude});", null
                )
            }
        } else {
            wv.post {
                wv.evaluateJavascript("clearSelected();", null)
            }
        }
    }

    // ── Scaffold ───────────────────────────────────────────────────────────────
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
                                if (hasGps) append("  •  GPS ✓")
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
                .verticalScroll(rememberScrollState())
        ) {
            when {
                // ── Loading ──────────────────────────────────────────────────
                isLoading -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = EnactGreen)
                            Spacer(Modifier.height(12.dp))
                            Text("Loading ${recordingFile.name}…", color = EnactOnSurfaceDim)
                        }
                    }
                }

                // ── Error ────────────────────────────────────────────────────
                loadError != null -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.ErrorOutline, null,
                                tint = EnactError, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("Failed to load recording",
                                color = EnactError, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(loadError ?: "", color = EnactOnSurfaceDim, fontSize = 12.sp)
                        }
                    }
                }

                // ── No data ──────────────────────────────────────────────────
                points.isEmpty() -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.BarChart, null,
                                tint = EnactGreen.copy(alpha = 0.3f),
                                modifier = Modifier.size(56.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("No data in file", color = EnactOnSurfaceDim)
                        }
                    }
                }

                // ── Chart + Map ──────────────────────────────────────────────
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
                            StatChip("SAMPLES", "${points.size}",    EnactOnSurfaceDim)
                        }
                    }

                    // Cursor readout — fixed height
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (selectedPoint != null)
                                    EnactGreen.copy(alpha = 0.10f) else EnactDark
                            )
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        if (selectedPoint != null) {
                            val pt = selectedPoint!!
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.MyLocation, null,
                                    tint = EnactGreen, modifier = Modifier.size(14.dp))
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
                                "Tap or drag on graph to trace value",
                                fontSize = 12.sp,
                                color = EnactOnSurfaceDim.copy(alpha = 0.4f)
                            )
                        }
                    }

                    // ── MPAndroidChart ────────────────────────────────────────
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .padding(bottom = 4.dp, start = 4.dp, end = 4.dp),
                        factory = { ctx ->
                            LineChart(ctx).apply {
                                description.isEnabled  = false
                                legend.isEnabled       = false
                                setBackgroundColor(bgArgb)
                                setDrawGridBackground(false)
                                setTouchEnabled(true)
                                isDragEnabled          = true
                                isScaleXEnabled        = true
                                isScaleYEnabled        = true
                                setPinchZoom(false)
                                setDrawMarkers(false)
                                isHighlightPerDragEnabled = true
                                setNoDataText("No data")

                                xAxis.apply {
                                    position = XAxis.XAxisPosition.BOTTOM
                                    setDrawGridLines(true)
                                    gridColor  = AndroidColor.argb(40, 255, 255, 255)
                                    textColor  = onSurfaceDimArgb
                                    textSize   = 9f
                                    labelCount = 6
                                    granularity        = 0.001f
                                    isGranularityEnabled = true
                                    setAvoidFirstLastClipping(true)
                                    valueFormatter = object : ValueFormatter() {
                                        override fun getFormattedValue(v: Float): String =
                                            if (v < 60f) "%.1fs".format(v)
                                            else         "%dm %ds".format(v.toInt() / 60, v.toInt() % 60)
                                    }
                                }
                                axisLeft.apply {
                                    setDrawGridLines(true)
                                    gridColor  = AndroidColor.argb(30, 255, 255, 255)
                                    textColor  = onSurfaceDimArgb
                                    textSize   = 9f
                                    setDrawAxisLine(false)
                                }
                                axisRight.isEnabled = false
                            }
                        },
                        update = { chart ->
                            if (entries.isEmpty()) {
                                chart.data = null; chart.invalidate(); return@AndroidView
                            }

                            // Always draw small circles on every data point (same colour as line)
                            // Threshold: skip circles only for extremely dense datasets (>5000 pts)
                            // where they would overlap into a solid band anyway.
                            val drawCircles = entries.size < 5000

                            val dataset = LineDataSet(entries, "").apply {
                                color           = accentArgb
                                lineWidth       = if (entries.size > 2000) 1f else 1.5f
                                // ── Task 1(a): small same-colour circles on every point ──
                                setDrawCircles(drawCircles)
                                setCircleColor(accentArgb)
                                circleHoleColor = bgArgb
                                circleRadius    = 2.5f
                                circleHoleRadius = 1.0f
                                setDrawValues(false)
                                mode            = LineDataSet.Mode.LINEAR
                                setDrawFilled(true)
                                fillColor       = accentArgb
                                fillAlpha       = 20
                                // ── Task 1(b): disable crosshair lines; use red circle instead ──
                                highLightColor      = AndroidColor.TRANSPARENT   // hides highlight line
                                highlightLineWidth  = 0f
                                isHighlightEnabled  = true
                                setDrawHighlightIndicators(false)  // no vertical/horizontal lines
                            }

                            chart.data = LineData(dataset)
                            chart.data.notifyDataChanged()
                            chart.notifyDataSetChanged()
                            chart.fitScreen()
                            chart.setVisibleXRangeMaximum(Float.MAX_VALUE)

                            // ── Custom renderer: draws a red circle on the highlighted entry ──
                            chart.renderer = object : com.github.mikephil.charting.renderer.LineChartRenderer(
                                chart, chart.animator, chart.viewPortHandler
                            ) {
                                private val selectedPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                    style = android.graphics.Paint.Style.FILL
                                    color = AndroidColor.RED
                                }
                                private val selectedBorderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                    style = android.graphics.Paint.Style.STROKE
                                    color = AndroidColor.WHITE
                                    strokeWidth = 2f
                                }

                                override fun drawHighlighted(
                                    c: android.graphics.Canvas?,
                                    indices: Array<out Highlight>?
                                ) {
                                    c ?: return
                                    indices ?: return
                                    val data = mChart.data ?: return
                                    for (high in indices) {
                                        val set = data.getDataSetByIndex(high.dataSetIndex) ?: continue
                                        val e = data.getEntryForHighlight(high) ?: continue
                                        val pix = mChart.getTransformer(set.axisDependency)
                                            .getPixelForValues(e.x, e.y)
                                        val x = pix.x.toFloat()
                                        val y = pix.y.toFloat()
                                        // Outer white ring
                                        c.drawCircle(x, y, 14f, selectedBorderPaint)
                                        // Red fill
                                        c.drawCircle(x, y, 12f, selectedPaint)
                                    }
                                }
                            }

                            // ── Value selection: tap ──────────────────────────
                            val selectionListener = object : OnChartValueSelectedListener {
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
                            chart.setOnChartValueSelectedListener(selectionListener)

                            // ── Gesture listener: drag updates highlight ───────
                            chart.onChartGestureListener = object : OnChartGestureListener {
                                override fun onChartGestureStart(me: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?) {}
                                override fun onChartGestureEnd(me: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?) {}
                                override fun onChartLongPressed(me: MotionEvent?) {}
                                override fun onChartDoubleTapped(me: MotionEvent?) {}
                                override fun onChartSingleTapped(me: MotionEvent?) {}
                                override fun onChartFling(me1: MotionEvent?, me2: MotionEvent?, velocityX: Float, velocityY: Float) {}
                                override fun onChartScale(me: MotionEvent?, scaleX: Float, scaleY: Float) {}
                                override fun onChartTranslate(me: MotionEvent?, dX: Float, dY: Float) {
                                    me ?: return
                                    val h = chart.getHighlightByTouchPoint(me.x, me.y)
                                    if (h != null) {
                                        chart.highlightValue(h, false)
                                        val e = chart.data?.getEntryForHighlight(h)
                                        val idx = e?.data as? Int ?: return
                                        if (idx in points.indices) {
                                            selectedPoint = points[idx]
                                            selectedIndex = idx
                                        }
                                    }
                                }
                            }

                            chart.invalidate()
                        }
                    )

                    // ── OSM Map (Tasks 2 & 3) — shown only when GPS data present ─
                    if (hasGps) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "  LOCATION TRACE",
                            fontSize = 10.sp,
                            color = EnactOnSurfaceDim,
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )

                        AndroidView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(340.dp)
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.apply {
                                        javaScriptEnabled   = true
                                        domStorageEnabled   = true
                                        cacheMode           = WebSettings.LOAD_DEFAULT
                                        setSupportZoom(true)
                                        builtInZoomControls = true
                                        displayZoomControls = false
                                    }
                                    webViewClient = WebViewClient()
                                    setBackgroundColor(AndroidColor.TRANSPARENT)

                                    // Build the polyline coords JSON for the cyan trace
                                    val coordsJson = gpsPoints.joinToString(",") {
                                        "[${it.latitude},${it.longitude}]"
                                    }

                                    // Centre map on the mean of all GPS points
                                    val centLat = gpsPoints.mapNotNull { it.latitude }.average()
                                    val centLon = gpsPoints.mapNotNull { it.longitude }.average()

                                    val html = buildOsmHtml(centLat, centLon, coordsJson)
                                    loadDataWithBaseURL(
                                        "https://tile.openstreetmap.org/",  // allow OSM tile fetches
                                        html, "text/html", "UTF-8", null
                                    )
                                    mapWebView = this
                                }
                            },
                            update = { /* map is driven via JS calls in LaunchedEffect */ }
                        )
                    }
                }
            }
        }
    }
}

// ── OSM map HTML — Leaflet.js via CDN ─────────────────────────────────────────
private fun buildOsmHtml(centLat: Double, centLon: Double, coordsJson: String): String = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width,initial-scale=1.0">
<link rel="stylesheet"
  href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<style>
  html,body,#map { margin:0; padding:0; width:100%; height:100%; background:#1a1f1a; }
</style>
</head>
<body>
<div id="map"></div>
<script>
var map = L.map('map', {
  center: [$centLat, $centLon],
  zoom: 16,
  zoomControl: true
});

L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
  attribution: '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
  maxZoom: 19
}).addTo(map);

// Cyan trace — all measurement positions
var coords = [$coordsJson];
var trace = L.polyline(coords, {
  color: '#00e5ff',
  weight: 2.5,
  opacity: 0.85
}).addTo(map);

// Small cyan dots at every measurement point
coords.forEach(function(c) {
  L.circleMarker(c, {
    radius: 3,
    color: '#00e5ff',
    fillColor: '#00e5ff',
    fillOpacity: 0.7,
    weight: 1
  }).addTo(map);
});

// Red circle for selected measurement (hidden until JS call)
var selectedMarker = L.circleMarker([0,0], {
  radius: 10,
  color: '#ffffff',
  fillColor: '#ff1744',
  fillOpacity: 0.9,
  weight: 2
});
var selectedVisible = false;

function moveSelected(lat, lon) {
  selectedMarker.setLatLng([lat, lon]);
  if (!selectedVisible) {
    selectedMarker.addTo(map);
    selectedVisible = true;
  }
  map.panTo([lat, lon], {animate: true, duration: 0.3});
}

function clearSelected() {
  if (selectedVisible) {
    map.removeLayer(selectedMarker);
    selectedVisible = false;
  }
}

// Fit map to the full trace on load
if (coords.length > 0) { map.fitBounds(trace.getBounds(), {padding: [16,16]}); }
</script>
</body>
</html>
""".trimIndent()

@Composable
private fun StatChip(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 9.sp, color = color.copy(alpha = 0.7f),
            letterSpacing = 0.8.sp, fontWeight = FontWeight.SemiBold)
        Text(value, fontSize = 12.sp, color = color, fontWeight = FontWeight.Medium)
    }
}
