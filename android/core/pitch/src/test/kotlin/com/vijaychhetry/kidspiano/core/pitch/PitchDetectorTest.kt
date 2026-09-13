package com.vijaychhetry.kidspiano.core.pitch

import com.vijaychhetry.kidspiano.core.common.AudioFrame
import com.vijaychhetry.kidspiano.core.notes.midiToFreq
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.api.Test

private const val SR = 44100

class YinHpsPitchDetectorTest {
    private val detector = YinHpsPitchDetector()

    @ParameterizedTest
    @ValueSource(ints = [60, 62, 64, 65, 67])
    fun locksCMajorWhiteKeys(midi: Int) {
        val tone = pianoTone(midiToFreq(midi), SR, 0.6)
        val frame = AudioFrame(tone.copyOfRange(2000, 2000 + 2048), SR, 0)
        val result = detector.detect(frame)
        assertEquals(midi, result.midiNote)
        assertTrue(result.confidence > 0.55)
    }

    @Test
    fun silenceIsNoPitch() {
        val result = detector.detect(AudioFrame(FloatArray(2048), SR, 0))
        assertNull(result.midiNote)
    }
}

class NoteDebouncerTest {
    @Test
    fun requiresStableFramesThenReleases() {
        val d = NoteDebouncer(DebounceConfig(stableFrames = 3, releaseFrames = 2))
        assertEquals(NotePhase.ATTACK, d.onFrame(60))
        assertEquals(NotePhase.ATTACK, d.onFrame(60))
        assertEquals(NotePhase.STABLE, d.onFrame(60))
        assertEquals(60, d.lockedMidi)
        d.onFrame(null)
        assertEquals(NotePhase.IDLE, d.onFrame(null))
        assertNull(d.lockedMidi)
    }
}
