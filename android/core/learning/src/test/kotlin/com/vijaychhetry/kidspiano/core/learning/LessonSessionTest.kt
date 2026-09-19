package com.vijaychhetry.kidspiano.core.learning

import com.vijaychhetry.kidspiano.core.calibration.CalibrationProfile
import com.vijaychhetry.kidspiano.core.calibration.MVP_CALIBRATION_MIDI
import com.vijaychhetry.kidspiano.core.calibration.MedianCalibrationEngine
import com.vijaychhetry.kidspiano.core.calibration.NoteCalibration
import com.vijaychhetry.kidspiano.core.common.AudioFrame
import com.vijaychhetry.kidspiano.core.common.RecognitionStatus
import com.vijaychhetry.kidspiano.core.notes.midiToFreq
import com.vijaychhetry.kidspiano.core.pitch.pianoTone
import com.vijaychhetry.kidspiano.core.pitch.sineTone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val SR = 44100
private const val FRAME_MS = 46L
private const val PRESS_FRAMES = 12

/**
 * AC-LEARN-* — Level 1 "find this key" driven by synthesised piano audio.
 */
class LessonSessionTest {
    @Test
    fun acLearn01_startsByAskingForC() {
        val session = LessonSession()
        session.begin(0)
        val snap = session.snapshot()
        assertEquals(60, snap.expectedMidi)
        assertEquals("C", snap.letter)
        assertEquals("C4 (middle C)", snap.noteName)
        assertEquals(LessonCue.LISTEN, snap.cue)
        assertTrue(snap.feedback.contains("C4"), "got: ${snap.feedback}")
        assertEquals(0, snap.completedCount)
        assertFalse(snap.complete)
    }

    @Test
    fun acLearn02_wrongKeySaysTryCAndDoesNotAdvance() {
        val session = LessonSession()
        session.begin(0)
        val snap = play(session, midi = 64, atFrame = 0)
        assertEquals(60, snap.expectedMidi)
        assertEquals(LessonCue.TRY, snap.cue)
        assertEquals(RecognitionStatus.INCORRECT, snap.status)
        assertTrue(snap.feedback.contains("E4"), "got: ${snap.feedback}")
        assertTrue(snap.feedback.contains("C4"), "got: ${snap.feedback}")
        assertEquals(0, snap.completedCount)
    }

    @Test
    fun acLearn03_aCorrectCAdvancesToDAndDoesNotScoreTheHeldNoteAsWrongD() {
        val session = LessonSession()
        session.begin(0)
        var frame = 0
        var snap = hold(session, 60, frame).also { frame += 8 }
        assertEquals(62, snap.expectedMidi, "C is done; now asking for D")
        assertEquals("D", snap.letter)
        assertEquals(LessonCue.YES, snap.cue)
        assertTrue(snap.feedback.contains("Yes"), "got: ${snap.feedback}")
        assertEquals(1, snap.completedCount)
        // Keep holding the same C. That press already counted; it must not
        // become "wrong D".
        snap = hold(session, 60, frame)
        assertEquals(62, snap.expectedMidi)
        assertNotEquals(RecognitionStatus.INCORRECT, snap.status)
        assertEquals(1, snap.completedCount)
    }

    @Test
    fun acLearn03b_oneDroppedFrameOnAHeldCorrectCIsNotAWrongD() {
        val session = LessonSession()
        session.begin(0)
        var frame = 0
        hold(session, 60, frame).also { frame += 8 }
        // Piano sustain routinely drops a frame; the debouncer treats that as
        // RELEASE, not IDLE. The lesson must not unlock the press.
        var snap = session.onFrame(AudioFrame(FloatArray(2048), SR, frame * FRAME_MS))
        frame++
        snap = hold(session, 60, frame)
        assertEquals(62, snap.expectedMidi)
        assertNotEquals(RecognitionStatus.INCORRECT, snap.status)
        assertEquals(LessonCue.YES, snap.cue)
        assertEquals(1, snap.completedCount)
        assertTrue(snap.feedback.contains("Yes"), "got: ${snap.feedback}")
    }

