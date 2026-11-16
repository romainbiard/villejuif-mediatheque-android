// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/** A place on the map. */
data class MapPin(val latitude: Double, val longitude: Double, val label: String)

/**
 * A map of OpenStreetMap data (OpenFreeMap tiles, MapLibre): no API key, no tracker, no account.
 * Panning and zooming work; rotation and tilt are off, as they only get people lost.
 * The map itself is a picture for TalkBack: [description] says what it shows, the screen lists the same places.
 */
@Composable
fun LibraryMap(
    pins: List<MapPin>,
    centerLatitude: Double,
    centerLongitude: Double,
    zoom: Double,
    description: String,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(28.dp)
    val semantics = Modifier.semantics { contentDescription = description }
    if (LocalInspectionMode.current) {
        // Previews and screenshot tests: no network, no native map.
        Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh).then(semantics))
        return
    }
    val context = LocalContext.current
    val pinColor = MaterialTheme.colorScheme.primary.toArgb()
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val haloColor = MaterialTheme.colorScheme.surface.toArgb()
    val mapView = remember { createMapView(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    AndroidView(
        factory = {
            mapView.apply {
                getMapAsync { map ->
                    map.uiSettings.isRotateGesturesEnabled = false
                    map.uiSettings.isTiltGesturesEnabled = false
                    map.cameraPosition = CameraPosition.Builder().target(LatLng(centerLatitude, centerLongitude)).zoom(zoom).build()
                    map.setStyle(STYLE_URL) { style ->
                        val features = pins.map { pin ->
                            Feature.fromGeometry(Point.fromLngLat(pin.longitude, pin.latitude)).apply {
                                addStringProperty("label", pin.label)
                            }
                        }
                        style.addSource(GeoJsonSource(SOURCE_ID, FeatureCollection.fromFeatures(features)))
                        style.addLayer(
                            CircleLayer("$SOURCE_ID-pins", SOURCE_ID).withProperties(
                                PropertyFactory.circleRadius(8f),
                                PropertyFactory.circleColor(pinColor),
                                PropertyFactory.circleStrokeWidth(2f),
                                PropertyFactory.circleStrokeColor(haloColor)
                            )
                        )
                        style.addLayer(
                            SymbolLayer("$SOURCE_ID-labels", SOURCE_ID).withProperties(
                                PropertyFactory.textField("{label}"),
                                PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
                                PropertyFactory.textSize(13f),
                                PropertyFactory.textOffset(arrayOf(0f, 1.3f)),
                                PropertyFactory.textAnchor("top"),
                                PropertyFactory.textColor(textColor),
                                PropertyFactory.textHaloColor(haloColor),
                                PropertyFactory.textHaloWidth(1.5f)
                            )
                        )
                    }
                }
            }
        },
        modifier = modifier.clip(shape).then(semantics)
    )
}

private fun createMapView(context: Context): MapView {
    MapLibre.getInstance(context.applicationContext)
    return MapView(context).apply { onCreate(null) }
}

/** OpenFreeMap's "Liberty" style: OpenStreetMap data, free, without a key. */
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val SOURCE_ID = "places"
