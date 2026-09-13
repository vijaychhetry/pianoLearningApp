package com.vijaychhetry.kidspiano.core.pitch

import com.vijaychhetry.kidspiano.core.common.AudioFrame
import com.vijaychhetry.kidspiano.core.common.RecognitionStatus
import com.vijaychhetry.kidspiano.core.common.ValidationResult
import com.vijaychhetry.kidspiano.core.notes.DefaultNoteValidator
import com.vijaychhetry.kidspiano.core.notes.NoteValidator

/**
 * Detect → recognize → validate. Ambiguous / low-confidence frames never
 * become INCORRECT (spec §5, §6 — each stage independently testable).
 */
class RecognitionPipeline(
    private val detector: PitchDetector,
    private val recognizer: NoteRecognizer = DefaultNoteRecognizer(),
    private val validator: NoteValidator = DefaultNoteValidator(),
) {
    fun evaluate(frame: AudioFrame, expectedMidi: Int): ValidationResult {
        val pitch = detector.detect(frame)
        if (pitch.ambiguous) {
            return ValidationResult(
                status = RecognitionStatus.AMBIGUOUS,
                expectedNote = expectedMidi,
                detectedNote = pitch.midiNote,
                confidence = pitch.confidence,
                message = "I couldn't hear that clearly. Try again.",
            )
        }
        return validator.validate(expectedMidi, recognizer.recognize(pitch))
    }
}
