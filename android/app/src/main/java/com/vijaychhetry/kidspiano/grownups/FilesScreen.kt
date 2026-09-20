package com.vijaychhetry.kidspiano.grownups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vijaychhetry.kidspiano.calibration.CalibrationStore
import com.vijaychhetry.kidspiano.core.diagnostics.calibrationToJson
import com.vijaychhetry.kidspiano.core.notes.lessonSets
import com.vijaychhetry.kidspiano.export.FileExporter
import com.vijaychhetry.kidspiano.export.SessionLogStore

@Composable
fun FilesScreen() {
    val context = LocalContext.current
    val store = remember { CalibrationStore(context) }
    val logStore = remember { SessionLogStore(context) }
    var lines by remember { mutableIntStateOf(logStore.lineCount()) }
    var setOpen by remember { mutableStateOf(false) }
    var lessonLabel by remember {
        mutableStateOf(store.lessonSet().label)
    }
    val profile = remember { store.load() }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Files", style = MaterialTheme.typography.titleLarge)
        Text(
            "Share the calibration profile and the session log. JSON only — no recordings.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(store.summary() ?: "No calibration saved yet.", style = MaterialTheme.typography.bodySmall)
        Text("Practice set (grown-ups only)", style = MaterialTheme.typography.titleMedium)
        Box {
            OutlinedButton(onClick = { setOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Text(lessonLabel)
            }
            DropdownMenu(expanded = setOpen, onDismissRequest = { setOpen = false }) {
                lessonSets().forEach { set ->
                    DropdownMenuItem(
                        text = { Text(set.label) },
                        onClick = {
                            setOpen = false
                            store.saveLessonSetId(set.id)
                            lessonLabel = set.label
                        },
                    )
                }
            }
        }
        Button(
            onClick = {
                val p = store.load()
                if (p != null) {
                    FileExporter.shareText(context, "calibration.json", calibrationToJson(p))
                }
            },
            enabled = profile != null || store.load() != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Share calibration JSON") }
        Text("Session log: $lines lines", style = MaterialTheme.typography.bodySmall)
        Button(
            onClick = {
                FileExporter.shareText(
                    context,
                    "session.jsonl",
                    logStore.readAll().ifBlank { "{}\n" },
                    mime = "application/json",
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Share session log") }
        OutlinedButton(
            onClick = {
                logStore.clear()
                lines = 0
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Clear session log") }
        Text(
            "On GitHub, use Download raw file for the APK. Use the share sheet here to email yourself the logs after a practice session.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
