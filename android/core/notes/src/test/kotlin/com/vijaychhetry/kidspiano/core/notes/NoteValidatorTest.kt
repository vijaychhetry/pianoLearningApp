package com.vijaychhetry.kidspiano.core.notes

import com.vijaychhetry.kidspiano.core.common.RecognitionStatus
import com.vijaychhetry.kidspiano.core.common.RecognizedNote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NoteValidatorTest {
    private val validator = DefaultNoteValidator()

    @Test
    fun correctHighConfidence() {
        val result = validator.validate(60, RecognizedNote(60, "C4", 261.6, 0.95))
        assertEquals(RecognitionStatus.CORRECT, result.status)
    }

    @Test
    fun octaveMismatchIsSoftCorrect() {
        val result = validator.validate(60, RecognizedNote(72, "C5", 523.2, 0.9))
        assertEquals(RecognitionStatus.CORRECT_OCTAVE_MISMATCH, result.status)
    }

    @Test
    fun lowConfidenceIsNeverIncorrect() {
        val result = validator.validate(60, RecognizedNote(62, "D4", 293.6, 0.4))
        assertEquals(RecognitionStatus.UNCLEAR, result.status)
    }

    @Test
    fun missingSignal() {
        val result = validator.validate(60, null)
        assertEquals(RecognitionStatus.NO_SIGNAL, result.status)
    }

    @Test
    fun wrongNoteHighConfidence() {
        val result = validator.validate(60, RecognizedNote(64, "E4", 329.6, 0.92))
        assertEquals(RecognitionStatus.INCORRECT, result.status)
    }
}
