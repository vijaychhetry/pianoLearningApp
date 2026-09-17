package com.vijaychhetry.kidspiano.core.pitch

import com.vijaychhetry.kidspiano.core.common.AudioFrame
import com.vijaychhetry.kidspiano.core.notes.midiToFreq
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.api.Test
import kotlin.math.abs

private const val SR = 44100

class YinHpsPitchDetectorTest {
    private val detector = YinHpsPitchDetector()

    @ParameterizedTest
    @ValueSource(ints = [60, 62, 64, 65, 67])
    fun acPitch01_locksCMajorWhiteKeysWithinTwoHz(midi: Int) {
        val expectedHz = midiToFreq(midi)
        val result = detector.detect(pianoFrame(expectedHz))
        assertEquals(midi, result.midiNote)
        assertTrue(result.frequency != null, "frequency missing for MIDI $midi")
        assertTrue(
            abs(result.frequency!! - expectedHz) < 2.0,
            "MIDI $midi: got ${result.frequency} Hz, expected $expectedHz ± 2",
        )
        assertTrue(result.confidence > 0.7, "confidence ${result.confidence} too low for a clean piano tone")
        assertFalse(result.ambiguous)
    }

    @Test
    fun acPitch02_silenceIsNoPitchAndBelowRmsGate() {
        val result = detector.detect(AudioFrame(FloatArray(2048), SR, 0))
        assertNull(result.midiNote)
        assertNull(result.frequency)
        assertEquals(0.0, result.confidence, 0.0)
        assertTrue(result.signalStrength < YinHpsPitchDetector.MIN_RMS)
        assertFalse(result.ambiguous)
    }

    @Test
    fun acPitch02b_belowRmsGateIsNoPitchEvenIfSineIsPresent() {
        val quiet = sineTone(midiToFreq(60), SR, 0.6, amplitude = 0.001f)
        val result = detector.detect(slice(quiet))
        assertTrue(rms(quiet.copyOfRange(2000, 4048)) < YinHpsPitchDetector.MIN_RMS)
        assertNull(result.midiNote)
        assertEquals(0.0, result.confidence, 0.0)
    }

    @Test
    fun acPitch03_c4PianoToneIsNotA4() {
        val result = detector.detect(pianoFrame(midiToFreq(60)))
        assertEquals(60, result.midiNote)
        assertNotEquals(69, result.midiNote)
        assertTrue(abs(result.frequency!! - midiToFreq(60)) < 2.0)
        assertTrue(abs(result.frequency!! - 440.0) > 100.0)
    }

    @Test
    fun acPitch04_twoSimultaneousFundamentalsAreAmbiguous() {
        val mix = mixTones(
            sineTone(midiToFreq(60), SR, 0.6, amplitude = 0.35f),
            sineTone(midiToFreq(64), SR, 0.6, amplitude = 0.35f),
        )
        val result = detector.detect(slice(mix))
        assertTrue(result.ambiguous, "C4+E4 must be AMBIGUOUS, not a guessed MIDI ${result.midiNote}")
        assertTrue(result.signalStrength > 0.008)
    }

    @Test
    fun acPitch05_harmonicRichSingleC4IsNotAmbiguous() {
        val result = detector.detect(pianoFrame(midiToFreq(60)))
        assertFalse(result.ambiguous, "harmonics of one note are not polyphony")
        assertEquals(60, result.midiNote)
    }

    @ParameterizedTest
    @ValueSource(ints = [60, 62, 64, 65, 67])
    fun acPitch05b_realPianoPartialsAndRoomNoiseAreNotAmbiguous(midi: Int) {
        val result = detector.detect(slice(realisticPianoTone(midiToFreq(midi), SR, 0.6)))
        assertFalse(
            result.ambiguous,
            "MIDI $midi: inharmonic partials plus hum must not read as two notes",
        )
        assertEquals(midi, result.midiNote)
    }

    @ParameterizedTest
    @ValueSource(ints = [45, 50, 55, 72, 79, 84])
    fun acPitch07_everyKeyFromA2ToC6ReadsOut(midi: Int) {
        val result = detector.detect(pianoFrame(midiToFreq(midi)))
        assertEquals(midi, result.midiNote, "a child exploring the keyboard must still see a note")
        assertTrue(result.confidence > 0.6)
    }

