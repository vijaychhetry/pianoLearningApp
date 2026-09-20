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

const val DEFAULT_LESSON_SET_ID = "c4g4"

data class LessonSet(
    val id: String,
    val label: String,
    val shortLabel: String,
    val notes: List<Int>,
    val shuffle: Boolean = false,
    val completionCopy: String,
    val subtitle: String,
)

fun defaultLessonMidi(): List<Int> = FIVE_KEYS

fun lowerClusterLessonMidi(): List<Int> = LOWER_CLUSTER

fun lessonSets(): List<LessonSet> = listOf(
    LessonSet(
        id = DEFAULT_LESSON_SET_ID,
        label = "C4–G4 (first)",
        shortLabel = "C4–G4",
        notes = defaultLessonMidi(),
        completionCopy = "You found all five keys!",
        subtitle = "C D E F G in order, middle of the piano",
    ),
    LessonSet(
        id = "c4g4-mix",
        label = "C4–G4 mix",
        shortLabel = "Mix",
        notes = defaultLessonMidi(),
        shuffle = true,
        completionCopy = "You mixed up C D E F G!",
        subtitle = "Same five keys, mixed up",
    ),
    LessonSet(
        id = "c3g3",
        label = "C3–G3 (lower)",
        shortLabel = "C3–G3",
        notes = lowerClusterLessonMidi(),
        completionCopy = "You found the lower five keys!",
        subtitle = "C D E F G one octave lower",
    ),
)

fun lessonSetById(id: String): LessonSet =
    lessonSets().firstOrNull { it.id == id } ?: lessonSets().first()

fun nextLessonSet(id: String): LessonSet? {
    val sets = lessonSets()
    val index = sets.indexOfFirst { it.id == id }
    if (index < 0 || index >= sets.lastIndex) return null
    return sets[index + 1]
}

fun promptsFor(set: LessonSet, seed: Long): List<Int> {
    if (!set.shuffle) return set.notes
    val out = set.notes.toMutableList()
    out.shuffle(kotlin.random.Random(seed))
    if (out == set.notes && out.size > 1) {
        val first = out.removeAt(0)
        out.add(first)
    }
    return out
}

/** Zoom strip: ~15 whites around the target, always including it. */
fun zoomWindow(centerMidi: Int = KEY_C4): Pair<Int, Int> {
    val whites = (PSR_LOW_MIDI..PSR_HIGH_MIDI).filter { isWhiteKey(it) }
    val idx = whites.indexOf(centerMidi).let { if (it < 0) whites.indexOf(KEY_C4) else it }
    var start = (idx - 7).coerceAtLeast(0)
    var end = (start + 14).coerceAtMost(whites.lastIndex)
    start = (end - 14).coerceAtLeast(0)
    return whites[start] to whites[end]
}

fun cOctaveMarkers(): List<Int> = listOf(36, 48, 60, 72, 84, 96)

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