    @Test
    fun acLearn04_unclearNeverMarksIncorrectOrAdvances() {
        val session = LessonSession()
        session.begin(0)
        val snap = session.onFrame(AudioFrame(FloatArray(2048), SR, 0))
        assertEquals(60, snap.expectedMidi)
        assertNotEquals(RecognitionStatus.INCORRECT, snap.status)
        assertNotEquals(RecognitionStatus.CORRECT, snap.status)
        assertTrue(
            snap.feedback.contains("couldn't hear") || snap.feedback.contains("Play the C"),
            "got: ${snap.feedback}",
        )
    }

    @Test
    fun acLearn05_twoNotesAtOnceAreUnclearNotWrong() {
        val session = LessonSession()
        session.begin(0)
        val mix = com.vijaychhetry.kidspiano.core.pitch.mixTones(
            sineTone(midiToFreq(60), SR, 0.6, amplitude = 0.35f),
            sineTone(midiToFreq(64), SR, 0.6, amplitude = 0.35f),
        )
        var snap = session.snapshot()
        repeat(8) { i ->
            val start = (2000 + i * 64).coerceAtMost(mix.size - 2048)
            snap = session.onFrame(AudioFrame(mix.copyOfRange(start, start + 2048), SR, i * FRAME_MS))
        }
        assertEquals(60, snap.expectedMidi)
        assertEquals(RecognitionStatus.AMBIGUOUS, snap.status)
        assertEquals(LessonCue.UNCLEAR, snap.cue)
        assertNotEquals(RecognitionStatus.INCORRECT, snap.status)
        assertTrue(snap.feedback.contains("couldn't hear"), "got: ${snap.feedback}")
    }

    @Test
    fun acLearn06_octaveMismatchDoesNotAdvance() {
        val session = LessonSession()
        session.begin(0)
        val snap = play(session, midi = 72, atFrame = 0)
        assertEquals(60, snap.expectedMidi)
        assertEquals(RecognitionStatus.CORRECT_OCTAVE_MISMATCH, snap.status)
        assertEquals(LessonCue.OCTAVE, snap.cue)
        assertTrue(snap.feedback.contains("C5"), "got: ${snap.feedback}")
        assertTrue(snap.feedback.contains("C4"), "got: ${snap.feedback}")
        assertEquals(0, snap.completedCount)
    }

    @Test
    fun acLearn07_allFiveKeysCompleteTheLesson() {
        val session = LessonSession()
        session.begin(0)
        var frame = 0
        var snap = session.snapshot()
        for (midi in MVP_CALIBRATION_MIDI) {
            snap = playPress(session, midi, frame).also { frame += PRESS_FRAMES }
        }
        assertTrue(snap.complete)
        assertEquals(LessonCue.DONE, snap.cue)
        assertEquals(5, snap.completedCount)
        assertTrue(snap.feedback.contains("all five"), "got: ${snap.feedback}")
        assertEquals(null, snap.expectedMidi)
    }

    @Test
    fun acLearn08_playAgainAsksForCOnceMore() {
        val session = LessonSession()
        session.begin(0)
        var frame = 0
        for (midi in MVP_CALIBRATION_MIDI) {
            playPress(session, midi, frame).also { frame += PRESS_FRAMES }
        }
        val snap = session.restart()
        assertEquals(60, snap.expectedMidi)
        assertEquals(LessonCue.LISTEN, snap.cue)
        assertFalse(snap.complete)
        assertEquals(0, snap.completedCount)
    }
}

