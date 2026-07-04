package com.dogtag.ui.fences

import android.annotation.SuppressLint
import android.location.LocationManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dogtag.DogTagApplication
import com.dogtag.data.model.AlertAction
import com.dogtag.data.model.AlertRule
import com.dogtag.data.model.AlertTrigger
import com.dogtag.data.model.Dog
import com.dogtag.data.model.Fence
import kotlinx.coroutines.launch

@Composable
fun FencesScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as DogTagApplication
    val scope = rememberCoroutineScope()

    var dogs by remember { mutableStateOf(emptyList<Dog>()) }
    var fences by remember { mutableStateOf(emptyList<Fence>()) }

    var name by remember { mutableStateOf("") }
    var selectedDog by remember { mutableStateOf<Dog?>(null) }
    var lat by remember { mutableStateOf("") }
    var lon by remember { mutableStateOf("") }
    var radius by remember { mutableStateOf("30") }
    var warnMargin by remember { mutableStateOf("10") }

    suspend fun refresh() {
        dogs = app.repository.listDogs()
        fences = app.repository.listFences()
    }

    LaunchedEffect(Unit) { refresh() }

    Column(Modifier.padding(16.dp)) {
        Text("Fences", style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Fence name") }, modifier = Modifier.fillMaxWidth())

        DogPicker(dogs = dogs, selected = selectedDog, onSelect = { selectedDog = it })

        Row(Modifier.fillMaxWidth()) {
            OutlinedTextField(value = lat, onValueChange = { lat = it }, label = { Text("Center lat") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = lon, onValueChange = { lon = it }, label = { Text("Center lon") }, modifier = Modifier.weight(1f))
        }
        Button(onClick = {
            useCurrentLocation(context) { newLat, newLon ->
                lat = newLat.toString()
                lon = newLon.toString()
            }
        }) { Text("Use current location") }

        Row(Modifier.fillMaxWidth()) {
            OutlinedTextField(value = radius, onValueChange = { radius = it }, label = { Text("Radius (m)") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = warnMargin, onValueChange = { warnMargin = it }, label = { Text("Warn margin (m)") }, modifier = Modifier.weight(1f))
        }

        Button(onClick = {
            val latD = lat.toDoubleOrNull()
            val lonD = lon.toDoubleOrNull()
            val radiusD = radius.toDoubleOrNull()
            val marginD = warnMargin.toDoubleOrNull()
            if (name.isNotBlank() && latD != null && lonD != null && radiusD != null && marginD != null) {
                scope.launch {
                    app.repository.createFence(
                        Fence(
                            dogId = selectedDog?.id,
                            name = name,
                            centerLat = latD,
                            centerLon = lonD,
                            radiusM = radiusD,
                            warnMarginM = marginD,
                        )
                    )
                    name = ""
                    refresh()
                }
            }
        }) { Text("Create fence") }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        LazyColumn {
            items(fences) { fence ->
                FenceRow(fence = fence, dogName = dogs.find { it.id == fence.dogId }?.name, onAddRule = { trigger, action ->
                    scope.launch {
                        fence.id?.let { app.repository.addRule(it, AlertRule(fenceId = it, trigger = trigger, action = action)) }
                    }
                })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun DogPicker(dogs: List<Dog>, selected: Dog?, onSelect: (Dog?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.name ?: "All dogs",
            onValueChange = {},
            readOnly = true,
            label = { Text("Applies to") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("All dogs") }, onClick = { onSelect(null); expanded = false })
            dogs.forEach { dog ->
                DropdownMenuItem(text = { Text(dog.name) }, onClick = { onSelect(dog); expanded = false })
            }
        }
    }
}

@Composable
private fun FenceRow(fence: Fence, dogName: String?, onAddRule: (AlertTrigger, AlertAction) -> Unit) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text("${fence.name} — ${dogName ?: "all dogs"}", style = MaterialTheme.typography.titleMedium)
        Text("radius ${fence.radiusM.toInt()}m, warn margin ${fence.warnMarginM.toInt()}m")
        Row {
            Button(onClick = { onAddRule(AlertTrigger.EXIT, AlertAction.NOTIFY_OWNER) }) { Text("Notify on exit") }
            Button(onClick = { onAddRule(AlertTrigger.APPROACH, AlertAction.RING_TAG) }) { Text("Ring tag near edge") }
        }
    }
}

@SuppressLint("MissingPermission")
private fun useCurrentLocation(context: android.content.Context, onResult: (Double, Double) -> Unit) {
    val locationManager = context.getSystemService(LocationManager::class.java) ?: return
    runCatching {
        val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        location?.let { onResult(it.latitude, it.longitude) }
    }
}
