package com.vijaychhetry.kidspiano.core.calibration

import com.vijaychhetry.kidspiano.core.notes.A4_HZ
import com.vijaychhetry.kidspiano.core.notes.centsOff
import com.vijaychhetry.kidspiano.core.notes.midiToFreq
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * The saved profile has to become a usable A4 for live recognition, not just
 * a quality badge on the Calibrate screen.
 */
class CalibrationTuningTest {
    @Test
    fun acTune01_emptyProfileStaysAtConcertA440() {
        val profile = MedianCalibrationEngine().buildProfile(emptyMap(), 44100, "MIC")
        assertEquals(A4_HZ, concertA4Hz(profile), 1e-9)
    }

    @Test
    fun acTune02_aPianoTwentyCentsSharpMovesA4ByTwentyCents() {
        val cents = 20.0
        val ratio = Math.pow(2.0, cents / 1200.0)
        val samples = MVP_CALIBRATION_MIDI.associateWith { midi ->
            List(4) { midiToFreq(midi) * ratio }
        }
        val profile = MedianCalibrationEngine().buildProfile(samples, 44100, "MIC")
        val a4 = concertA4Hz(profile)
        assertEquals(A4_HZ * ratio, a4, 0.2)
        val observedC4 = midiToFreq(60) * ratio
        assertTrue(abs(centsOff(observedC4, 60, a4)) < 1.0, "C4 should sit on the retuned A4")
    }

    @Test
    fun acTune03_parseSavedNotesRebuildsAUsableProfile() {
        val profile = parseSavedProfile(
            quality = "EXCELLENT",
            notesCsv = "60:261.90:4,62:294.10:4,64:330.60:4,65:348.90:4,67:393.40:4",
            source = "VOICE_RECOGNITION",
            sampleRate = 44100,
            savedAt = 1L,
        )
        assertNotNull(profile)
        assertTrue(profile!!.usableAsDefault)
        assertEquals(5, profile.notes.size)
        assertEquals(60, profile.notes.first().midiNote)
        assertEquals(261.90, profile.notes.first().observedMedianFrequency, 0.001)
        assertEquals("VOICE_RECOGNITION", profile.microphoneSource)
        assertTrue(concertA4Hz(profile) > A4_HZ)
    }

    @Test
    fun acTune04_garbageSavedNotesAreRefused() {
        assertNull(parseSavedProfile("EXCELLENT", "", "MIC", 44100, 1L))
        assertNull(parseSavedProfile("NOPE", "60:261.6:4", "MIC", 44100, 1L))
        assertNull(parseSavedProfile("GOOD", "not-a-note", "MIC", 44100, 1L))
    }
}
