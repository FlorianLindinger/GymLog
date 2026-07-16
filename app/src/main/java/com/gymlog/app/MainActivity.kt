package com.gymlog.app

import android.os.Bundle
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
import com.gymlog.app.ui.HealthConnectGateway
import com.gymlog.app.ui.theme.GymLogTheme
import java.time.LocalDate
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = GymRepository(this)
        setContent {
            var dataVersion by remember { mutableIntStateOf(0) }
            var accentColor by remember { mutableStateOf(repository.accentColor()) }
            var bodyData by remember { mutableStateOf(BodyDataSnapshot()) }
            var healthSyncStatus by remember { mutableStateOf("Not synced yet.") }
            val healthGateway = remember { HealthConnectGateway(this) }
            val scope = rememberCoroutineScope()
            fun readHealthData() {
                scope.launch {
                    healthSyncStatus = "Syncing Health Connect…"
                    runCatching { healthGateway.readBodyData() }
                        .onSuccess {
                            bodyData = it
                            healthSyncStatus = "Synced ${it.measurements.size} measurements."
                        }
                        .onFailure { healthSyncStatus = "Health Connect sync failed: ${it.message}" }
                }
            }
            val healthPermissionLauncher = rememberLauncherForActivityResult(
                PermissionController.createRequestPermissionResultContract(),
            ) { granted ->
                if (granted.containsAll(HealthConnectGateway.permissions)) readHealthData()
                else healthSyncStatus = "Health Connect permission was not granted."
            }
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                if (healthGateway.sdkStatus() == HealthConnectClient.SDK_AVAILABLE) {
                    scope.launch {
                        if (healthGateway.hasPermissions()) readHealthData()
                    }
                }
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
                    Toast.makeText(this, "Backup restored", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(this, "Import failed: ${it.message}", Toast.LENGTH_LONG).show()
                }
            }

            GymLogTheme(accentColor) {
                GymLogApp(
                    repository = repository,
                    externalDataVersion = dataVersion,
                    onExport = { exportLauncher.launch("GymLog-${LocalDate.now()}.json") },
                    onImport = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                    onAccentColorChanged = {
                        repository.setAccentColor(it)
                        accentColor = it
                    },
                    bodyData = bodyData,
                    healthSyncStatus = healthSyncStatus,
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
