package com.vijaychhetry.kidspiano.core.notes

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.abs

class NotesTest {
    @Test
    fun a4Is440AndMidi69() {
        assertEquals(440.0, midiToFreq(69), 1e-6)
        assertEquals(69, freqToMidi(440.0))
        assertEquals("A4", midiToNoteName(69))
    }

    @Test
    fun c4AndC5() {
        assertEquals("C4", midiToNoteName(60))
        assertEquals(60, freqToMidi(261.63))
        assertEquals(72, freqToMidi(523.25))
    }

    @Test
    fun centsOffSharp() {
        val sharp = midiToFreq(69) * Math.pow(2.0, 15.0 / 1200.0)
        assertEquals(15.0, centsOff(sharp, 69), 0.01)
        assertEquals(true, abs(centsOff(sharp, 69) - 15.0) < 0.05)
    }
}
