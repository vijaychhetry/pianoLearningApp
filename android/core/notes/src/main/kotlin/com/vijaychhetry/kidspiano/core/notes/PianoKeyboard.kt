package com.vijaychhetry.kidspiano.core.notes

/**
 * PSR-F52 layout: 61 keys, C2 (MIDI 36) to C7 (MIDI 96).
 * Detector hearing range A2–F6 (45–89) is what Calibrate may pick.
 */
const val PSR_LOW_MIDI = 36
const val PSR_HIGH_MIDI = 96
const val HEARABLE_LOW_MIDI = 45 // A2
const val HEARABLE_HIGH_MIDI = 89 // F6

data class KeyboardKey(
    val midi: Int,
    val name: String,
    val white: Boolean,
)

fun psrF52Keys(): List<KeyboardKey> =
    (PSR_LOW_MIDI..PSR_HIGH_MIDI).map { midi ->
        KeyboardKey(midi, midiToNoteName(midi), isWhiteKey(midi))
    }

fun whiteKeys(from: Int = PSR_LOW_MIDI, to: Int = PSR_HIGH_MIDI): List<KeyboardKey> =
    psrF52Keys().filter { it.white && it.midi in from..to }

fun selectableMidi(): List<Int> =
    whiteKeys(HEARABLE_LOW_MIDI, HEARABLE_HIGH_MIDI).map { it.midi }

fun hearable(midi: Int): Boolean = midi in HEARABLE_LOW_MIDI..HEARABLE_HIGH_MIDI

fun defaultLessonMidi(): List<Int> = FIVE_KEYS

fun lowerClusterLessonMidi(): List<Int> = LOWER_CLUSTER

fun lessonSets(): List<Pair<String, List<Int>>> = listOf(
    "C4–G4 (first)" to defaultLessonMidi(),
    "C3–G3 (lower)" to lowerClusterLessonMidi(),
)

/** Zoom strip: C4–C5 for the default lesson set (spec §9.3). */
fun zoomWindow(centerMidi: Int = KEY_C4): Pair<Int, Int> {
    return if (centerMidi in 48..55) 48 to 60 else 60 to 72
}

fun whiteKeyLeftFraction(midi: Int, from: Int, to: Int): Float {
    val whites = (from..to).filter { isWhiteKey(it) }
    val index = whites.indexOf(midi).coerceAtLeast(0)
    return if (whites.isEmpty()) 0f else index.toFloat() / whites.size
}

fun blackKeyLeftFraction(midi: Int, from: Int, to: Int): Float {
    val whites = (from..to).filter { isWhiteKey(it) }
    val whitesToLeft = whites.count { it < midi }
    return if (whites.isEmpty()) 0f else (whitesToLeft.toFloat() / whites.size) - (0.35f / whites.size)
}
