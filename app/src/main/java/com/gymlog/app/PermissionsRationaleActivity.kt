package com.gymlog.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gymlog.app.ui.theme.GymLogTheme

class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GymLogTheme {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("Health Connect Privacy", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "GymLog reads weight, body-fat percentage, and lean body mass only when you request a sync. " +
                            "The data is used on-device for Body graphs and is not sent to GymLog servers.",
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                    Button(onClick = { finish() }) { Text("Close") }
                }
            }
        }
    }
}
