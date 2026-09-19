package com.vijaychhetry.kidspiano.core.notes

/** Spec v4 §2.1. Same colour for the same key everywhere. */
const val KEY_C4 = 60
const val KEY_D4 = 62
const val KEY_E4 = 64
const val KEY_F4 = 65
const val KEY_G4 = 67

val FIVE_KEYS: List<Int> = listOf(KEY_C4, KEY_D4, KEY_E4, KEY_F4, KEY_G4)
val LOWER_CLUSTER: List<Int> = listOf(48, 50, 52, 53, 55)

data class KeyColor(val hex: String, val inkOnColor: Boolean)

private val COLORS = mapOf(
    KEY_C4 to KeyColor("#E5484D", inkOnColor = true),
    KEY_D4 to KeyColor("#F2731E", inkOnColor = true),
    KEY_E4 to KeyColor("#F1B92B", inkOnColor = true),
    KEY_F4 to KeyColor("#2FA66A", inkOnColor = false),
    KEY_G4 to KeyColor("#3B82E0", inkOnColor = false),
    48 to KeyColor("#E5484D", inkOnColor = true),
    50 to KeyColor("#F2731E", inkOnColor = true),
    52 to KeyColor("#F1B92B", inkOnColor = true),
    53 to KeyColor("#2FA66A", inkOnColor = false),
    55 to KeyColor("#3B82E0", inkOnColor = false),
)

fun keyColor(midi: Int): KeyColor =
    COLORS[midi] ?: throw IllegalArgumentException("no colour for MIDI $midi")

fun keyColorHex(midi: Int): String = keyColor(midi).hex

fun displayNoteName(midi: Int): String {
    val name = midiToNoteName(midi)
    return if (midi == KEY_C4) "C4 (middle C)" else name
}

fun letterOf(midi: Int): String = midiToNoteName(midi).dropLast(1)

/** White-key steps from [fromMidi] to [toMidi], matching "two keys to the left". */
fun whiteKeysBetween(fromMidi: Int, toMidi: Int): Int {
    val lo = minOf(fromMidi, toMidi)
    val hi = maxOf(fromMidi, toMidi)
    var count = 0
    for (m in lo..hi) {
        if (isWhiteKey(m)) count++
    }
    return (count - 1).coerceAtLeast(0)
}

fun isWhiteKey(midi: Int): Boolean {
    val pc = pitchClass(midi)
    return pc == 0 || pc == 2 || pc == 4 || pc == 5 || pc == 7 || pc == 9 || pc == 11
}

fun numberWord(n: Int): String = when (n) {
    1 -> "one"
    2 -> "two"
    3 -> "three"
    4 -> "four"
    5 -> "five"
    6 -> "six"
    7 -> "seven"
    8 -> "eight"
    9 -> "nine"
    10 -> "ten"
    else -> n.toString()
}

fun distanceSentence(heardMidi: Int, expectedMidi: Int): String? {
    if (!isWhiteKey(heardMidi) || !isWhiteKey(expectedMidi)) return null
    val steps = whiteKeysBetween(heardMidi, expectedMidi)
    if (steps == 0) return null
    val word = if (steps == 1) "key" else "keys"
    return if (heardMidi > expectedMidi) {
        "${midiToNoteName(expectedMidi)} is ${numberWord(steps)} $word to the left ←"
    } else {
        "${midiToNoteName(expectedMidi)} is ${numberWord(steps)} $word to the right →"
    }
}
