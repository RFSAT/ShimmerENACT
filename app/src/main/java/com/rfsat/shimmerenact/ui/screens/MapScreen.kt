package com.rfsat.shimmerenact.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.*
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.circleLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import com.mapbox.maps.plugin.animation.camera
import com.rfsat.shimmerenact.data.models.LocationPoint
import com.rfsat.shimmerenact.ui.theme.*
import com.rfsat.shimmerenact.viewmodel.ShimmerViewModel

private const val SOURCE_TRACE = "enact-trace-source"
private const val SOURCE_PREV  = "enact-prev-source"
private const val SOURCE_DOT   = "enact-dot-source"
private const val LAYER_TRACE  = "enact-trace-layer"
private const val LAYER_PREV   = "enact-prev-layer"
private const val LAYER_DOT    = "enact-dot-layer"

@Composable
fun MapScreen(viewModel: ShimmerViewModel) {
    val uiState      by viewModel.uiState.collectAsState()
    val currentLoc    = uiState.currentLocation
    val trace         = uiState.locationTrace
    val sessions     by viewModel.sessions.collectAsState()

    // Parse GPS columns from the most recent recording session CSV
    val prevTrace = remember(sessions) {
        sessions.firstOrNull()?.files?.firstOrNull()?.path
            ?.let { loadGpsFromCsv(it) } ?: emptyList()
    }

    var mapView    by remember { mutableStateOf<MapView?>(null) }
    var followMode by remember { mutableStateOf(true) }

    // Update map layers whenever trace or position changes
    LaunchedEffect(trace, prevTrace, currentLoc) {
        val mv = mapView ?: return@LaunchedEffect
        mv.mapboxMap.getStyle { style ->
            // Live trace
            style.getSourceAs<GeoJsonSource>(SOURCE_TRACE)?.featureCollection(
                FeatureCollection.fromFeatures(listOf(
                    Feature.fromGeometry(
                        LineString.fromLngLats(trace.map { Point.fromLngLat(it.lon, it.lat) })
                    )
                ))
            )
            // Previous session trace
            style.getSourceAs<GeoJsonSource>(SOURCE_PREV)?.featureCollection(
                FeatureCollection.fromFeatures(listOf(
                    Feature.fromGeometry(
                        LineString.fromLngLats(prevTrace.map { Point.fromLngLat(it.lon, it.lat) })
                    )
                ))
            )
            // Current position dot
            val dotFeatures = if (currentLoc != null) listOf(
                Feature.fromGeometry(Point.fromLngLat(currentLoc.lon, currentLoc.lat))
            ) else emptyList()
            style.getSourceAs<GeoJsonSource>(SOURCE_DOT)
                ?.featureCollection(FeatureCollection.fromFeatures(dotFeatures))
        }
    }

    // Follow current position when follow mode is on
    LaunchedEffect(currentLoc, followMode) {
        if (!followMode || currentLoc == null) return@LaunchedEffect
        mapView?.mapboxMap?.setCamera(
            CameraOptions.Builder()
                .center(Point.fromLngLat(currentLoc.lon, currentLoc.lat))
                .zoom(16.0)
                .build()
        )
    }

    Box(Modifier.fillMaxSize()) {

        // ── Mapbox map ────────────────────────────────────────────────────────
        AndroidView(
            factory = { ctx ->
                MapView(ctx).also { mv ->
                    mapView = mv
                    mv.mapboxMap.loadStyle(Style.MAPBOX_STREETS) { style ->
                        style.addSource(geoJsonSource(SOURCE_TRACE) {})
                        style.addSource(geoJsonSource(SOURCE_PREV)  {})
                        style.addSource(geoJsonSource(SOURCE_DOT)   {})

                        // Previous session — grey dashed
                        style.addLayer(lineLayer(LAYER_PREV, SOURCE_PREV) {
                            lineColor("#888888")
                            lineWidth(3.0)
                            lineOpacity(0.6)
                            lineCap(LineCap.ROUND)
                            lineJoin(LineJoin.ROUND)
                            lineDasharray(listOf(2.0, 2.0))
                        })
                        // Live trace — ENACT green
                        style.addLayer(lineLayer(LAYER_TRACE, SOURCE_TRACE) {
                            lineColor("#43AF81")
                            lineWidth(4.0)
                            lineCap(LineCap.ROUND)
                            lineJoin(LineJoin.ROUND)
                        })
                        // Position dot — accent green with white ring
                        style.addLayer(circleLayer(LAYER_DOT, SOURCE_DOT) {
                            circleRadius(9.0)
                            circleColor("#86BA39")
                            circleStrokeWidth(3.0)
                            circleStrokeColor("#FFFFFF")
                        })
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // ── Info overlay ──────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = EnactDark.copy(alpha = 0.88f),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (currentLoc != null) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LocationOn, null,
                            tint = EnactGreen, modifier = Modifier.size(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "%.6f°,  %.6f°".format(currentLoc.lat, currentLoc.lon),
                                color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Alt %.1f m  ·  ±%.0f m".format(currentLoc.altM, currentLoc.accuracyM),
                                color = Color.White.copy(alpha = 0.65f), fontSize = 10.sp
                            )
                        }
                        Text("${trace.size} pts", color = EnactGreen, fontSize = 10.sp)
                    }
                } else {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LocationOff, null,
                            tint = Color.Gray, modifier = Modifier.size(16.dp))
                        Text("No GPS fix", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }

            // Legend
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = EnactDark.copy(alpha = 0.88f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LegendDot(EnactGreen,       "This session")
                    LegendDot(Color(0xFF888888), "Last recording")
                }
            }
        }

        // ── FABs ──────────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End
        ) {
            // Follow toggle
            SmallFloatingActionButton(
                onClick = { followMode = !followMode },
                containerColor = if (followMode) EnactGreen else EnactDarkMid
            ) {
                Icon(
                    if (followMode) Icons.Default.GpsFixed else Icons.Default.GpsNotFixed,
                    contentDescription = "Follow",
                    tint = Color.White
                )
            }

            // Fit all points
            SmallFloatingActionButton(
                onClick = {
                    val mv = mapView ?: return@SmallFloatingActionButton
                    val allPts = (trace + prevTrace)
                        .map { Point.fromLngLat(it.lon, it.lat) }
                    if (allPts.size < 2) return@SmallFloatingActionButton
                    followMode = false
                    // CoordinateBounds.hull() is a Java vararg static method —
                    // Kotlin's spread operator cannot be used on it (KT-48162).
                    // Build the bounding box manually from min/max lat/lon instead.
                    val lats = allPts.map { it.latitude() }
                    val lons = allPts.map { it.longitude() }
                    val bounds = CoordinateBounds(
                        Point.fromLngLat(lons.min(), lats.min()),  // SW
                        Point.fromLngLat(lons.max(), lats.max())   // NE
                    )
                    mv.mapboxMap.setCamera(
                        mv.mapboxMap.cameraForCoordinateBounds(
                            bounds,
                            EdgeInsets(80.0, 40.0, 40.0, 40.0),
                            null, null
                        )
                    )
                },
                containerColor = EnactDarkMid
            ) {
                Icon(Icons.Default.FitScreen, contentDescription = "Fit all",
                    tint = Color.White)
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(label, color = Color.White, fontSize = 9.sp)
    }
}

/** Parse GPS columns from a CSV written by RecordingRepository. */
private fun loadGpsFromCsv(path: String?): List<LocationPoint> {
    if (path == null) return emptyList()
    return try {
        java.io.File(path).bufferedReader().useLines { lines ->
            lines.filter { !it.startsWith("#") }
                .drop(1)   // skip header row
                .mapNotNull { line ->
                    val c = line.split(",")
                    if (c.size < 7) return@mapNotNull null
                    val lat = c[3].trim().toDoubleOrNull() ?: return@mapNotNull null
                    val lon = c[4].trim().toDoubleOrNull() ?: return@mapNotNull null
                    val alt = c[5].trim().toDoubleOrNull() ?: 0.0
                    val acc = c[6].trim().toFloatOrNull()  ?: 0f
                    val ts  = c[1].trim().toLongOrNull()   ?: 0L
                    if (lat == 0.0 && lon == 0.0) return@mapNotNull null
                    LocationPoint(lat, lon, alt, acc, ts)
                }.toList()
        }
    } catch (_: Exception) { emptyList() }
}