class LessonPolicyTest {
    @Test
    fun acLearn09_practiceRequiresAUsableSavedProfile() {
        assertFalse(lessonMayStart(null))
        val engine = MedianCalibrationEngine()
        val poor = engine.buildProfile(mapOf(60 to listOf(261.0)), 44100, "MIC")
        assertFalse(lessonMayStart(poor))
        val goodNotes = MVP_CALIBRATION_MIDI.associateWith { midi ->
            List(4) { midiToFreq(midi) }
        }
        val good = engine.buildProfile(goodNotes, 44100, "MIC")
        assertTrue(good.usableAsDefault)
        assertTrue(lessonMayStart(good))
    }

    @Test
    fun acLearn10_needsImprovementProfileIsBlockedEvenIfNotesExist() {
        val notes = listOf(
            NoteCalibration(60, midiToFreq(60), 261.6, 20.0, 0.4, 1),
        )
        val blocked = CalibrationProfile(
            id = "local",
            createdAtEpochMs = 1L,
            sampleRate = 44100,
            microphoneSource = "MIC",
            notes = notes,
            calibrationQuality = CalibrationProfile.Quality.NEEDS_IMPROVEMENT,
        )
        assertFalse(lessonMayStart(blocked))
    }

    @Test
    fun acLearn12_lowerClusterUsesTheSameProfileAndAsksForC3() {
        val engine = MedianCalibrationEngine()
        val goodNotes = MVP_CALIBRATION_MIDI.associateWith { midi ->
            List(4) { midiToFreq(midi) }
        }
        val good = engine.buildProfile(goodNotes, 44100, "MIC")
        val session = lessonSessionFor(good, com.vijaychhetry.kidspiano.core.notes.lowerClusterLessonMidi())
        session.begin(0)
        assertEquals(48, session.expectedMidi)
        val snap = play(session, 48, 0)
        assertEquals(50, snap.expectedMidi)
        assertTrue(snap.feedback.contains("Yes"), "got: ${snap.feedback}")
    }
}

class ParentGateTest {
    @Test
    fun acR15_gateIsATimesTableInSixToNineAndWrongAnswersDoNotLockOut() {
        val gate = ParentGate.of(7, 8)
        assertEquals("What is 7 × 8?", gate.prompt)
        assertTrue(gate.accepts("56"))
        assertTrue(gate.accepts(" 56 "))
        assertFalse(gate.accepts("54"))
        assertFalse(gate.accepts(""))
        assertFalse(gate.accepts("seven"))
        assertFalse(gate.accepts("15"))
        repeat(40) {
            val roll = ParentGate()
            assertTrue(roll.a in 6..9, "a=${roll.a}")
            assertTrue(roll.b in 6..9, "b=${roll.b}")
            assertTrue(roll.accepts((roll.a * roll.b).toString()))
        }
    }
}

private fun play(session: LessonSession, midi: Int, atFrame: Int): LessonSnapshot =
    playHz(session, midiToFreq(midi), atFrame)

private fun playHz(session: LessonSession, hz: Double, atFrame: Int): LessonSnapshot {
    var snap = session.snapshot()
    repeat(8) { i -> snap = session.onFrame(frame(hz, atFrame + i)) }
    return snap
}

private fun hold(session: LessonSession, midi: Int, atFrame: Int): LessonSnapshot =
    play(session, midi, atFrame)

private fun playPress(session: LessonSession, midi: Int, fromFrame: Int): LessonSnapshot {
    var snap = session.snapshot()
    val hz = midiToFreq(midi)
    for (i in 0 until 8) {
        snap = session.onFrame(frame(hz, fromFrame + i))
    }
    for (i in 8 until PRESS_FRAMES) {
        snap = session.onFrame(AudioFrame(FloatArray(2048), SR, (fromFrame + i) * FRAME_MS))
    }
    return snap
}

private fun frame(hz: Double, index: Int, n: Int = 2048): AudioFrame {
    val tone = pianoTone(hz, SR, 1.0)
    val start = (2000 + index % 8 * 64).coerceAtMost(tone.size - n)
    return AudioFrame(tone.copyOfRange(start, start + n), SR, index * FRAME_MS)
}
