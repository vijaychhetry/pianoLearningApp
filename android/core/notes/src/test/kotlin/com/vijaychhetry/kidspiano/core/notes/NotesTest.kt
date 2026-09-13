package com.vijaychhetry.kidspiano.core.notes

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.sqrt

class NotesTest {
    @Test
    fun acNote01_a4Is440HzAndMidi69() {
        assertEquals(440.0, midiToFreq(69), 1e-9)
        assertEquals(69, freqToMidi(440.0))
        assertEquals("A4", midiToNoteName(69))
        assertEquals(9, pitchClass(69))
        assertEquals(0, pitchClass(60))
    }

    @Test
    fun acNote01b_c4AndC5MatchConcertPitch() {
        assertEquals("C4", midiToNoteName(60))
        assertEquals("C5", midiToNoteName(72))
        assertEquals(60, freqToMidi(261.63))
        assertEquals(72, freqToMidi(523.25))
        assertEquals(261.625565, midiToFreq(60), 0.001)
    }

    @Test
    fun acNote02_boundaryBetweenC4AndCSharp4() {
        val c4 = midiToFreq(60)
        val cSharp4 = midiToFreq(61)
        val midpoint = sqrt(c4 * cSharp4)
        assertEquals(60, freqToMidi(midpoint * 0.999))
        assertEquals(61, freqToMidi(midpoint * 1.001))
        assertEquals("C#4", midiToNoteName(61))
        assertTrue(midpoint > 269.0 && midpoint < 270.0, "geometric midpoint should sit near 269.4 Hz")
    }

    @Test
    fun acNote02b_centsOffSharpA4() {
        val sharp = midiToFreq(69) * Math.pow(2.0, 15.0 / 1200.0)
        assertEquals(15.0, centsOff(sharp, 69), 0.01)
        assertEquals(69, freqToMidi(sharp))
        assertEquals(69, freqToMidi(452.0))
        assertTrue(abs(centsOff(452.0, 69)) > 45.0)
    }
}
