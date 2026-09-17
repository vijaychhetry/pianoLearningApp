package com.vijaychhetry.kidspiano.core.pitch

import com.vijaychhetry.kidspiano.core.common.AudioFrame
import com.vijaychhetry.kidspiano.core.common.RecognitionStatus
import com.vijaychhetry.kidspiano.core.notes.midiToFreq
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val SR = 44100
private const val FRAME_MS = 46L

/**
 * AC-LAB-* — what the Audio Lab screen promises the user (spec §15), driven by
 * the same audio a phone would deliver. The reported bug was a screen that
 * showed nothing at all, so these assert the screen cannot go quiet.
 */
class LabSessionTest {
    @Test
    fun acLab01_silentRoomStillProducesLogLinesAndALevelReading() {
        val session = LabSession()
        session.begin(0)
        var last = session.snapshot
        for (i in 0 until 100) {
            last = session.onFrame(AudioFrame(FloatArray(2048), SR, i * FRAME_MS))
        }
        assertEquals(RecognitionStatus.NO_SIGNAL, last.status)
        assertTrue(last.log.size >= 2, "silence is a reading; the log must not stay empty")
        assertEquals(100, last.frameCount)
        assertTrue(last.hint.contains("quiet", ignoreCase = true))
    }

    @Test
    fun acLab02_heldC4IsLoggedAsAHighConfidenceNote() {
        val session = LabSession()
        session.begin(0)
        var last = session.snapshot
        repeat(10) { i -> last = session.onFrame(frameOf(midiToFreq(60), i)) }
        assertEquals("C4", last.noteName)
        assertEquals(RecognitionStatus.HIGH_CONFIDENCE, last.status)
        assertTrue(last.log.any { it.label == "C4" }, "the note must reach the log")
        assertEquals(midiToFreq(60), last.pitch!!.frequency!!, 2.0)
    }

    @Test
    fun acLab03_levelMovesWithTheRoomEvenWhenNoNoteIsRecognised() {
        val session = LabSession()
        session.begin(0)
        val quiet = session.onFrame(AudioFrame(FloatArray(2048), SR, 0)).level
        val noisy = session.onFrame(noiseFrame(1)).level
        assertTrue(quiet < 0.0001, "digital silence should read as no level")
        assertTrue(noisy > quiet * 10, "the meter must react to sound, not only to notes")
        assertTrue(session.snapshot.peakLevel >= noisy)
    }

    @Test
    fun acLab04_aSourceThatNeverDeliversAFrameIsReportedByTheClock() {
        val session = LabSession()
        session.begin(0)
        assertNull(session.onTick(1_000), "not stalled yet")
        assertFalse(session.sourceLooksDead)
        val stalled = session.onTick(1_600)
        assertNotNull(stalled, "a mic that opens and sends nothing must be reported")
        assertTrue(session.sourceLooksDead)
        assertTrue(stalled!!.log.isNotEmpty(), "the stall itself is a log line")
        assertTrue(stalled.hint.contains("not sending", ignoreCase = true))
    }

    @Test
    fun acLab05_frameArrivalKeepsTheStallDetectorQuiet() {
        val session = LabSession()
        session.begin(0)
        for (i in 0 until 40) {
            session.onFrame(frameOf(midiToFreq(60), i))
            assertNull(session.onTick(i * FRAME_MS + 10), "frames are arriving; nothing is stalled")
        }
        assertFalse(session.sourceLooksDead)
    }

    @Test
    fun acLab06_digitallySilentSourceIsFlaggedForASourceSwitch() {
        val session = LabSession()
        session.begin(0)
        for (i in 0 until 60) {
            session.onFrame(AudioFrame(FloatArray(2048), SR, i * FRAME_MS))
        }
        assertTrue(session.sourceLooksDead, "60 frames of digital silence means the source is dead")
    }

    @Test
    fun acLab07_logSurvivesASourceSwitchSoTheUserCanSeeWhatHappened() {
        val session = LabSession()
        session.begin(0)
        repeat(10) { i -> session.onFrame(frameOf(midiToFreq(60), i)) }
        val before = session.snapshot.log.size
        assertTrue(before > 0)
        session.begin(1_000)
        assertEquals(
            before,
            session.snapshot.log.size,
            "restarting capture on another mic must not wipe the evidence",
        )
        session.clearLog()
        assertTrue(session.snapshot.log.isEmpty(), "only an explicit clear empties the log")
    }

    @Test
    fun acLab08_twoNotesAtOnceAreReportedAsAmbiguousNotGuessed() {
        val session = LabSession()
        session.begin(0)
        val mix = mixTones(
            sineTone(midiToFreq(60), SR, 0.6, amplitude = 0.35f),
            sineTone(midiToFreq(64), SR, 0.6, amplitude = 0.35f),
        )
        var last = session.snapshot
        repeat(5) { last = session.onFrame(sliceOf(mix, it)) }
        assertEquals(RecognitionStatus.AMBIGUOUS, last.status)
        assertTrue(last.hint.contains("more than one note"))
    }
}

