package com.vijaychhetry.kidspiano.lesson

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = {
                model.stop()
                onHome()
            }) { Text("Home") }
            Text(
                Copy.NOT_CALIBRATED,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        return
    }

    val midi = state.expectedMidi
    val letter = if (state.complete) "★" else midi?.let { letterOf(it) } ?: state.letter
    val subtitle = when {
        state.complete -> "You did it"
        midi == KEY_C4 -> "C4 · middle C"
        midi != null -> midiToNoteName(midi)
        else -> state.noteName
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        val heroSp = (maxHeight.value * 0.22f).coerceIn(40f, 56f).sp
        val promptWidth = maxWidth * 0.34f
        val compactBtn = ButtonDefaults.buttonColors()
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                Modifier
                    .width(promptWidth)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            model.stop()
                            onHome()
                        },
                        contentPadding = ButtonDefaults.TextButtonContentPadding,
                    ) { Text("Home") }
                    Text(
                        if (state.complete) "Done" else "${state.completedCount}/${state.total}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Column {
                    Text(
                        letter,
                        fontSize = heroSp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        lineHeight = heroSp,
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CueBadge(state.cue)
                    Text(
                        state.feedback,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(state.progress.coerceIn(0f, 1f))
                                .height(6.dp)
                                .background(MaterialTheme.colorScheme.secondary),
                        )
                    }
                    state.error?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.complete) {
                            Button(
                                onClick = { model.playAgain() },
                                colors = compactBtn,
                                modifier = Modifier.height(40.dp),
                            ) { Text("Play again") }
                        } else if (!state.running) {
                            Button(
                                onClick = {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO,
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) model.start() else launcher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                modifier = Modifier.height(40.dp),
                            ) { Text("Start") }
                        } else {
                            OutlinedButton(
                                onClick = { model.stop() },
                                modifier = Modifier.height(40.dp),
                            ) { Text("Pause") }
                        }
                    }
                }
            }
            PianoKeyboardView(
                highlightMidi = state.expectedMidi,
                heardMidi = state.heardMidi,
                compact = true,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun CueBadge(cue: LessonCue) {
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
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.2f))
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, fontSize = 18.sp, color = color, fontWeight = FontWeight.Bold)
    }
}
