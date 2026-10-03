package zw.marketmoo.app.ui.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.style.layers.FillLayer
import zw.marketmoo.app.util.FarmShape
import zw.marketmoo.app.util.Geometry
import zw.marketmoo.app.util.LatLon
import zw.marketmoo.app.util.ShapefileImport
import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import zw.marketmoo.app.FarmPin
import zw.marketmoo.app.ServiceLocator

private const val BLANK_STYLE =
    "{\"version\":8,\"name\":\"offline\",\"sources\":{},\"layers\":[{\"id\":\"bg\",\"type\":\"background\",\"paint\":{\"background-color\":\"#faf7f0\"}}]}"

private fun colorFor(minutes: Int): String = when {
    minutes < 0 -> "#bbbbbb"
    minutes <= 30 -> "#1a9850"
    minutes <= 60 -> "#91cf60"
    minutes <= 120 -> "#fee08b"
    minutes <= 180 -> "#fc8d59"
    else -> "#d73027"
}

private fun pinJson(p: FarmPin?): String =
    if (p == null) "{\"type\":\"FeatureCollection\",\"features\":[]}"
    else "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"Point\",\"coordinates\":[${p.lon},${p.lat}]}}]}"

private fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "'")

private fun lineJson(points: List<LatLon>): String =
    if (points.size < 2) "{\"type\":\"FeatureCollection\",\"features\":[]}"
    else "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[" +
        points.joinToString(",") { "[${it.lon},${it.lat}]" } + "]}}]}"

private fun pointsJson(points: List<LatLon>): String =
    "{\"type\":\"FeatureCollection\",\"features\":[" +
        points.joinToString(",") { "{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"Point\",\"coordinates\":[${it.lon},${it.lat}]}}" } + "]}"

private fun shapeJson(shape: FarmShape?): String {
    if (shape == null) return "{\"type\":\"FeatureCollection\",\"features\":[]}"
    return "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{},\"geometry\":" + Geometry.toGeoJson(shape).toString() + "}]}"
}

private fun draftPolygonJson(points: List<LatLon>): String =
    if (points.size < 3) "{\"type\":\"FeatureCollection\",\"features\":[]}"
    else shapeJson(FarmShape(listOf(points)))

private fun fitTo(map: MapLibreMap?, points: List<LatLon>) {
    if (map == null || points.isEmpty()) return
    if (points.size == 1) { map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(points[0].lat, points[0].lon), 15.0)); return }
    val b = LatLngBounds.Builder()
    points.forEach { b.include(LatLng(it.lat, it.lon)) }
    map.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), 120))
}

private sealed class ImportState {
    data class Preview(val shape: FarmShape, val notes: List<String>) : ImportState()
    data class Failed(val message: String) : ImportState()
}

/**
 * Map screen: district data, a travel-time grid and an OSM basemap, plus the farm boundary tools.
 * The farmer can walk or tap the outline of the farm, or import a zipped shapefile. The centre of the boundary becomes the
 * farm pin, so Farm Insights and listings work the same as before. The outline itself stays private to the farmer.
 */
