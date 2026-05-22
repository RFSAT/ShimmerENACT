package com.rfsat.shimmerenact.ui.screens

import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// ── osmdroid canvas overlays ────────────────────────────────────────────────
// Custom Overlay subclasses avoid all IGeoPoint generics issues.
private class DotsOverlay(
    private val points: List<GeoPoint>,
    private val paint: Paint
) : Overlay() {
    private val reuse = android.graphics.Point()
    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val projection = mapView.projection
        for (pt in points) { projection.toPixels(pt, reuse); canvas.drawCircle(reuse.x.toFloat(), reuse.y.toFloat(), 8f, paint) }
    }
}

private class SelectionOverlay(private val paint: Paint, private val ringPaint: Paint) : Overlay() {
    var point: GeoPoint? = null
    private val reuse = android.graphics.Point()
    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val p = point ?: return
        val projection = mapView.projection
        projection.toPixels(p, reuse)
        val x = reuse.x.toFloat(); val y = reuse.y.toFloat()
        canvas.drawCircle(x, y, 22f, ringPaint)
        canvas.drawCircle(x, y, 16f, paint)
    }
}

// ── Data model ──────────────────────────────────────────────────────────────
data class CsvPoint(
    val timestampMs: Long,
    val value: Double,
    val latitude: Double? = null,
    val longitude: Double? = null
)

// One fully-loaded signal: file metadata + parsed data points
data class SignalData(
    val file: RecordingFile,
    val points: List<CsvPoint>,
    val color: Color
) {
    val label: String get() = file.signalDisplayName.ifBlank { file.name }
    val unit:  String get() = file.signalUnit
}

