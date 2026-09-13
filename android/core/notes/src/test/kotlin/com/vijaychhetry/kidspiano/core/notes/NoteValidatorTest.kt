package com.vijaychhetry.kidspiano.core.notes

import com.vijaychhetry.kidspiano.core.common.RecognitionStatus
import com.vijaychhetry.kidspiano.core.common.RecognizedNote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * AC-VAL-* — expected-note matching. These fail if the policy rubber-ducks
 * "any detection is correct" or marks unclear audio as INCORRECT.
 */
class NoteValidatorTest {
    private val validator = DefaultNoteValidator()

    @Test
    fun acVal01_highConfidenceExpectedIsCorrect() {
        val result = validator.validate(60, note(60, 261.63, 0.95))
        assertEquals(RecognitionStatus.CORRECT, result.status)
        assertEquals(60, result.detectedNote)
        assertEquals(60, result.expectedNote)
        assertTrue(result.confidence >= 0.95)
    }

    @Test
    fun acVal02_highConfidenceOtherLetterIsIncorrect() {
        val result = validator.validate(60, note(64, 329.63, 0.92))
        assertEquals(RecognitionStatus.INCORRECT, result.status)
        assertEquals(64, result.detectedNote)
        assertEquals(60, result.expectedNote)
    }

    @Test
    fun acVal03_octaveMismatchIsSoftCorrectNotIncorrect() {
        val result = validator.validate(60, note(72, 523.25, 0.9))
        assertEquals(RecognitionStatus.CORRECT_OCTAVE_MISMATCH, result.status)
        assertNotEquals(RecognitionStatus.INCORRECT, result.status)
        assertNotEquals(RecognitionStatus.CORRECT, result.status)
        assertEquals(72, result.detectedNote)
    }

    @Test
    fun acVal04_lowConfidenceWrongLetterIsNeverIncorrect() {
        val result = validator.validate(60, note(62, 293.66, 0.4))
        assertEquals(RecognitionStatus.UNCLEAR, result.status)
        assertNotEquals(RecognitionStatus.INCORRECT, result.status)
        assertNotEquals(RecognitionStatus.CORRECT, result.status)
    }

    @Test
    fun acVal04b_mediumConfidenceSameMidiIsUnclearNotCorrect() {
        val result = validator.validate(60, note(60, 261.63, 0.6))
        assertEquals(RecognitionStatus.UNCLEAR, result.status)
        assertNotEquals(RecognitionStatus.CORRECT, result.status)
        assertNotEquals(RecognitionStatus.CORRECT_OCTAVE_MISMATCH, result.status)
    }

    @Test
    fun acVal04c_mediumConfidenceWrongLetterIsUnclearNotIncorrect() {
        val result = validator.validate(60, note(64, 329.63, 0.6))
        assertEquals(RecognitionStatus.UNCLEAR, result.status)
        assertNotEquals(RecognitionStatus.INCORRECT, result.status)
    }

    @Test
    fun acVal05_pitchBetweenCAndCSharpIsAmbiguousNeverScored() {
        val freq = 269.0
        val midi = freqToMidi(freq)
        assertEquals(60, midi, "fixture: 269 Hz must round to C4 so we test cents-off-center")
        assertTrue(kotlin.math.abs(centsOff(freq, midi)) > 40.0)
        val result = validator.validate(60, note(midi, freq, 0.95))
        assertEquals(RecognitionStatus.AMBIGUOUS, result.status)
        assertNotEquals(RecognitionStatus.CORRECT, result.status)
        assertNotEquals(RecognitionStatus.INCORRECT, result.status)
    }

    @Test
    fun acVal06_missingSignalIsNoSignal() {
        val result = validator.validate(60, null)
        assertEquals(RecognitionStatus.NO_SIGNAL, result.status)
        assertNull(result.detectedNote)
        assertEquals(0.0, result.confidence, 0.0)
    }

    private fun note(midi: Int, freq: Double, confidence: Double) =
        RecognizedNote(midi, midiToNoteName(midi), freq, confidence)
}
