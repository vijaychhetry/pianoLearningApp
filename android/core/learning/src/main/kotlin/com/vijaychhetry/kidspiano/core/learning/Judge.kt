package com.vijaychhetry.kidspiano.core.learning

import com.vijaychhetry.kidspiano.core.common.Config
import com.vijaychhetry.kidspiano.core.common.PressEvent
import com.vijaychhetry.kidspiano.core.common.PressKind
import com.vijaychhetry.kidspiano.core.common.UnclearReason
import com.vijaychhetry.kidspiano.core.common.Verdict
import com.vijaychhetry.kidspiano.core.notes.pitchClass
import kotlin.math.abs

/**
 * expected vs press → Verdict (spec v4 §4.7). Never returns WrongKey when
 * the press is not a confident named note.
 */
fun judge(
    expectedMidi: Int,
    press: PressEvent,
    promptShownAtNanos: Long,
    octaveFundamentalDb: Double = Config.OCTAVE_FUNDAMENTAL_DB,
    minConfidence: Double = Config.MIN_CONFIDENCE,
    minClarity: Double = Config.MIN_CLARITY,
): Verdict {
    if (press.onsetNanos < promptShownAtNanos) return Verdict.IgnoredStale(press)
    return when (press.kind) {
        PressKind.TWO_NOTES -> Verdict.Unclear(UnclearReason.TWO_NOTES, press)
        PressKind.NOISY -> Verdict.Unclear(UnclearReason.NOISY, press)
        PressKind.LOW_CONFIDENCE -> Verdict.Unclear(UnclearReason.LOW_CONFIDENCE, press)
        PressKind.TOO_SHORT -> Verdict.Unclear(UnclearReason.TOO_SHORT, press)
        PressKind.TOO_QUIET -> Verdict.Unclear(UnclearReason.TOO_QUIET, press)
        PressKind.NOTE -> judgeNote(expectedMidi, press, octaveFundamentalDb, minConfidence, minClarity)
    }
}

private fun judgeNote(
    expectedMidi: Int,
    press: PressEvent,
    octaveFundamentalDb: Double,
    minConfidence: Double,
    minClarity: Double,
): Verdict {
    if (press.confidence < minConfidence || press.clarity < minClarity) {
        return Verdict.Unclear(UnclearReason.LOW_CONFIDENCE, press)
    }
    val heard = press.midi ?: return Verdict.Unclear(UnclearReason.NO_SIGNAL, press)
    if (heard == expectedMidi) return Verdict.Correct(press)
    if (pitchClass(heard) == pitchClass(expectedMidi) && abs(heard - expectedMidi) % 12 == 0) {
        val ev = press.octaveEvidence
            ?: return Verdict.Unclear(UnclearReason.OCTAVE_UNSURE, press)
        val delta = ev.detectedFundamentalDb - ev.expectedFundamentalDb
        return when {
            delta <= octaveFundamentalDb -> Verdict.Correct(press)
            delta >= octaveFundamentalDb * 2 -> Verdict.WrongOctave(heard, press)
            else -> Verdict.Unclear(UnclearReason.OCTAVE_UNSURE, press)
        }
    }
    return Verdict.WrongKey(heard, press)
}

fun unclearNeverCosts(verdict: Verdict): Boolean = verdict is Verdict.Unclear || verdict is Verdict.IgnoredStale
