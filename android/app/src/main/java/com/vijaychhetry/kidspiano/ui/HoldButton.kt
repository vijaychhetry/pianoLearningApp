package com.vijaychhetry.kidspiano.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vijaychhetry.kidspiano.core.common.Config
import kotlinx.coroutines.delay

@Composable
fun HoldButton(
    label: String,
    holdLabel: String = "Keep holding…",
    holdMs: Long = Config.GATE_HOLD_MS,
    enabled: Boolean = true,
    textButton: Boolean = false,
    modifier: Modifier = Modifier,
    onHeld: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(pressed, enabled) {
        if (!pressed || !enabled) {
            progress = 0f
            return@LaunchedEffect
        }
        val started = System.nanoTime()
        while (true) {
            val elapsed = (System.nanoTime() - started) / 1_000_000L
            progress = (elapsed.toFloat() / holdMs).coerceIn(0f, 1f)
            if (elapsed >= holdMs) {
                progress = 0f
                onHeld()
                return@LaunchedEffect
            }
            delay(16)
        }
    }

    Column(modifier.fillMaxWidth()) {
        if (textButton) {
            TextButton(
                onClick = {},
                enabled = enabled,
                interactionSource = interaction,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (pressed) holdLabel else label) }
        } else {
            Button(
                onClick = {},
                enabled = enabled,
                interactionSource = interaction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) { Text(if (pressed) holdLabel else label) }
        }
        if (pressed) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )
        } else {
            Text(
                "Hold for 2 seconds",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
