package com.vijaychhetry.kidspiano.core.calibration

import com.vijaychhetry.kidspiano.core.notes.midiToFreq
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * AC-SESS-* — the guided "which key should I play" flow (spec §§9–11).
 */
class CalibrationSessionTest {
    @Test
    fun acSess01_startsByAskingForC4() {
        val session = CalibrationSession()
        assertEquals(60, session.currentMidi)
        assertEquals(listOf(60, 62, 64, 65, 67), session.notes)
        assertFalse(session.isComplete)
        assertEquals(0f, session.progress)
    }

    @Test
    fun acSess02_wrongKeyIsRejectedAndDoesNotAdvance() {
        val session = CalibrationSession()
        val result = session.offer(64, midiToFreq(64), 0.95)
        assertEquals(CalibrationSession.Offer.WRONG_KEY, result)
        assertEquals(60, session.currentMidi)
        assertEquals(0, session.acceptedCount(60))
        assertEquals(0, session.acceptedCount(64), "a wrong key must not be banked as E4 either")
    }

    @Test
    fun acSess03_lowConfidenceIsUnclearNotAccepted() {
        val session = CalibrationSession()
        assertEquals(CalibrationSession.Offer.UNCLEAR, session.offer(60, midiToFreq(60), 0.3))
        assertEquals(0, session.acceptedCount(60))
        assertEquals(60, session.currentMidi)
    }

    @Test
    fun acSess03b_badlyOutOfTunePitchIsUnclearEvenWhenConfident() {
        val session = CalibrationSession()
        val offCenter = midiToFreq(60) * Math.pow(2.0, 60.0 / 1200.0)
        assertEquals(CalibrationSession.Offer.UNCLEAR, session.offer(60, offCenter, 0.95))
        assertEquals(0, session.acceptedCount(60))
    }

    @Test
    fun acSess04_advancesOnlyAfterEnoughCleanSamples() {
        val session = CalibrationSession(samplesPerNote = 3)
        repeat(2) { session.offer(60, midiToFreq(60), 0.9) }
        assertEquals(60, session.currentMidi, "still collecting C4")
        assertEquals(1, session.samplesNeeded(60))
        session.offer(60, midiToFreq(60), 0.9)
        assertEquals(62, session.currentMidi, "moves on to D4")
        assertEquals(3, session.acceptedCount(60))
    }

    @Test
    fun acSess05_fullRunCompletesAndScoresExcellent() {
        val session = CalibrationSession(samplesPerNote = 8)
        while (!session.isComplete) {
            val midi = session.currentMidi!!
            val hz = midiToFreq(midi) + (session.acceptedCount(midi) % 3) * 0.1
            assertEquals(CalibrationSession.Offer.ACCEPTED, session.offer(midi, hz, 0.9))
        }
        assertNull(session.currentMidi)
        assertEquals(1f, session.progress)
        assertEquals(CalibrationSession.Offer.DONE, session.offer(60, midiToFreq(60), 0.9))

        val profile = session.buildProfile(MedianCalibrationEngine(), 44100, "VOICE_RECOGNITION")
        assertEquals(CalibrationProfile.Quality.EXCELLENT, profile.calibrationQuality)
        assertEquals(setOf(60, 62, 64, 65, 67), profile.notes.map { it.midiNote }.toSet())
        for (note in profile.notes) {
            assertEquals(midiToFreq(note.midiNote), note.observedMedianFrequency, 0.3)
        }
    }

    @Test
    fun acSess06_skippedNoteCannotProduceAnExcellentProfile() {
        val session = CalibrationSession(samplesPerNote = 8)
        session.skipCurrent()
        while (!session.isComplete) {
            val midi = session.currentMidi!!
            session.offer(midi, midiToFreq(midi), 0.9)
        }
        val profile = session.buildProfile(MedianCalibrationEngine(), 44100, "MIC")
        assertNotEquals(CalibrationProfile.Quality.EXCELLENT, profile.calibrationQuality)
        assertEquals(CalibrationProfile.Quality.NEEDS_IMPROVEMENT, profile.calibrationQuality)
        assertEquals(0, session.acceptedCount(60))
    }

    @Test
    fun acSess07_restartClearsEverything() {
        val session = CalibrationSession(samplesPerNote = 2)
        session.offer(60, midiToFreq(60), 0.9)
        session.offer(60, midiToFreq(60), 0.9)
        assertEquals(62, session.currentMidi)
        session.restart()
        assertEquals(60, session.currentMidi)
        assertEquals(0, session.acceptedCount(60))
        assertTrue(session.samplesByMidi().isEmpty())
    }

    @Test
    fun acSess08_progressTracksAcceptedSamplesOnly() {
        val session = CalibrationSession(samplesPerNote = 4)
        session.offer(64, midiToFreq(64), 0.95)
        assertEquals(0f, session.progress, "wrong keys must not move the bar")
        session.offer(60, midiToFreq(60), 0.9)
        assertEquals(1f / 20f, session.progress, 1e-6f)
    }
}