/** AC-MICCYCLE-* — recovering from a bad mic source must terminate. */
class MicSourceCyclerTest {
    @Test
    fun acMicCycle01_walksEverySourceExactlyOnceThenGivesUp() {
        val cycler = MicSourceCycler(listOf(1, 2, 3))
        assertEquals(1, cycler.current)
        assertEquals(2, cycler.advance())
        assertEquals(3, cycler.advance())
        assertNull(cycler.advance(), "a muted phone must not be cycled forever")
        assertNull(cycler.advance())
        assertTrue(cycler.exhausted)
    }

    @Test
    fun acMicCycle02_healthyAudioReArmsTheCycle() {
        val cycler = MicSourceCycler(listOf(1, 2, 3))
        cycler.advance()
        cycler.advance()
        assertTrue(cycler.exhausted)
        cycler.reset()
        assertFalse(cycler.exhausted)
        assertEquals(1, cycler.advance(), "wraps on from the third source")
    }

    @Test
    fun acMicCycle03_manualChoiceAlwaysMovesAndReArms() {
        val cycler = MicSourceCycler(listOf(1, 2, 3))
        cycler.advance()
        cycler.advance()
        assertTrue(cycler.exhausted)
        assertEquals(1, cycler.forceAdvance())
        assertFalse(cycler.exhausted, "the user asking by hand restarts the automatic pass")
    }
}

/** AC-STALL-* — an open microphone that sends nothing. */
class FrameArrivalMonitorTest {
    @Test
    fun acStall01_reportsOnceWhenNoFrameEverArrives() {
        val monitor = FrameArrivalMonitor(stallMs = 1_000)
        monitor.start(0)
        assertFalse(monitor.isStalled(999))
        assertTrue(monitor.isStalled(1_000))
        assertFalse(monitor.isStalled(5_000), "one report per stall, not one per tick")
    }

    @Test
    fun acStall02_arrivingFramesKeepItQuietAndReArmIt() {
        val monitor = FrameArrivalMonitor(stallMs = 1_000)
        monitor.start(0)
        monitor.onFrame(900)
        assertFalse(monitor.isStalled(1_500))
        monitor.onFrame(1_600)
        assertFalse(monitor.isStalled(2_000))
        assertTrue(monitor.isStalled(2_600), "frames stopped again, so report again")
    }

    @Test
    fun acStall03_neverFiresBeforeStart() {
        val monitor = FrameArrivalMonitor(stallMs = 1_000)
        assertFalse(monitor.isStalled(10_000))
    }
}

/** AC-METER-* — the bar the user watches. */
class LevelScaleTest {
    @Test
    fun acMeter01_silenceIsEmptyAndLoudIsFull() {
        assertEquals(0f, levelFraction(0.0))
        assertEquals(0f, levelFraction(-1.0))
        assertEquals(1f, levelFraction(1.0))
        assertEquals(1f, levelFraction(4.0), "over-range input is clamped, not wrapped")
    }

    @Test
    fun acMeter02_quietAndStruckKeysAreVisiblyDifferent() {
        val room = levelFraction(0.002)
        val key = levelFraction(0.08)
        assertTrue(room > 0f, "room noise must be visible, not pinned at zero")
        assertTrue(key - room > 0.2f, "a struck key must look clearly louder: $room vs $key")
        assertTrue(key < 1f)
    }

    @Test
    fun acMeter03_isMonotonicAndAlwaysInsideTheBar() {
        var previous = -1f
        for (rms in listOf(0.0, 1e-6, 1e-4, 1e-3, 1e-2, 0.1, 0.5, 1.0)) {
            val f = levelFraction(rms)
            assertTrue(f in 0f..1f, "fraction $f out of range for rms $rms")
            assertTrue(f >= previous, "level must not go down as rms goes up")
            previous = f
        }
    }

    @Test
    fun acMeter04_nanCannotReachCompose() {
        assertEquals(0f, levelFraction(Double.NaN))
        assertEquals(0f, levelFraction(Double.NEGATIVE_INFINITY))
        assertEquals(0f, levelFraction(Double.POSITIVE_INFINITY))
    }
}

private fun frameOf(hz: Double, index: Int): AudioFrame =
    sliceOf(pianoTone(hz, SR, 0.6), index)

private fun sliceOf(tone: FloatArray, index: Int, n: Int = 2048): AudioFrame {
    val start = (2000 + index * 64).coerceAtMost(tone.size - n)
    return AudioFrame(tone.copyOfRange(start, start + n), SR, index * FRAME_MS)
}

private fun noiseFrame(index: Int): AudioFrame {
    val rnd = java.util.Random(index.toLong())
    val samples = FloatArray(2048) { (rnd.nextGaussian() * 0.02).toFloat() }
    return AudioFrame(samples, SR, index * FRAME_MS)
}