@SuppressLint("MissingPermission")
@Composable
fun MapScreen(sl: ServiceLocator, onInsights: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val mapView = remember { MapView(ctx) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val pinMode = remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var importState by remember { mutableStateOf<ImportState?>(null) }
    var locationOn by remember { mutableStateOf(false) }

    fun hasLocation() = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun enableLocation(m: MapLibreMap, s: Style) {
        if (!hasLocation()) return
        runCatching {
            m.locationComponent.activateLocationComponent(LocationComponentActivationOptions.builder(ctx, s).build())
            m.locationComponent.isLocationComponentEnabled = true
            locationOn = true
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val m = map; val s = style
        if (m != null && s != null) enableLocation(m, s)
    }
    LaunchedEffect(Unit) {
        if (!hasLocation()) permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    ctx.contentResolver.openInputStream(uri)?.use { ShapefileImport.fromZip(it) } ?: ShapefileImport.Result.Error("Could not open that file.")
                } catch (e: Exception) { ShapefileImport.Result.Error("Could not read that file: ${e.message ?: "unknown error"}") }
            }
            importState = when (result) {
                is ShapefileImport.Result.Ok -> ImportState.Preview(result.shape, result.notes)
                is ShapefileImport.Result.Error -> ImportState.Failed(result.message)
            }
            (importState as? ImportState.Preview)?.let { fitTo(map, it.shape.rings.flatten()) }
        }
    }

    val gridJson by produceState<String?>(null) {
        value = withContext(Dispatchers.Default) {
            val sb = StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[")
            var first = true
            sl.packs.districts.forEach { d ->
                sl.packs.mapPoints(d).forEach { (lon, lat, t) ->
                    if (!first) sb.append(',')
                    first = false
                    sb.append("{\"type\":\"Feature\",\"properties\":{\"c\":\"").append(colorFor(t)).append("\"},\"geometry\":{\"type\":\"Point\",\"coordinates\":[")
                        .append("%.4f,%.4f".format(java.util.Locale.US, lon, lat)).append("]}}")
                }
            }
            sb.append("]}").toString()
        }
    }
    val featJson by produceState<String?>(null) {
        value = withContext(Dispatchers.Default) {
            val sb = StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[")
            var first = true
            sl.packs.districts.forEach { d ->
                sl.packs.features(d).forEach { f ->
                    val group = when {
                        f.kind.startsWith("water") -> "water"
                        f.kind.contains("proxy") || f.kind.startsWith("vet") || f.kind.startsWith("DVS") -> "service"
                        else -> "market"
                    }
                    if (!first) sb.append(',')
                    first = false
                    sb.append("{\"type\":\"Feature\",\"properties\":{\"g\":\"").append(group).append("\",\"n\":\"").append(esc(f.name)).append("\"},\"geometry\":{\"type\":\"Point\",\"coordinates\":[")
                        .append("%.4f,%.4f".format(java.util.Locale.US, f.lon, f.lat)).append("]}}")
                }
            }
            sb.append("]}").toString()
        }
    }
    val districtsJson = remember { ctx.assets.open("districts.geojson").bufferedReader().use { it.readText() } }

    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); mapView.onPause(); mapView.onStop(); mapView.onDestroy() }
    }

    LaunchedEffect(Unit) {
        mapView.getMapAsync { m ->
            map = m
            val start = sl.boundary?.rings?.flatten()
            if (start.isNullOrEmpty()) m.cameraPosition = CameraPosition.Builder().target(LatLng(-20.4, 29.9)).zoom(6.2).build()
            m.setStyle(Style.Builder().fromJson(BLANK_STYLE)) { s ->
                // OSM basemap under the data layers (needs a connection; the data layers below work offline)
                s.addSource(RasterSource("osm-source", TileSet("2.1.0", "https://tile.openstreetmap.org/{z}/{x}/{y}.png"), 256))
                s.addLayerAbove(RasterLayer("osm-layer", "osm-source"), "bg")
                enableLocation(m, s)

                s.addSource(GeoJsonSource("districts", districtsJson))
                s.addLayer(LineLayer("districts-line", "districts").withProperties(PropertyFactory.lineColor("#155c2b"), PropertyFactory.lineWidth(1.6f)))
                s.addSource(GeoJsonSource("grid", "{\"type\":\"FeatureCollection\",\"features\":[]}"))
                s.addLayer(
                    CircleLayer("grid-circles", "grid").withProperties(
                        PropertyFactory.circleColor(Expression.toColor(Expression.get("c"))),
                        PropertyFactory.circleOpacity(0.75f),
                        PropertyFactory.circleRadius(Expression.interpolate(Expression.exponential(2.0f), Expression.zoom(), Expression.stop(6, 0.8f), Expression.stop(9, 4.0f), Expression.stop(12, 30.0f))),
                    ),
                )
                s.addSource(GeoJsonSource("features", "{\"type\":\"FeatureCollection\",\"features\":[]}"))
                s.addLayer(
                    CircleLayer("features-circles", "features").withProperties(
                        PropertyFactory.circleRadius(5.0f), PropertyFactory.circleStrokeColor("#ffffff"), PropertyFactory.circleStrokeWidth(1.5f),
                        PropertyFactory.circleColor(
                            Expression.match(Expression.get("g"), Expression.color(android.graphics.Color.parseColor("#1e88e5")),
                                Expression.stop("service", Expression.color(android.graphics.Color.parseColor("#7b1fa2"))),
                                Expression.stop("water", Expression.color(android.graphics.Color.parseColor("#00acc1")))),
                        ),
                    ),
                )
                // saved boundary (green) and the outline being drawn or imported (orange)
                s.addSource(GeoJsonSource("boundary", shapeJson(sl.boundary)))
                s.addLayer(FillLayer("boundary-fill", "boundary").withProperties(PropertyFactory.fillColor("#1fb36b"), PropertyFactory.fillOpacity(0.28f)))
                s.addLayer(LineLayer("boundary-line", "boundary").withProperties(PropertyFactory.lineColor("#0b5d3b"), PropertyFactory.lineWidth(3.5f)))
                s.addSource(GeoJsonSource("draft-poly", "{\"type\":\"FeatureCollection\",\"features\":[]}"))
                s.addLayer(FillLayer("draft-fill", "draft-poly").withProperties(PropertyFactory.fillColor("#ff7a3d"), PropertyFactory.fillOpacity(0.28f)))
                s.addSource(GeoJsonSource("draft-line", "{\"type\":\"FeatureCollection\",\"features\":[]}"))
                s.addLayer(LineLayer("draft-line-layer", "draft-line").withProperties(PropertyFactory.lineColor("#e0561a"), PropertyFactory.lineWidth(3.5f), PropertyFactory.lineDasharray(arrayOf(2f, 1.5f))))
                s.addSource(GeoJsonSource("draft-pts", "{\"type\":\"FeatureCollection\",\"features\":[]}"))
                s.addLayer(CircleLayer("draft-pts-layer", "draft-pts").withProperties(PropertyFactory.circleRadius(7.0f), PropertyFactory.circleColor("#ffffff"), PropertyFactory.circleStrokeColor("#e0561a"), PropertyFactory.circleStrokeWidth(3.0f)))
                s.addSource(GeoJsonSource("pin", pinJson(sl.pin)))
                s.addLayer(CircleLayer("pin-circle", "pin").withProperties(PropertyFactory.circleRadius(9.0f), PropertyFactory.circleColor("#ffe08a"), PropertyFactory.circleStrokeColor("#155c2b"), PropertyFactory.circleStrokeWidth(3.0f)))
                style = s
                if (!start.isNullOrEmpty()) fitTo(m, start)
            }
            m.addOnMapClickListener { ll ->
                when {
                    sl.drawing -> { sl.draft.add(LatLon(ll.latitude, ll.longitude)); message = null; true }
                    pinMode.value -> { sl.pin = FarmPin(ll.latitude, ll.longitude); pinMode.value = false; true }
                    else -> false
                }
            }
        }
    }
    LaunchedEffect(style, gridJson) { val g = gridJson; if (g != null) style?.getSourceAs<GeoJsonSource>("grid")?.setGeoJson(g) }
    LaunchedEffect(style, featJson) { val f = featJson; if (f != null) style?.getSourceAs<GeoJsonSource>("features")?.setGeoJson(f) }
    LaunchedEffect(style, sl.pin) { style?.getSourceAs<GeoJsonSource>("pin")?.setGeoJson(pinJson(sl.pin)) }
    LaunchedEffect(style, sl.boundary) { style?.getSourceAs<GeoJsonSource>("boundary")?.setGeoJson(shapeJson(sl.boundary)) }
    val draftNow = sl.draft.toList()
    val preview = (importState as? ImportState.Preview)?.shape
    LaunchedEffect(style, draftNow, preview) {
        val s = style ?: return@LaunchedEffect
        if (preview != null) {
            s.getSourceAs<GeoJsonSource>("draft-poly")?.setGeoJson(shapeJson(preview))
            s.getSourceAs<GeoJsonSource>("draft-line")?.setGeoJson("{\"type\":\"FeatureCollection\",\"features\":[]}")
            s.getSourceAs<GeoJsonSource>("draft-pts")?.setGeoJson("{\"type\":\"FeatureCollection\",\"features\":[]}")
        } else {
            s.getSourceAs<GeoJsonSource>("draft-poly")?.setGeoJson(draftPolygonJson(draftNow))
            s.getSourceAs<GeoJsonSource>("draft-line")?.setGeoJson(lineJson(if (draftNow.size >= 3) draftNow + draftNow.first() else draftNow))
            s.getSourceAs<GeoJsonSource>("draft-pts")?.setGeoJson(pointsJson(draftNow))
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        // legend / instructions
        Column(Modifier.align(Alignment.TopStart).padding(8.dp).background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(14.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (sl.drawing) {
                Text("Drawing your farm boundary", style = MaterialTheme.typography.labelLarge, color = Color(0xFFE0561A))
                Text("Tap each corner in order. You can also walk the edge and add your GPS position as a corner.", style = MaterialTheme.typography.labelSmall)
            } else {
                Text("Road time to nearest major market", style = MaterialTheme.typography.labelLarge)
                Text("green 30 min or less, light green 60, yellow 120, orange 180, red over 180", style = MaterialTheme.typography.labelSmall)
                Text("Blue dot: market or town. Purple: service point (proxy). Cyan: water point.", style = MaterialTheme.typography.labelSmall)
            }
        }

        if (locationOn) FloatingActionButton(
            onClick = {
                map?.locationComponent?.lastKnownLocation?.let { loc ->
                    map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 16.0))
                } ?: run { message = "Waiting for a GPS fix. Go outside or wait a moment." }
            },
            modifier = Modifier.align(Alignment.CenterEnd).padding(12.dp),
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) { Icon(Icons.Default.MyLocation, contentDescription = "Go to my location") }

        // bottom control panel
        Card(Modifier.align(Alignment.BottomCenter).padding(10.dp).fillMaxWidth(), shape = RoundedCornerShape(22.dp), elevation = CardDefaults.cardElevation(10.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                if (sl.drawing) {
                    val ha = if (draftNow.size >= 3) Geometry.areaHectares(draftNow) else 0.0
                    Text("${draftNow.size} corners" + if (draftNow.size >= 3) " · %.2f hectares".format(ha) else " (need at least 3)", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { if (sl.draft.isNotEmpty()) sl.draft.removeAt(sl.draft.lastIndex) }, enabled = draftNow.isNotEmpty()) { Text("Undo") }
                        OutlinedButton(onClick = {
                            val loc = map?.locationComponent?.lastKnownLocation
                            if (loc == null) message = "No GPS fix yet." else { sl.draft.add(LatLon(loc.latitude, loc.longitude)); message = null }
                        }, enabled = locationOn) { Text("+ My GPS") }
                        OutlinedButton(onClick = { sl.draft.clear() }, enabled = draftNow.isNotEmpty()) { Text("Clear") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { sl.drawing = false; sl.draft.clear(); message = null }) { Text("Cancel") }
                        Button(enabled = draftNow.size >= 3, onClick = {
                            when {
                                Geometry.selfIntersects(draftNow) -> message = "The outline crosses itself. Use Undo and re-tap the corners in order around the edge."
                                ha < 0.005 -> message = "That is smaller than 50 square metres. Check the corners."
                                else -> scope.launch {
                                    val shape = FarmShape(listOf(draftNow))
                                    sl.saveBoundary(shape, "drawn")
                                    sl.drawing = false; sl.draft.clear(); message = null
                                    fitTo(map, shape.rings.flatten())
                                }
                            }
                        }, modifier = Modifier.weight(1f)) { Text("Save boundary") }
                    }
                } else {
                    sl.boundary?.let { b ->
                        Text("Farm boundary saved: %.2f hectares. Insights use its centre.".format(b.areaHa), fontWeight = FontWeight.Bold, color = Color(0xFF0B5D3B))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { sl.draft.clear(); sl.drawing = true; message = null; pinMode.value = false }, modifier = Modifier.weight(1f)) { Text(if (sl.boundary == null) "Draw my boundary" else "Redraw") }
                        OutlinedButton(onClick = { picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream", "*/*")) }, modifier = Modifier.weight(1f)) { Text("Upload shapefile") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onInsights, enabled = sl.pin != null, modifier = Modifier.weight(1f)) { Text("Insights") }
                        if (sl.boundary == null) OutlinedButton(onClick = { pinMode.value = !pinMode.value }, modifier = Modifier.weight(1f)) { Text(if (pinMode.value) "Tap the map..." else "Quick pin only") }
                        else OutlinedButton(onClick = { scope.launch { sl.removeBoundary() } }, modifier = Modifier.weight(1f)) { Text("Remove boundary") }
                        if (sl.boundary == null && sl.pin != null) OutlinedButton(onClick = { sl.pin = null }) { Text("Clear pin") }
                    }
                }
            }
        }
    }

    when (val st = importState) {
        is ImportState.Preview -> AlertDialog(
            onDismissRequest = { importState = null },
            title = { Text("Use this boundary?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${st.shape.rings.size} polygon(s), ${st.shape.vertexCount} points, %.2f hectares. Shown in orange on the map.".format(st.shape.areaHa))
                    st.notes.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary) }
                    Text("Your outline stays private to you. Buyers only ever see your area, blurred to about 1 km.", style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = { TextButton(onClick = { scope.launch { sl.saveBoundary(st.shape, "shapefile"); importState = null; sl.drawing = false; sl.draft.clear(); fitTo(map, st.shape.rings.flatten()) } }) { Text("Save boundary") } },
            dismissButton = { TextButton(onClick = { importState = null }) { Text("Cancel") } },
        )
        is ImportState.Failed -> AlertDialog(
            onDismissRequest = { importState = null },
            title = { Text("Could not use that file") },
            text = { Text(st.message) },
            confirmButton = { TextButton(onClick = { importState = null }) { Text("OK") } },
        )
        null -> {}
    }
}
