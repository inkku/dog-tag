package com.dogtag.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as DogTagApplication
    val scope = rememberCoroutineScope()

    var backendUrl by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        backendUrl = app.settingsRepository.backendBaseUrl.first()
    }

    Column(Modifier.padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)
        Text(
            "Point this at the backend service you're self-hosting (see backend/README.md). " +
                "On a real phone this must be a LAN address/hostname it can actually reach - " +
                "10.0.2.2 only works from the Android emulator.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = backendUrl,
            onValueChange = { backendUrl = it; saved = false },
            label = { Text("Backend base URL") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        Button(onClick = {
            scope.launch {
                app.settingsRepository.setBackendBaseUrl(backendUrl)
                saved = true
            }
        }) { Text(if (saved) "Saved" else "Save") }
    }
}
