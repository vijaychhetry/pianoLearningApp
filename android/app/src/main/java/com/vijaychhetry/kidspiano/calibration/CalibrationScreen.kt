package com.vijaychhetry.kidspiano.calibration

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vijaychhetry.kidspiano.core.diagnostics.calibrationToJson
import com.vijaychhetry.kidspiano.core.notes.midiToNoteName
import com.vijaychhetry.kidspiano.core.notes.selectableMidi
import com.vijaychhetry.kidspiano.core.pitch.levelFraction
import com.vijaychhetry.kidspiano.export.FileExporter
import com.vijaychhetry.kidspiano.ui.PianoKeyboardView
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

@Composable
fun CalibrationScreen(model: CalibrationViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val store = remember { CalibrationStore(context) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) model.start() }

    LaunchedEffect(Unit) { model.setSavedSummary(store.summary()) }
    // Only a profile the engine cleared, or one the user explicitly kept,
    // is allowed to become the stored default (spec §11).
    LaunchedEffect(state.profileToSave) {
        state.profileToSave?.let {
            store.save(it)
            model.setSavedSummary(store.summary())
        }
    }

    var keyOpen by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Calibrate teaches the app how your piano sounds. " +
                "It asks for five keys, one at a time, and saves a profile for this room and phone.",
            style = MaterialTheme.typography.bodySmall,
        )

        val profile = state.profile
        if (profile == null) {
            Text("Play this key", style = MaterialTheme.typography.titleMedium)
            Text(
                state.askNoteName,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "${state.askNoteName} · sample ${state.samplesDone} of ${state.samplesNeeded}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Box {
                OutlinedButton(
                    onClick = { if (state.profile == null) keyOpen = true },
                    enabled = state.profile == null,
                ) { Text("Key: ${state.askNoteName}") }
                DropdownMenu(expanded = keyOpen, onDismissRequest = { keyOpen = false }) {
                    selectableMidi().forEach { midi ->
                        DropdownMenuItem(
                            text = { Text(midiToNoteName(midi)) },
                            onClick = {
                                keyOpen = false
                                model.jumpTo(midi)
                            },
                        )
                    }
                }
            }
            PianoKeyboardView(
                highlightMidi = state.askMidi ?: 60,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text("Calibration result", style = MaterialTheme.typography.titleMedium)
            Text(
                profile.calibrationQuality.name.replace('_', ' '),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    profile.notes.forEach { note ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(midiToNoteName(note.midiNote), style = MaterialTheme.typography.bodySmall)
                            Text(
                                "median %.1f Hz · spread %.1f Hz · %d samples".format(
                                    note.observedMedianFrequency,
                                    note.frequencySpread,
                                    note.sampleCount,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            Button(
                onClick = {
                    FileExporter.shareText(context, "calibration.json", calibrationToJson(profile))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Share this profile") }
        }

        ProgressBar(state.progress)
        LevelBar(state.level)

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Text(state.feedback, style = MaterialTheme.typography.bodyLarge)
        state.savedSummary?.let {
            Text(it, style = MaterialTheme.typography.labelMedium)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Mic source", style = MaterialTheme.typography.bodySmall)
            Text(state.sourceLabel, style = MaterialTheme.typography.bodySmall)
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) model.start() else launcher.launch(Manifest.permission.RECORD_AUDIO)
                },
                enabled = !state.running && state.profile == null,
            ) { Text(if (state.running) "Listening" else "Start") }
            OutlinedButton(onClick = { model.stop() }, enabled = state.running) { Text("Pause") }
            if (state.profile == null) {
                TextButton(onClick = { model.skip() }, enabled = state.running) { Text("Skip key") }
            } else {
                TextButton(onClick = { model.restart() }) { Text("Redo") }
            }
        }
        if (state.needsConfirmation) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = { model.acceptProfileAnyway() }) { Text("Use anyway") }
                Text(
                    "Redo is better: a weak profile makes the app guess.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        // Also offered while stopped: a pinned source that fails to start
        // would otherwise leave no way out of the loop.
        TextButton(onClick = { model.switchSource() }) { Text("Change mic") }
    }
}

@Composable
private fun ProgressBar(progress: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(10.dp)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun LevelBar(level: Double) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            Modifier
                .fillMaxWidth(levelFraction(level))
                .height(10.dp)
                .background(MaterialTheme.colorScheme.tertiary),
        )
    }
}
