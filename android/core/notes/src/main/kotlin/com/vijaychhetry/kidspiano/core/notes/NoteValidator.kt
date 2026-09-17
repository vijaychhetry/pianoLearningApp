package com.vijaychhetry.kidspiano.core.notes

import com.vijaychhetry.kidspiano.core.common.RecognitionStatus
import com.vijaychhetry.kidspiano.core.common.RecognizedNote
import com.vijaychhetry.kidspiano.core.common.ValidationResult
import kotlin.math.abs

interface NoteValidator {
    fun validate(expected: Int, detected: RecognizedNote?): ValidationResult
}

/**
 * Beginner policy (spec §5, §44): same letter in another octave is a soft match.
 * Low confidence and between-note pitches must never become INCORRECT or CORRECT.
 */
class DefaultNoteValidator(
    private val highConfidence: Double = 0.7,
    private val maxCentsFromNamedNote: Double = 40.0,
) : NoteValidator {
    override fun validate(expected: Int, detected: RecognizedNote?): ValidationResult {
        if (detected == null) {
            return ValidationResult(
                status = RecognitionStatus.NO_SIGNAL,
                expectedNote = expected,
                detectedNote = null,
                confidence = 0.0,
                message = "I couldn't hear that clearly. Try again.",
            )
        }
        val centsFromNamed = abs(centsOff(detected.frequency, detected.midi))
        if (centsFromNamed > maxCentsFromNamedNote) {
            return retry(RecognitionStatus.AMBIGUOUS, expected, detected)
        }
        if (detected.confidence < highConfidence) {
            return retry(RecognitionStatus.UNCLEAR, expected, detected)
        }
        if (detected.midi == expected) {
            return ValidationResult(
                status = RecognitionStatus.CORRECT,
                expectedNote = expected,
                detectedNote = detected.midi,
                confidence = detected.confidence,
                message = "Yes — ${midiToNoteName(expected)}",
            )
        }
        if (pitchClass(detected.midi) == pitchClass(expected)) {
            return ValidationResult(
                status = RecognitionStatus.CORRECT_OCTAVE_MISMATCH,
                expectedNote = expected,
                detectedNote = detected.midi,
                confidence = detected.confidence,
                message = "Right note, try the other ${midiToNoteName(expected).dropLast(1)}!",
            )
        }
        return ValidationResult(
            status = RecognitionStatus.INCORRECT,
            expectedNote = expected,
            detectedNote = detected.midi,
            confidence = detected.confidence,
            message = "Try ${midiToNoteName(expected)}!",
        )
    }

    private fun retry(
        status: RecognitionStatus,
        expected: Int,
        detected: RecognizedNote,
    ) = ValidationResult(
        status = status,
        expectedNote = expected,
        detectedNote = detected.midi,
        confidence = detected.confidence,
        message = "I couldn't hear that clearly. Try again.",
    )
}