    @Test
    fun acPitch07b_theTopOfTheRangeIsWiderThanTheOldEightHundredHertzCeiling() {
        val c6 = midiToFreq(84)
        assertTrue(
            YinHpsPitchDetector.MAX_FREQ_HZ > c6,
            "C6 is ${"%.1f".format(c6)} Hz and must be inside the detector range",
        )
        val result = detector.detect(pianoFrame(c6))
        assertEquals(84, result.midiNote)
        assertTrue(abs(result.frequency!! - c6) < 6.0)
    }

    @Test
    fun acPitch06_sharpA4SineDoesNotFoldToMidi50() {
        val result = detector.detect(slice(sineTone(452.0, SR, 0.6)))
        assertNotEquals(50, result.midiNote, "HPS 3× fold regression: 452 Hz must not become D3")
        assertEquals(69, result.midiNote)
        assertTrue(abs(result.frequency!! - 452.0) < 5.0)
        assertFalse(result.ambiguous)
    }
}

class NoteDebouncerTest {
    @Test
    fun acDeb00_defaultConfigNeedsThreeAgreeingFramesBecauseProductionUsesTheDefault() {
        val d = NoteDebouncer()
        assertEquals(NotePhase.ATTACK, d.onFrame(60))
        assertEquals(NotePhase.ATTACK, d.onFrame(60), "two frames must not be a press")
        assertNull(d.lockedMidi)
        assertEquals(NotePhase.STABLE, d.onFrame(60))
        assertEquals(60, d.lockedMidi)
    }

    @Test
    fun acDeb04_resetDropsAPressInFlight() {
        val d = NoteDebouncer()
        repeat(3) { d.onFrame(60) }
        assertEquals(60, d.lockedMidi)
        d.reset()
        assertNull(d.lockedMidi)
        assertEquals(NotePhase.ATTACK, d.onFrame(60), "after a reset the next note starts over")
    }

    @Test
    fun acDeb01_doesNotLockUntilStableFrames() {
        val d = NoteDebouncer(DebounceConfig(stableFrames = 3, releaseFrames = 2))
        assertEquals(NotePhase.ATTACK, d.onFrame(60))
        assertEquals(NotePhase.ATTACK, d.onFrame(60))
        assertNull(d.lockedMidi)
        assertEquals(NotePhase.STABLE, d.onFrame(60))
        assertEquals(60, d.lockedMidi)
        d.onFrame(null)
        assertEquals(NotePhase.IDLE, d.onFrame(null))
        assertNull(d.lockedMidi)
    }

    @Test
    fun acDeb02_competingCandidateResetsAndDoesNotLockEitherNote() {
        val d = NoteDebouncer(DebounceConfig(stableFrames = 3, releaseFrames = 4))
        d.onFrame(60)
        d.onFrame(60)
        assertNull(d.lockedMidi)
        assertEquals(NotePhase.ATTACK, d.onFrame(64))
        assertEquals(NotePhase.ATTACK, d.onFrame(64))
        assertNull(d.lockedMidi)
        assertEquals(NotePhase.STABLE, d.onFrame(64))
        assertEquals(64, d.lockedMidi)
    }

    @Test
    fun acDeb03_oneHeldNoteIsASinglePress() {
        val d = NoteDebouncer(DebounceConfig(stableFrames = 3, releaseFrames = 2))
        var presses = 0
        var prev = NotePhase.IDLE
        repeat(20) {
            val phase = d.onFrame(60)
            if (phase == NotePhase.STABLE && prev != NotePhase.STABLE) presses++
            prev = phase
        }
        assertEquals(1, presses)
        assertEquals(60, d.lockedMidi)
    }

    @Test
    fun acDeb03b_twoSeparatedNotesAreTwoPresses() {
        val d = NoteDebouncer(DebounceConfig(stableFrames = 3, releaseFrames = 2))
        var presses = 0
        var prev = NotePhase.IDLE
        fun feed(midi: Int?) {
            val phase = d.onFrame(midi)
            if (phase == NotePhase.STABLE && prev != NotePhase.STABLE) presses++
            prev = phase
        }
        repeat(6) { feed(60) }
        repeat(3) { feed(null) }
        repeat(6) { feed(64) }
        assertEquals(2, presses)
        assertEquals(64, d.lockedMidi)
    }
}

private fun pianoFrame(frequency: Double): AudioFrame =
    slice(pianoTone(frequency, SR, 0.6))

private fun slice(tone: FloatArray, start: Int = 2000, n: Int = 2048): AudioFrame =
    AudioFrame(tone.copyOfRange(start, start + n), SR, 0)