// ── Main composable ─────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingViewerScreen(
    // Single-file entry point (backward-compatible)
    recordingFile: RecordingFile,
    onBack: () -> Unit
) = RecordingViewerScreen(
    files   = listOf(recordingFile),
    title   = recordingFile.signalDisplayName.ifBlank { recordingFile.name },
    onBack  = onBack
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingViewerScreen(
    // Multi-file entry point
    files:  List<RecordingFile>,
    title:  String,
    onBack: () -> Unit
) {
    // ── Parse each file ──────────────────────────────────────────────────────
    var signals    by remember { mutableStateOf<List<SignalData>>(emptyList()) }
    var loadError  by remember { mutableStateOf<String?>(null) }
    var isLoading  by remember { mutableStateOf(true) }

    val isoFmt = remember {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }

    LaunchedEffect(files) {
        isLoading  = true
        loadError  = null
        signals    = emptyList()
        try {
            val loaded = withContext(Dispatchers.IO) {
                files.mapIndexed { idx, rf ->
                    val color = ChartColors[idx % ChartColors.size]
                    val pts   = parseCsv(rf.path, isoFmt)
                    SignalData(rf, pts, color)
                }
            }
            signals = loaded
        } catch (e: Exception) {
            loadError = e.message ?: "Failed to load files"
        } finally {
            isLoading = false
        }
    }

    // ── Visible signals (user toggle) ────────────────────────────────────────
    // Default: all signals visible
    var visibleMask by remember(signals) {
        mutableStateOf(signals.map { true })
    }
    val visibleSignals = remember(signals, visibleMask) {
        signals.filterIndexed { i, _ -> visibleMask.getOrElse(i) { true } }
    }

    // ── Selection state ──────────────────────────────────────────────────────
    // selectedTimeMs: the timestamp the user tapped — null if nothing selected
    var selectedTimeMs by remember { mutableStateOf<Long?>(null) }

    // For each visible signal, find the point nearest to selectedTimeMs
    val selectedPoints: List<Pair<SignalData, CsvPoint>> = remember(selectedTimeMs, visibleSignals) {
        val t = selectedTimeMs ?: return@remember emptyList()
        visibleSignals.mapNotNull { sig ->
            sig.points.minByOrNull { kotlin.math.abs(it.timestampMs - t) }
                ?.let { sig to it }
        }
    }

    // GPS points from the first signal that has them
    val gpsPoints = remember(signals) {
        signals.firstOrNull { s -> s.points.any { it.latitude != null } }
            ?.points?.filter { it.latitude != null && it.longitude != null }
            ?: emptyList()
    }
    val hasGps = gpsPoints.isNotEmpty()

    // Selected GPS point — nearest to selected time
    val selectedGps: CsvPoint? = remember(selectedTimeMs, gpsPoints) {
        val t = selectedTimeMs ?: return@remember null
        gpsPoints.minByOrNull { kotlin.math.abs(it.timestampMs - t) }
    }

    // ── Stats per visible signal ─────────────────────────────────────────────
    val statsMap = remember(visibleSignals) {
        visibleSignals.associate { sig ->
            sig to if (sig.points.isEmpty()) null
            else { val v = sig.points.map { it.value }; Triple(v.min(), v.average(), v.max()) }
        }
    }

    // ── Chart entries: time origin = earliest timestamp across all visible signals
    val chartOriginMs: Long = remember(visibleSignals) {
        visibleSignals.flatMap { it.points }.minOfOrNull { it.timestampMs } ?: 0L
    }
    val entriesMap: Map<SignalData, List<Entry>> = remember(visibleSignals, chartOriginMs) {
        visibleSignals.associate { sig ->
            sig to sig.points.map { pt ->
                Entry((pt.timestampMs - chartOriginMs) / 1000f, pt.value.toFloat(),
                      pt.timestampMs)   // store timestamp as data payload for selection
            }
        }
    }

    // ── MPAndroidChart ref ───────────────────────────────────────────────────
    val chartRef      = remember { mutableStateOf<LineChart?>(null) }
    val selOverlayRef = remember { mutableStateOf<SelectionOverlay?>(null) }
    val mapViewRef    = remember { mutableStateOf<MapView?>(null) }

    // Stable ARGB
    val bgArgb           = EnactDark.toArgb()
    val onSurfaceDimArgb = EnactOnSurfaceDim.toArgb()

    // ── Push map selection marker ────────────────────────────────────────────
    LaunchedEffect(selectedGps) {
        val mv  = mapViewRef.value ?: return@LaunchedEffect
        val sel = selOverlayRef.value ?: return@LaunchedEffect
        val sp  = selectedGps
        if (sp?.latitude != null && sp.longitude != null) {
            val gp = GeoPoint(sp.latitude, sp.longitude)
            sel.point = gp
            mv.controller.setCenter(gp)
        } else {
            sel.point = null
        }
        mv.invalidate()
    }

    // ── Rebuild chart datasets when visible signals or entries change ─────────
    LaunchedEffect(entriesMap, visibleSignals) {
        val chart = chartRef.value ?: return@LaunchedEffect
        if (entriesMap.isEmpty() || visibleSignals.isEmpty()) {
            chart.data = null; chart.invalidate(); return@LaunchedEffect
        }

        val datasets = visibleSignals.mapNotNull { sig ->
            val entries = entriesMap[sig] ?: return@mapNotNull null
            if (entries.isEmpty()) return@mapNotNull null
            val argb = sig.color.toArgb()
            LineDataSet(entries, sig.label).apply {
                color            = argb
                lineWidth        = if (entries.size > 2000) 1f else 1.5f
                val drawCircles  = entries.size < 5000
                setDrawCircles(drawCircles)
                setCircleColor(argb)
                circleHoleColor  = bgArgb
                circleRadius     = 2.5f
                circleHoleRadius = 1.0f
                setDrawValues(false)
                mode             = LineDataSet.Mode.LINEAR
                setDrawFilled(false)   // fill off for multi-signal — too visually noisy
                highLightColor      = AndroidColor.TRANSPARENT
                highlightLineWidth  = 0f
                isHighlightEnabled  = true
                setDrawHighlightIndicators(false)
            }
        }

        chart.data = LineData(datasets)
        chart.data.notifyDataChanged()
        chart.notifyDataSetChanged()
        chart.fitScreen()
        chart.invalidate()
    }

    // ── Scaffold ─────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, color = EnactOnSurface, fontSize = 15.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            buildString {
                                append("${files.size} signal${if (files.size != 1) "s" else ""}")
                                val totalPts = signals.sumOf { it.points.size }
                                if (totalPts > 0) append("  •  $totalPts pts")
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

        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = EnactGreen)
                            Spacer(Modifier.height(12.dp))
                            Text("Loading…", color = EnactOnSurfaceDim)
                        }
                    }
                }

                loadError != null -> {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.ErrorOutline, null, tint = EnactError, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("Failed to load", color = EnactError, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(loadError ?: "", color = EnactOnSurfaceDim, fontSize = 12.sp)
                        }
                    }
                }

                signals.isEmpty() || signals.all { it.points.isEmpty() } -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.BarChart, null, tint = EnactGreen.copy(alpha = 0.3f), modifier = Modifier.size(56.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("No data in file${if (files.size > 1) "s" else ""}", color = EnactOnSurfaceDim)
                        }
                    }
                }

                else -> {
                    // ── Upper scrollable panel ────────────────────────────────
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // ── Signal selection chips ────────────────────────────
                        // Only shown when there are 2+ signals
                        if (signals.size > 1) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(EnactDarkMid)
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                signals.forEachIndexed { idx, sig ->
                                    val visible = visibleMask.getOrElse(idx) { true }
                                    SignalChip(
                                        label   = sig.label,
                                        color   = sig.color,
                                        checked = visible,
                                        onToggle = {
                                            // Build a new list (immutable state)
                                            val newMask = visibleMask.toMutableList()
                                            // Prevent deselecting the last visible signal
                                            if (visible && newMask.count { it } <= 1) return@SignalChip
                                            newMask[idx] = !visible
                                            visibleMask = newMask
                                        }
                                    )
                                }
                            }
                        }

                        // ── Stats strip ───────────────────────────────────────
                        if (visibleSignals.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(EnactDarkMid)
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                visibleSignals.forEach { sig ->
                                    val st = statsMap[sig]
                                    if (st != null) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                sig.label,
                                                fontSize = 8.sp,
                                                color = sig.color.copy(alpha = 0.7f),
                                                letterSpacing = 0.5.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                StatChip("MIN",  "%.3f".format(st.first),  sig.color)
                                                StatChip("MEAN", "%.3f".format(st.second), sig.color)
                                                StatChip("MAX",  "%.3f".format(st.third),  sig.color)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // ── Cursor readout ────────────────────────────────────
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .background(
                                    if (selectedPoints.isNotEmpty()) EnactGreen.copy(alpha = 0.10f)
                                    else EnactDark
                                )
                        ) {
                            if (selectedPoints.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(Icons.Default.MyLocation, null,
                                        tint = EnactGreen, modifier = Modifier.size(13.dp))
                                    // Time from the first selected point
                                    Text(
                                        timeFmt.format(Date(selectedPoints.first().second.timestampMs)),
                                        fontSize = 11.sp, color = EnactOnSurface
                                    )
                                    selectedPoints.forEach { (sig, pt) ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .background(sig.color, RoundedCornerShape(50))
                                            )
                                            Text(
                                                "%.4f".format(pt.value) +
                                                    if (sig.unit.isNotBlank()) " ${sig.unit}" else "",
                                                fontSize = 12.sp, color = sig.color,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text("Tap or drag on graph to trace values",
                                        fontSize = 12.sp, color = EnactOnSurfaceDim.copy(alpha = 0.4f))
                                }
                            }
                        }

                        // ── MPAndroidChart ────────────────────────────────────
                        AndroidView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .padding(bottom = 4.dp, start = 4.dp, end = 4.dp),
                            factory = { ctx ->
                                LineChart(ctx).apply {
                                    description.isEnabled     = false
                                    legend.isEnabled          = signals.size > 1  // legend meaningful only for multi-signal
                                    legend.textColor          = onSurfaceDimArgb
                                    legend.textSize           = 9f
                                    setBackgroundColor(bgArgb)
                                    setDrawGridBackground(false)
                                    setTouchEnabled(true)
                                    isDragEnabled             = true
                                    isScaleXEnabled           = true
                                    isScaleYEnabled           = true
                                    setPinchZoom(false)        // independent axis zoom
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

                                    // Custom renderer: red circle on selected point
                                    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                        style = Paint.Style.FILL
                                        color = AndroidColor.rgb(220, 0, 0)
                                    }
                                    val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                        style       = Paint.Style.STROKE
                                        color       = AndroidColor.WHITE
                                        strokeWidth = 3f
                                    }
                                    renderer = object : com.github.mikephil.charting.renderer.LineChartRenderer(
                                        this, animator, viewPortHandler
                                    ) {
                                        override fun drawHighlighted(c: Canvas?, indices: Array<out Highlight>?) {
                                            c ?: return; indices ?: return
                                            val data = mChart.data ?: return
                                            for (high in indices) {
                                                val set = data.getDataSetByIndex(high.dataSetIndex) ?: continue
                                                val e   = data.getEntryForHighlight(high) ?: continue
                                                val pix = mChart.getTransformer(set.axisDependency)
                                                              .getPixelForValues(e.x, e.y)
                                                val x = pix.x.toFloat(); val y = pix.y.toFloat()
                                                c.drawCircle(x, y, 22f, ringPaint)
                                                c.drawCircle(x, y, 18f, fillPaint)
                                            }
                                        }
                                    }

                                    // Selection listener — extracts timestamp from Entry.data
                                    setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                                        override fun onValueSelected(e: Entry?, h: Highlight?) {
                                            selectedTimeMs = e?.data as? Long
                                        }
                                        override fun onNothingSelected() { selectedTimeMs = null }
                                    })

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
                                                selectedTimeMs = data?.getEntryForHighlight(h)?.data as? Long
                                            }
                                        }
                                    }
                                    chartRef.value = this
                                }
                            },
                            update = { /* data pushed via LaunchedEffect(entriesMap) */ }
                        )
                    } // end scrollable upper panel

                    // ── Map ────────────────────────────────────────────────────
                    if (hasGps) {
                        Divider(color = EnactDarkMid, thickness = 1.dp)
                        Row(
                            modifier = Modifier.fillMaxWidth().background(EnactDarkMid)
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.LocationOn, null, tint = EnactGreen, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("LOCATION TRACE", fontSize = 10.sp, color = EnactOnSurfaceDim,
                                letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Box(
                            modifier = Modifier.fillMaxWidth().requiredHeight(280.dp).clipToBounds()
                        ) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { ctx ->
                                    Configuration.getInstance().userAgentValue =
                                        "ShimmerENACT/2.0 (ENACT Project; Horizon Europe 101157151)"
                                    val geoPoints = gpsPoints.map { GeoPoint(it.latitude!!, it.longitude!!) }
                                    val dotPaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = AndroidColor.rgb(0x00, 0xe5, 0xff) }
                                    val selFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = AndroidColor.rgb(220, 0, 0) }
                                    val selRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = AndroidColor.WHITE; strokeWidth = 3f }

                                    MapView(ctx).apply {
                                        setTileSource(TileSourceFactory.MAPNIK)
                                        setMultiTouchControls(true)
                                        isClickable = true
                                        val polyline = Polyline().apply {
                                            setPoints(geoPoints)
                                            outlinePaint.color      = AndroidColor.rgb(0x00, 0xe5, 0xff)
                                            outlinePaint.strokeWidth = 4f
                                            outlinePaint.isAntiAlias = true
                                        }
                                        overlays.add(polyline)
                                        overlays.add(DotsOverlay(geoPoints, dotPaint))
                                        val selOverlay = SelectionOverlay(selFillPaint, selRingPaint)
                                        overlays.add(selOverlay)
                                        selOverlayRef.value = selOverlay
                                        val centre = GeoPoint(
                                            gpsPoints.mapNotNull { it.latitude  }.average(),
                                            gpsPoints.mapNotNull { it.longitude }.average()
                                        )
                                        controller.setCenter(centre)
                                        controller.setZoom(16.0)
                                        post {
                                            val north = geoPoints.maxOf { it.latitude }
                                            val south = geoPoints.minOf { it.latitude }
                                            val east  = geoPoints.maxOf { it.longitude }
                                            val west  = geoPoints.minOf { it.longitude }
                                            zoomToBoundingBox(org.osmdroid.util.BoundingBox(north, east, south, west), true, 40)
                                        }
                                        mapViewRef.value = this
                                    }
                                },
                                update = { /* driven via LaunchedEffect(selectedGps) */ }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── CSV parser (shared) ──────────────────────────────────────────────────────
