package com.dogtag.ui.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.dogtag.DogTagApplication
import com.dogtag.data.model.Dog
import com.dogtag.data.model.LocationSample
import com.dogtag.data.model.Tag
import kotlinx.coroutines.delay
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

private const val POLL_INTERVAL_MS = 15_000L
private val DEFAULT_CENTER = GeoPoint(52.0, 4.0) // fallback until real data arrives

@Composable
fun MapScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as DogTagApplication
    var mapView by remember { mutableStateOf<MapView?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            runCatching {
                val dogs = app.repository.listDogs()
                val tags = app.repository.listTags()
                val latest = app.repository.latestLocations()
                mapView?.let { updateMarkers(it, dogs, tags, latest) }
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            MapView(ctx).apply {
                setMultiTouchControls(true)
                controller.setZoom(17.0)
                controller.setCenter(DEFAULT_CENTER)
                mapView = this
            }
        },
    )
}

private fun updateMarkers(
    map: MapView,
    dogs: List<Dog>,
    tags: List<Tag>,
    latestByTagId: Map<Int, LocationSample>,
) {
    map.overlays.clear()
    var firstPoint: GeoPoint? = null

    for (dog in dogs) {
        val dogId = dog.id ?: continue
        val dogTagIds = tags.filter { it.dogId == dogId }.mapNotNull { it.id }
        val sample = dogTagIds.mapNotNull { latestByTagId[it] }.maxByOrNull { it.id ?: 0 } ?: continue

        val point = GeoPoint(sample.lat, sample.lon)
        firstPoint = firstPoint ?: point

        val marker = Marker(map).apply {
            position = point
            title = dog.name
            snippet = "±${sample.accuracyM?.let { "%.0f".format(it) } ?: "?"} m (${sample.source})"
        }
        map.overlays.add(marker)
    }

    firstPoint?.let { map.controller.setCenter(it) }
    map.invalidate()
}
