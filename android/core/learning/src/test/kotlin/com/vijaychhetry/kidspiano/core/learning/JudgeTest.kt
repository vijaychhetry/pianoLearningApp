package com.vijaychhetry.kidspiano.core.learning

import com.vijaychhetry.kidspiano.core.common.OctaveEvidence
import com.vijaychhetry.kidspiano.core.common.PressEvent
import com.vijaychhetry.kidspiano.core.common.PressKind
import com.vijaychhetry.kidspiano.core.common.UnclearReason
import com.vijaychhetry.kidspiano.core.common.Verdict
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class JudgeTest {
    private fun press(
        kind: PressKind = PressKind.NOTE,
        midi: Int? = 60,
        onset: Long = 1_000_000,
        confidence: Double = 0.94,
        clarity: Double = 0.97,
        evidence: OctaveEvidence? = null,
    ) = PressEvent(
        onsetNanos = onset,
        verdictNanos = onset + 90_000_000,
        kind = kind,
        hz = 261.6,
        midi = midi,
        centsOff = -4.0,
        confidence = confidence,
        clarity = clarity,
        levelDb = -31.0,
        octaveEvidence = evidence,
    )

    @ParameterizedTest
    @EnumSource(UnclearReason::class)
    fun acR01_everyUnclearReasonCostsNothing(reason: UnclearReason) {
        val kind = when (reason) {
            UnclearReason.TWO_NOTES -> PressKind.TWO_NOTES
            UnclearReason.NOISY -> PressKind.NOISY
            UnclearReason.LOW_CONFIDENCE -> PressKind.LOW_CONFIDENCE
            UnclearReason.TOO_SHORT -> PressKind.TOO_SHORT
            UnclearReason.TOO_QUIET -> PressKind.TOO_QUIET
            UnclearReason.NO_SIGNAL -> PressKind.LOW_CONFIDENCE
            UnclearReason.OCTAVE_UNSURE -> PressKind.NOTE
        }
        val event = if (reason == UnclearReason.OCTAVE_UNSURE) {
            press(midi = 72, evidence = OctaveEvidence(-34.0, -12.0))
        } else {
            press(kind = kind)
        }
        val v = judge(60, event, promptShownAtNanos = 0)
        assertTrue(v is Verdict.Unclear || (reason == UnclearReason.OCTAVE_UNSURE && v is Verdict.Unclear))
        assertTrue(unclearNeverCosts(v))
    }

    @Test
    fun acR04_aPressThatBeganBeforeThePromptIsIgnoredStale() {
        val v = judge(60, press(onset = 100), promptShownAtNanos = 200)
        assertTrue(v is Verdict.IgnoredStale)
    }

    @Test
    fun acR11_wrongOctaveOnlyWhenDetectedFundamentalIsClearlyAlone() {
        val sure = press(
            midi = 72,
            evidence = OctaveEvidence(expectedFundamentalDb = -40.0, detectedFundamentalDb = -10.0),
        )
        val v = judge(60, sure, 0)
        assertTrue(v is Verdict.WrongOctave, "got $v")
        assertEquals(72, (v as Verdict.WrongOctave).heardMidi)
    }

    @Test
    fun acR12_expectedFundamentalPresentIsJudgedCorrectEvenIfDetectorJumped() {
        val rescued = press(
            midi = 72,
            evidence = OctaveEvidence(expectedFundamentalDb = -12.0, detectedFundamentalDb = -14.0),
        )
        val v = judge(60, rescued, 0)
        assertTrue(v is Verdict.Correct, "got $v")
    }

    @Test
    fun acR01b_lowConfidenceNeverBecomesWrongKey() {
        val v = judge(60, press(midi = 64, confidence = 0.4, clarity = 0.4), 0)
        assertTrue(v is Verdict.Unclear)
        assertEquals(UnclearReason.LOW_CONFIDENCE, (v as Verdict.Unclear).reason)
    }

    @Test
    fun acJudge01_theAskedKeyIsCorrectAndADifferentWhiteKeyIsWrong() {
        assertTrue(judge(60, press(midi = 60), 0) is Verdict.Correct)
        val wrong = judge(60, press(midi = 64), 0)
        assertTrue(wrong is Verdict.WrongKey)
        assertEquals(64, (wrong as Verdict.WrongKey).heardMidi)
    }

    @Test
    fun acR13_secondHarmonicLouderThanFundamentalIsStillCorrect() {
        val rescued = press(
            midi = 72,
            evidence = OctaveEvidence(expectedFundamentalDb = -18.0, detectedFundamentalDb = -6.0),
        )
        val v = judge(60, rescued, 0)
        assertTrue(v is Verdict.Correct, "C4 whose 2nd harmonic dominates at the mic is still C4; got $v")
    }
}
