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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import com.vijaychhetry.kidspiano.core.learning.PracticeFooter
import com.vijaychhetry.kidspiano.core.learning.practiceChrome
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
        model.useProfile(store.load(), store.lessonSetId())
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

    val chrome = practiceChrome(
        complete = state.complete,
        running = state.running,
        shortLabel = state.lessonSetShort,
        completedCount = state.completedCount,
        total = state.total,
        nextLabel = state.nextLabel,
        expectedMidi = state.expectedMidi,
        letter = state.letter,
        noteName = state.noteName,
    )

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        val heroSp = (maxHeight.value * 0.12f).coerceIn(28f, 44f).sp
        val pianoHeight = (maxHeight * 0.28f).coerceIn(88.dp, 120.dp)
        val compactBtn = ButtonDefaults.buttonColors()
        Column(
            Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
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
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) { Text("Home") }
                if (chrome.doneIsButton) {
                    TextButton(
                        onClick = {
                            model.stop()
                            onHome()
                        },
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) { Text(Copy.DONE) }
                } else {
                    Text(
                        chrome.counterText.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            PianoKeyboardView(
                highlightMidi = state.expectedMidi,
                heardMidi = state.heardMidi,
                compact = true,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(pianoHeight),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                chrome.hero.letter,
                fontSize = heroSp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                lineHeight = heroSp,
            )
            Text(
                chrome.hero.subtitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(0.86f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                CueBadge(state.cue)
                Text(
                    state.feedback,
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            chrome.nextHint?.let { hint ->
                Text(
                    hint,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .fillMaxWidth(0.7f)
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
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (chrome.footer) {
                    PracticeFooter.COMPLETE -> {
                        if (chrome.playAgain) {
                            OutlinedButton(
                                onClick = { model.playAgain() },
                                modifier = Modifier.height(40.dp),
                            ) { Text(Copy.PLAY_AGAIN) }
                        }
                        chrome.nextButtonLabel?.let { label ->
                            Button(
                                onClick = { model.nextCourse { store.saveLessonSetId(it) } },
                                colors = compactBtn,
                                modifier = Modifier.height(40.dp),
                            ) { Text(label) }
                        }
                    }
                    PracticeFooter.START -> {
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
                    }
                    PracticeFooter.PAUSE -> {
                        OutlinedButton(
                            onClick = { model.stop() },
                            modifier = Modifier.height(40.dp),
                        ) { Text("Pause") }
                    }
                }
            }
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
