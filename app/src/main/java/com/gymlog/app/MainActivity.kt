package com.gymlog.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.gymlog.app.data.GymRepository
import com.gymlog.app.ui.GymLogApp
import com.gymlog.app.ui.theme.GymLogTheme
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = GymRepository(this)
        setContent {
            var dataVersion by remember { mutableIntStateOf(0) }
            var accentColor by remember { mutableStateOf(repository.accentColor()) }
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
                )
            }
        }
    }
}
