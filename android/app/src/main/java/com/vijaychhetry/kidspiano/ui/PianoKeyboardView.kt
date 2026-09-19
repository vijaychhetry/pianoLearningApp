package com.vijaychhetry.kidspiano.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vijaychhetry.kidspiano.core.notes.FIVE_KEYS
import com.vijaychhetry.kidspiano.core.notes.LOWER_CLUSTER
import com.vijaychhetry.kidspiano.core.notes.PSR_HIGH_MIDI
import com.vijaychhetry.kidspiano.core.notes.PSR_LOW_MIDI
import com.vijaychhetry.kidspiano.core.notes.blackKeyLeftFraction
import com.vijaychhetry.kidspiano.core.notes.displayNoteName
import com.vijaychhetry.kidspiano.core.notes.hearable
import com.vijaychhetry.kidspiano.core.notes.isWhiteKey
import com.vijaychhetry.kidspiano.core.notes.keyColorHex
import com.vijaychhetry.kidspiano.core.notes.midiToNoteName
import com.vijaychhetry.kidspiano.core.notes.cOctaveMarkers
import com.vijaychhetry.kidspiano.core.notes.whiteKeyLeftFraction
import com.vijaychhetry.kidspiano.core.notes.zoomWindow

/**
 * Two-layer PSR-F52 guide: 61-key mini-map plus a zoomed octave.
 * Not tappable — kids press the real piano (spec v4 §9.3).
 */
@Composable
fun PianoKeyboardView(
    highlightMidi: Int?,
    heardMidi: Int? = null,
    modifier: Modifier = Modifier,
) {
    val target = highlightMidi ?: 60
    Column(
        modifier.semantics {
            contentDescription = "Piano guide. Play ${displayNoteName(target)}."
        },
    ) {
        MiniMap(target, heardMidi)
        CLabels()
        ZoomStrip(target, heardMidi, Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun MiniMap(highlightMidi: Int, heardMidi: Int?) {
    val whites = (PSR_LOW_MIDI..PSR_HIGH_MIDI).filter { isWhiteKey(it) }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFE8E0D8)),
    ) {
        Row(Modifier.fillMaxSize()) {
            whites.forEach { midi ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 0.3.dp)
                        .background(miniFill(midi, highlightMidi), RoundedCornerShape(1.dp)),
                )
            }
        }
        (PSR_LOW_MIDI..PSR_HIGH_MIDI).filter { !isWhiteKey(it) }.forEach { midi ->
            val left = blackKeyLeftFraction(midi, PSR_LOW_MIDI, PSR_HIGH_MIDI)
            Box(
                Modifier
                    .offset(x = maxWidth * left)
                    .width(maxWidth / whites.size * 0.55f)
                    .fillMaxHeight(0.62f)
                    .background(Color(0xFF2A2438), RoundedCornerShape(1.dp)),
            )
        }
        if (heardMidi != null && isWhiteKey(heardMidi)) {
            val idx = whites.indexOf(heardMidi)
            if (idx >= 0) {
                Box(
                    Modifier
                        .offset(x = maxWidth * (idx.toFloat() / whites.size))
                        .width(maxWidth / whites.size)
                        .fillMaxHeight()
                        .border(1.5.dp, Color(0xFF111111), RoundedCornerShape(1.dp)),
                )
            }
        }
    }
}

@Composable
private fun CLabels() {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(16.dp),
    ) {
        cOctaveMarkers().forEach { midi ->
            val left = whiteKeyLeftFraction(midi, PSR_LOW_MIDI, PSR_HIGH_MIDI)
            Text(
                midiToNoteName(midi),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF3C3450),
                modifier = Modifier.offset(x = maxWidth * left),
            )
        }
    }
}

@Composable
private fun ZoomStrip(highlightMidi: Int, heardMidi: Int?, modifier: Modifier = Modifier) {
    val (from, to) = zoomWindow(highlightMidi)
    val whites = (from..to).filter { isWhiteKey(it) }
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFD9D0C6)),
    ) {
        Row(Modifier.fillMaxSize()) {
            whites.forEach { midi ->
                val taught = midi in FIVE_KEYS || midi in LOWER_CLUSTER
                val fill = when {
                    midi == highlightMidi && taught -> hex(keyColorHex(midi))
                    taught -> tint(hex(keyColorHex(midi)), 0.32f)
                    else -> Color(0xFFFFFBF5)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 1.dp)
                        .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                        .background(fill)
                        .then(
                            if (midi == heardMidi) {
                                Modifier.border(2.dp, Color(0xFF111111), RoundedCornerShape(4.dp))
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (taught) {
                        Text(
                            midiToNoteName(midi),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (midi == highlightMidi) Color.White else Color(0xFF3C3450),
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    if (midi == highlightMidi) {
                        Box(
                            Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (taught) hex(keyColorHex(midi)) else MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                displayNoteName(midi),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                            )
                        }
                    }
                }
            }
        }
        (from..to).filter { !isWhiteKey(it) }.forEach { midi ->
            val left = blackKeyLeftFraction(midi, from, to)
            Box(
                Modifier
                    .offset(x = maxWidth * left)
                    .width(maxWidth / whites.size * 0.55f)
                    .fillMaxHeight(0.58f)
                    .clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                    .background(Color(0xFF1B1724)),
            )
        }
    }
}

private fun miniFill(midi: Int, highlight: Int): Color {
    val taught = midi in FIVE_KEYS || midi in LOWER_CLUSTER
    val base = when {
        midi == highlight && taught -> hex(keyColorHex(midi))
        taught -> tint(hex(keyColorHex(midi)), 0.30f)
        !hearable(midi) -> Color(0xFFD0C8C0)
        else -> Color(0xFFFFFBF5)
    }
    return base
}

private fun hex(value: String): Color {
    val raw = value.removePrefix("#")
    return Color(android.graphics.Color.parseColor("#$raw"))
}

private fun tint(color: Color, amount: Float): Color {
    val r = color.red * amount + (1f - amount)
    val g = color.green * amount + (1f - amount)
    val b = color.blue * amount + (1f - amount)
    return Color(r, g, b, 1f)
}
