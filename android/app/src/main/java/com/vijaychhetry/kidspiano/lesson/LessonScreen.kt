package com.vijaychhetry.kidspiano.lesson

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vijaychhetry.kidspiano.calibration.CalibrationStore
import com.vijaychhetry.kidspiano.core.learning.Copy
import com.vijaychhetry.kidspiano.core.learning.LessonCue
import com.vijaychhetry.kidspiano.core.notes.KEY_C4
import com.vijaychhetry.kidspiano.core.notes.letterOf
import com.vijaychhetry.kidspiano.core.notes.midiToNoteName
import com.vijaychhetry.kidspiano.ui.PianoKeyboardView

@Composable
fun LessonScreen(model: LessonViewModel, onHome: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val store = remember { CalibrationStore(context) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) model.start() }
    LaunchedEffect(Unit) {
        model.useProfile(store.load(), store.lessonMidi())
    }
    LaunchedEffect(state.ready) {
        if (!state.ready || state.running || state.complete) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) model.start()
    }

    if (!state.ready) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(onClick = {
                model.stop()
                onHome()
            }) { Text("Home") }
            Text(
                Copy.NOT_CALIBRATED,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        return
    }

    val hero = if (state.complete) {
        "★" to "You did it"
    } else {
        val midi = state.expectedMidi
        val letter = midi?.let { letterOf(it) } ?: state.letter
        val subtitle = when (midi) {
            KEY_C4 -> "C4  ·  middle C"
            null -> state.noteName
            else -> midiToNoteName(midi)
        }
        letter to subtitle
    }

    Row(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            Modifier
                .widthIn(min = 200.dp, max = 320.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = {
                    model.stop()
                    onHome()
                }) { Text("Home") }
                Text(state.lessonSetLabel, style = MaterialTheme.typography.labelSmall)
            }
            Text(
                if (state.complete) "You did it" else "Play this key",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                hero.first,
                fontSize = 72.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            Text(
                hero.second,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CueBadge(state.cue, compact = true)
                Column(Modifier.weight(1f)) {
                    Text(
                        state.feedback,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    state.line2?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Text(
                if (state.complete) "All five keys" else "${state.completedCount} of ${state.total}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(state.progress.coerceIn(0f, 1f))
                        .height(8.dp)
                        .background(MaterialTheme.colorScheme.secondary),
                )
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, maxLines = 2)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.complete) {
                    Button(onClick = { model.playAgain() }) { Text("Play again") }
                } else {
                    if (!state.running) {
                        Button(
                            onClick = {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO,
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) model.start() else launcher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                        ) { Text("Start") }
                    }
                    OutlinedButton(onClick = { model.stop() }, enabled = state.running) {
                        Text("Pause")
                    }
                }
            }
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
        ) {
            PianoKeyboardView(
                highlightMidi = state.expectedMidi,
                heardMidi = state.heardMidi,
                compact = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CueBadge(cue: LessonCue, compact: Boolean = false) {
    val (symbol, description) = when (cue) {
        LessonCue.YES, LessonCue.DONE -> "✓" to "correct"
        LessonCue.TRY -> "↺" to "try again"
        LessonCue.UNCLEAR -> "?" to "not sure"
        LessonCue.OCTAVE -> "↕" to "right letter, other octave"
        LessonCue.LISTEN -> "♪" to "listening"
    }
    val color = when (cue) {
        LessonCue.YES, LessonCue.DONE -> MaterialTheme.colorScheme.secondary
        LessonCue.TRY, LessonCue.OCTAVE -> MaterialTheme.colorScheme.tertiary
        LessonCue.UNCLEAR -> MaterialTheme.colorScheme.primary
        LessonCue.LISTEN -> MaterialTheme.colorScheme.outline
    }
    val size = if (compact) 44.dp else 56.dp
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.2f))
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, fontSize = if (compact) 22.sp else 28.sp, color = color, fontWeight = FontWeight.Bold)
    }
}
