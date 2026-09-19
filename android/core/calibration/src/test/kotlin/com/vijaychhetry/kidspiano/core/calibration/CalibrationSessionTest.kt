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
    fun acSess04_advancesOnlyAfterEnoughCleanPresses() {
        val session = CalibrationSession(samplesPerNote = 3)
        repeat(2) { session.press(60, midiToFreq(60)) }
        assertEquals(60, session.currentMidi, "still collecting C4")
        assertEquals(1, session.samplesNeeded(60))
        session.press(60, midiToFreq(60))
        assertEquals(62, session.currentMidi, "moves on to D4")
        assertEquals(3, session.acceptedCount(60))
    }

    @Test
    fun acSess05_fullRunCompletesAndScoresExcellent() {
        val session = CalibrationSession(samplesPerNote = 8)
        // Bounded: a broken press contract must fail here, not hang the suite.
        repeat(5 * 8) {
            val midi = session.currentMidi ?: return@repeat
            val hz = midiToFreq(midi) + (session.acceptedCount(midi) % 3) * 0.1
            assertEquals(CalibrationSession.Offer.ACCEPTED, session.press(midi, hz))
        }
        assertTrue(session.isComplete, "40 clean presses must finish five notes of eight")
        assertNull(session.currentMidi)
        assertEquals(1f, session.progress)
        assertEquals(CalibrationSession.Offer.DONE, session.press(60, midiToFreq(60)))

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
        repeat(4 * 8) {
            val midi = session.currentMidi ?: return@repeat
            session.press(midi, midiToFreq(midi))
        }
        assertTrue(session.isComplete)
        val profile = session.buildProfile(MedianCalibrationEngine(), 44100, "MIC")
        assertNotEquals(CalibrationProfile.Quality.EXCELLENT, profile.calibrationQuality)
        assertEquals(CalibrationProfile.Quality.NEEDS_IMPROVEMENT, profile.calibrationQuality)
        assertEquals(0, session.acceptedCount(60))
    }

    @Test
    fun acSess07_restartClearsEverything() {
        val session = CalibrationSession(samplesPerNote = 2)
        session.press(60, midiToFreq(60))
        session.press(60, midiToFreq(60))
        assertEquals(62, session.currentMidi)
        session.restart()
        assertEquals(60, session.currentMidi)
        assertEquals(0, session.acceptedCount(60))
        assertTrue(session.samplesByMidi().isEmpty())
    }

    @Test
    fun acSess08_progressTracksAcceptedSamplesOnly() {
        val session = CalibrationSession(samplesPerNote = 4)
        session.press(64, midiToFreq(64), 0.95)
        assertEquals(0f, session.progress, "wrong keys must not move the bar")
        session.press(60, midiToFreq(60))
        assertEquals(1f / 20f, session.progress, 1e-6f)
    }

    @Test
    fun acSess09_oneHeldKeyContributesExactlyOneSample() {
        val session = CalibrationSession(samplesPerNote = 4)
        assertEquals(CalibrationSession.Offer.ACCEPTED, session.offer(60, midiToFreq(60), 0.9))
        // Frames keep arriving ~46 ms apart while the key is still down.
        repeat(30) {
            assertEquals(
                CalibrationSession.Offer.SAME_PRESS,
                session.offer(60, midiToFreq(60), 0.9),
                "one sustained note must not fill the whole profile",
            )
        }
        assertEquals(1, session.acceptedCount(60))
        assertEquals(60, session.currentMidi, "holding one key cannot finish C4")
        session.onRelease()
        assertEquals(CalibrationSession.Offer.ACCEPTED, session.offer(60, midiToFreq(60), 0.9))
        assertEquals(2, session.acceptedCount(60))
    }

    @Test
    fun acSess10_defaultAsksForFourPressesPerNote() {
        val session = CalibrationSession()
        assertEquals(4, session.samplesPerNote)
        repeat(3) { session.press(60, midiToFreq(60)) }
        assertEquals(60, session.currentMidi, "three presses is not a note's worth of samples")
        session.press(60, midiToFreq(60))
        assertEquals(62, session.currentMidi)
    }

    @Test
    fun acSess11_wrongKeyDuringAPressDoesNotConsumeThePress() {
        val session = CalibrationSession(samplesPerNote = 2)
        assertEquals(CalibrationSession.Offer.WRONG_KEY, session.offer(62, midiToFreq(62), 0.9))
        assertEquals(CalibrationSession.Offer.ACCEPTED, session.offer(60, midiToFreq(60), 0.9))
        assertEquals(1, session.acceptedCount(60))
    }

    @Test
    fun acCalJump01_jumpingToASampledKeyClearsItAndDoesNotMixOldReadings() {
        val session = CalibrationSession(samplesPerNote = 2)
        session.press(60, midiToFreq(60))
        session.press(60, midiToFreq(60))
        assertEquals(62, session.currentMidi)
        session.jumpTo(60)
        assertEquals(60, session.currentMidi)
        assertEquals(0, session.acceptedCount(60))
        session.jumpTo(64)
        assertEquals(64, session.currentMidi)
        assertTrue(64 in session.notes)
        assertFalse(session.jumpTo(61))
        assertFalse(session.jumpTo(36))
        assertEquals(64, session.currentMidi)
    }
}

/** One press: the key was up, then struck. */
private fun CalibrationSession.press(
    midi: Int?,
    hz: Double?,
    confidence: Double = 0.9,
): CalibrationSession.Offer {
    onRelease()
    return offer(midi, hz, confidence)
}
