package com.vijaychhetry.kidspiano.lab

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vijaychhetry.kidspiano.calibration.CalibrationStore
import com.vijaychhetry.kidspiano.core.calibration.concertA4Hz
import com.vijaychhetry.kidspiano.core.notes.A4_HZ
import com.vijaychhetry.kidspiano.core.pitch.levelFraction

@Composable
fun AudioLabScreen(model: AudioLabViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val store = remember { CalibrationStore(context) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        model.onPermission(granted)
        if (granted) model.start()
    }

    LaunchedEffect(Unit) {
        val profile = store.load()
        model.retune(profile?.let { concertA4Hz(it) } ?: A4_HZ)
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        model.onPermission(granted)
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Engineering screen: it only shows what the microphone hears. " +
                "It does not teach or score. Use Calibrate to set the piano up.",
            style = MaterialTheme.typography.bodySmall,
        )

        Text(state.noteName, style = MaterialTheme.typography.displayMedium)
        Text(
            state.status.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        LevelMeter(state.level, state.peakLevel)

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Text(state.hint, style = MaterialTheme.typography.bodyMedium)

        val pitch = state.pitch
        MetricRow("Frequency", pitch?.frequency?.let { "%.1f Hz".format(it) } ?: "—")
        MetricRow("MIDI", pitch?.midiNote?.toString() ?: "—")
        MetricRow("Confidence", pitch?.let { "${(it.confidence * 100).toInt()}%" } ?: "—")
        MetricRow("Mic level", "%.4f RMS".format(state.level))
        MetricRow("Latency", pitch?.let { "${it.latencyMs} ms (compute)" } ?: "—")
        MetricRow("Phase", state.phase.name)
        MetricRow("Mic source", state.sourceLabel)
        MetricRow("Sample rate", if (state.sampleRate == 0) "—" else "${state.sampleRate} Hz")
        MetricRow("Frames", state.frameCount.toString())

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (state.permissionNeeded) {
                    launcher.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    model.start()
                }
            }, enabled = !state.running) {
                Text(if (state.running) "Listening" else "Start listening")
            }
            OutlinedButton(onClick = { model.stop() }, enabled = state.running) { Text("Stop") }
            TextButton(onClick = { model.switchSource() }) { Text("Change mic") }
        }

        Text("Event log", style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth().weight(1f, fill = true)) {
            if (state.events.isEmpty()) {
                Text(
                    if (state.running) {
                        "Waiting for the first frame…"
                    } else {
                        "Empty until you start listening."
                    },
                    Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                LazyColumn(Modifier.padding(12.dp)) {
                    items(state.events) { event ->
                        val hz = event.hz?.let { "%.1f Hz".format(it) } ?: "—"
                        Text(
                            "${event.status}  ${event.label}  $hz  ${(event.confidence * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

/** Log-scaled bar so room noise and a struck key look different. */
@Composable
private fun LevelMeter(level: Double, peak: Double) {
    val fraction = levelFraction(level)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(16.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Text(
            "peak %.4f".format(peak),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
