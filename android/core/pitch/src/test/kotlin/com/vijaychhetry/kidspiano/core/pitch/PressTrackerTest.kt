package com.vijaychhetry.kidspiano.core.pitch

import com.vijaychhetry.kidspiano.core.common.AudioFrame
import com.vijaychhetry.kidspiano.core.common.PressKind
import com.vijaychhetry.kidspiano.core.notes.midiToFreq
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val SR = 44100
private const val FRAME_MS = 46L

class PressTrackerTest {
    @Test
    fun acR02_aHeldKeyProducesOnePressEvent() {
        val tracker = PressTracker()
        var events = 0
        var lastKind: PressKind? = null
        repeat(20) { i ->
            val e = tracker.onFrame(tone(60, i))
            if (e != null) {
                events++
                lastKind = e.kind
            }
        }
        assertEquals(1, events)
        assertEquals(PressKind.NOTE, lastKind)
    }

    @Test
    fun acR03_aSecondStrikeAfterReleaseIsASecondPress() {
        val tracker = PressTracker()
        var events = 0
        repeat(10) { tracker.onFrame(tone(60, it)) }
        repeat(8) { i -> tracker.onFrame(AudioFrame(FloatArray(2048), SR, (10 + i) * FRAME_MS)) }
        repeat(10) { i ->
            if (tracker.onFrame(tone(60, 20 + i)) != null) events++
        }
        assertTrue(events >= 1, "second strike produced no event")
    }

    @Test
    fun acR03b_aNewAttackWhileHeldIsASecondPress() {
        val tracker = PressTracker()
        var events = 0
        repeat(10) { if (tracker.onFrame(tone(60, it, 1.0f)) != null) events++ }
        assertEquals(1, events)
        repeat(4) { tracker.onFrame(tone(60, 10 + it, 0.15f)) }
        repeat(10) { i -> if (tracker.onFrame(tone(60, 20 + i, 1.0f)) != null) events++ }
        assertEquals(2, events, "re-press during sustain should emit a second event")
    }

    @Test
    fun acR05_holdingCWhileNothingNewHappensDoesNotEmitAgain() {
        val tracker = PressTracker()
        val first = (0 until 10).map { tracker.onFrame(tone(60, it)) }.firstOrNull { it != null }
        assertNotNull(first)
        val later = (10 until 18).map { tracker.onFrame(tone(60, it)) }
        assertTrue(later.all { it == null })
    }

    @Test
    fun acPress01_silenceEmitsNothing() {
        val tracker = PressTracker()
        assertNull(tracker.onFrame(AudioFrame(FloatArray(2048), SR, 0)))
    }

    @Test
    fun acR06_oneAmbiguousAttackFrameIsNotTwoNotes() {
        val tracker = PressTracker(detector = AlwaysAmbiguous())
        val early = (0 until 2).map { tracker.onFrame(tone(60, it)) }
        assertTrue(
            early.all { it == null || it.kind != PressKind.TWO_NOTES },
            "first messy frames must wait; got $early",
        )
    }

    @Test
    fun acR06b_sustainedTwoNotesStillEmitTwoNotes() {
        val tracker = PressTracker(detector = AlwaysAmbiguous())
        val kinds = (0 until 14).mapNotNull { tracker.onFrame(tone(60, it))?.kind }
        assertTrue(PressKind.TWO_NOTES in kinds, "got $kinds")
    }

    @Test
    fun acR07_secondHarmonicDominantC4EmitsC4NotC5() {
        val tracker = PressTracker()
        var event: com.vijaychhetry.kidspiano.core.common.PressEvent? = null
        repeat(16) { i ->
            if (event == null) event = tracker.onFrame(secondHeavyFrame(60, i))
        }
        assertNotNull(event)
        assertEquals(PressKind.NOTE, event!!.kind, "got $event")
        assertEquals(60, event!!.midi, "C4 with a loud 2nd harmonic must not lock as C5")
    }

    @Test
    fun acR09_heldSustainRippleDoesNotStartASecondPress() {
        val tracker = PressTracker()
        var events = 0
        repeat(10) { if (tracker.onFrame(tone(60, it, 1.0f)) != null) events++ }
        assertEquals(1, events)
        repeat(8) { i ->
            val amp = if (i % 2 == 0) 0.85f else 1.0f
            if (tracker.onFrame(tone(60, 10 + i, amp)) != null) events++
        }
        assertEquals(1, events, "small sustain ripples must not count as a new press")
    }

    private fun tone(midi: Int, index: Int, amplitude: Float = 1.0f): AudioFrame {
        val t = pianoTone(midiToFreq(midi), SR, 1.0, amplitude = amplitude)
        val start = (2000 + index % 8 * 64).coerceAtMost(t.size - 2048)
        return AudioFrame(t.copyOfRange(start, start + 2048), SR, index * FRAME_MS)
    }

    private fun secondHeavyFrame(midi: Int, index: Int): AudioFrame {
        val t = secondHarmonicDominant(midiToFreq(midi), SR, 1.0)
        val start = (2000 + index % 8 * 64).coerceAtMost(t.size - 2048)
        return AudioFrame(t.copyOfRange(start, start + 2048), SR, index * FRAME_MS)
    }
}

private class AlwaysAmbiguous : PitchDetector {
    override fun detect(frame: AudioFrame) = com.vijaychhetry.kidspiano.core.common.PitchResult(
        frequency = null,
        midiNote = null,
        confidence = 0.0,
        clarity = 0.0,
        signalStrength = 0.2,
        stability = 0.0,
        timestampMs = frame.capturedAtMs,
        latencyMs = 1,
        ambiguous = true,
    )
}
