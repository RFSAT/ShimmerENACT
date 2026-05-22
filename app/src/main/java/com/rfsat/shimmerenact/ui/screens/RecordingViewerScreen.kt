package com.rfsat.shimmerenact.ui.screens

import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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

// ── Custom overlay: draws a fixed-radius circle at every GPS point ─────────────
// Avoids all SimpleFastPointOverlay / IGeoPoint generics issues.
private class DotsOverlay(
    private val points: List<GeoPoint>,
    private val paint: Paint
) : Overlay() {
    private val reuse = android.graphics.Point()
    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val projection = mapView.projection
        for (pt in points) {
            projection.toPixels(pt, reuse)
            canvas.drawCircle(reuse.x.toFloat(), reuse.y.toFloat(), 8f, paint)
        }
    }
}

// ── Custom overlay: draws a single larger circle (selected measurement) ────────
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
    val context = LocalContext.current

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
                            if (tsCol >= 0) cols.getOrNull(tsCol)?.trim()?.toLongOrNull() else null
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
        if (points.isEmpty()) null
        else { val v = points.map { it.value }; Triple(v.min(), v.average(), v.max()) }
    }

    val entries = remember(points) {
        if (points.isEmpty()) emptyList()
        else {
            val t0 = points.first().timestampMs
            points.mapIndexed { i, pt -> Entry((pt.timestampMs - t0) / 1000f, pt.value.toFloat(), i) }
        }
    }

    val gpsPoints = remember(points) { points.filter { it.latitude != null && it.longitude != null } }
    val hasGps    = gpsPoints.isNotEmpty()

    // Stable ARGB colours
    val accentArgb       = EnactGreen.toArgb()
    val onSurfaceDimArgb = EnactOnSurfaceDim.toArgb()
    val bgArgb           = EnactDark.toArgb()

    val chartRef       = remember { mutableStateOf<LineChart?>(null) }
    val selOverlayRef  = remember { mutableStateOf<SelectionOverlay?>(null) }
    val mapViewRef     = remember { mutableStateOf<MapView?>(null) }

    // ── Move selected-point marker on the map ──────────────────────────────────
    LaunchedEffect(selectedIndex) {
        val mv  = mapViewRef.value ?: return@LaunchedEffect
        val sel = selOverlayRef.value ?: return@LaunchedEffect
        val sp  = selectedPoint
        if (sp?.latitude != null && sp.longitude != null) {
            val gp = GeoPoint(sp.latitude, sp.longitude)
            sel.point = gp
            mv.controller.setCenter(gp)   // instant — avoids repeated requestLayout during animation
        } else {
            sel.point = null
        }
        mv.invalidate()
    }

    // ── Push dataset to chart when entries change (preserves zoom) ─────────────
    LaunchedEffect(entries) {
        val chart = chartRef.value ?: return@LaunchedEffect
        if (entries.isEmpty()) { chart.data = null; chart.invalidate(); return@LaunchedEffect }

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
            highLightColor      = AndroidColor.TRANSPARENT
            highlightLineWidth  = 0f
            isHighlightEnabled  = true
            setDrawHighlightIndicators(false)
        }
        chart.data = LineData(dataset)
        chart.data.notifyDataChanged()
        chart.notifyDataSetChanged()
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
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = EnactGreen) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EnactDarkMid)
            )
        },
        containerColor = EnactDark
    ) { padding ->

        // Non-scrollable outer Column so the MapView gets a proper measured height
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
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
                            Icon(Icons.Default.ErrorOutline, null, tint = EnactError, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("Failed to load recording", color = EnactError, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(loadError ?: "", color = EnactOnSurfaceDim, fontSize = 12.sp)
                        }
                    }
                }

                points.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.BarChart, null,
                                tint = EnactGreen.copy(alpha = 0.3f), modifier = Modifier.size(56.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("No data in file", color = EnactOnSurfaceDim)
                        }
                    }
                }

                else -> {
                    // Upper scrollable panel: stats + readout + chart
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
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
                                .height(36.dp)   // fixed height — prevents layout shift on selection change
                                .background(
                                    if (selectedPoint != null) EnactGreen.copy(alpha = 0.10f) else EnactDark
                                )
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            if (selectedPoint != null) {
                                val pt = selectedPoint!!
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.MyLocation, null,
                                        tint = EnactGreen, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("t = ${timeFmt.format(Date(pt.timestampMs))}",
                                        fontSize = 12.sp, color = EnactOnSurface, modifier = Modifier.weight(1f))
                                    Text(
                                        "%.6f".format(pt.value) +
                                            if (recordingFile.signalUnit.isNotBlank()) "  ${recordingFile.signalUnit}" else "",
                                        fontSize = 13.sp, color = EnactGreen, fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("#${selectedIndex + 1}", fontSize = 11.sp, color = EnactOnSurfaceDim)
                                }
                            } else {
                                Text("Tap or drag on graph to trace value",
                                    fontSize = 12.sp, color = EnactOnSurfaceDim.copy(alpha = 0.4f))
                            }
                        }

                        // ── MPAndroidChart ─────────────────────────────────────
                        AndroidView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
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
                                    // Independent axis zoom: dominant gesture direction zooms only that axis
                                    setPinchZoom(false)
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

                                    // Custom renderer: large red circle on selected point
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

                                    setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                                        override fun onValueSelected(e: Entry?, h: Highlight?) {
                                            val idx = e?.data as? Int ?: return
                                            if (idx in points.indices) { selectedPoint = points[idx]; selectedIndex = idx }
                                        }
                                        override fun onNothingSelected() { selectedPoint = null; selectedIndex = -1 }
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
                                                val idx = data?.getEntryForHighlight(h)?.data as? Int ?: return
                                                if (idx in points.indices) { selectedPoint = points[idx]; selectedIndex = idx }
                                            }
                                        }
                                    }
                                    chartRef.value = this
                                }
                            },
                            update = { /* intentionally empty — zoom preserved via LaunchedEffect(entries) */ }
                        )
                    } // end scrollable upper panel

                    // ── osmdroid Location Trace Map ───────────────────────────
                    // Fixed-height block OUTSIDE the scroll container so MapView
                    // receives a proper measured height from the Android view system.
                    if (hasGps) {
                        Divider(color = EnactDarkMid, thickness = 1.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(EnactDarkMid)
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.LocationOn, null,
                                tint = EnactGreen, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("LOCATION TRACE", fontSize = 10.sp, color = EnactOnSurfaceDim,
                                letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
                        }

                        // Wrap MapView in a size-locked, clipped Box.
                        // requiredHeight + clipToBounds ensures osmdroid can never
                        // paint or resize beyond the allocated 320 dp, regardless
                        // of how many requestLayout() calls it makes internally.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .requiredHeight(320.dp)
                                .clipToBounds()
                        ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                Configuration.getInstance().userAgentValue =
                                    "ShimmerENACT/2.0 (ENACT Project; Horizon Europe 101157151)"

                                val geoPoints = gpsPoints.map { GeoPoint(it.latitude!!, it.longitude!!) }

                                val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    style = Paint.Style.FILL
                                    color = AndroidColor.rgb(0x00, 0xe5, 0xff)
                                }
                                val selFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    style = Paint.Style.FILL
                                    color = AndroidColor.rgb(220, 0, 0)
                                }
                                val selRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    style       = Paint.Style.STROKE
                                    color       = AndroidColor.WHITE
                                    strokeWidth = 3f
                                }

                                MapView(ctx).apply {
                                    setTileSource(TileSourceFactory.MAPNIK)
                                    setMultiTouchControls(true)
                                    isClickable = true

                                    // Cyan polyline
                                    val polyline = Polyline().apply {
                                        setPoints(geoPoints)
                                        outlinePaint.color       = AndroidColor.rgb(0x00, 0xe5, 0xff)
                                        outlinePaint.strokeWidth  = 4f
                                        outlinePaint.isAntiAlias  = true
                                    }
                                    overlays.add(polyline)

                                    // Cyan dots — custom canvas overlay, no IGeoPoint generics
                                    overlays.add(DotsOverlay(geoPoints, dotPaint))

                                    // Red selection circle — canvas overlay, mutated via selOverlayRef
                                    val selOverlay = SelectionOverlay(selFillPaint, selRingPaint)
                                    overlays.add(selOverlay)
                                    selOverlayRef.value = selOverlay

                                    // Centre and fit view
                                    val centre = GeoPoint(
                                        gpsPoints.mapNotNull { it.latitude  }.average(),
                                        gpsPoints.mapNotNull { it.longitude }.average()
                                    )
                                    controller.setCenter(centre)
                                    controller.setZoom(16.0)
                                    post {
                                        // Compute bounding box from raw lat/lon — avoids
                                        // the IGeoPoint generic type mismatch.
                                        val north = geoPoints.maxOf { it.latitude }
                                        val south = geoPoints.minOf { it.latitude }
                                        val east  = geoPoints.maxOf { it.longitude }
                                        val west  = geoPoints.minOf { it.longitude }
                                        val box = org.osmdroid.util.BoundingBox(north, east, south, west)
                                        zoomToBoundingBox(box, true, 40)
                                    }

                                    mapViewRef.value = this
                                }
                            },
                            update = { /* selection driven via LaunchedEffect(selectedIndex) */ }
                        )
                        } // end clipped Box
                    }
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
