package com.gymlog.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun BodyPlotLayoutDialog(current: BodyPlotLayout, onDismiss: () -> Unit, onSave: (BodyPlotLayout) -> Unit) {
    var draft by remember(current) { mutableStateOf(current) }
    fun move(index: Int, direction: Int) {
        val order = draft.order.toMutableList()
        val metric = order.removeAt(index)
        order.add(index + direction, metric)
        draft = draft.copy(order = order)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.padding(horizontal = 12.dp).widthIn(max = 640.dp).fillMaxWidth(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("Order and hide plots") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Move plots with the arrows. Switch off a plot to hide it; its measurements and settings are kept.",
                    style = MaterialTheme.typography.bodySmall)
                draft.order.forEachIndexed { index, metric ->
                    val name = "${metric.label} (${metric.unit})"
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${index + 1}. ${metric.label}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = if (metric in draft.hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                            Text(metric.unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { move(index, -1) }, enabled = index > 0,
                            modifier = Modifier.semantics { contentDescription = "Move $name up" },
                            contentPadding = PaddingValues(horizontal = 4.dp)) { Text("↑") }
                        TextButton(onClick = { move(index, 1) }, enabled = index < draft.order.lastIndex,
                            modifier = Modifier.semantics { contentDescription = "Move $name down" },
                            contentPadding = PaddingValues(horizontal = 4.dp)) { Text("↓") }
                        Switch(checked = metric !in draft.hidden, onCheckedChange = { show ->
                            draft = draft.copy(hidden = if (show) draft.hidden - metric else draft.hidden + metric)
                        }, modifier = Modifier.semantics { contentDescription = "Show $name" })
                    }
                }
                TextButton(onClick = { draft = BodyPlotLayout() }) { Text("Reset order and show all") }
            }
        },
        confirmButton = { Button(onClick = { onSave(draft) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
