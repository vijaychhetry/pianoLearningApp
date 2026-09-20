package com.vijaychhetry.kidspiano.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vijaychhetry.kidspiano.core.notes.DETAIL_FROM_MIDI
import com.vijaychhetry.kidspiano.core.notes.DETAIL_TO_MIDI
import com.vijaychhetry.kidspiano.core.notes.OVERVIEW_FROM_MIDI
import com.vijaychhetry.kidspiano.core.notes.OVERVIEW_TO_MIDI
import com.vijaychhetry.kidspiano.core.notes.keyboardLayout
import com.vijaychhetry.kidspiano.core.notes.midiToNoteName
import com.vijaychhetry.kidspiano.core.notes.pitchClass

/**
 * Compact picture keyboard: a thin full-range strip (C2–C7) plus a shorter
 * playing keyboard (C3–C6). Heights are fixed so landscape phones do not
 * spend the top of the screen on empty cream and oversized keys.
 */
@Composable
fun PictureKeyboard(
    targetMidi: Int?,
    modifier: Modifier = Modifier,
    overviewHeight: Dp = 28.dp,
    detailHeight: Dp = 88.dp,
) {
    Column(modifier.fillMaxWidth()) {
        PianoStrip(
            fromMidi = OVERVIEW_FROM_MIDI,
            toMidi = OVERVIEW_TO_MIDI,
            targetMidi = targetMidi,
            showKeyLabels = false,
            showOctaveLabels = true,
            focusFrom = DETAIL_FROM_MIDI,
            focusTo = DETAIL_TO_MIDI,
            modifier = Modifier.fillMaxWidth().height(overviewHeight),
        )
        Spacer(Modifier.height(8.dp))
        PianoStrip(
            fromMidi = DETAIL_FROM_MIDI,
            toMidi = DETAIL_TO_MIDI,
            targetMidi = targetMidi,
            showKeyLabels = true,
            showOctaveLabels = false,
            modifier = Modifier.fillMaxWidth().height(detailHeight),
        )
    }
}

@Composable
internal fun PianoStrip(
    fromMidi: Int,
    toMidi: Int,
    targetMidi: Int?,
    showKeyLabels: Boolean,
    showOctaveLabels: Boolean,
    modifier: Modifier = Modifier,
    focusFrom: Int = fromMidi,
    focusTo: Int = toMidi,
) {
    val layout = keyboardLayout(fromMidi, toMidi)
    val measurer = rememberTextMeasurer()
    Box(modifier) {
        Canvas(Modifier.fillMaxSize().padding(bottom = if (showOctaveLabels) 12.dp else 0.dp)) {
            val whiteW = size.width / layout.whiteCount
            val blackH = size.height * 0.55f
            val blackW = whiteW * 0.58f
            val corner = CornerRadius(4.dp.toPx(), 4.dp.toPx())

            for (white in layout.whites) {
                val inFocus = white.midi in focusFrom..focusTo
                val left = white.index * whiteW
                drawRoundRect(
                    color = KidsColors.whiteKey(white.midi, targetMidi, inFocus),
                    topLeft = Offset(left + 0.5f, 0f),
                    size = Size(whiteW - 1f, size.height),
                    cornerRadius = corner,
                )
                drawRoundRect(
                    color = KidsColors.keyEdge,
                    topLeft = Offset(left + 0.5f, 0f),
                    size = Size(whiteW - 1f, size.height),
                    cornerRadius = corner,
                    style = Stroke(width = 1.dp.toPx()),
                )
                if (showKeyLabels && shouldLabel(white.midi)) {
                    val label = midiToNoteName(white.midi)
                    val layoutResult = measurer.measure(
                        label,
                        style = TextStyle(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (white.midi == targetMidi) androidx.compose.ui.graphics.Color.White else KidsColors.ink,
                        ),
                    )
                    drawText(
                        layoutResult,
                        topLeft = Offset(
                            left + (whiteW - layoutResult.size.width) / 2f,
                            size.height - layoutResult.size.height - 6.dp.toPx(),
                        ),
                    )
                }
            }

            for (black in layout.blacks) {
                val inFocus = black.midi in focusFrom..focusTo
                val left = (black.leftInWhiteKeys * whiteW).toFloat()
                drawRoundRect(
                    color = KidsColors.blackKey(black.midi, targetMidi, inFocus),
                    topLeft = Offset(left, 0f),
                    size = Size(blackW, blackH),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                )
            }
        }
        if (showOctaveLabels) {
            Canvas(Modifier.fillMaxSize()) {
                val whiteW = size.width / layout.whiteCount
                for (white in layout.whites) {
                    if (pitchClass(white.midi) != 0) continue
                    val label = midiToNoteName(white.midi)
                    val layoutResult = measurer.measure(
                        label,
                        style = TextStyle(fontSize = 8.sp, color = KidsColors.muted),
                    )
                    val left = white.index * whiteW
                    drawText(
                        layoutResult,
                        topLeft = Offset(
                            left + (whiteW - layoutResult.size.width) / 2f,
                            size.height - layoutResult.size.height,
                        ),
                    )
                }
            }
        }
    }
}

private fun shouldLabel(midi: Int): Boolean {
    val pc = pitchClass(midi)
    val octave = midi / 12 - 1
    return pc in listOf(0, 2, 4, 5, 7) && octave in 3..4
}
