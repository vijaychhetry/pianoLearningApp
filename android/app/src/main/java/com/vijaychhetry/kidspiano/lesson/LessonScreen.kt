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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vijaychhetry.kidspiano.calibration.CalibrationStore
import com.vijaychhetry.kidspiano.core.learning.LessonCue
import com.vijaychhetry.kidspiano.core.pitch.levelFraction

@Composable
fun LessonScreen(model: LessonViewModel, onHome: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val store = remember { CalibrationStore(context) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) model.start() }

    LaunchedEffect(Unit) { model.useProfile(store.load()) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TextButton(onClick = {
            model.stop()
            onHome()
        }, modifier = Modifier.align(Alignment.Start)) {
            Text("Home")
        }

        if (!state.ready) {
            Text(
                "Ask a grown-up to set up the piano first.",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            return@Column
        }

        Text(
            if (state.complete) "You did it" else "Play this key",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            if (state.complete) "★" else state.letter,
            fontSize = 96.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
        )
        CueBadge(state.cue)
        Text(
            state.feedback,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            if (state.complete) "All five keys" else "${state.completedCount} of ${state.total}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(state.progress.coerceIn(0f, 1f))
                    .height(10.dp)
                    .background(MaterialTheme.colorScheme.secondary),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(levelFraction(state.level))
                    .height(10.dp)
                    .background(MaterialTheme.colorScheme.tertiary),
            )
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.complete) {
                Button(onClick = { model.playAgain() }) { Text("Play again") }
            } else {
                Button(
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) model.start() else launcher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    enabled = !state.running,
                ) { Text(if (state.running) "Listening" else "Start") }
                OutlinedButton(onClick = { model.stop() }, enabled = state.running) {
                    Text("Pause")
                }
            }
        }
        TextButton(onClick = { model.switchSource() }) { Text("Change mic") }
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
            .size(56.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.2f))
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, fontSize = 28.sp, color = color, fontWeight = FontWeight.Bold)
    }
}
