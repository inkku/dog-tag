package com.dogtag.ui.calibration

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dogtag.DogTagApplication
import com.dogtag.ble.NearbyDevice
import com.dogtag.ble.PathLossCalibrator
import com.dogtag.ble.TagScanner
import com.dogtag.data.model.Tag
import com.dogtag.data.model.TagType
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Walk-away calibration: pick a nearby tag, then at a few known distances
 * (e.g. 1m, 5m, 10m, 20m) tap "record sample" to log its current RSSI. Fitting
 * those samples gives this tag's own rssiAt1m / path-loss-exponent, which is a
 * much better basis for virtual-fence distance thresholds than the generic
 * defaults in RangeEstimator - BLE attenuation varies a lot with terrain,
 * obstacles, and even how the tag is mounted on the dog.
 */
@Composable
fun CalibrationScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as DogTagApplication
    val scope = rememberCoroutineScope()
    val scanner = remember { TagScanner(context) }

    var nearby by remember { mutableStateOf(mapOf<String, NearbyDevice>()) }
    var selected by remember { mutableStateOf<NearbyDevice?>(null) }
    var tagName by remember { mutableStateOf("") }
    var distanceInput by remember { mutableStateOf("") }
    val samples = remember { mutableStateOf(listOf<PathLossCalibrator.CalibrationSample>()) }
    var statusMessage by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        val job: Job = scope.launch {
            runCatching {
                scanner.scanNearby().collect { device ->
                    nearby = nearby + (device.macAddress to device)
                }
            }
        }
        onDispose { job.cancel() }
    }

    Column(Modifier.padding(16.dp)) {
        Text("Calibrate a tag", style = MaterialTheme.typography.titleLarge)
        Text(
            "1. Hold the tag near the phone so it's the strongest signal below. " +
                "2. Pick it. 3. Walk it out to a known distance and tap record. Repeat at 2-3 distances.",
            style = MaterialTheme.typography.bodySmall,
        )

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
            items(nearby.values.sortedByDescending { it.rssi }.take(15)) { device ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(
                        "${device.name ?: "(unnamed)"}  ${device.macAddress}  RSSI ${device.rssi}",
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = { selected = device; samples.value = emptyList() }) {
                        Text(if (selected?.macAddress == device.macAddress) "Selected" else "Select")
                    }
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        selected?.let { device ->
            Text("Calibrating ${device.macAddress}", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = tagName, onValueChange = { tagName = it }, label = { Text("Tag name (e.g. Rex's OTAG)") }, modifier = Modifier.fillMaxWidth())

            Row(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = distanceInput,
                    onValueChange = { distanceInput = it },
                    label = { Text("Current distance (m)") },
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = {
                    val distance = distanceInput.toDoubleOrNull()
                    val liveRssi = nearby[device.macAddress]?.rssi
                    if (distance != null && liveRssi != null) {
                        samples.value = samples.value + PathLossCalibrator.CalibrationSample(distance, liveRssi)
                        statusMessage = "${samples.value.size} sample(s) recorded"
                    }
                }) { Text("Record sample") }
            }

            Text(statusMessage, style = MaterialTheme.typography.bodySmall)

            Button(
                enabled = samples.value.size >= 2 && tagName.isNotBlank(),
                onClick = {
                    val result = PathLossCalibrator.fit(samples.value)
                    if (result == null) {
                        statusMessage = "Fit failed - record samples at more varied distances"
                    } else {
                        scope.launch {
                            app.repository.createTag(
                                Tag(
                                    name = tagName,
                                    type = TagType.BLE,
                                    macAddress = device.macAddress,
                                    rssiAt1m = result.rssiAt1m,
                                    pathLossExponent = result.pathLossExponent,
                                )
                            )
                            statusMessage = "Saved: rssiAt1m=${"%.1f".format(result.rssiAt1m)}, " +
                                "pathLossExponent=${"%.2f".format(result.pathLossExponent)}"
                        }
                    }
                },
            ) { Text("Compute + save tag") }
        }
    }
}
