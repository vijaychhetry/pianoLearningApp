package com.vijaychhetry.kidspiano.core.pitch

import com.vijaychhetry.kidspiano.core.common.PitchResult
import com.vijaychhetry.kidspiano.core.common.RecognizedNote
import com.vijaychhetry.kidspiano.core.notes.midiToNoteName

interface NoteRecognizer {
    fun recognize(pitch: PitchResult): RecognizedNote?
}

class DefaultNoteRecognizer(
    private val minConfidence: Double = 0.55,
) : NoteRecognizer {
    override fun recognize(pitch: PitchResult): RecognizedNote? {
        if (pitch.ambiguous) return null
        val midi = pitch.midiNote ?: return null
        val freq = pitch.frequency ?: return null
        if (pitch.confidence < minConfidence) return null
        return RecognizedNote(
            midi = midi,
            name = midiToNoteName(midi),
            frequency = freq,
            confidence = pitch.confidence,
        )
    }
}
