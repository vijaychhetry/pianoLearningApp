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
    fun acNote03_c4CaptionNamesMiddleC() {
        assertEquals("C4 · middle C", midiToCaption(60))
        assertEquals("D4", midiToCaption(62))
        assertEquals("C5", midiToCaption(72))
    }

    @Test
    fun acKey01_overviewAndDetailKeyCounts() {
        val overview = keyboardLayout(OVERVIEW_FROM_MIDI, OVERVIEW_TO_MIDI)
        val detail = keyboardLayout(DETAIL_FROM_MIDI, DETAIL_TO_MIDI)
        assertEquals(36, overview.whites.single { it.midi == 36 }.midi)
        assertEquals("C2", midiToNoteName(OVERVIEW_FROM_MIDI))
        assertEquals("C7", midiToNoteName(OVERVIEW_TO_MIDI))
        // C2–C7 inclusive is 5 octaves of 7 whites plus the final C = 36 white keys.
        assertEquals(36, overview.whiteCount)
        // C3–C6 inclusive is 3 octaves of 7 whites plus the final C = 22 white keys.
        assertEquals(22, detail.whiteCount)
        assertTrue(overview.blacks.all { isBlackKey(it.midi) })
        assertTrue(detail.whites.none { isBlackKey(it.midi) })
    }

    @Test
    fun acKey02_c4SitsInTheMiddleOfTheDetailKeyboard() {
        val detail = keyboardLayout(DETAIL_FROM_MIDI, DETAIL_TO_MIDI)
        val c4 = detail.whites.single { it.midi == 60 }
        // 22 whites, C4 is the 8th (0-based 7): C3 D3 E3 F3 G3 A3 B3 C4.
        assertEquals(7, c4.index)
        assertTrue(c4.index > 3, "C4 must not sit against the left edge")
        assertTrue(c4.index < detail.whiteCount - 4, "C4 must not sit against the right edge")
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
