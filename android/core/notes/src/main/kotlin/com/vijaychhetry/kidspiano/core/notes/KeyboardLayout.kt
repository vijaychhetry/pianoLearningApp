package com.vijaychhetry.kidspiano.core.notes

/** True for the five black pitch classes. */
fun isBlackKey(midi: Int): Boolean = when (pitchClass(midi)) {
    1, 3, 6, 8, 10 -> true
    else -> false
}

data class WhiteKeySlot(val midi: Int, val index: Int)

/**
 * A black key's left edge as a fraction of one white-key width, measured from
 * the left of the keyboard. 0.62 of a white key wide is the usual overlay.
 */
data class BlackKeySlot(val midi: Int, val leftInWhiteKeys: Double)

data class KeyboardLayout(
    val whites: List<WhiteKeySlot>,
    val blacks: List<BlackKeySlot>,
) {
    val whiteCount: Int get() = whites.size
}

/**
 * Picture-keyboard geometry for C2–C7 (overview) and C3–C6 (the playing range).
 * Independent of Compose so the key counts can be asserted off-device.
 */
fun keyboardLayout(fromMidi: Int, toMidi: Int): KeyboardLayout {
    require(fromMidi <= toMidi)
    val whites = ArrayList<WhiteKeySlot>()
    val blacks = ArrayList<BlackKeySlot>()
    var whiteIndex = 0
    for (midi in fromMidi..toMidi) {
        if (isBlackKey(midi)) {
            blacks += BlackKeySlot(midi, whiteIndex - 0.35)
        } else {
            whites += WhiteKeySlot(midi, whiteIndex)
            whiteIndex += 1
        }
    }
    return KeyboardLayout(whites, blacks)
}

const val OVERVIEW_FROM_MIDI = 36 // C2
const val OVERVIEW_TO_MIDI = 96 // C7
const val DETAIL_FROM_MIDI = 48 // C3
const val DETAIL_TO_MIDI = 84 // C6

/** Caption under the big letter: C4 is named as middle C. */
fun midiToCaption(midi: Int): String = when (midi) {
    60 -> "C4 · middle C"
    else -> midiToNoteName(midi)
}
