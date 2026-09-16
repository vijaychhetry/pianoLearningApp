package com.vijaychhetry.kidspiano.core.pitch

import com.vijaychhetry.kidspiano.core.common.RecognitionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * AC-LOG-* — the Audio Lab log must never look dead while the mic runs.
 */
class LabEventLogTest {
    @Test
    fun acLog01_firstFrameIsAlwaysLoggedEvenWithNoSignal() {
        val log = LabEventLog()
        assertTrue(log.onFrame(RecognitionStatus.NO_SIGNAL, "—", 0.0, null, 0))
        assertEquals(1, log.entries.size)
        assertEquals(RecognitionStatus.NO_SIGNAL, log.entries.first().status)
    }

    @Test
    fun acLog02_identicalFramesDoNotSpamButHeartbeatProvesLiveness() {
        val log = LabEventLog(heartbeatMs = 2_000)
        log.onFrame(RecognitionStatus.NO_SIGNAL, "—", 0.0, null, 0)
        assertFalse(log.onFrame(RecognitionStatus.NO_SIGNAL, "—", 0.0, null, 500))
        assertFalse(log.onFrame(RecognitionStatus.NO_SIGNAL, "—", 0.0, null, 1_999))
        assertEquals(1, log.entries.size)
        assertTrue(log.onFrame(RecognitionStatus.NO_SIGNAL, "—", 0.0, null, 2_000))
        assertEquals(2, log.entries.size)
    }

    @Test
    fun acLog03_noteChangeIsLoggedImmediately() {
        val log = LabEventLog()
        log.onFrame(RecognitionStatus.HIGH_CONFIDENCE, "C4", 0.9, 261.6, 0)
        assertTrue(log.onFrame(RecognitionStatus.HIGH_CONFIDENCE, "E4", 0.9, 329.6, 120))
        assertEquals(listOf("E4", "C4"), log.entries.map { it.label })
    }

    @Test
    fun acLog04_statusChangeOnSameNoteIsLogged() {
        val log = LabEventLog()
        log.onFrame(RecognitionStatus.NOTE_DETECTED, "C4", 0.6, 261.6, 0)
        assertTrue(log.onFrame(RecognitionStatus.HIGH_CONFIDENCE, "C4", 0.9, 261.6, 60))
        assertEquals(2, log.entries.size)
        assertEquals(RecognitionStatus.HIGH_CONFIDENCE, log.entries.first().status)
    }

    @Test
    fun acLog05_ambiguousAndLowConfidenceAreVisibleNotSwallowed() {
        val log = LabEventLog()
        log.onFrame(RecognitionStatus.HIGH_CONFIDENCE, "C4", 0.9, 261.6, 0)
        log.onFrame(RecognitionStatus.AMBIGUOUS, "C4", 0.9, 269.0, 60)
        log.onFrame(RecognitionStatus.LOW_CONFIDENCE, "C4", 0.3, 262.0, 120)
        assertEquals(
            listOf(
                RecognitionStatus.LOW_CONFIDENCE,
                RecognitionStatus.AMBIGUOUS,
                RecognitionStatus.HIGH_CONFIDENCE,
            ),
            log.entries.map { it.status },
        )
    }

    @Test
    fun acLog06_capacityKeepsNewestLines() {
        val log = LabEventLog(capacity = 3)
        val notes = listOf("C4", "D4", "E4", "F4", "G4")
        notes.forEachIndexed { i, note ->
            log.onFrame(RecognitionStatus.HIGH_CONFIDENCE, note, 0.9, 260.0 + i, i * 100L)
        }
        assertEquals(3, log.entries.size)
        assertEquals(listOf("G4", "F4", "E4"), log.entries.map { it.label })
    }
}

/**
 * AC-MIC-* — a source that initializes but returns silence must be detected.
 */
class SilenceWatchdogTest {
    @Test
    fun acMic01_allSilentFramesReportOnceAfterWindow() {
        val dog = SilenceWatchdog(windowMs = 1_500)
        assertFalse(dog.onFrame(0.0, 0))
        assertFalse(dog.onFrame(0.0, 1_400))
        assertTrue(dog.onFrame(0.0, 1_500))
        assertFalse(dog.onFrame(0.0, 3_000), "must not keep firing once reported")
    }

    @Test
    fun acMic02_realSignalNeverTripsTheWatchdog() {
        val dog = SilenceWatchdog(windowMs = 1_500)
        dog.onFrame(0.0, 0)
        dog.onFrame(0.02, 200)
        assertFalse(dog.onFrame(0.0, 5_000))
        assertFalse(dog.onFrame(0.0, 9_000))
    }

    @Test
    fun acMic03_resetArmsTheNextSource() {
        val dog = SilenceWatchdog(windowMs = 1_000)
        assertFalse(dog.onFrame(0.0, 0))
        assertTrue(dog.onFrame(0.0, 1_000))
        dog.reset()
        assertFalse(dog.onFrame(0.0, 1_100))
        assertTrue(dog.onFrame(0.0, 2_100))
    }
}
