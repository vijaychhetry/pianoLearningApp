package com.vijaychhetry.kidspiano.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object KidsColors {
    val cream = Color(0xFFFAF6F1)
    val purple = Color(0xFF6B4CFF)
    val ink = Color(0xFF2C2A32)
    val muted = Color(0xFF8B8794)
    val track = Color(0xFFE6DFF8)
    val keyWhite = Color(0xFFFFFCF8)
    val keyBlack = Color(0xFF2A2A2E)
    val keyEdge = Color(0xFFE4DDD4)
    val target = Color(0xFFE23D3D)
    val dimWhite = Color(0xFFE8E2DA)
    val dimBlack = Color(0xFF9A958E)

    private val tints = mapOf(
        48 to Color(0xFFF4B7A6), // C3
        50 to Color(0xFFF5C8A0), // D3
        52 to Color(0xFFBFE8CB), // E3
        53 to Color(0xFFF6E7A3), // F3
        60 to Color(0xFFF4B7A6), // C4
        62 to Color(0xFFF5C8A0), // D4
        65 to Color(0xFFBFE8CB), // F4
        67 to Color(0xFFB8C6F5), // G4
    )

    fun whiteKey(midi: Int, targetMidi: Int?, inFocus: Boolean): Color {
        if (midi == targetMidi) return target
        if (!inFocus) return dimWhite
        return tints[midi] ?: keyWhite
    }

    fun blackKey(midi: Int, targetMidi: Int?, inFocus: Boolean): Color {
        if (midi == targetMidi) return target
        if (!inFocus) return dimBlack
        return keyBlack
    }
}

private val KidsColorScheme = lightColorScheme(
    primary = KidsColors.purple,
    onPrimary = Color.White,
    background = KidsColors.cream,
    onBackground = KidsColors.ink,
    surface = KidsColors.cream,
    onSurface = KidsColors.ink,
    outline = KidsColors.purple,
)

@Composable
fun KidsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KidsColorScheme,
        typography = MaterialTheme.typography.copy(
            displayLarge = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 56.sp,
                color = KidsColors.purple,
            ),
        ),
        content = content,
    )
}
