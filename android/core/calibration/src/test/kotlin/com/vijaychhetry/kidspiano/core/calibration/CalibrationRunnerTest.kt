package com.vijaychhetry.kidspiano.core.calibration

import com.vijaychhetry.kidspiano.core.common.AudioFrame
import com.vijaychhetry.kidspiano.core.notes.midiToFreq
import com.vijaychhetry.kidspiano.core.pitch.pianoTone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val SR = 44100
private const val FRAME_MS = 46L

/**
 * AC-RUN-* — the guided calibration screen driven by synthesised audio, the
 * way a phone would drive it. The reporter's complaint was that nothing asked
 * which key to play, so these assert the prompt sequence end to end.
 */
class CalibrationRunnerTest {
    @Test
    fun acRun01_startsByAskingForTheCKeyAndSaysSo() {
        val runner = CalibrationRunner()
        runner.begin(0)
        val snapshot = runner.snapshot()
        assertEquals(60, snapshot.targetMidi)
        assertEquals("C", snapshot.letter)
        assertEquals("C4", snapshot.noteName)
        assertTrue(snapshot.feedback.contains("Play the C key"))
        assertEquals(0f, snapshot.progress)
    }

    @Test
    fun acRun02_playingTheWrongKeySaysWhichKeyWasHeardAndDoesNotAdvance() {
        val runner = CalibrationRunner()
        runner.begin(0)
        val snapshot = play(runner, midi = 64, atFrame = 0)
        assertEquals(60, snapshot.targetMidi, "still waiting for C")
        assertEquals(0, snapshot.samplesDone)
        assertTrue(snapshot.feedback.contains("That was E"), "got: ${snapshot.feedback}")
        assertTrue(snapshot.feedback.contains("play C"), "got: ${snapshot.feedback}")
    }

    @Test
    fun acRun03_oneLongHeldKeyIsOneSampleNotAWholeNote() {
        val runner = CalibrationRunner()
        runner.begin(0)
        // Two seconds of one sustained C — 43 frames.
        var snapshot = runner.snapshot()
        for (i in 0 until 43) {
            snapshot = runner.onFrame(frame(midiToFreq(60), i))
        }
        assertEquals(
            1,
            snapshot.samplesDone,
            "holding one key must not fill the profile with correlated frames",
        )
        assertEquals(60, snapshot.targetMidi, "C is not finished after a single press")
        assertTrue(snapshot.feedback.contains("Let go"), "got: ${snapshot.feedback}")
    }

    @Test
    fun acRun04_fourSeparatePressesFinishTheNoteAndMoveToD() {
        val runner = CalibrationRunner()
        runner.begin(0)
        var snapshot = runner.snapshot()
        var frameIndex = 0
        repeat(4) {
            snapshot = playPress(runner, 60, frameIndex).also { frameIndex += PRESS_FRAMES }
        }
        assertEquals(62, snapshot.targetMidi, "four presses of C moves on to D")
        assertEquals("D", snapshot.letter)
        assertEquals(0, snapshot.samplesDone, "D starts from zero")
        assertEquals(0.2f, snapshot.progress, 1e-6f)
    }

    @Test
    fun acRun05_aWholeSessionProducesASavableProfileWithFiveNotes() {
        val runner = CalibrationRunner()
        runner.microphoneLabel = "VOICE_RECOGNITION"
        runner.begin(0)
        var frameIndex = 0
        var snapshot = runner.snapshot()
        for (midi in MVP_CALIBRATION_MIDI) {
            repeat(4) {
                snapshot = playPress(runner, midi, frameIndex).also { frameIndex += PRESS_FRAMES }
            }
        }
        val profile = snapshot.profile
        assertNotNull(profile, "a completed session must produce a profile")
        assertEquals(setOf(60, 62, 64, 65, 67), profile!!.notes.map { it.midiNote }.toSet())
        assertEquals("VOICE_RECOGNITION", profile.microphoneSource)
        assertEquals(SR, profile.sampleRate)
        assertTrue(profile.usableAsDefault, "quality was ${profile.calibrationQuality}")
        for (note in profile.notes) {
            assertEquals(4, note.sampleCount, "midi ${note.midiNote}")
            assertEquals(
                midiToFreq(note.midiNote),
                note.observedMedianFrequency,
                2.0,
                "midi ${note.midiNote} median is off",
            )
        }
    }

    @Test
    fun acRun06_skippingKeysProducesAProfileThatMayNotBecomeTheDefault() {
        val runner = CalibrationRunner()
        runner.begin(0)
        var snapshot = runner.snapshot()
        repeat(MVP_CALIBRATION_MIDI.size) { snapshot = runner.skip() }
        val profile = snapshot.profile
        assertNotNull(profile)
        assertEquals(CalibrationProfile.Quality.NEEDS_IMPROVEMENT, profile!!.calibrationQuality)
        assertFalse(
            profile.usableAsDefault,
            "spec §11: a skipped-through profile must not silently become the default",
        )
    }

    @Test
    fun acRun07_tooQuietSaysSoInsteadOfSayingNothing() {
        val runner = CalibrationRunner()
        runner.begin(0)
        val snapshot = runner.onFrame(AudioFrame(FloatArray(2048), SR, 0))
        assertTrue(snapshot.feedback.contains("Too quiet"), "got: ${snapshot.feedback}")
        assertEquals(60, snapshot.targetMidi)
    }

    @Test
    fun acRun08_aMicThatSendsNothingIsReportedByTheClock() {
        val runner = CalibrationRunner()
        runner.begin(0)
        assertNull(runner.onTick(1_000))
        assertFalse(runner.sourceLooksDead)
        val stalled = runner.onTick(1_600)
        assertNotNull(stalled)
        assertTrue(runner.sourceLooksDead)
        assertTrue(stalled!!.feedback.contains("not sending"), "got: ${stalled.feedback}")
    }

    @Test
    fun acRun09_redoClearsTheProfileAndAsksForCAgain() {
        val runner = CalibrationRunner()
        runner.begin(0)
        repeat(MVP_CALIBRATION_MIDI.size) { runner.skip() }
        assertNotNull(runner.profile)
        val snapshot = runner.restart()
        assertNull(snapshot.profile)
        assertNull(runner.profile)
        assertEquals(60, snapshot.targetMidi)
        assertEquals(0f, snapshot.progress)
    }
}

private const val PRESS_FRAMES = 12

/** One key press: enough frames to lock the note, then silence as it is let go. */
private fun playPress(
    runner: CalibrationRunner,
    midi: Int,
    fromFrame: Int,
): CalibrationRunner.Snapshot {
    var snapshot = runner.snapshot()
    val hz = midiToFreq(midi)
    for (i in 0 until 8) {
        snapshot = runner.onFrame(frame(hz, fromFrame + i))
    }
    for (i in 8 until PRESS_FRAMES) {
        snapshot = runner.onFrame(AudioFrame(FloatArray(2048), SR, (fromFrame + i) * FRAME_MS))
    }
    return snapshot
}

private fun play(runner: CalibrationRunner, midi: Int, atFrame: Int): CalibrationRunner.Snapshot {
    var snapshot = runner.snapshot()
    repeat(8) { i -> snapshot = runner.onFrame(frame(midiToFreq(midi), atFrame + i)) }
    return snapshot
}

private fun frame(hz: Double, index: Int, n: Int = 2048): AudioFrame {
    val tone = pianoTone(hz, SR, 1.0)
    val start = (2000 + index % 8 * 64).coerceAtMost(tone.size - n)
    return AudioFrame(tone.copyOfRange(start, start + n), SR, index * FRAME_MS)
}
