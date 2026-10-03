package zw.marketmoo.app.ui.map

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

/**
 * Map screen showing district data, a travel-time grid, and an OSM basemap.
 */
@SuppressLint("MissingPermission")
@Composable
fun MapScreen(sl: ServiceLocator, onInsights: () -> Unit) {
    val ctx = LocalContext.current
    val mapView = remember { MapView(ctx) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

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
            m.cameraPosition = CameraPosition.Builder().target(LatLng(-20.4, 29.9)).zoom(6.2).build()
            m.setStyle(Style.Builder().fromJson(BLANK_STYLE)) { s ->
                // Add OSM basemap layer under other data layers
                val osmSource = RasterSource("osm-source", TileSet("2.1.0", "https://tile.openstreetmap.org/{z}/{x}/{y}.png"), 256)
                s.addSource(osmSource)
                val osmLayer = RasterLayer("osm-layer", "osm-source")
                s.addLayerAbove(osmLayer, "bg")

                // Enable location component
                val activationOptions = LocationComponentActivationOptions.builder(ctx, s).build()
                m.locationComponent.activateLocationComponent(activationOptions)
                m.locationComponent.isLocationComponentEnabled = true

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
                s.addSource(GeoJsonSource("pin", pinJson(sl.pin)))
                s.addLayer(CircleLayer("pin-circle", "pin").withProperties(PropertyFactory.circleRadius(9.0f), PropertyFactory.circleColor("#ffe08a"), PropertyFactory.circleStrokeColor("#155c2b"), PropertyFactory.circleStrokeWidth(3.0f)))
                style = s
            }
            m.addOnMapClickListener { ll ->
                sl.pin = FarmPin(ll.latitude, ll.longitude)
                true
            }
        }
    }
    LaunchedEffect(style, gridJson) { val g = gridJson; if (g != null) style?.getSourceAs<GeoJsonSource>("grid")?.setGeoJson(g) }
    LaunchedEffect(style, featJson) { val f = featJson; if (f != null) style?.getSourceAs<GeoJsonSource>("features")?.setGeoJson(f) }
    LaunchedEffect(style, sl.pin) { style?.getSourceAs<GeoJsonSource>("pin")?.setGeoJson(pinJson(sl.pin)) }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        
        Column(Modifier.align(Alignment.TopStart).padding(8.dp).background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(12.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Road time to nearest major market", style = MaterialTheme.typography.labelLarge)
            Text("green 30 min or less, light green 60, yellow 120, orange 180, red over 180", style = MaterialTheme.typography.labelSmall)
            Text("Blue dot: market or town. Purple: service point (proxy). Cyan: water point.", style = MaterialTheme.typography.labelSmall)
            Text("Tap the map to set your farm pin.", style = MaterialTheme.typography.labelSmall)
        }

        FloatingActionButton(
            onClick = {
                map?.locationComponent?.lastKnownLocation?.let { loc ->
                    map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 14.0))
                }
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).padding(bottom = 72.dp),
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = "Current location")
        }

        Row(Modifier.align(Alignment.BottomCenter).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onInsights, enabled = sl.pin != null) { Text("Insights for my pin") }
            OutlinedButton(onClick = { sl.pin = null }, enabled = sl.pin != null, colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(containerColor = Color.White)) { Text("Clear pin") }
        }
    }
}
