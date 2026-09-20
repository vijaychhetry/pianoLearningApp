package com.vijaychhetry.kidspiano.core.pitch

import com.vijaychhetry.kidspiano.core.common.AudioFrame
import com.vijaychhetry.kidspiano.core.common.PitchResult
import com.vijaychhetry.kidspiano.core.common.RecognitionStatus
import com.vijaychhetry.kidspiano.core.notes.midiToFreq
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val SR = 44100

class NoteRecognizerTest {
    private val recognizer = DefaultNoteRecognizer()

    @Test
    fun acRec01_stablePitchBecomesNamedNote() {
        val note = recognizer.recognize(pitch(midi = 64, freq = midiToFreq(64), confidence = 0.9))
        assertNotNull(note)
        assertEquals(64, note!!.midi)
        assertEquals("E4", note.name)
        assertEquals(midiToFreq(64), note.frequency, 0.01)
        assertEquals(0.9, note.confidence, 0.0)
    }

    @Test
    fun acRec02_ambiguousPitchIsNotGuessed() {
        val note = recognizer.recognize(
            pitch(midi = 60, freq = midiToFreq(60), confidence = 0.95, ambiguous = true),
        )
        assertNull(note)
    }

    @Test
    fun acRec03_lowConfidenceIsNotANote() {
        assertNull(recognizer.recognize(pitch(midi = 60, freq = midiToFreq(60), confidence = 0.4)))
    }
}

class RecognitionPipelineTest {
    private val pipeline = RecognitionPipeline(YinHpsPitchDetector())

    @Test
    fun acPipe01_c4ToneExpectedC4IsCorrect() {
        val result = pipeline.evaluate(pianoFrame(midiToFreq(60)), expectedMidi = 60)
        assertEquals(RecognitionStatus.CORRECT, result.status)
        assertEquals(60, result.detectedNote)
        assertEquals(60, result.expectedNote)
        assertTrue(result.confidence > 0.7)
    }

    @Test
    fun acPipe01b_c4ToneExpectedD4IsIncorrect() {
        val result = pipeline.evaluate(pianoFrame(midiToFreq(60)), expectedMidi = 62)
        assertEquals(RecognitionStatus.INCORRECT, result.status)
        assertEquals(60, result.detectedNote)
        assertEquals(62, result.expectedNote)
    }

    @Test
    fun acPipe02_twoNoteMixIsAmbiguousNeverIncorrect() {
        val mix = mixTones(
            sineTone(midiToFreq(60), SR, 0.6, amplitude = 0.35f),
            sineTone(midiToFreq(64), SR, 0.6, amplitude = 0.35f),
        )
        val result = pipeline.evaluate(slice(mix), expectedMidi = 60)
        assertEquals(RecognitionStatus.AMBIGUOUS, result.status)
        assertNotEquals(RecognitionStatus.INCORRECT, result.status)
        assertNotEquals(RecognitionStatus.CORRECT, result.status)
    }

    @Test
    fun acPipe03_silenceExpectedC4IsNoSignalNeverIncorrect() {
        val result = pipeline.evaluate(AudioFrame(FloatArray(2048), SR, 0), expectedMidi = 60)
        assertEquals(RecognitionStatus.NO_SIGNAL, result.status)
        assertNotEquals(RecognitionStatus.INCORRECT, result.status)
        assertNull(result.detectedNote)
        assertEquals(0.0, result.confidence, 0.0)
    }
}

private fun pitch(
    midi: Int?,
    freq: Double?,
    confidence: Double,
    ambiguous: Boolean = false,
) = PitchResult(
    frequency = freq,
    midiNote = midi,
    confidence = confidence,
    clarity = confidence,
    signalStrength = 0.2,
    stability = confidence,
    timestampMs = 0,
    latencyMs = 0,
    ambiguous = ambiguous,
)

private fun pianoFrame(frequency: Double): AudioFrame =
    slice(pianoTone(frequency, SR, 0.6))

private fun slice(tone: FloatArray, start: Int = 2000, n: Int = 2048): AudioFrame =
    AudioFrame(tone.copyOfRange(start, start + n), SR, 0)
