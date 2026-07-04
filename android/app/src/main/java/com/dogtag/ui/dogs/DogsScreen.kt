package com.dogtag.ui.dogs

import androidx.compose.foundation.layout.Arrangement
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
import com.dogtag.data.model.Dog
import com.dogtag.data.model.Tag
import com.dogtag.data.model.TagType
import kotlinx.coroutines.launch

@Composable
fun DogsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as DogTagApplication
    val scope = rememberCoroutineScope()

    var dogs by remember { mutableStateOf(emptyList<Dog>()) }
    var tags by remember { mutableStateOf(emptyList<Tag>()) }
    var newDogName by remember { mutableStateOf("") }

    suspend fun refresh() {
        dogs = app.repository.listDogs()
        tags = app.repository.listTags()
    }

    LaunchedEffect(Unit) { refresh() }

    Column(Modifier.padding(16.dp)) {
        Text("Dogs", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = newDogName,
                onValueChange = { newDogName = it },
                label = { Text("New dog name") },
                modifier = Modifier.weight(1f),
            )
            Button(onClick = {
                scope.launch {
                    if (newDogName.isNotBlank()) {
                        app.repository.createDog(Dog(name = newDogName))
                        newDogName = ""
                        refresh()
                    }
                }
            }) { Text("Add") }
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        Text("Add an FMDN or Tractive tag", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        Text(
            "BLE tags are paired from the Calibrate tab instead, since they need a live scan.",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        )
        NonBleTagForm(onCreate = { tag -> scope.launch { app.repository.createTag(tag); refresh() } })

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        LazyColumn {
            items(dogs) { dog ->
                DogRow(dog = dog, tags = tags.filter { it.dogId == dog.id }, allUnassignedTags = tags.filter { it.dogId == null }, onAssign = { tag ->
                    scope.launch {
                        val tagId = tag.id ?: return@launch
                        app.repository.updateTag(tagId, tag.copy(dogId = dog.id))
                        refresh()
                    }
                })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun NonBleTagForm(onCreate: (Tag) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TagType.FMDN) }
    var externalId by remember { mutableStateOf("") }
    var typeMenuExpanded by remember { mutableStateOf(false) }

    Column {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())

        ExposedDropdownMenuBox(expanded = typeMenuExpanded, onExpandedChange = { typeMenuExpanded = it }) {
            OutlinedTextField(
                value = type.name,
                onValueChange = {},
                readOnly = true,
                label = { Text("Type") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuExpanded) },
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownMenu(expanded = typeMenuExpanded, onDismissRequest = { typeMenuExpanded = false }) {
                listOf(TagType.FMDN, TagType.TRACTIVE).forEach { option ->
                    DropdownMenuItem(text = { Text(option.name) }, onClick = { type = option; typeMenuExpanded = false })
                }
            }
        }

        OutlinedTextField(
            value = externalId,
            onValueChange = { externalId = it },
            label = { Text(if (type == TagType.FMDN) "FMDN device id" else "Tractive tracker id") },
            modifier = Modifier.fillMaxWidth(),
        )

        Button(onClick = {
            if (name.isNotBlank() && externalId.isNotBlank()) {
                val tag = Tag(
                    name = name,
                    type = type,
                    fmdnDeviceId = if (type == TagType.FMDN) externalId else null,
                    tractiveTrackerId = if (type == TagType.TRACTIVE) externalId else null,
                )
                onCreate(tag)
                name = ""
                externalId = ""
            }
        }) { Text("Create tag") }
    }
}

@Composable
private fun DogRow(dog: Dog, tags: List<Tag>, allUnassignedTags: List<Tag>, onAssign: (Tag) -> Unit) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(dog.name, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        tags.forEach { tag ->
            Text("  • ${tag.name} (${tag.type})")
        }
        if (allUnassignedTags.isNotEmpty()) {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = "Assign an unpaired tag...",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Assign tag") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    allUnassignedTags.forEach { tag ->
                        DropdownMenuItem(
                            text = { Text("${tag.name} (${tag.type})") },
                            onClick = {
                                expanded = false
                                onAssign(tag)
                            },
                        )
                    }
                }
            }
        }
    }
}
