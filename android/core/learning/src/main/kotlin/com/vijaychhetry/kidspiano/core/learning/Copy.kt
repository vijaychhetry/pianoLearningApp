package com.vijaychhetry.kidspiano.core.learning

import com.vijaychhetry.kidspiano.core.common.UnclearReason
import com.vijaychhetry.kidspiano.core.common.Verdict
import com.vijaychhetry.kidspiano.core.notes.distanceSentence
import com.vijaychhetry.kidspiano.core.notes.displayNoteName
import com.vijaychhetry.kidspiano.core.notes.midiToNoteName

/** Spec v4 §10 copy deck. Use these strings or shorter. */
object Copy {
    const val UNCLEAR = "I couldn't hear that. One key at a time."
    const val TOO_SHORT = "I heard something. Hold the key a bit longer."
    const val NUDGE = "Still there? Press any key."
    const val MIC_PAUSED = "Tap to keep playing."
    const val HINT = "Play the glowing key."
    const val NOT_CALIBRATED = "Ask a grown-up to help wake up the piano first!"
    const val READY = "Piano is ready"
    const val SONG_OFFER = "You can play a song now"
    const val EXIT_DONE = "Done for today"
    const val EXIT_MORE = "One more"
    const val GATE_WRONG = "That is not the number."
    const val SONG_HINT = "Play the big note. No hurry."

    fun playThis(midi: Int): String = "Play the ${displayNoteName(midi)} key."

    fun yesNow(nextMidi: Int, sameAsLast: Boolean): String =
        if (sameAsLast) "Yes! ${midiToNoteName(nextMidi)} again."
        else "Yes! Now ${midiToNoteName(nextMidi)}."

    fun wrongKey(heardMidi: Int, expectedMidi: Int): Pair<String, String?> {
        val line1 = "That was ${midiToNoteName(heardMidi)}."
        val line2 = distanceSentence(heardMidi, expectedMidi)
            ?: "Press ${midiToNoteName(expectedMidi)}."
        return line1 to line2
    }

    fun wrongOctave(heardMidi: Int, expectedMidi: Int): Pair<String, String> =
        "That was ${midiToNoteName(heardMidi)}." to "Press ${midiToNoteName(expectedMidi)}."

    fun unclear(reason: UnclearReason): String = when (reason) {
        UnclearReason.TOO_SHORT -> TOO_SHORT
        UnclearReason.TOO_QUIET, UnclearReason.NO_SIGNAL -> UNCLEAR
        else -> UNCLEAR
    }

    fun fromVerdict(verdict: Verdict, expectedMidi: Int, nextMidi: Int?): Pair<String, String?> =
        when (verdict) {
            is Verdict.Correct -> {
                val next = nextMidi ?: expectedMidi
                yesNow(next, next == expectedMidi) to null
            }
            is Verdict.WrongKey -> wrongKey(verdict.heardMidi, expectedMidi)
            is Verdict.WrongOctave -> wrongOctave(verdict.heardMidi, expectedMidi)
            is Verdict.Unclear -> unclear(verdict.reason) to "One key at a time."
            is Verdict.IgnoredStale -> "" to null
        }
}
