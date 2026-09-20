package com.vijaychhetry.kidspiano.calibration

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vijaychhetry.kidspiano.core.pitch.levelFraction
import com.vijaychhetry.kidspiano.ui.KidsColors
import com.vijaychhetry.kidspiano.ui.KidsTheme
import com.vijaychhetry.kidspiano.ui.PictureKeyboard

@Composable
fun CalibrationScreen(
    model: CalibrationViewModel = viewModel(),
    onHome: () -> Unit = {},
) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val store = remember { CalibrationStore(context) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) model.start() }

    LaunchedEffect(Unit) { model.setSavedSummary(store.summary()) }
    LaunchedEffect(state.profileToSave) {
        state.profileToSave?.let {
            store.save(it)
            model.setSavedSummary(store.summary())
        }
    }

    KidsTheme {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(KidsColors.cream)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // Tight top, short keys: the landscape bug was cream padding plus a
            // keyboard that ate the first third of the screen.
            val shortScreen = maxHeight < 360.dp
            val overviewH = if (shortScreen) 22.dp else 26.dp
            val detailH = if (shortScreen) 72.dp else 88.dp

            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp, bottom = 8.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = {
                        model.stop()
                        onHome()
                    }) {
                        Text(
                            "Home",
                            color = KidsColors.purple,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                        )
                    }
                    Text(
                        "${state.notesCompleted}/${state.notesTotal}",
                        color = KidsColors.ink,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                    )
                }

                PictureKeyboard(
                    targetMidi = state.targetMidi,
                    overviewHeight = overviewH,
                    detailHeight = detailH,
                    modifier = Modifier.fillMaxWidth(),
                )

                val profile = state.profile
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (profile == null) {
                        Text(
                            state.askLetter,
                            style = MaterialTheme.typography.displayLarge,
                            color = KidsColors.purple,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            state.askCaption,
                            color = KidsColors.ink,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    } else {
                        Text(
                            profile.calibrationQuality.name.replace('_', ' '),
                            color = KidsColors.purple,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                        )
                        Text(
                            state.feedback,
                            color = KidsColors.ink,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                ThinBar(fill = state.progress, color = KidsColors.purple)
                Spacer(Modifier.height(4.dp))
                ThinBar(fill = levelFraction(state.level), color = KidsColors.track)

                state.error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (profile == null) {
                    Text(
                        state.feedback,
                        color = KidsColors.muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp).align(Alignment.CenterHorizontally),
                    )
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (profile == null) {
                        PillButton(
                            label = if (state.running) "Pause" else "Start",
                            filled = !state.running,
                            onClick = {
                                if (state.running) {
                                    model.stop()
                                } else {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO,
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) model.start() else launcher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                        )
                        TextButton(onClick = { model.skip() }, enabled = state.running) {
                            Text("Skip key", color = KidsColors.muted, fontSize = 13.sp)
                        }
                    } else {
                        PillButton(label = "Redo", filled = false, onClick = { model.restart() })
                        if (state.needsConfirmation) {
                            PillButton(
                                label = "Use anyway",
                                filled = true,
                                onClick = { model.acceptProfileAnyway() },
                            )
                        }
                    }
                }
                TextButton(
                    onClick = { model.switchSource() },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(
                        "Change mic · ${state.sourceLabel}",
                        color = KidsColors.muted,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThinBar(fill: Float, color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(KidsColors.track),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fill.coerceIn(0f, 1f))
                .height(6.dp)
                .background(color),
        )
    }
}

@Composable
private fun PillButton(label: String, filled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .widthIn(min = 96.dp)
            .then(
                if (filled) {
                    Modifier.background(KidsColors.purple, shape)
                } else {
                    Modifier.border(1.dp, KidsColors.purple, shape)
                },
            )
            .padding(horizontal = 8.dp),
    ) {
        Text(
            label,
            color = if (filled) androidx.compose.ui.graphics.Color.White else KidsColors.purple,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
