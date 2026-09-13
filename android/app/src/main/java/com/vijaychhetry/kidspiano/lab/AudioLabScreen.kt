package com.vijaychhetry.kidspiano.lab

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun AudioLabScreen(model: AudioLabViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        model.onPermission(granted)
        if (granted) model.start()
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        model.onPermission(granted)
    }

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Piano Audio Lab", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Engineering screen (spec Phase 1). No games. Place the phone on the piano and play one key.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                state.noteName,
                style = MaterialTheme.typography.displayLarge,
            )
            val pitch = state.pitch
            MetricRow("Frequency", pitch?.frequency?.let { "%.1f Hz".format(it) } ?: "—")
            MetricRow("MIDI", pitch?.midiNote?.toString() ?: "—")
            MetricRow("Confidence", pitch?.let { "${(it.confidence * 100).toInt()}%" } ?: "—")
            MetricRow("Signal", pitch?.let { "%.4f RMS".format(it.signalStrength) } ?: "—")
            MetricRow("Latency", pitch?.let { "${it.latencyMs} ms (compute)" } ?: "—")
            MetricRow("Phase", state.phase.name)
            MetricRow("Mic source", state.sourceLabel)
            MetricRow("Sample rate", if (state.sampleRate == 0) "—" else "${state.sampleRate} Hz")
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (state.permissionNeeded) {
                        launcher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        model.start()
                    }
                }) { Text(if (state.running) "Listening" else "Start mic") }
                OutlinedButton(onClick = { model.stop() }, enabled = state.running) { Text("Stop") }
            }
            Text("Event log", style = MaterialTheme.typography.titleMedium)
            Card(Modifier.fillMaxWidth().weight(1f, fill = true)) {
                LazyColumn(Modifier.padding(12.dp)) {
                    items(state.events) { event ->
                        val midiHint = event.note
                        Text(
                            "${event.status}  $midiHint  ${(event.confidence * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
