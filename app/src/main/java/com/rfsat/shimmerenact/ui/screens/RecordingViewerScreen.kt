package com.rfsat.shimmerenact.ui.screens

import android.graphics.Color as AndroidColor
import android.view.MotionEvent
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
                val result       = mutableListOf<CsvPoint>()
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
                            val cols    = line.split(",")
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

                        val v   = cols.getOrNull(valCol)?.trim()?.toDoubleOrNull() ?: continue
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

    // ── Derived state ──────────────────────────────────────────────────────────
    val stats = remember(points) {
        if (points.isEmpty()) null else {
            val values = points.map { it.value }
            Triple(values.min(), values.average(), values.max())
        }
    }

    // Chart entries — rebuilt only when points change, never on recomposition
    val entries = remember(points) {
        if (points.isEmpty()) emptyList()
        else {
            val t0 = points.first().timestampMs
            points.mapIndexed { i, pt ->
                Entry((pt.timestampMs - t0) / 1000f, pt.value.toFloat(), i)
            }
        }
    }

    val gpsPoints = remember(points) { points.filter { it.latitude != null && it.longitude != null } }
    val hasGps    = gpsPoints.isNotEmpty()

    // Stable ARGB — computed once, not inside update lambdas
    val accentArgb       = EnactGreen.toArgb()
    val onSurfaceDimArgb = EnactOnSurfaceDim.toArgb()
    val bgArgb           = EnactDark.toArgb()

    // Shared mutable ref so the chart object can be accessed from listeners without
    // holding a stale Compose snapshot reference.
    val chartRef = remember { mutableStateOf<LineChart?>(null) }

    // WebView ref for JS-driven map updates
    val mapWebView = remember { mutableStateOf<WebView?>(null) }

    // OSM HTML state — built once per GPS dataset, held in MutableState so the
    // WebView update lambda can always read the current value. factory() is called
    // only once by Compose; update() runs on every recompose, so we load HTML there.
    val osmHtmlState = remember { mutableStateOf("") }
    LaunchedEffect(gpsPoints) {
        if (gpsPoints.isEmpty()) return@LaunchedEffect
        val coordsJson = gpsPoints.joinToString(",") { "[${it.latitude},${it.longitude}]" }
        val centLat    = gpsPoints.mapNotNull { it.latitude  }.average()
        val centLon    = gpsPoints.mapNotNull { it.longitude }.average()
        osmHtmlState.value = buildOsmHtml(centLat, centLon, coordsJson)
    }

    // ── Push selected-point marker to map ──────────────────────────────────────
    // Runs whenever selectedIndex changes — does NOT trigger a chart recomposition.
    LaunchedEffect(selectedIndex) {
        val wv = mapWebView.value ?: return@LaunchedEffect
        val sp = selectedPoint
        if (sp?.latitude != null && sp.longitude != null) {
            wv.post { wv.evaluateJavascript("moveSelected(${sp.latitude},${sp.longitude});", null) }
        } else {
            wv.post { wv.evaluateJavascript("clearSelected();", null) }
        }
    }

    // ── Push new dataset to chart when entries change ──────────────────────────
    // Separated from the factory so that zoom state is preserved across
    // recompositions that don't change the underlying data.
    LaunchedEffect(entries) {
        val chart = chartRef.value ?: return@LaunchedEffect
        if (entries.isEmpty()) {
            chart.data = null
            chart.invalidate()
            return@LaunchedEffect
        }

        val drawCircles = entries.size < 5000
        val dataset = LineDataSet(entries, "").apply {
            color            = accentArgb
            lineWidth        = if (entries.size > 2000) 1f else 1.5f
            setDrawCircles(drawCircles)
            setCircleColor(accentArgb)
            circleHoleColor  = bgArgb
            circleRadius     = 2.5f
            circleHoleRadius = 1.0f
            setDrawValues(false)
            mode             = LineDataSet.Mode.LINEAR
            setDrawFilled(true)
            fillColor        = accentArgb
            fillAlpha        = 20
            // No crosshair lines — selection is shown by the custom renderer
            highLightColor   = AndroidColor.TRANSPARENT
            highlightLineWidth  = 0f
            isHighlightEnabled  = true
            setDrawHighlightIndicators(false)
        }

        chart.data = LineData(dataset)
        chart.data.notifyDataChanged()
        chart.notifyDataSetChanged()
        // fitScreen() only on first load — zoom state is preserved for subsequent
        // recompositions because this LaunchedEffect only re-runs when entries change
        chart.fitScreen()
        chart.invalidate()
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
                        Modifier.fillMaxWidth().height(300.dp),
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
                        Modifier.fillMaxWidth().height(300.dp).padding(24.dp),
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
                        Modifier.fillMaxWidth().height(300.dp),
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

                    // Cursor readout
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (selectedPoint != null) EnactGreen.copy(alpha = 0.10f)
                                else EnactDark
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
                    // factory: create and fully configure the chart once.
                    // update: intentionally empty — all data pushes happen in
                    //         LaunchedEffect(entries) to avoid resetting zoom on recompose.
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .padding(bottom = 4.dp, start = 4.dp, end = 4.dp),
                        factory = { ctx ->
                            LineChart(ctx).apply {
                                description.isEnabled     = false
                                legend.isEnabled          = false
                                setBackgroundColor(bgArgb)
                                setDrawGridBackground(false)
                                setTouchEnabled(true)
                                isDragEnabled             = true
                                isScaleXEnabled           = true
                                isScaleYEnabled           = true
                                setPinchZoom(true)         // pinch-to-zoom enabled
                                setDrawMarkers(false)
                                isHighlightPerDragEnabled = true
                                setNoDataText("No data")

                                xAxis.apply {
                                    position             = XAxis.XAxisPosition.BOTTOM
                                    setDrawGridLines(true)
                                    gridColor            = AndroidColor.argb(40, 255, 255, 255)
                                    textColor            = onSurfaceDimArgb
                                    textSize             = 9f
                                    labelCount           = 6
                                    granularity          = 0.001f
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

                                // ── Custom renderer: large red filled circle on selected point ──
                                // Paints are allocated once here in factory, not on every draw call.
                                val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                    style = android.graphics.Paint.Style.FILL
                                    color = AndroidColor.rgb(220, 0, 0)   // solid red
                                }
                                val ringPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                    style       = android.graphics.Paint.Style.STROKE
                                    color       = AndroidColor.WHITE
                                    strokeWidth = 3f
                                }
                                renderer = object : com.github.mikephil.charting.renderer.LineChartRenderer(
                                    this, animator, viewPortHandler
                                ) {
                                    override fun drawHighlighted(
                                        c: android.graphics.Canvas?,
                                        indices: Array<out Highlight>?
                                    ) {
                                        c ?: return
                                        indices ?: return
                                        val data = mChart.data ?: return
                                        for (high in indices) {
                                            val set = data.getDataSetByIndex(high.dataSetIndex) ?: continue
                                            val e   = data.getEntryForHighlight(high) ?: continue
                                            val pix = mChart.getTransformer(set.axisDependency)
                                                          .getPixelForValues(e.x, e.y)
                                            val x = pix.x.toFloat()
                                            val y = pix.y.toFloat()
                                            // White ring (drawn first, underneath)
                                            c.drawCircle(x, y, 22f, ringPaint)
                                            // Solid red fill
                                            c.drawCircle(x, y, 18f, fillPaint)
                                        }
                                    }
                                }

                                // ── Value selection listener ──────────────────
                                setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                                    override fun onValueSelected(e: Entry?, h: Highlight?) {
                                        val idx = e?.data as? Int ?: return
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

                                // ── Gesture listener: continuous drag tracing ──
                                onChartGestureListener = object : OnChartGestureListener {
                                    override fun onChartGestureStart(me: MotionEvent?, g: ChartTouchListener.ChartGesture?) {}
                                    override fun onChartGestureEnd(me: MotionEvent?, g: ChartTouchListener.ChartGesture?) {}
                                    override fun onChartLongPressed(me: MotionEvent?) {}
                                    override fun onChartDoubleTapped(me: MotionEvent?) {}
                                    override fun onChartSingleTapped(me: MotionEvent?) {}
                                    override fun onChartFling(me1: MotionEvent?, me2: MotionEvent?, vX: Float, vY: Float) {}
                                    override fun onChartScale(me: MotionEvent?, sX: Float, sY: Float) {}
                                    override fun onChartTranslate(me: MotionEvent?, dX: Float, dY: Float) {
                                        me ?: return
                                        val h = getHighlightByTouchPoint(me.x, me.y)
                                        if (h != null) {
                                            highlightValue(h, false)
                                            val idx = data?.getEntryForHighlight(h)?.data as? Int ?: return
                                            if (idx in points.indices) {
                                                selectedPoint = points[idx]
                                                selectedIndex = idx
                                            }
                                        }
                                    }
                                }

                                // Store reference so LaunchedEffect can push data into it
                                chartRef.value = this
                            }
                        },
                        update = { /* intentionally empty — zoom state must not be reset here */ }
                    )

                    // ── OSM Location Trace Map ────────────────────────────────
                    // Shown only when the CSV contains GPS columns.
                    if (hasGps) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "  LOCATION TRACE",
                            fontSize   = 10.sp,
                            color      = EnactOnSurfaceDim,
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier   = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )

                        AndroidView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(360.dp)
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.apply {
                                        javaScriptEnabled    = true
                                        domStorageEnabled    = true
                                        mixedContentMode     = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                        cacheMode            = WebSettings.LOAD_DEFAULT
                                        setSupportZoom(true)
                                        builtInZoomControls  = true
                                        displayZoomControls  = false
                                        useWideViewPort      = true
                                        loadWithOverviewMode = true
                                    }
                                    webViewClient = WebViewClient()
                                    setBackgroundColor(AndroidColor.rgb(0x1a, 0x1f, 0x1a))
                                    mapWebView.value = this
                                }
                            },
                            update = { wv ->
                                // update() runs on every recompose, so it always has the
                                // current osmHtmlState value — load only when non-empty and
                                // only when the content has actually changed.
                                val html = osmHtmlState.value
                                if (html.isNotEmpty() && wv.tag != html.hashCode()) {
                                    wv.tag = html.hashCode()
                                    wv.loadDataWithBaseURL(
                                        "https://www.openstreetmap.org/",
                                        html,
                                        "text/html",
                                        "UTF-8",
                                        null
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

// ── OSM map HTML — Leaflet.js 1.9.4 via unpkg CDN ────────────────────────────
private fun buildOsmHtml(centLat: Double, centLon: Double, coordsJson: String): String = """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<style>
  html, body, #map {
    margin: 0; padding: 0;
    width: 100%; height: 100%;
    background: #1a1f1a;
    font-family: sans-serif;
  }
</style>
</head>
<body>
<div id="map"></div>
<script>
  var map = L.map('map', {
    center: [$centLat, $centLon],
    zoom: 16
  });

  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '\u00a9 <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
    maxZoom: 19
  }).addTo(map);

  var coords = [$coordsJson];

  // Cyan polyline connecting all measurement positions
  var trace = L.polyline(coords, {
    color: '#00e5ff', weight: 2.5, opacity: 0.85
  }).addTo(map);

  // Small cyan dot at every individual measurement position
  coords.forEach(function(c) {
    L.circleMarker(c, {
      radius: 3, color: '#00e5ff',
      fillColor: '#00e5ff', fillOpacity: 1.0, weight: 1
    }).addTo(map);
  });

  // Red circle for the currently selected measurement (hidden initially)
  var selMarker = L.circleMarker([0, 0], {
    radius: 10, color: '#ffffff',
    fillColor: '#dd0000', fillOpacity: 0.92, weight: 3
  });
  var selVisible = false;

  function moveSelected(lat, lon) {
    selMarker.setLatLng([lat, lon]);
    if (!selVisible) { selMarker.addTo(map); selVisible = true; }
    map.panTo([lat, lon], { animate: true, duration: 0.25 });
  }

  function clearSelected() {
    if (selVisible) { map.removeLayer(selMarker); selVisible = false; }
  }

  // Fit view to the full trace on load
  if (coords.length > 1) {
    map.fitBounds(trace.getBounds(), { padding: [16, 16] });
  }
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
