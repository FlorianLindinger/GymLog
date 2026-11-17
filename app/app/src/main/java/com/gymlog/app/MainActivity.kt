package com.gymlog.app

import android.os.Bundle
import android.os.SystemClock
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.gymlog.app.data.GymRepository
import com.gymlog.app.ui.GymLogApp
import com.gymlog.app.ui.BodyDataSnapshot
import com.gymlog.app.ui.BodyMeasurement
import com.gymlog.app.ui.ManualBodyEntry
import com.gymlog.app.ui.ManualBodyStore
import com.gymlog.app.ui.BodyMetric
import com.gymlog.app.ui.HealthConnectGateway
import com.gymlog.app.ui.shouldAutoSyncBody
import com.gymlog.app.ui.theme.GymLogTheme
import java.time.LocalDate
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun demoBodyData(): BodyDataSnapshot {
    val now = System.currentTimeMillis()
    val week = 7L * 24L * 60L * 60L * 1000L
    val measurements = (0..11).flatMap { index ->
        val timestamp = now - (11L - index) * week
        val weight = 84.2 - index * 0.48 + if (index % 3 == 0) 0.25 else 0.0
        val fatPercent = 23.4 - index * 0.42
        val muscleMass = 64.5 + index * 0.12
        listOf(
            BodyMeasurement(BodyMetric.WEIGHT, weight, timestamp, "GymLog demo"),
            BodyMeasurement(BodyMetric.FAT_PERCENTAGE, fatPercent, timestamp, "GymLog demo"),
            BodyMeasurement(BodyMetric.FAT_MASS, weight * fatPercent / 100.0, timestamp, "GymLog demo"),
            BodyMeasurement(BodyMetric.MUSCLE_MASS, muscleMass, timestamp, "GymLog demo"),
            BodyMeasurement(BodyMetric.MUSCLE_PERCENTAGE, muscleMass / weight * 100.0, timestamp, "GymLog demo"),
        )
    }
    return BodyDataSnapshot(measurements, now)
}
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = GymRepository(this)
        setContent {
            var dataVersion by remember { mutableIntStateOf(0) }
            var accentColor by remember { mutableStateOf(repository.accentColor()) }
            val manualBodyStore = remember { ManualBodyStore(this) }
            var bodyData by remember { mutableStateOf(if (BuildConfig.DEBUG && intent.getBooleanExtra("demo_body_data", false)) demoBodyData() else manualBodyStore.merge(BodyDataSnapshot())) }
            var healthSyncStatus by remember { mutableStateOf("Not synced yet.") }
            var healthSyncing by remember { mutableStateOf(false) }
            var checkingHealthPermissions by remember { mutableStateOf(false) }
            var lastHealthSyncElapsed by remember { mutableStateOf<Long?>(null) }
            val healthGateway = remember { HealthConnectGateway(this) }
            val scope = rememberCoroutineScope()
            fun readHealthData() {
                if (healthSyncing) return
                healthSyncing = true
                scope.launch {
                    healthSyncStatus = "Syncing Health Connect…"
                    try {
                        runCatching { withContext(Dispatchers.IO) { healthGateway.readBodyData() } }
                            .onSuccess {
                                bodyData = manualBodyStore.merge(it)
                                lastHealthSyncElapsed = SystemClock.elapsedRealtime()
                                healthSyncStatus = "Synced ${it.measurements.size} measurements."
                            }
                            .onFailure { healthSyncStatus = "Health Connect sync failed: ${it.message}" }
                    } finally {
                        healthSyncing = false
                    }
                }
            }
            fun autoSyncHealthData(force: Boolean = false) {
                if (healthSyncing || checkingHealthPermissions) return
                if (!force && !shouldAutoSyncBody(lastHealthSyncElapsed, SystemClock.elapsedRealtime())) return
                if (healthGateway.sdkStatus() != HealthConnectClient.SDK_AVAILABLE) return
                checkingHealthPermissions = true
                scope.launch {
                    try {
                        if (healthGateway.hasPermissions()) readHealthData()
                    } catch (exception: kotlinx.coroutines.CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        healthSyncStatus = "Health Connect sync failed: ${exception.message}"
                    } finally {
                        checkingHealthPermissions = false
                    }
                }
            }
            val healthPermissionLauncher = rememberLauncherForActivityResult(
                PermissionController.createRequestPermissionResultContract(),
            ) { granted ->
                if (granted.containsAll(HealthConnectGateway.permissions)) readHealthData()
                else healthSyncStatus = "Health Connect permission was not granted."
            }
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                autoSyncHealthData()
            }
            val exportLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/json"),
            ) { uri ->
                if (uri != null) runCatching {
                    contentResolver.openOutputStream(uri)?.bufferedWriter().use { writer ->
                        requireNotNull(writer) { "Could not open the selected file" }
                        writer.write(repository.exportJson())
                    }
                }.onSuccess {
                    Toast.makeText(this, "Backup saved", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(this, "Export failed: ${it.message}", Toast.LENGTH_LONG).show()
                }
            }
            val importLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri ->
                if (uri != null) runCatching {
                    val json = contentResolver.openInputStream(uri)?.bufferedReader().use { reader ->
                        requireNotNull(reader) { "Could not open the selected file" }
                        reader.readText()
                    }
                    repository.importJson(json)
                }.onSuccess {
                    dataVersion++
                    bodyData = manualBodyStore.merge(bodyData.copy(measurements = bodyData.measurements.filter { it.source != packageName }))
                    Toast.makeText(this, "Backup restored", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(this, "Import failed: ${it.message}", Toast.LENGTH_LONG).show()
                }
            }

            var pendingBodyWrite by remember { mutableStateOf<Pair<ManualBodyEntry, (String?) -> Unit>?>(null) }
            suspend fun saveBodyEntry(entry: ManualBodyEntry, complete: (String?) -> Unit) {
                runCatching {
                    withContext(Dispatchers.IO) {
                        if (entry.syncHealthConnect) healthGateway.writeBodyData(entry)
                        manualBodyStore.save(entry)
                    }
                    bodyData = manualBodyStore.merge(bodyData)
                    if (entry.syncHealthConnect) autoSyncHealthData(force = true)
                }.onSuccess { complete(null) }.onFailure { complete("Could not save: ${it.message}") }
            }
            val bodyWritePermissionLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
                pendingBodyWrite?.let { (entry, complete) ->
                    pendingBodyWrite = null
                    if (granted.containsAll(healthGateway.writePermissions(entry))) scope.launch { saveBodyEntry(entry, complete) }
                    else complete("Health Connect write permission was not granted. You can turn off sync to save in GymLog only.")
                }
            }
            var pendingPlanExport by remember { mutableStateOf<String?>(null) }
            val exportPlanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                if (uri != null) runCatching {
                    val text = requireNotNull(pendingPlanExport) { "Select the plan and download it again" }
                    contentResolver.openOutputStream(uri)?.bufferedWriter().use { writer -> requireNotNull(writer).write(text) }
                }.onSuccess { Toast.makeText(this, "Training plan saved", Toast.LENGTH_SHORT).show() }
                    .onFailure { Toast.makeText(this, "Plan export failed: ${it.message}", Toast.LENGTH_LONG).show() }
                pendingPlanExport = null
            }
            val importPlanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) runCatching {
                    val text = contentResolver.openInputStream(uri)?.bufferedReader().use { requireNotNull(it).readText() }
                    repository.importTrainingPlan(text)
                }.onSuccess { dataVersion++; Toast.makeText(this, "Training plan added", Toast.LENGTH_SHORT).show() }
                    .onFailure { Toast.makeText(this, "Plan import failed: ${it.message}", Toast.LENGTH_LONG).show() }
            }
            GymLogTheme(accentColor) {
                GymLogApp(
                    repository = repository,
                    externalDataVersion = dataVersion,
                    onExport = { exportLauncher.launch("GymLog-${LocalDate.now()}.json") },
                    onImport = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                    onSaveBodyEntry = { entry, complete ->
                        scope.launch {
                            if (!entry.syncHealthConnect) saveBodyEntry(entry, complete)
                            else if (healthGateway.sdkStatus() != HealthConnectClient.SDK_AVAILABLE) complete("Health Connect is unavailable. Turn off sync to save in GymLog only.")
                            else runCatching {
                                if (healthGateway.grantedPermissions().containsAll(healthGateway.writePermissions(entry))) saveBodyEntry(entry, complete)
                                else { pendingBodyWrite = entry to complete; bodyWritePermissionLauncher.launch(healthGateway.writePermissions(entry)) }
                            }.onFailure { complete("Could not request Health Connect access: ${it.message}") }
                        }
                    },
                    onExportPlan = {
                        pendingPlanExport = repository.exportTrainingPlan()
                        exportPlanLauncher.launch("GymLog-plan-${LocalDate.now()}.json")
                    },
                    onImportPlan = { importPlanLauncher.launch(arrayOf("application/json", "text/plain")) },
                    onAccentColorChanged = {
                        repository.setAccentColor(it)
                        accentColor = it
                    },
                    bodyData = bodyData,
                    healthSyncStatus = healthSyncStatus,
                    healthSyncing = healthSyncing,
                    onAutoHealthSync = { autoSyncHealthData(force = true) },
                    onHealthSync = {
                        when (healthGateway.sdkStatus()) {
                            HealthConnectClient.SDK_AVAILABLE -> scope.launch {
                                if (healthGateway.hasPermissions()) readHealthData()
                                else healthPermissionLauncher.launch(HealthConnectGateway.permissions)
                            }
                            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                                runCatching {
                                    startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("market://details?id=com.google.android.apps.healthdata"),
                                        ),
                                    )
                                }.onFailure {
                                    startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"),
                                        ),
                                    )
                                }.also {
                                    healthSyncStatus = "Install or update Health Connect, then return and sync again."
                                }
                            else -> healthSyncStatus = "Health Connect is unavailable on this device."
                        }
                    },
                )
            }
        }
    }
}
