package com.vijaychhetry.kidspiano.grownups

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vijaychhetry.kidspiano.BuildConfig
import com.vijaychhetry.kidspiano.calibration.CalibrationStore
import com.vijaychhetry.kidspiano.core.notes.FIVE_KEYS
import com.vijaychhetry.kidspiano.core.notes.displayNoteName
import com.vijaychhetry.kidspiano.core.notes.keyColorHex
import com.vijaychhetry.kidspiano.core.notes.letterOf
import com.vijaychhetry.kidspiano.export.FileExporter

@Composable
fun SetupScreen() {
    val context = LocalContext.current
    val store = remember { CalibrationStore(context) }
    val calibrated = store.load()?.usableAsDefault == true
    val steps = listOf(
        "Put the phone on the piano music stand, mic toward the keys." to true,
        "Stick the five colour labels on C4 D4 E4 F4 G4 (middle C and the four keys to the right)." to true,
        "Calibrate: four separate presses of each key." to calibrated,
        "Kids Home → Practice. Play the glowing key." to calibrated,
    )
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Setup", style = MaterialTheme.typography.titleLarge)
        Text(
            "Yamaha PSR-F52 · Kids Piano ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
        )
        steps.forEachIndexed { i, (text, done) ->
            Text(
                "${i + 1}. ${if (done) "✓ " else ""}$text",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Text("Sticker colours", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        FIVE_KEYS.forEach { midi ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(android.graphics.Color.parseColor(keyColorHex(midi)))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(letterOf(midi), color = Color.White, fontWeight = FontWeight.Black)
                }
                Text(displayNoteName(midi), style = MaterialTheme.typography.bodyLarge)
            }
        }
        Button(
            onClick = {
                val body = FIVE_KEYS.joinToString("\n") { midi ->
                    "${letterOf(midi)}  ${displayNoteName(midi)}  ${keyColorHex(midi)}"
                }
                FileExporter.shareText(context, "stickers.txt", body, mime = "text/plain")
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Share sticker labels") }
        Text(
            "Colour is never the only signal. The letter and the on-screen keyboard say which key to press.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
