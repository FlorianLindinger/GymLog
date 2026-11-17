package com.gymlog.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.UUID
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
internal fun AddBodyMeasurementDialog(onDismiss: () -> Unit, onSave: (ManualBodyEntry, (String?) -> Unit) -> Unit) {
    var weight by remember { mutableStateOf("") }; var fat by remember { mutableStateOf("") }; var muscle by remember { mutableStateOf("") }
    var sync by remember { mutableStateOf(true) }; var saving by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    val id = remember { UUID.randomUUID().toString() }; val time = remember { System.currentTimeMillis() }
    var date by remember { mutableStateOf(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate().toString()) }
    val measuredAt = manualBodyTimestamp(date, time)
    fun number(text: String) = text.replace(',', '.').toDoubleOrNull()
    val entry = runCatching {
        require(fat.isBlank() || number(fat) != null); require(muscle.isBlank() || number(muscle) != null)
        ManualBodyEntry(requireNotNull(number(weight)), number(fat), number(muscle), requireNotNull(measuredAt), id, sync)
    }.getOrNull()
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text("Add weight") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(weight, { weight = it; error = null }, label = { Text("Weight (kg)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(date, { date = it; error = null }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true,
                enabled = !saving, isError = measuredAt == null, supportingText = { if (measuredAt == null) Text("Use YYYY-MM-DD, today or earlier") })
            OutlinedTextField(fat, { fat = it; error = null }, label = { Text("Body fat (%) · optional") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(muscle, { muscle = it; error = null }, label = { Text("Muscle (%) · optional") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            Row { Checkbox(sync, { sync = it }, enabled = !saving); Text("Sync weight and fat with Health Connect") }
            Text("Muscle % stays in GymLog. Google Fit needs Health Connect sync enabled.", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { Button(enabled = entry != null && !saving, onClick = {
            entry?.let { value -> saving = true; onSave(value) { failure -> saving = false; error = failure; if (failure == null) onDismiss() } }
        }) { Text(if (saving) "Saving…" else "Save") } },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") } })
}

internal fun manualBodyTimestamp(date: String, capturedNow: Long, zone: ZoneId = ZoneId.systemDefault()): Long? = runCatching {
    val now = Instant.ofEpochMilli(capturedNow).atZone(zone)
    val day = LocalDate.parse(date)
    require(!day.isAfter(now.toLocalDate()))
    day.atTime(now.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
}.getOrNull()