private fun parseCsv(path: String, isoFmt: SimpleDateFormat): List<CsvPoint> {
    val file = File(path)
    if (!file.exists()) throw Exception("File not found:\n$path")
    val result       = mutableListOf<CsvPoint>()
    var headerParsed = false
    var tsCol = 1; var valCol = 2; var latCol = -1; var lonCol = -1

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
                headerParsed = true; continue
            }
            val cols = line.split(",")
            if (cols.size <= valCol) continue
            val tsMs: Long = (if (tsCol >= 0) cols.getOrNull(tsCol)?.trim()?.toLongOrNull() else null)
                ?: cols.getOrNull(0)?.trim()?.let { runCatching { isoFmt.parse(it)?.time }.getOrNull() }
                ?: continue
            val v   = cols.getOrNull(valCol)?.trim()?.toDoubleOrNull() ?: continue
            val lat = if (latCol >= 0) cols.getOrNull(latCol)?.trim()?.toDoubleOrNull() else null
            val lon = if (lonCol >= 0) cols.getOrNull(lonCol)?.trim()?.toDoubleOrNull() else null
            result.add(CsvPoint(tsMs, v, lat, lon))
        }
    }
    return result
}

// ── Signal selection chip ────────────────────────────────────────────────────
@Composable
private fun SignalChip(
    label:    String,
    color:    Color,
    checked:  Boolean,
    onToggle: () -> Unit
) {
    val bg     = if (checked) color.copy(alpha = 0.18f) else EnactDark
    val border = if (checked) color.copy(alpha = 0.7f)  else EnactOnSurfaceDim.copy(alpha = 0.25f)
    val text   = if (checked) color                      else EnactOnSurfaceDim.copy(alpha = 0.5f)

    Surface(
        onClick    = onToggle,
        shape      = RoundedCornerShape(20.dp),
        color      = bg,
        border     = BorderStroke(1.dp, border),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(modifier = Modifier.size(8.dp).background(if (checked) color else border, RoundedCornerShape(50)))
            Text(label, fontSize = 11.sp, color = text, maxLines = 1)
        }
    }
}

// ── Stat chip ────────────────────────────────────────────────────────────────
@Composable
private fun StatChip(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 9.sp, color = color.copy(alpha = 0.7f),
            letterSpacing = 0.8.sp, fontWeight = FontWeight.SemiBold)
        Text(value, fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium)
    }
}
