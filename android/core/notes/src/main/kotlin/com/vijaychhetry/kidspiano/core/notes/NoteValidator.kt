package com.vijaychhetry.kidspiano.core.notes

import com.vijaychhetry.kidspiano.core.common.RecognitionStatus
import com.vijaychhetry.kidspiano.core.common.RecognizedNote
import com.vijaychhetry.kidspiano.core.common.ValidationResult

interface NoteValidator {
    fun validate(expected: Int, detected: RecognizedNote?): ValidationResult
}

/**
 * Beginner policy (spec §44): same letter in another octave is a soft match,
 * never treat low confidence as INCORRECT.
 */
class DefaultNoteValidator(
    private val highConfidence: Double = 0.7,
    private val unclearBelow: Double = 0.55,
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
        if (detected.confidence < unclearBelow) {
            return ValidationResult(
                status = RecognitionStatus.UNCLEAR,
                expectedNote = expected,
                detectedNote = detected.midi,
                confidence = detected.confidence,
                message = "I couldn't hear that clearly. Try again.",
            )
        }
        if (detected.midi == expected && detected.confidence >= highConfidence) {
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
        if (detected.confidence < highConfidence) {
            return ValidationResult(
                status = RecognitionStatus.UNCLEAR,
                expectedNote = expected,
                detectedNote = detected.midi,
                confidence = detected.confidence,
                message = "I couldn't hear that clearly. Try again.",
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
}
